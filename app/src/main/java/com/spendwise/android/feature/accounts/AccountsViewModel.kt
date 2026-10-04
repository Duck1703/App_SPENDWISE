package com.spendwise.android.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.AccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Accounts list (S06). Loads on first composition (cache-first), explicit
 * refresh always hits the network. A 401 never reaches here as UI — the
 * session layer pushes the expired overlay instead.
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<AccountsUiState>(AccountsUiState.Loading)
    val state: StateFlow<AccountsUiState> = _state.asStateFlow()

    init {
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        viewModelScope.launch {
            val current = _state.value
            _state.value = if (current is AccountsUiState.Content) {
                current.copy(isRefreshing = true)
            } else {
                AccountsUiState.Loading
            }
            try {
                val accounts = accountRepository.list(refresh = !initial)
                loadSettled = true
                _state.value = if (accounts.isEmpty()) {
                    AccountsUiState.Empty
                } else {
                    AccountsUiState.Content(
                        accounts = accounts,
                        mixedCurrencies = accounts.map { it.balance.currency }.distinct().size > 1,
                    )
                }
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    // The expired overlay is being pushed over this screen;
                    // the same-user resume re-composition calls onVisible(),
                    // which re-runs the aborted load.
                    expiredMidLoad = !loadSettled
                    (current as? AccountsUiState.Content)
                        ?.let { _state.value = it.copy(isRefreshing = false) }
                    return@launch
                }
                loadSettled = true
                _state.value = AccountsUiState.Error(e.error.toListError())
            }
        }
    }

    private var loadSettled = false
    private var expiredMidLoad = false

    /**
     * Called when the destination re-enters composition — notably when a
     * same-user resume pops the expired overlay back off this screen.
     */
    fun onVisible() {
        if (expiredMidLoad) {
            expiredMidLoad = false
            refresh()
        }
    }

    private fun AppError.toListError(): AccountsError = when (this) {
        AppError.NetworkUnavailable -> AccountsError.Network
        is AppError.Server -> AccountsError.Server
        else -> AccountsError.Generic
    }
}
