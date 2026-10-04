package com.spendwise.android.feature.auth

/**
 * Field-level error kinds. `Server` carries the backend's structured
 * `fields{...}` message verbatim — that channel is designed for display;
 * titles/details/exception text are never shown (§12/§15).
 */
sealed interface AuthFieldError {

    data object Required : AuthFieldError

    data class TooShort(val minLength: Int) : AuthFieldError

    data class Server(val message: String) : AuthFieldError
}

/** Form-level error kinds for auth screens. */
sealed interface AuthFormError {

    data object InvalidCredentials : AuthFormError

    /** 400 without `fields` — duplicate username is indistinguishable (§15 hedged copy). */
    data object UsernamePossiblyTaken : AuthFormError

    data object NetworkUnavailable : AuthFormError

    data object ServerError : AuthFormError

    data object Generic : AuthFormError
}

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val usernameError: AuthFieldError? = null,
    val passwordError: AuthFieldError? = null,
    val formError: AuthFormError? = null,
    val isSubmitting: Boolean = false,
    val showAccountCreatedBanner: Boolean = false,
    val isResume: Boolean = false,
)

data class RegisterUiState(
    val username: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val usernameError: AuthFieldError? = null,
    val passwordError: AuthFieldError? = null,
    val formError: AuthFormError? = null,
    val isSubmitting: Boolean = false,
)
