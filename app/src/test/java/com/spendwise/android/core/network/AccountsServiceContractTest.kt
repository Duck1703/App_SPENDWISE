package com.spendwise.android.core.network

import com.spendwise.android.core.session.Session
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.remote.AccountsService
import com.spendwise.android.data.remote.CreateAccountRequestDto
import com.spendwise.android.fakes.FakeTokenStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Wire-level contract for `GET/POST /api/accounts` + `GET /{id}` (M2):
 * paths, request/response DTO shapes, bearer propagation, and the
 * authenticated-401 → `Session.Expired` path through the REAL interceptors.
 */
class AccountsServiceContractTest {

    private val now: Instant = Instant.parse("2026-10-04T12:00:00Z")

    private lateinit var server: MockWebServer
    private lateinit var sessionManager: SessionManager
    private lateinit var service: AccountsService

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
            .create(AccountsService::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private suspend fun signIn() =
        sessionManager.onSignedIn("live-token", now.plusSeconds(900), "ana", "u1")

    @Test
    fun `list sends bearer and parses the account array`() = runTest {
        signIn()
        server.enqueue(
            MockResponse(
                code = 200,
                body = """
                    [
                      {"id":"a1","name":"Wallet","type":"CASH","openingBalance":1000.00,"currency":"VND","balance":1250.50},
                      {"id":"a2","name":"Checking","type":"BANK","openingBalance":0,"currency":"USD","balance":42.10}
                    ]
                """.trimIndent(),
            ),
        )

        val accounts = service.list()

        assertEquals(2, accounts.size)
        assertEquals("a1", accounts[0].id)
        assertEquals("CASH", accounts[0].type)
        assertEquals(BigDecimal("1250.50"), accounts[0].balance)
        assertEquals("VND", accounts[0].currency)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/accounts", request.url.encodedPath)
        assertEquals("Bearer live-token", request.headers["Authorization"])
    }

    @Test
    fun `getById hits the path segment and parses the detail`() = runTest {
        signIn()
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"id":"a9","name":"Savings","type":"BANK","openingBalance":500,"currency":"USD","balance":500}""",
            ),
        )

        val account = service.getById("a9")

        assertEquals("a9", account.id)
        assertEquals(BigDecimal("500"), account.balance)
        val request = server.takeRequest()
        assertEquals("/api/accounts/a9", request.url.encodedPath)
    }

    @Test
    fun `create posts the contract body and parses 201`() = runTest {
        signIn()
        server.enqueue(
            MockResponse(
                code = 201,
                body = """{"id":"new-1","name":"Jar","type":"CASH","openingBalance":1500.50,"currency":"VND","balance":1500.50}""",
            ),
        )

        val created = service.create(
            CreateAccountRequestDto(
                name = "Jar",
                type = "CASH",
                openingBalance = BigDecimal("1500.50"),
                currency = "VND",
            ),
        )

        assertEquals("new-1", created.id)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/accounts", request.url.encodedPath)
        assertEquals("Bearer live-token", request.headers["Authorization"])
        assertEquals(
            """{"name":"Jar","type":"CASH","openingBalance":1500.50,"currency":"VND"}""",
            request.body?.utf8(),
        )
    }

    @Test
    fun `authenticated 401 on list expires the session`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 401, body = ""))

        try {
            service.list()
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
        }

        assertEquals(Session.Expired("ana", "u1"), sessionManager.session.value)
    }

    @Test
    fun `foreign-account 404 maps to ApiException with empty body`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 404, body = ""))

        try {
            service.getById("not-mine")
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(404, e.status)
        }
        assertTrue(sessionManager.session.value is Session.SignedIn)
    }

    @Test
    fun `create validation error maps structured fields`() = runTest {
        signIn()
        server.enqueue(
            MockResponse(
                code = 400,
                body = """{"type":"about:blank","title":"Validation failed","status":400,"fields":{"name":"must not be blank","openingBalance":"must be greater than or equal to 0"}}""",
            ),
        )

        try {
            service.create(
                CreateAccountRequestDto(" ", "CASH", BigDecimal("-1"), "VND"),
            )
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(400, e.status)
            assertEquals("must not be blank", e.fields["name"])
            assertEquals("must be greater than or equal to 0", e.fields["openingBalance"])
        }
    }

    @Test
    fun `list tolerates unknown future fields`() = runTest {
        signIn()
        server.enqueue(
            MockResponse(
                code = 200,
                body = """[{"id":"a1","name":"Wallet","type":"CASH","openingBalance":0,"currency":"VND","balance":0,"futureField":true}]""",
            ),
        )

        val accounts = service.list()

        assertEquals(1, accounts.size)
    }
}
