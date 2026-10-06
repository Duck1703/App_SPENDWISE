package com.spendwise.android.core.network

import java.io.IOException

/**
 * Non-2xx HTTP response, raised by [ErrorInterceptor].
 *
 * Carries the HTTP status plus any structured `fields` map from a
 * problem-detail body. Human-readable `title`/`detail` is retained for
 * diagnostics only — business logic must never parse it (ARCHITECTURE §12).
 */
class ApiException(
    val status: Int,
    val title: String? = null,
    val fields: Map<String, String> = emptyMap(),
    cause: Throwable? = null,
    /**
     * M13: set on a 401 when the session was NOT proven dead — a concurrent
     * renewal already swapped the credential, or a refresh credential still
     * survives. Repositories map this to a retryable error instead of
     * [AppError.SessionExpired].
     */
    val sessionRetryable: Boolean = false,
) : IOException("HTTP $status", cause)
