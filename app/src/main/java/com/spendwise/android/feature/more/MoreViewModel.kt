package com.spendwise.android.feature.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.android.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MoreUiState(
    val isSigningOut: Boolean = false,
)

/**
 * More hub. M1 scope: sign-out — confirmation in the screen, then
 * [AuthRepository.logout] (best-effort remote blacklist + always-clear
 * local session, §13). The session state transition routes to the auth
 * graph; no manual navigation here.
 */
@HiltViewModel
class MoreViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MoreUiState())
    val uiState: StateFlow<MoreUiState> = _uiState.asStateFlow()

    fun signOut() {
        if (_uiState.value.isSigningOut) return
        _uiState.value = MoreUiState(isSigningOut = true)
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}
