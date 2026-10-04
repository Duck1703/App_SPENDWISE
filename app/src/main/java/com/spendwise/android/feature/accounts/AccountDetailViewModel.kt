package com.spendwise.android.feature.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.AccountRepository
import com.spendwise.android.navigation.AccountDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Account detail (S08) — M2 header scope: hero, identity, timestamps. The
 * transaction-history section arrives with M3; the screen deliberately
 * shows no placeholder for it.
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class AccountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
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
                loadSettled = true
                _state.value = AccountDetailUiState.Content(account)
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    // Expired overlay is being pushed; onVisible() on the
                    // same-user resume re-composition re-runs this load.
                    expiredMidLoad = !loadSettled
                    (current as? AccountDetailUiState.Content)
                        ?.let { _state.value = it.copy(isRefreshing = false) }
                    return@launch
                }
                loadSettled = true
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

    private var loadSettled = false
    private var expiredMidLoad = false

    /** Re-fired when this destination re-enters composition (resume-pop). */
    fun onVisible() {
        if (expiredMidLoad) {
            expiredMidLoad = false
            refresh()
        }
    }
}
