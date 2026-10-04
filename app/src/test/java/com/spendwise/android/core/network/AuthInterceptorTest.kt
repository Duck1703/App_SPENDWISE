package com.spendwise.android.core.network

import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.fakes.FakeTokenStore
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AuthInterceptorTest {

    private val now: Instant = Instant.parse("2026-10-04T12:00:00Z")
    private val futureExpiry: Instant = now.plusSeconds(900)

    private lateinit var server: MockWebServer
    private lateinit var sessionManager: SessionManager

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        sessionManager = SessionManager(
            tokenStore = FakeTokenStore(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun client(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(sessionManager))
            .build()

    private fun call(request: Request = Request.Builder().url(server.url("/")).build()) =
        client().newCall(request).execute()

    @Test
    fun `signed-out request carries no Authorization header`() = runTest {
        sessionManager.restore()
        server.enqueue(MockResponse(code = 200, body = "ok"))
        call().use { it.body.string() }

        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `signed-in request carries Bearer token`() = runTest {
        sessionManager.onSignedIn("token-xyz", futureExpiry, null)
        server.enqueue(MockResponse(code = 200, body = "ok"))
        call().use { it.body.string() }

        assertEquals("Bearer token-xyz", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `existing Authorization header is not overwritten`() = runTest {
        sessionManager.onSignedIn("token-xyz", futureExpiry, null)
        server.enqueue(MockResponse(code = 200, body = "ok"))
        val request = Request.Builder()
            .url(server.url("/"))
            .header("Authorization", "Custom scheme")
            .build()
        call(request).use { it.body.string() }

        assertEquals("Custom scheme", server.takeRequest().headers["Authorization"])
    }
}
