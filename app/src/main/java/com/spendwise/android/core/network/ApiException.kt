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
) : IOException("HTTP $status", cause)
