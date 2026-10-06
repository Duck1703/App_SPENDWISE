package com.spendwise.android.core.network

import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.core.session.SessionRefresher
import com.spendwise.android.fakes.FakeTokenStore
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class NetworkModuleTest {

    @Test
    fun `the client never transparently retries — a re-sent POST could duplicate a write`() {
        // EXT-MIN-1: OkHttp's retryOnConnectionFailure default could
        // re-transmit a repeatable request body on a recoverable
        // connection failure — silently duplicating a financial write
        // below every application-level no-retry guarantee. The module
        // must disable it; every failure path surfaces honestly instead.
        val sessionManager = SessionManager(
            tokenStore = FakeTokenStore(),
            clock = Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC),
        )
        val json = Json { ignoreUnknownKeys = true }
        val client = NetworkModule.provideOkHttpClient(
            authInterceptor = AuthInterceptor(sessionManager),
            errorInterceptor = ErrorInterceptor(json, sessionManager),
            tokenAuthenticator = TokenAuthenticator(
                sessionManager,
                SessionRefresher(sessionManager, Clock.systemUTC(), json, "http://localhost/"),
            ),
        )

        assertFalse(client.retryOnConnectionFailure)
    }
}
