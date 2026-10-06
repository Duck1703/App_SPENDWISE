package com.spendwise.android.core.network

import com.spendwise.android.core.session.Session
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.core.session.SessionRefresher
import com.spendwise.android.data.remote.AuthService
import com.spendwise.android.data.remote.LogoutRequestDto
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
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * M13 (REQ-BE-002): refresh behavior through the REAL production client
 * stack — AuthInterceptor + TokenAuthenticator + ErrorInterceptor — against
 * a MockWebServer. These tests prove the safety invariant end to end:
 * renewal must never duplicate a transmitted financial mutation.
 */
class SessionRefreshContractTest {

    /** Tiny probe service: one idempotent read, one mutation (write). */
    interface ProbeService {
        @GET("api/probe")
        suspend fun read(): Response<Void>

        @POST("api/probe")
        suspend fun write(): Response<Void>
    }

    private val now: Instant = Instant.parse("2026-10-04T12:00:00Z")
    private val future: Instant = now.plusSeconds(900)
    private val past: Instant = now.minusSeconds(60)

    private lateinit var server: MockWebServer
    private lateinit var store: FakeTokenStore
    private lateinit var sessionManager: SessionManager
    private lateinit var retrofit: Retrofit

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = FakeTokenStore()
        sessionManager = SessionManager(
            tokenStore = store,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        val json = Json { ignoreUnknownKeys = true }
        // One shared refresher instance — matching the @Singleton wiring.
        val refresher = SessionRefresher(
            sessionManager = sessionManager,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            json = json,
            apiBaseUrl = server.url("/").toString(),
        )
        val client = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .authenticator(TokenAuthenticator(sessionManager, refresher))
            .addInterceptor(AuthInterceptor(sessionManager, refresher))
            .addInterceptor(ErrorInterceptor(json, sessionManager))
            .build()
        retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun refreshOk() = MockResponse(
        code = 200,
        body = """{"accessToken":"fresh-access","refreshToken":"rt-2","expiresAt":"${now.plusSeconds(900)}"}""",
    )

    @Test
    fun `locally-expired token is renewed before the request is sent — mutation transmitted once`() = runTest {
        val service = retrofit.create(ProbeService::class.java)
        sessionManager.onSignedIn("dead-access", past, "ana", "u1", refreshToken = "rt-1")
        server.enqueue(refreshOk())
        server.enqueue(MockResponse(code = 201, body = ""))

        val response = service.write()

        assertTrue(response.isSuccessful)
        // Exactly two wire calls: the refresh, then the mutation with the
        // NEW bearer. The stale token never hit the wire → the write can
        // never be 401-replayed into a duplicate.
        val refresh = server.takeRequest()
        assertEquals("/api/auth/refresh", refresh.url.encodedPath)
        val write = server.takeRequest()
        assertEquals("Bearer fresh-access", write.headers["Authorization"])
        assertEquals(2, server.requestCount)

        val session = sessionManager.session.value as Session.SignedIn
        assertEquals("fresh-access", session.token)
        assertEquals("rt-2", session.refreshToken)
    }

    @Test
    fun `server-side 401 on a read is renewed and retried exactly once`() = runTest {
        val service = retrofit.create(ProbeService::class.java)
        // Locally "valid" token, server rejects it — the authenticator path.
        sessionManager.onSignedIn("rejected-access", future, "ana", "u1", refreshToken = "rt-1")
        server.enqueue(MockResponse(code = 401, body = "")) // read → 401
        server.enqueue(refreshOk()) // authenticator refresh
        server.enqueue(MockResponse(code = 200, body = "")) // retried read

        val response = service.read()

        assertTrue(response.isSuccessful)
        assertEquals("/api/probe", server.takeRequest().url.encodedPath)
        assertEquals("/api/auth/refresh", server.takeRequest().url.encodedPath)
        val retried = server.takeRequest()
        assertEquals("/api/probe", retried.url.encodedPath)
        assertEquals("Bearer fresh-access", retried.headers["Authorization"])
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `a transmitted mutation that 401s is NEVER replayed — credentials renew but the write surfaces`() = runTest {
        val service = retrofit.create(ProbeService::class.java)
        sessionManager.onSignedIn("rejected-access", future, "ana", "u1", refreshToken = "rt-1")
        server.enqueue(MockResponse(code = 401, body = "")) // mutation → 401
        server.enqueue(refreshOk()) // authenticator renews for NEXT attempt

        val error = try {
            service.write()
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            e
        }

        // THE M13 INVARIANT: exactly one /api/probe hit the wire — the
        // ambiguous 401 is surfaced, not replayed into a duplicate write.
        assertEquals("/api/probe", server.takeRequest().url.encodedPath)
        assertEquals("/api/auth/refresh", server.takeRequest().url.encodedPath)
        assertEquals(2, server.requestCount)

        assertEquals(401, error.status)
        assertEquals(true, error.sessionRetryable)
        assertEquals(AppError.SessionRetryable, error.toAppError())
        // Session alive with fresh credentials — the user's next explicit
        // attempt transmits under the renewed token.
        val session = sessionManager.session.value as Session.SignedIn
        assertEquals("fresh-access", session.token)
    }

    @Test
    fun `refresh rejection after a mutation 401 expires the session definitively`() = runTest {
        val service = retrofit.create(ProbeService::class.java)
        sessionManager.onSignedIn("rejected-access", future, "ana", "u1", refreshToken = "rt-dead")
        server.enqueue(MockResponse(code = 401, body = ""))
        server.enqueue(MockResponse(code = 401, body = "")) // refresh → dead

        val error = try {
            service.write()
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            e
        }

        assertEquals(401, error.status)
        assertEquals(false, error.sessionRetryable)
        assertEquals(AppError.SessionExpired, error.toAppError())
        assertEquals(Session.Expired("ana", "u1"), sessionManager.session.value)
        assertNull(store.refreshToken)
    }

    @Test
    fun `a 401 without any refresh credential expires the session unchanged`() = runTest {
        val service = retrofit.create(ProbeService::class.java)
        // Legacy no-refresh session — M12 semantics preserved exactly.
        sessionManager.onSignedIn("tok", future, "ana", "u1")
        server.enqueue(MockResponse(code = 401, body = ""))

        val error = try {
            service.read()
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            e
        }

        assertEquals(false, error.sessionRetryable)
        assertEquals(Session.Expired("ana", "u1"), sessionManager.session.value)
        assertEquals(1, server.requestCount) // no refresh attempt was made
    }

    @Test
    fun `logout is refresh-exempt — expired access still revokes via the body`() = runTest {
        val auth = retrofit.create(AuthService::class.java)
        sessionManager.onSignedIn("dead-access", past, "ana", "u1", refreshToken = "rt-1")
        server.enqueue(MockResponse(code = 204, body = ""))

        val response = auth.logout(LogoutRequestDto(refreshToken = sessionManager.renewalToken()))

        assertTrue(response.isSuccessful)
        // No renewal was spent on the way out: ONE wire call, body carries
        // the refresh credential (access token was dead locally → not sent).
        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("/api/auth/logout", request.url.encodedPath)
        assertEquals("""{"refreshToken":"rt-1"}""", request.body?.utf8())
    }
}
