package com.spendwise.android.core.network

import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.core.session.SessionRefresher
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M13 (REQ-BE-002): backstop for server-side 401s the local clock missed
 * (revocation, clock skew). Runs inside OkHttp's retry-and-follow-up layer
 * BEFORE the response reaches [ErrorInterceptor].
 *
 * Contract:
 * - Idempotent methods (GET/HEAD) retry exactly once with the renewed token —
 *   the marker header breaks any loop.
 * - Non-idempotent methods are NEVER re-issued: a transmitted mutation whose
 *   response is ambiguous is not ours to replay (INV — renewal must never
 *   create duplicate financial writes). We still renew the credentials so the
 *   *next* user-initiated attempt succeeds, then return null and let the
 *   caller surface the original 401.
 * - Public auth calls are skipped — a login 401 is bad credentials, not a
 *   dead session.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val sessionManager: SessionManager,
    private val refresher: SessionRefresher,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        if (request.header(RETRY_MARKER) != null) return null
        if (request.isPublicCall()) return null

        val staleToken = request.header(AUTH_HEADER)?.removePrefix("Bearer ")?.trim()
        val outcome = refresher.refreshBlocking(expectedStaleToken = staleToken)
        return when (outcome) {
            SessionRefresher.Outcome.Renewed,
            SessionRefresher.Outcome.AlreadyAlive,
            -> when (request.method) {
                "GET", "HEAD" -> {
                    val fresh = sessionManager.accessToken() ?: return null
                    request.newBuilder()
                        .header(AUTH_HEADER, "Bearer $fresh")
                        .header(RETRY_MARKER, "1")
                        .build()
                }
                // Credentials renewed — the mutation is intentionally not
                // replayed; its caller sees the 401 and surfaces a retryable
                // (not session-expired) state.
                else -> null
            }
            else -> null
        }
    }

    private fun Request.isPublicCall(): Boolean =
        tag(retrofit2.Invocation::class.java)
            ?.method()
            ?.isAnnotationPresent(PublicEndpoint::class.java) == true

    private companion object {
        const val AUTH_HEADER = "Authorization"
        const val RETRY_MARKER = "X-Spendwise-Refresh-Retry"
    }
}
