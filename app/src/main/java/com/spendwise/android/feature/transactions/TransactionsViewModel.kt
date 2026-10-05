package com.spendwise.android.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.android.core.model.TransactionProvenance
import com.spendwise.android.core.model.TransactionType
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.AccountRepository
import com.spendwise.android.data.repository.CategoryRepository
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.data.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Transactions list (S09, PRODUCT §19).
 *
 * Filter model: the three real server params (`accountId`/`type`/
 * `category`) go to `GET /api/transactions`; `Uncategorized` and
 * provenance are honest client-side passes over the fetched list.
 *
 * Race discipline (§52/§53): every load is keyed by a monotonic request
 * id; `flatMapLatest` cancels the superseded call, and [loadSerial]
 * makes an already-completed stale response unable to overwrite newer
 * state — only the latest request may publish.
 *
 * Session boundary: a 401 maps to `Session.Expired` at the interceptor;
 * the overlay pushes over this screen and the same-user resume calls
 * [onVisible], re-running the aborted load.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@dagger.hilt.android.lifecycle.HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<TransactionsUiState>(TransactionsUiState.Loading)
    val state: StateFlow<TransactionsUiState> = _state.asStateFlow()

    private val _options = MutableStateFlow(FilterOptions())
    /** Account + category values behind the filter chips. */
    val options: StateFlow<FilterOptions> = _options.asStateFlow()

    private val query = MutableStateFlow(Query())

    /**
     * The live filter selection — chips read this so a pending load
     * never shows stale "All" chips while the new query is in flight.
     */
    val filters: StateFlow<TransactionFilters> = query
        .map { it.filters }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TransactionFilters())

    /** Monotonic request id — the "latest load" token (§52). */
    private var loadSerial = 0L

    /**
     * A query emission: [nonce] makes refresh() and back-to-identical
     * filters re-fire instead of conflating inside the StateFlow.
     */
    private data class Query(
        val filters: TransactionFilters = TransactionFilters(),
        val nonce: Long = 0L,
        val tick: Long = 0L,
    )

    init {
        loadOptions()
        viewModelScope.launch {
            query
                .map { q -> q.copy(tick = ++loadSerial) }
                .flatMapLatest { q ->
                    flow {
                        markPending()
                        // SessionEndedException (a dead-generation signal)
                        // must NOT propagate — it would cancel this whole
                        // collect coroutine and freeze the pipeline. Drop
                        // the stale result instead: no emit, no publish.
                        // The same-user resume contract still applies —
                        // arm the retry flag and release the pending
                        // state markPending() just set, so onVisible()
                        // re-runs the aborted load.
                        val result = try {
                            fetch(q.filters)
                        } catch (e: SessionEndedException) {
                            expiredMidLoad = true
                            (_state.value as? TransactionsUiState.Content)
                                ?.let { _state.value = it.copy(isRefreshing = false) }
                            null
                        }
                        if (result != null) emit(result to q.tick)
                    }
                }
                .collect { (result, tick) ->
                    // A stale response — one superseded by a newer query
                    // before it published — never replaces newer content.
                    if (tick == loadSerial) _state.value = result
                }
        }
        // A successful create reloads the current view — the new entry
        // appears whether or not this screen is on top right now.
        viewModelScope.launch {
            transactionRepository.mutations.collect { refresh() }
        }
    }

    private suspend fun markPending() {
        val current = _state.value
        _state.value = if (current is TransactionsUiState.Content) {
            current.copy(isRefreshing = true, refreshError = null)
        } else {
            TransactionsUiState.Loading
        }
    }

    private suspend fun fetch(
        filters: TransactionFilters,
    ): TransactionsUiState = try {
        val all = transactionRepository.list(
            accountId = filters.accountId,
            type = filters.type,
            category = filters.serverCategory,
        )
        val displayed = all.filter { transaction ->
            (filters.category != CategorySelection.Uncategorized || transaction.category == null) &&
                (filters.provenance == null || transaction.provenance == filters.provenance)
        }
        if (displayed.isEmpty() && all.isEmpty() && filters.isEmpty) {
            TransactionsUiState.Empty
        } else {
            TransactionsUiState.Content(
                items = displayed,
                accountNames = accountNames(),
                filters = filters,
            )
        }
    } catch (e: AppErrorException) {
        if (e.error is AppError.SessionExpired) {
            // A load was provably in flight — even from a mid-refresh or
            // the trailing account-name lookup — so same-user resume
            // always re-runs it via onVisible().
            expiredMidLoad = true
            (_state.value as? TransactionsUiState.Content)
                ?.let { it.copy(isRefreshing = false) }
                ?: TransactionsUiState.Loading
        } else {
            (_state.value as? TransactionsUiState.Content)
                ?.copy(isRefreshing = false, refreshError = e.error.toListError())
                ?: TransactionsUiState.Error(e.error.toListError())
        }
    }

    /**
     * Row labels resolve from the session-scoped account list. A name
     * fetch failure must not sink loaded financial rows — names degrade
     * to a neutral "Account" label (never fabricated) while session
     * expiry still propagates so the overlay can push.
     */
    private suspend fun accountNames(): Map<String, String> =
        try {
            accountRepository.list().associate { it.id to it.name }
        } catch (e: AppErrorException) {
            if (e.error is AppError.SessionExpired) throw e
            emptyMap()
        }

    private fun loadOptions() {
        viewModelScope.launch {
            val accounts = try {
                accountRepository.list()
            } catch (e: SessionEndedException) {
                // Dead-generation write — resume reloads via onVisible.
                optionsExpired = true
                return@launch
            } catch (e: AppErrorException) {
                // Expiry → overlay owns it; same-user resume reloads via
                // onVisible. Other failures degrade to empty options.
                if (e.error is AppError.SessionExpired) {
                    optionsExpired = true
                    return@launch
                }
                emptyList()
            }
            val categories = try {
                categoryRepository.list()
            } catch (e: SessionEndedException) {
                optionsExpired = true
                return@launch
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    optionsExpired = true
                    return@launch
                }
                emptyList()
            }
            _options.value = FilterOptions(accounts = accounts, categories = categories)
        }
    }

    fun refresh() {
        loadOptions()
        query.value = query.value.copy(nonce = query.value.nonce + 1)
    }

    fun onAccountFilter(accountId: String?) = setFilters { copy(accountId = accountId) }
    fun onTypeFilter(type: TransactionType?) = setFilters { copy(type = type) }
    fun onCategoryFilter(category: CategorySelection) = setFilters { copy(category = category) }
    fun onProvenanceFilter(provenance: TransactionProvenance?) =
        setFilters { copy(provenance = provenance) }

    fun clearFilters() = setFilters { TransactionFilters() }

    private fun setFilters(transform: TransactionFilters.() -> TransactionFilters) {
        query.value = query.value.copy(filters = query.value.filters.transform())
    }

    private var expiredMidLoad = false
    private var optionsExpired = false

    /** Re-runs an aborted load after a same-user resume pops the overlay. */
    fun onVisible() {
        if (expiredMidLoad) {
            expiredMidLoad = false
            optionsExpired = false
            refresh() // also reloads the filter options
        } else if (optionsExpired) {
            optionsExpired = false
            loadOptions()
        }
    }

    private fun AppError.toListError(): TransactionsError = when (this) {
        AppError.NetworkUnavailable -> TransactionsError.Network
        is AppError.Server -> TransactionsError.Server
        else -> TransactionsError.Generic
    }
}
