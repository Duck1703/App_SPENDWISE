package com.spendwise.android.core.network

/**
 * Canonical error surface for repositories → ViewModels (AD-012).
 *
 * Mapping is driven by HTTP status and structured `fields` only — never by
 * parsing human-readable message text (INV, ARCHITECTURE §12).
 */
sealed interface AppError {

    data object NetworkUnavailable : AppError

    data object SessionExpired : AppError

    /**
     * M13: an authenticated call returned 401 but the session is alive —
     * a concurrent renewal already swapped the credential, or a refresh
     * token still survives (the renewal may have hit a transient failure).
     * Safe to retry; the user is not signed out. ViewModels surface the
     * ordinary generic/retry path (falls into `else` branches).
     */
    data object SessionRetryable : AppError

    /** Auth-endpoint failure (e.g. invalid credentials), distinct from expiry. */
    data object InvalidCredentials : AppError

    data class Validation(val fields: Map<String, String>) : AppError

    data object BadRequest : AppError

    data object NotFound : AppError

    data object Conflict : AppError

    data class Server(val status: Int) : AppError

    data class Unknown(val cause: Throwable?) : AppError
}

/**
 * Thrown by repositories so ViewModels catch a single typed failure family
 * (AD-016: throw AppError-typed, catch in VM).
 */
class AppErrorException(
    val error: AppError,
    cause: Throwable? = null,
) : Exception("AppError: $error", cause)

/**
 * Default mapping for **authenticated** calls: a 401 here means the session
 * died (SessionExpired). Auth endpoints (login/register) inspect the
 * [ApiException] themselves and must not route their 401 through this
 * function — a login 401 is [AppError.InvalidCredentials], not expiry.
 */
fun ApiException.toAppError(): AppError = when {
    status == 400 && fields.isNotEmpty() -> AppError.Validation(fields)
    status == 400 -> AppError.BadRequest
    status == 401 && sessionRetryable -> AppError.SessionRetryable
    status == 401 -> AppError.SessionExpired
    status == 404 -> AppError.NotFound
    status == 409 -> AppError.Conflict
    status in 500..599 -> AppError.Server(status)
    else -> AppError.Unknown(this)
}
