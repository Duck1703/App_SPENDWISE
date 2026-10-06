package com.spendwise.android.core.network

import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.core.session.SessionRefresher
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import retrofit2.Invocation
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches `Authorization: Bearer <token>` when a session is signed in
 * (AD-011). Requests that already carry an Authorization header are left
 * untouched; unauthenticated requests pass through unchanged.
 *
 * M13 (REQ-BE-002): when the access token is locally expired but a refresh
 * credential survives, this performs a single-flight renewal BEFORE the
 * request is transmitted — the safe path for non-idempotent mutations,
 * which are then sent exactly once under fresh credentials. A mutation that
 * already transmitted and came back 401 is handled by [TokenAuthenticator]
 * (never replayed).
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
    private val refresher: SessionRefresher? = null,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var token = sessionManager.accessToken()
        if (token == null && request.header(HEADER) == null &&
            refresher != null && sessionManager.renewalToken() != null &&
            !request.isPublicCall()
        ) {
            // Expired access + live refresh credential: renew first so the
            // request — possibly a financial mutation — is sent once, with a
            // token the server will accept. Interceptor threads are blocking
            // contexts; the call is single-flighted inside the refresher.
            refresher.refreshBlocking(expectedStaleToken = null)
            token = sessionManager.accessToken()
        }
        if (token == null || request.header(HEADER) != null) {
            return chain.proceed(request)
        }
        return chain.proceed(
            request.newBuilder()
                .header(HEADER, "Bearer $token")
                .build(),
        )
    }

    /**
     * `@PublicEndpoint` calls are refresh-exempt: login/register have no
     * session to renew, and logout's body carries its own revocation
     * authority — spending a rotation just to sign out could race the
     * very credential being revoked.
     */
    private fun Request.isPublicCall(): Boolean =
        tag(Invocation::class.java)
            ?.method()
            ?.isAnnotationPresent(PublicEndpoint::class.java) == true

    private companion object {
        const val HEADER = "Authorization"
    }
}
