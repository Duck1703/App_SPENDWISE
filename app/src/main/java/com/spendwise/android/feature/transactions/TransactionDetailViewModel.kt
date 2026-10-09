package com.spendwise.android.feature.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.android.core.model.TransactionType
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.AccountRepository
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Transaction detail (S10): derived from already-loaded list data (no
 * `GET /api/transactions/{id}` exists — BACKEND-CONTRACT §11). The
 * ledger is append-only — the row itself never changes; M20 adds the
 * §21 "offsetting entry" affordance (a new compensating row, not an
 * edit). A session boundary mid-load re-runs on same-user resume via
 * [onVisible].
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val transactionId: String = checkNotNull(savedStateHandle["transactionId"]) {
        "TransactionDetailViewModel requires a transactionId nav argument"
    }

    private val _state = MutableStateFlow<TransactionDetailUiState>(TransactionDetailUiState.Loading)
    val state: StateFlow<TransactionDetailUiState> = _state.asStateFlow()

    init {
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        viewModelScope.launch {
            val current = _state.value
            _state.value = if (current is TransactionDetailUiState.Content) {
                current.copy(isRefreshing = true)
            } else {
                TransactionDetailUiState.Loading
            }
            try {
                val transaction = transactionRepository.findById(transactionId)
                // Account context serves the name label AND the M20
                // offset gate — a lookup failure degrades to a dash and
                // hides the action, never sinks the detail.
                val account = runCatching {
                    accountRepository.findById(transaction.accountId)
                }.getOrNull()
                _state.value = TransactionDetailUiState.Content(
                    transaction = transaction,
                    accountName = account?.name,
                    // PRODUCT §21: offset is INCOME/EXPENSE only — a
                    // TRANSFER_* leg offset would un-pair the atomic
                    // transfer — and never for an archived account, which
                    // M19 keeps out of entry pickers.
                    offsetEligible =
                        (transaction.type == TransactionType.INCOME ||
                            transaction.type == TransactionType.EXPENSE) &&
                            account?.archived == false,
                )
            } catch (e: SessionEndedException) {
                // Dead-generation publish — the session ended between
                // dispatch and response. Same resume contract as the 401
                // path: arm the retry flag and release the pending state
                // so onVisible() re-runs the aborted load.
                expiredMidLoad = true
                (current as? TransactionDetailUiState.Content)
                    ?.let { _state.value = it.copy(isRefreshing = false) }
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    // A load was in flight — resume always re-runs it.
                    expiredMidLoad = true
                    (current as? TransactionDetailUiState.Content)
                        ?.let { _state.value = it.copy(isRefreshing = false) }
                    return@launch
                }
                _state.value = when (e.error) {
                    is AppError.NotFound -> TransactionDetailUiState.NotFound
                    AppError.NetworkUnavailable ->
                        TransactionDetailUiState.Error(TransactionsError.Network)
                    is AppError.Server ->
                        TransactionDetailUiState.Error(TransactionsError.Server)
                    else -> TransactionDetailUiState.Error(TransactionsError.Generic)
                }
            }
        }
    }

    private var expiredMidLoad = false

    /** Re-fired when this destination re-enters composition (resume-pop). */
    fun onVisible() {
        if (expiredMidLoad) {
            expiredMidLoad = false
            refresh()
        }
    }
}
