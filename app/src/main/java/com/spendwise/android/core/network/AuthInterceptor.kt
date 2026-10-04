package com.spendwise.android.core.network

import com.spendwise.android.core.session.SessionManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches `Authorization: Bearer <token>` when a session is signed in
 * (AD-011). Requests that already carry an Authorization header are left
 * untouched; unauthenticated requests pass through unchanged.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = sessionManager.accessToken()
        if (token == null || request.header(HEADER) != null) {
            return chain.proceed(request)
        }
        return chain.proceed(
            request.newBuilder()
                .header(HEADER, "Bearer $token")
                .build(),
        )
    }

    private companion object {
        const val HEADER = "Authorization"
    }
}
