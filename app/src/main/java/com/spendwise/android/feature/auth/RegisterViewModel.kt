package com.spendwise.android.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Registration form. The backend returns no token — success emits
 * [Event.Registered] and the screen navigates to login with the username
 * prefilled (§13).
 */
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    sealed interface Event {
        data class Registered(val username: String) : Event
    }

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

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
        val passwordError = when {
            current.password.isBlank() -> AuthFieldError.Required
            current.password.length < MIN_PASSWORD_LENGTH -> AuthFieldError.TooShort(MIN_PASSWORD_LENGTH)
            else -> null
        }
        if (usernameError != null || passwordError != null) {
            _uiState.update { it.copy(usernameError = usernameError, passwordError = passwordError) }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, formError = null) }
        viewModelScope.launch {
            try {
                val user = authRepository.register(current.username.trim(), current.password)
                _uiState.update { it.copy(isSubmitting = false) }
                _events.send(Event.Registered(user.username))
            } catch (e: AppErrorException) {
                applyError(e.error)
            }
        }
    }

    private fun applyError(error: AppError) {
        _uiState.update { state ->
            when (error) {
                is AppError.Validation -> {
                    val unhandled = error.fields.keys - setOf(FIELD_USERNAME, FIELD_PASSWORD)
                    state.copy(
                        isSubmitting = false,
                        usernameError = error.fields[FIELD_USERNAME]?.let(AuthFieldError::Server),
                        passwordError = error.fields[FIELD_PASSWORD]?.let(AuthFieldError::Server),
                        formError = if (unhandled.isNotEmpty()) AuthFormError.Generic else null,
                    )
                }
                // 400 without fields — duplicate username is indistinguishable
                // on the wire (§15): hedged copy, never a false claim.
                AppError.BadRequest, AppError.Conflict ->
                    state.copy(isSubmitting = false, formError = AuthFormError.UsernamePossiblyTaken)
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

        /** Mirrors the verified backend `@Size(min = 8)` contract. */
        const val MIN_PASSWORD_LENGTH = 8
    }
}
