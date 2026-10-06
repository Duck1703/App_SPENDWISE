package com.spendwise.android.core.network

import com.spendwise.android.core.session.SessionManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts every non-2xx response into an [ApiException] carrying the HTTP
 * status and any structured `fields` from a problem-detail body (AD-012).
 * Message text is never parsed for meaning.
 *
 * Session semantics (§13): a 401 on an **authenticated** call (one not
 * marked [PublicEndpoint]) invalidates the session once — the signal is
 * idempotent inside [SessionManager]. Public 401s are auth failures and
 * flow to the caller untouched.
 */
@Singleton
class ErrorInterceptor @Inject constructor(
    private val json: Json,
    private val sessionManager: SessionManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.isSuccessful) return response

        var sessionRetryable = false
        if (response.code == 401 && !chain.request().isPublicCall()) {
            // OkHttp threads are blocking contexts — bridging is intended.
            // M13: the session dies only when the rejected credential is the
            // live one AND no refresh credential survives; a stale/absent
            // bearer after a concurrent renewal is retryable, not fatal.
            val rejectedToken = chain.request().header("Authorization")
                ?.removePrefix("Bearer ")?.trim()
            sessionRetryable = runBlocking {
                sessionManager.onRequestRejected(rejectedToken)
            }
        }

        val body = runCatching { response.peekBody(MAX_BODY_PEEK).string() }.getOrNull()
        val problem = parseProblemDetail(body)
        response.close()
        throw ApiException(
            status = response.code,
            title = problem?.title,
            fields = problem?.fields.orEmpty(),
            sessionRetryable = sessionRetryable,
        )
    }

    private fun okhttp3.Request.isPublicCall(): Boolean =
        tag(Invocation::class.java)
            ?.method()
            ?.isAnnotationPresent(PublicEndpoint::class.java) == true

    private fun parseProblemDetail(body: String?): ProblemDetailDto? {
        if (body.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<ProblemDetailDto>(body) }.getOrNull()
    }

    private companion object {
        const val MAX_BODY_PEEK = 1024L * 64L
    }
}
