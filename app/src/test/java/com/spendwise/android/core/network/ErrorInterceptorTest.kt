package com.spendwise.android.core.network

import com.spendwise.android.core.session.Session
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.fakes.FakeTokenStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ErrorInterceptorTest {

    private val now: Instant = Instant.parse("2026-10-04T12:00:00Z")

    private lateinit var server: MockWebServer
    private lateinit var sessionManager: SessionManager
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        sessionManager = SessionManager(
            tokenStore = FakeTokenStore(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        client = OkHttpClient.Builder()
            .addInterceptor(ErrorInterceptor(Json { ignoreUnknownKeys = true }, sessionManager))
            .build()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun call() =
        client.newCall(Request.Builder().url(server.url("/")).build()).execute()

    private fun apiExceptionOf(block: () -> Unit): ApiException {
        try {
            block()
        } catch (e: ApiException) {
            return e
        }
        throw AssertionError("Expected ApiException")
    }

    @Test
    fun `2xx response passes through unchanged`() {
        server.enqueue(MockResponse(code = 200, body = "hello"))
        call().use { assertEquals("hello", it.body.string()) }
    }

    @Test
    fun `4xx with problem detail parses structured fields`() {
        server.enqueue(
            MockResponse(
                code = 400,
                body = """{"type":"about:blank","title":"Validation failed","status":400,"fields":{"username":"taken","amount":"required"}}""",
            ),
        )
        val error = apiExceptionOf { call().use {} }

        assertEquals(400, error.status)
        assertEquals("Validation failed", error.title)
        assertEquals(mapOf("username" to "taken", "amount" to "required"), error.fields)
    }

    @Test
    fun `4xx without body yields ApiException with empty fields`() {
        server.enqueue(MockResponse(code = 401, body = ""))
        val error = apiExceptionOf { call().use {} }

        assertEquals(401, error.status)
        assertTrue(error.fields.isEmpty())
    }

    @Test
    fun `non-json error body still yields ApiException`() {
        server.enqueue(MockResponse(code = 503, body = "<html>upstream down</html>"))
        val error = apiExceptionOf { call().use {} }

        assertEquals(503, error.status)
    }

    @Test
    fun `ApiException is an IOException so callers see one failure family`() {
        server.enqueue(MockResponse(code = 404, body = ""))
        try {
            call().use {}
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e is ApiException)
        }
    }

    @Test
    fun `401 on an authenticated call expires the session`() = runTest {
        sessionManager.onSignedIn("tok", now.plusSeconds(900), "ana", "u1")
        server.enqueue(MockResponse(code = 401, body = ""))

        apiExceptionOf { call().use {} }

        // Raw OkHttp requests carry no Retrofit Invocation tag → treated as
        // authenticated; a 401 invalidates the session (§13).
        assertEquals(Session.Expired("ana", "u1"), sessionManager.session.value)
    }

    @Test
    fun `401 while signed out still throws but leaves state SignedOut`() = runTest {
        sessionManager.restore()
        server.enqueue(MockResponse(code = 401, body = ""))

        apiExceptionOf { call().use {} }

        assertEquals(Session.SignedOut, sessionManager.session.value)
    }
}
