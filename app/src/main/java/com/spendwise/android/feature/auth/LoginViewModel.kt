package com.spendwise.android.feature.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Login + re-authentication form (§13).
 *
 * Successful sign-in only mutates [AuthRepository]/session state — the root
 * navigation effect observes the session and decides whether to clear the
 * auth stack (cold login) or pop back to the preserved stack (resume).
 * Credentials are cleared from state on success; a login 401 maps to the
 * inline [AuthFormError.InvalidCredentials], never to session expiry.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LoginUiState(
            username = savedStateHandle.get<String>("prefill").orEmpty(),
            showAccountCreatedBanner = savedStateHandle.get<Boolean>("accountCreated") == true,
            isResume = savedStateHandle.get<Boolean>("resume") == true,
        ),
    )
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onUsernameChanged(value: String) {
        _uiState.update { it.copy(username = value, usernameError = null, formError = null) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, formError = null) }
    }

    fun onTogglePasswordVisibility() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun submit() {
        val current = _uiState.value
        if (current.isSubmitting) return

        val usernameError = if (current.username.isBlank()) AuthFieldError.Required else null
        val passwordError = if (current.password.isBlank()) AuthFieldError.Required else null
        if (usernameError != null || passwordError != null) {
            _uiState.update { it.copy(usernameError = usernameError, passwordError = passwordError) }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, formError = null) }
        viewModelScope.launch {
            try {
                authRepository.login(current.username.trim(), current.password)
                _uiState.update { it.copy(isSubmitting = false, password = "") }
            } catch (e: AppErrorException) {
                applyError(e.error)
            }
        }
    }

    private fun applyError(error: AppError) {
        _uiState.update { state ->
            when (error) {
                AppError.InvalidCredentials ->
                    state.copy(isSubmitting = false, formError = AuthFormError.InvalidCredentials)
                is AppError.Validation -> {
                    val unhandled = error.fields.keys - setOf(FIELD_USERNAME, FIELD_PASSWORD)
                    state.copy(
                        isSubmitting = false,
                        usernameError = error.fields[FIELD_USERNAME]?.let(AuthFieldError::Server),
                        passwordError = error.fields[FIELD_PASSWORD]?.let(AuthFieldError::Server),
                        formError = if (unhandled.isNotEmpty()) AuthFormError.Generic else null,
                    )
                }
                AppError.NetworkUnavailable ->
                    state.copy(isSubmitting = false, formError = AuthFormError.NetworkUnavailable)
                is AppError.Server ->
                    state.copy(isSubmitting = false, formError = AuthFormError.ServerError)
                else -> state.copy(isSubmitting = false, formError = AuthFormError.Generic)
            }
        }
    }

    private companion object {
        const val FIELD_USERNAME = "username"
        const val FIELD_PASSWORD = "password"
    }
}
