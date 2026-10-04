package com.spendwise.android.core.network

import com.spendwise.android.core.session.Session
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.remote.AuthService
import com.spendwise.android.data.remote.LoginRequestDto
import com.spendwise.android.data.remote.RegisterRequestDto
import com.spendwise.android.fakes.FakeTokenStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Wire-level contract against the verified backend API (M1): request paths,
 * bodies, headers, response DTO shapes, and the public-vs-authenticated 401
 * distinction — all through the REAL interceptor stack.
 */
class AuthServiceContractTest {

    private val now: Instant = Instant.parse("2026-10-04T12:00:00Z")

    private lateinit var server: MockWebServer
    private lateinit var sessionManager: SessionManager
    private lateinit var service: AuthService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        sessionManager = SessionManager(
            tokenStore = FakeTokenStore(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        val json = Json { ignoreUnknownKeys = true }
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(sessionManager))
            .addInterceptor(ErrorInterceptor(json, sessionManager))
            .build()
        service = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AuthService::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `login posts contract body and parses the token response`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"accessToken":"jwt-123","expiresAt":"2026-10-04T12:15:00Z"}""",
            ),
        )

        val response = service.login(LoginRequestDto("ana", "secret-pw"))

        assertEquals("jwt-123", response.accessToken)
        assertEquals("2026-10-04T12:15:00Z", response.expiresAt)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/auth/login", request.url.encodedPath)
        assertEquals("""{"username":"ana","password":"secret-pw"}""", request.body?.utf8())
        assertNull(request.headers["Authorization"])
    }

    @Test
    fun `register posts contract body and parses the created user`() = runTest {
        server.enqueue(
            MockResponse(
                code = 201,
                body = """{"id":"9b0b7f0e-1111-4222-8333-444455556666","username":"ana"}""",
            ),
        )

        val response = service.register(RegisterRequestDto("ana", "secret-pw"))

        assertEquals("ana", response.username)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/auth/register", request.url.encodedPath)
        assertEquals("""{"username":"ana","password":"secret-pw"}""", request.body?.utf8())
        assertNull(request.headers["Authorization"])
    }

    @Test
    fun `login 401 yields ApiException and does NOT expire the session`() = runTest {
        sessionManager.onSignedIn("live-token", now.plusSeconds(900), "ana", "u1")
        server.enqueue(
            MockResponse(
                code = 401,
                body = """{"title":"Unauthorized","detail":"Invalid credentials.","status":401}""",
            ),
        )

        try {
            service.login(LoginRequestDto("ana", "wrong"))
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
        }

        // A public-endpoint 401 is an auth failure, never session expiry (§13).
        assertTrue(sessionManager.session.value is Session.SignedIn)
    }

    @Test
    fun `authenticated 401 expires the session`() = runTest {
        sessionManager.onSignedIn("live-token", now.plusSeconds(900), "ana", "u1")
        server.enqueue(MockResponse(code = 401, body = ""))

        try {
            service.logout()
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
        }

        assertEquals(Session.Expired("ana", "u1"), sessionManager.session.value)
    }

    @Test
    fun `logout sends the bearer token and accepts 204`() = runTest {
        sessionManager.onSignedIn("live-token", now.plusSeconds(900), "ana", "u1")
        server.enqueue(MockResponse(code = 204, body = ""))

        val response = service.logout()

        assertTrue(response.isSuccessful)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/auth/logout", request.url.encodedPath)
        assertEquals("Bearer live-token", request.headers["Authorization"])
    }

    @Test
    fun `register validation error maps structured fields`() = runTest {
        server.enqueue(
            MockResponse(
                code = 400,
                body = """{"type":"about:blank","title":"Validation failed","status":400,"fields":{"password":"size must be between 8 and 255"}}""",
            ),
        )

        try {
            service.register(RegisterRequestDto("ana", "short"))
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(400, e.status)
            assertEquals(mapOf("password" to "size must be between 8 and 255"), e.fields)
        }
    }
}
