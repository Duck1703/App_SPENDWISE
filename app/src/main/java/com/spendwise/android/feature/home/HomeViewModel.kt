package com.spendwise.android.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.android.core.model.Account
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.AccountRepository
import com.spendwise.android.data.repository.DashboardRepository
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.data.repository.TransactionRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Home (S05, PRODUCT §17): "How much do I have, and how did this month
 * go?" — three independently honest sections over three live reads.
 *
 * - **Accounts** (`accountRepository.list`) is the spine — its failure
 *   decides Empty vs Error vs Content; every other section degrades
 *   inside Content instead of sinking the screen.
 * - **Hero totals** (`dashboardRepository.month`) run ONLY under a
 *   uniform account currency — the §17 hard rule. Mixed portfolios
 *   skip the call entirely (the product rule is unconditional) and a
 *   400 maps to the same honest [MonthTotals.Unavailable] state.
 * - **Activity** (`transactionRepository.list`, unfiltered) renders
 *   the first rows as returned — no chronology is promised.
 *
 * Mutations reload everything: a transaction create changes balances
 * and totals server-side; an account create changes the carousel.
 * Session boundaries follow the EXT-MAJOR-1 uniform invariant — a 401
 * or dead-generation publish on ANY leg arms [expiredMidLoad] so
 * `onVisible()` on a same-user resume re-runs the aborted load.
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class HomeViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val dashboardRepository: DashboardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load(initial = true)
        viewModelScope.launch {
            transactionRepository.mutations.collect { load(initial = false) }
        }
        viewModelScope.launch {
            accountRepository.mutations.collect { load(initial = false) }
        }
    }

    fun refresh() = load(initial = false)

    /** Hero-section retry — re-runs the totals leg alone. */
    fun retryTotals() {
        val content = _state.value as? HomeUiState.Content ?: return
        _state.value = content.copy(totals = MonthTotals.Loading)
        viewModelScope.launch {
            (_state.value as? HomeUiState.Content)?.let { current ->
                _state.value = current.copy(totals = fetchTotals(current.accounts))
            }
        }
    }

    /** Activity-section retry — re-runs the activity leg alone. */
    fun retryActivity() {
        val content = _state.value as? HomeUiState.Content ?: return
        _state.value = content.copy(activity = ActivityState.Loading)
        viewModelScope.launch {
            (_state.value as? HomeUiState.Content)?.let { current ->
                _state.value = current.copy(activity = fetchActivity())
            }
        }
    }

    /**
     * Monotonic load stamp — only the newest load publishes. A
     * mutation-triggered reload can overtake a parked refresh; without
     * the stamp the older response would clobber the newer one.
     */
    private var loadSeq = 0L

    private fun load(initial: Boolean) {
        val seq = ++loadSeq
        viewModelScope.launch {
            val current = _state.value
            _state.value = if (current is HomeUiState.Content) {
                current.copy(isRefreshing = true, refreshError = null)
            } else {
                HomeUiState.Loading
            }
            try {
                val accounts = accountRepository.list(refresh = !initial)
                if (seq != loadSeq) return@launch // superseded mid-flight
                if (accounts.isEmpty()) {
                    _state.value = HomeUiState.Empty
                    return@launch
                }
                // §17: each section shows its OWN loading state — the
                // carousel stands immediately; totals/activity land as
                // each leg returns instead of gating the whole screen
                // on the slowest section. A refresh keeps last-good
                // figures under the indicator (MIN-1).
                val prior = current as? HomeUiState.Content
                _state.value = HomeUiState.Content(
                    accounts = accounts,
                    totals = prior?.totals ?: MonthTotals.Loading,
                    activity = prior?.activity ?: ActivityState.Loading,
                    isRefreshing = prior != null,
                )
                // Section legs run concurrently — §17 responsiveness:
                // the hero never waits on the activity list or back.
                val totalsDeferred = async { fetchTotals(accounts) }
                val activityDeferred = async { fetchActivity() }
                val totals = totalsDeferred.await()
                if (seq != loadSeq) return@launch // superseded mid-flight
                (_state.value as? HomeUiState.Content)?.let { c ->
                    // A leg failure during refresh preserves the
                    // last-good section under the refresh banner —
                    // content is never replaced by an error.
                    val preserved = totals is MonthTotals.Failed &&
                        (c.totals is MonthTotals.Loaded || c.totals is MonthTotals.Unavailable)
                    _state.value = c.copy(
                        totals = if (preserved) c.totals else totals,
                        refreshError = c.refreshError
                            ?: (totals as? MonthTotals.Failed)?.kind.takeIf { preserved },
                    )
                }
                val activity = activityDeferred.await()
                if (seq != loadSeq) return@launch // superseded mid-flight
                (_state.value as? HomeUiState.Content)?.let { c ->
                    val preserved = activity is ActivityState.Failed &&
                        c.activity is ActivityState.Items
                    _state.value = c.copy(
                        activity = if (preserved) c.activity else activity,
                        isRefreshing = false,
                        refreshError = c.refreshError
                            ?: (activity as? ActivityState.Failed)?.kind.takeIf { preserved },
                    )
                }
            } catch (e: SessionEndedException) {
                // Dead-generation publish — the session ended between
                // dispatch and response. Same resume contract as the
                // 401 path: arm the retry flag and release pending UI.
                expiredMidLoad = true
                (current as? HomeUiState.Content)
                    ?.let { _state.value = it.copy(isRefreshing = false) }
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    // Overlay is being pushed; a load was in flight, so
                    // same-user resume re-runs it via onVisible().
                    expiredMidLoad = true
                    (current as? HomeUiState.Content)
                        ?.let { _state.value = it.copy(isRefreshing = false) }
                    return@launch
                }
                _state.value = (current as? HomeUiState.Content)
                    ?.copy(isRefreshing = false, refreshError = e.error.toHomeError())
                    ?: HomeUiState.Error(e.error.toHomeError())
            }
        }
    }

    /**
     * Uniform currency → fetch + label; mixed → the honest
     * totals-unavailable state with NO dashboard call (the §17 product
     * rule is unconditional); 400 → the same honest state
     * (same-type mixed-currency aggregation). Other failures degrade
     * to a section retry — the hero never sinks the screen.
     */
    private suspend fun fetchTotals(accounts: List<Account>): MonthTotals {
        val label = accounts.map { it.balance.currency }.distinct().singleOrNull() ?: run {
            return MonthTotals.Unavailable
        }
        return try {
            val month = dashboardRepository.month()
            MonthTotals.Loaded(
                income = month.totalIncome,
                expense = month.totalExpense,
                currency = label,
            )
        } catch (e: SessionEndedException) {
            // A load was in flight — arm the full-load retry; the
            // section stays pending until the resume re-runs it.
            expiredMidLoad = true
            MonthTotals.Loading
        } catch (e: AppErrorException) {
            when (e.error) {
                AppError.SessionExpired -> {
                    expiredMidLoad = true
                    MonthTotals.Loading
                }
                AppError.BadRequest -> MonthTotals.Unavailable
                else -> MonthTotals.Failed(e.error.toHomeError())
            }
        }
    }

    /** Activity rows — non-fatal section: failure degrades to retry. */
    private suspend fun fetchActivity(): ActivityState =
        try {
            // Server-paged read (occurredOn DESC, id DESC): only the newest
            // MAX_ACTIVITY_ROWS rows cross the wire, never the whole ledger.
            ActivityState.Items(
                transactionRepository.listPage(page = 0, size = MAX_ACTIVITY_ROWS).items,
            )
        } catch (e: SessionEndedException) {
            expiredMidLoad = true
            ActivityState.Loading
        } catch (e: AppErrorException) {
            if (e.error is AppError.SessionExpired) {
                expiredMidLoad = true
                ActivityState.Loading
            } else {
                ActivityState.Failed(e.error.toHomeError())
            }
        }

    private var expiredMidLoad = false

    /** Re-runs an aborted load after a same-user resume pops the overlay. */
    fun onVisible() {
        if (expiredMidLoad) {
            expiredMidLoad = false
            refresh()
        }
    }

    private fun AppError.toHomeError(): HomeError = when (this) {
        AppError.NetworkUnavailable -> HomeError.Network
        is AppError.Server -> HomeError.Server
        else -> HomeError.Generic
    }

    private companion object {
        const val MAX_ACTIVITY_ROWS = 5
    }
}
