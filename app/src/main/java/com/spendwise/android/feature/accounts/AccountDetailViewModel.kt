package com.spendwise.android.feature.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.AccountRepository
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.data.repository.TransactionRepository
import com.spendwise.android.navigation.AccountDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Account detail (S08): identity + balance header plus the account's
 * transaction history (`?accountId=` — the one server list filter that
 * scopes rows to a single account). History failures are non-fatal: the
 * section renders an inline retry while the header stays put.
 *
 * [TransactionRepository.mutations] reloads both halves — a create
 * finished elsewhere already invalidated the caches, so the server
 * balances and rows come back fresh (INV-012 — never recomputed locally).
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class AccountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    // Typed nav args land under the property name; plain get<> stays
    // unit-testable (toRoute needs android.os.Bundle).
    private val accountId: String = checkNotNull(savedStateHandle["accountId"]) {
        "AccountDetailViewModel requires an accountId nav argument"
    }

    private val _state = MutableStateFlow<AccountDetailUiState>(AccountDetailUiState.Loading)
    val state: StateFlow<AccountDetailUiState> = _state.asStateFlow()

    init {
        load(initial = true)
        viewModelScope.launch {
            transactionRepository.mutations.collect { load(initial = false) }
        }
    }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        viewModelScope.launch {
            val current = _state.value
            _state.value = if (current is AccountDetailUiState.Content) {
                current.copy(isRefreshing = true)
            } else {
                AccountDetailUiState.Loading
            }
            try {
                val account = accountRepository.findById(accountId, refresh = !initial)
                _state.value = AccountDetailUiState.Content(
                    account = account,
                    history = fetchHistory(),
                )
            } catch (e: SessionEndedException) {
                // Dead-generation write — this surface is being torn
                // down, or the overlay/resume path already owns
                // recovery. Unwind this launch quietly.
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    // Expired overlay is being pushed; a load was in
                    // flight, so onVisible() on the same-user resume
                    // re-composition always re-runs it.
                    expiredMidLoad = true
                    (current as? AccountDetailUiState.Content)
                        ?.let { _state.value = it.copy(isRefreshing = false) }
                    return@launch
                }
                _state.value = when (e.error) {
                    is AppError.NotFound -> AccountDetailUiState.NotFound
                    AppError.NetworkUnavailable ->
                        AccountDetailUiState.Error(AccountsError.Network)
                    is AppError.Server ->
                        AccountDetailUiState.Error(AccountsError.Server)
                    else -> AccountDetailUiState.Error(AccountsError.Generic)
                }
            }
        }
    }

    /**
     * The account-scoped list — a failure degrades the section, never
     * the header. A session expiry marks [historyExpired] so same-user
     * resume re-runs it via [onVisible] instead of leaving it spinning.
     */
    private suspend fun fetchHistory(): AccountDetailUiState.AccountHistory =
        try {
            AccountDetailUiState.AccountHistory.Items(
                transactionRepository.list(accountId = accountId),
            )
        } catch (e: AppErrorException) {
            if (e.error is AppError.SessionExpired) {
                historyExpired = true
                AccountDetailUiState.AccountHistory.Loading
            } else {
                AccountDetailUiState.AccountHistory.Error
            }
        }

    /** Inline retry for the history section only — the header is untouched. */
    fun retryHistory() {
        val content = _state.value as? AccountDetailUiState.Content ?: return
        _state.value = content.copy(history = AccountDetailUiState.AccountHistory.Loading)
        viewModelScope.launch {
            (_state.value as? AccountDetailUiState.Content)?.let {
                _state.value = it.copy(history = fetchHistory())
            }
        }
    }

    private var expiredMidLoad = false
    private var historyExpired = false

    /** Re-fired when this destination re-enters composition (resume-pop). */
    fun onVisible() {
        when {
            expiredMidLoad -> {
                expiredMidLoad = false
                historyExpired = false
                refresh()
            }
            historyExpired -> {
                historyExpired = false
                retryHistory()
            }
        }
    }
}
