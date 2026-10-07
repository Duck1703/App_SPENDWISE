package com.spendwise.android.core.network

import com.spendwise.android.core.session.Session
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.core.session.SessionRefresher
import com.spendwise.android.data.remote.AuthService
import com.spendwise.android.data.remote.CreateTransactionRequestDto
import com.spendwise.android.data.remote.CreateTransferRequestDto
import com.spendwise.android.data.remote.LogoutRequestDto
import com.spendwise.android.data.remote.BudgetsService
import com.spendwise.android.data.remote.RecurringService
import com.spendwise.android.data.remote.TransactionsService
import com.spendwise.android.data.remote.TransfersService
import com.spendwise.android.data.remote.UpdateBudgetRequestDto
import com.spendwise.android.data.remote.UpdateRecurringRequestDto
import com.spendwise.android.fakes.FakeTokenStore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    // ── §80: real financial endpoints — the no-replay invariant is proven
    // against the actual TransactionsService/TransfersService, not a probe. ──

    @Test
    fun `create transaction 401 after transmission is never replayed`() = runTest {
        val transactions = retrofit.create(TransactionsService::class.java)
        sessionManager.onSignedIn("rejected-access", future, "ana", "u1", refreshToken = "rt-1")
        server.enqueue(MockResponse(code = 401, body = "")) // the write → 401
        server.enqueue(refreshOk()) // authenticator renews for NEXT attempt only

        try {
            transactions.create(
                CreateTransactionRequestDto(
                    accountId = "acc-1",
                    type = "EXPENSE",
                    amount = java.math.BigDecimal("12.50"),
                    currency = "VND",
                    occurredOn = "2026-10-04",
                ),
            )
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
        }

        // THE INVARIANT on the real financial path: /api/transactions was
        // hit exactly ONCE — no duplicate write is physically possible.
        assertEquals("/api/transactions", server.takeRequest().url.encodedPath)
        assertEquals("/api/auth/refresh", server.takeRequest().url.encodedPath)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `transfer 401 after transmission is never replayed`() = runTest {
        val transfers = retrofit.create(TransfersService::class.java)
        sessionManager.onSignedIn("rejected-access", future, "ana", "u1", refreshToken = "rt-1")
        server.enqueue(MockResponse(code = 401, body = ""))
        server.enqueue(refreshOk())

        try {
            transfers.create(
                CreateTransferRequestDto(
                    sourceAccountId = "acc-1",
                    destinationAccountId = "acc-2",
                    amount = java.math.BigDecimal("500000"),
                    currency = "VND",
                ),
            )
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
        }

        // A duplicate transfer would be a real financial loss — the wire
        // shows exactly one attempt plus the background credential renewal.
        assertEquals("/api/transfers", server.takeRequest().url.encodedPath)
        assertEquals("/api/auth/refresh", server.takeRequest().url.encodedPath)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `expired-access transfer renews first then transmits exactly once`() = runTest {
        val transfers = retrofit.create(TransfersService::class.java)
        sessionManager.onSignedIn("dead-access", past, "ana", "u1", refreshToken = "rt-1")
        server.enqueue(refreshOk())
        server.enqueue(
            MockResponse(
                code = 201,
                body = """{"transferRef":"tref-1","outTransaction":{"id":"o1","accountId":"acc-1","amount":"-500000","currency":"VND","occurredOn":"2026-10-04"},"inTransaction":{"id":"i1","accountId":"acc-2","amount":"500000","currency":"VND","occurredOn":"2026-10-04"}}""",
            ),
        )

        transfers.create(
            CreateTransferRequestDto(
                sourceAccountId = "acc-1",
                destinationAccountId = "acc-2",
                amount = java.math.BigDecimal("500000"),
                currency = "VND",
            ),
        )

        // Pre-send renewal: exactly 1 refresh + exactly 1 transfer — the
        // stale bearer never transmitted, so no replay hazard exists.
        assertEquals("/api/auth/refresh", server.takeRequest().url.encodedPath)
        val write = server.takeRequest()
        assertEquals("/api/transfers", write.url.encodedPath)
        assertEquals("Bearer fresh-access", write.headers["Authorization"])
        assertEquals(2, server.requestCount)
    }

    // ── M14 (REQ-BE-011): budget lifecycle mutations are non-idempotent
    // methods — the authenticator must never replay them either. ──

    @Test
    fun `budget PATCH 401 after transmission is never replayed`() = runTest {
        val budgets = retrofit.create(BudgetsService::class.java)
        sessionManager.onSignedIn("rejected-access", future, "ana", "u1", refreshToken = "rt-1")
        server.enqueue(MockResponse(code = 401, body = ""))
        server.enqueue(refreshOk())

        try {
            budgets.update("b1", UpdateBudgetRequestDto(limit = java.math.BigDecimal("750")))
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
        }

        // Exactly one PATCH hit the wire; credentials renew for the NEXT
        // attempt only — a patched budget can never be double-applied.
        val write = server.takeRequest()
        assertEquals("/api/budgets/b1", write.url.encodedPath)
        assertEquals("PATCH", write.method)
        assertEquals("/api/auth/refresh", server.takeRequest().url.encodedPath)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `budget DELETE 401 after transmission is never replayed`() = runTest {
        val budgets = retrofit.create(BudgetsService::class.java)
        sessionManager.onSignedIn("rejected-access", future, "ana", "u1", refreshToken = "rt-1")
        server.enqueue(MockResponse(code = 401, body = ""))
        server.enqueue(refreshOk())

        try {
            budgets.delete("b1")
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
        }

        // A second DELETE would be a phantom 404 — the wire shows the
        // single transmitted attempt plus the renewal.
        assertEquals("/api/budgets/b1", server.takeRequest().url.encodedPath)
        assertEquals("/api/auth/refresh", server.takeRequest().url.encodedPath)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `locally-expired token renews before exactly one budget PATCH`() = runTest {
        val budgets = retrofit.create(BudgetsService::class.java)
        sessionManager.onSignedIn("dead-access", past, "ana", "u1", refreshToken = "rt-1")
        server.enqueue(refreshOk())
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"id":"b1","category":"FOOD","period":"2026-11","limit":750.00,"currency":"VND"}""",
            ),
        )

        budgets.update("b1", UpdateBudgetRequestDto(period = "2026-11"))

        // Refresh first, then exactly ONE PATCH bearing the fresh
        // credential — the stale token never hit the wire.
        assertEquals("/api/auth/refresh", server.takeRequest().url.encodedPath)
        val write = server.takeRequest()
        assertEquals("/api/budgets/b1", write.url.encodedPath)
        assertEquals("PATCH", write.method)
        assertEquals("Bearer fresh-access", write.headers["Authorization"])
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `recurring PATCH 401 after transmission is never replayed`() = runTest {
        val recurring = retrofit.create(RecurringService::class.java)
        sessionManager.onSignedIn("rejected-access", future, "ana", "u1", refreshToken = "rt-1")
        server.enqueue(MockResponse(code = 401, body = ""))
        server.enqueue(refreshOk())

        try {
            recurring.update(
                "r1",
                UpdateRecurringRequestDto(amount = java.math.BigDecimal("950.50")),
            )
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
        }

        // Exactly one PATCH hit the wire; credentials renew for the NEXT
        // attempt only — a template edit can never be double-applied.
        val write = server.takeRequest()
        assertEquals("/api/recurring-transactions/r1", write.url.encodedPath)
        assertEquals("PATCH", write.method)
        assertEquals("/api/auth/refresh", server.takeRequest().url.encodedPath)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `locally-expired token renews before exactly one recurring PATCH`() = runTest {
        val recurring = retrofit.create(RecurringService::class.java)
        sessionManager.onSignedIn("dead-access", past, "ana", "u1", refreshToken = "rt-1")
        server.enqueue(refreshOk())
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"id":"r1","accountId":"a1","direction":"EXPENSE","amount":950.50,"currency":"VND","category":"HOUSING","frequency":"MONTHLY","nextDueDate":"2026-11-01","endDate":null,"active":true}""",
            ),
        )

        recurring.update("r1", UpdateRecurringRequestDto(amount = java.math.BigDecimal("950.50")))

        // Refresh first, then exactly ONE PATCH bearing the fresh
        // credential — the stale token never hit the wire.
        assertEquals("/api/auth/refresh", server.takeRequest().url.encodedPath)
        val write = server.takeRequest()
        assertEquals("/api/recurring-transactions/r1", write.url.encodedPath)
        assertEquals("PATCH", write.method)
        assertEquals("Bearer fresh-access", write.headers["Authorization"])
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `locally-expired token renews before exactly one budget DELETE`() = runTest {
        val budgets = retrofit.create(BudgetsService::class.java)
        sessionManager.onSignedIn("dead-access", past, "ana", "u1", refreshToken = "rt-1")
        server.enqueue(refreshOk())
        server.enqueue(MockResponse(code = 204, body = ""))

        budgets.delete("b1")

        assertEquals("/api/auth/refresh", server.takeRequest().url.encodedPath)
        val write = server.takeRequest()
        assertEquals("/api/budgets/b1", write.url.encodedPath)
        assertEquals("DELETE", write.method)
        assertEquals("Bearer fresh-access", write.headers["Authorization"])
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `concurrent GETs that all 401 collapse into a single refresh`() = runTest {
        val probe = retrofit.create(ProbeService::class.java)
        sessionManager.onSignedIn("rejected-access", future, "ana", "u1", refreshToken = "rt-1")
        // FIFO queue matches any interleaving: both initial reads consume
        // the two 401s, the single-flight refresh consumes the 200 rotation,
        // the two retries consume the two successes.
        server.enqueue(MockResponse(code = 401, body = ""))
        server.enqueue(MockResponse(code = 401, body = ""))
        server.enqueue(refreshOk())
        server.enqueue(MockResponse(code = 200, body = ""))
        server.enqueue(MockResponse(code = 200, body = ""))

        val (r1, r2) = coroutineScope {
            val first = async { kotlin.runCatching { probe.read() } }
            val second = async { kotlin.runCatching { probe.read() } }
            first.await() to second.await()
        }

        assertTrue(r1.isSuccess)
        assertTrue(r2.isSuccess)
        // THE STORM INVARIANT: 5 wire calls total, exactly ONE refresh.
        assertEquals(5, server.requestCount)
        val refreshCalls = (1..server.requestCount)
            .map { server.takeRequest() }
            .count { it.url.encodedPath == "/api/auth/refresh" }
        assertEquals("401 storm must produce exactly one refresh call", 1, refreshCalls)
    }
}
