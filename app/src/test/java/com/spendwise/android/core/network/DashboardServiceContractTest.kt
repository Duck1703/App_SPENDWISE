package com.spendwise.android.core.network

import com.spendwise.android.core.session.Session
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.remote.DashboardService
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
 * Wire-level contract for `GET /api/dashboard?month=YYYY-MM` (M4):
 * exact path, the one real query param format, full-response decode
 * (incl. unknown-key tolerance and the breakdown/accounts/budgets
 * members), BigDecimal precision, bearer propagation, 400 →
 * ApiException, and the authenticated-401 → `Session.Expired` path —
 * all through the REAL interceptors.
 */
class DashboardServiceContractTest {

    private val now: Instant = Instant.parse("2026-10-04T12:00:00Z")

    private lateinit var server: MockWebServer
    private lateinit var sessionManager: SessionManager
    private lateinit var dashboard: DashboardService

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
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        dashboard = retrofit.create(DashboardService::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private suspend fun signIn() =
        sessionManager.onSignedIn("live-token", now.plusSeconds(900), "ana", "u1")

    private fun dashboardBody() = """
        {
          "month":"2026-10",
          "totalIncome":4500.00,
          "totalExpense":1200.50,
          "categoryBreakdown":[
            {"categoryCode":"FOOD","amount":800.50},
            {"categoryCode":"TRANSPORT","amount":400.00}
          ],
          "accounts":[
            {"id":"a1","name":"Everyday","type":"CASH","openingBalance":1000.00,"currency":"VND","balance":4299.50}
          ],
          "budgets":[
            {"budgetId":"b1","category":"FOOD","period":"MONTHLY","limit":1000.00,"currency":"VND","spent":800.50,"remaining":199.50,"percentage":80.05,"status":"WITHIN_BUDGET"}
          ]
        }
    """.trimIndent()

    @Test
    fun `month hits GET api-dashboard with the YYYY-MM param and decodes the full response`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 200, body = dashboardBody()))

        val dto = dashboard.month("2026-10")

        assertEquals("2026-10", dto.month)
        assertEquals(BigDecimal("4500.00"), dto.totalIncome)
        assertEquals(BigDecimal("1200.50"), dto.totalExpense)
        assertEquals(2, dto.categoryBreakdown.size)
        assertEquals("FOOD", dto.categoryBreakdown[0].categoryCode)
        assertEquals(BigDecimal("800.50"), dto.categoryBreakdown[0].amount)
        assertEquals("a1", dto.accounts.single().id)
        assertEquals(BigDecimal("4299.50"), dto.accounts.single().balance)
        val budget = dto.budgets.single()
        assertEquals("b1", budget.budgetId)
        assertEquals("FOOD", budget.category)
        assertEquals(BigDecimal("1000.00"), budget.limit)
        assertEquals("WITHIN_BUDGET", budget.status)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/dashboard", request.url.encodedPath)
        assertEquals("2026-10", request.url.queryParameter("month"))
        assertEquals("Bearer live-token", request.headers["Authorization"])
    }

    @Test
    fun `month sends exactly one query param — nothing invented`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 200, body = dashboardBody()))

        dashboard.month("2026-11")

        val request = server.takeRequest()
        assertEquals(setOf("month"), request.url.queryParameterNames)
        assertEquals("2026-11", request.url.queryParameter("month"))
    }

    @Test
    fun `empty optional members decode when the response is totals-only`() = runTest {
        signIn()
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"month":"2026-10","totalIncome":0,"totalExpense":0,"categoryBreakdown":[],"accounts":[],"budgets":[]}""",
            ),
        )

        val dto = dashboard.month("2026-10")

        assertEquals(BigDecimal("0"), dto.totalIncome)
        assertTrue(dto.categoryBreakdown.isEmpty())
        assertTrue(dto.accounts.isEmpty())
        assertTrue(dto.budgets.isEmpty())
    }

    @Test
    fun `omitted optional members still decode — server variability tolerated`() = runTest {
        signIn()
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"month":"2026-10","totalIncome":10.25,"totalExpense":3.50}""",
            ),
        )

        val dto = dashboard.month("2026-10")

        assertEquals(BigDecimal("10.25"), dto.totalIncome)
        assertEquals(BigDecimal("3.50"), dto.totalExpense)
        assertTrue(dto.budgets.isEmpty())
    }

    @Test
    fun `unknown future fields are tolerated`() = runTest {
        signIn()
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"month":"2026-10","totalIncome":1,"totalExpense":0,"currency":"VND","netFlow":1}""",
            ),
        )

        // A future "currency" field must decode without breaking —
        // and the client never treats it as authoritative anyway.
        assertEquals(BigDecimal("1"), dashboard.month("2026-10").totalIncome)
    }

    @Test
    fun `400 — mixed-currency aggregation — surfaces as ApiException`() = runTest {
        signIn()
        server.enqueue(
            MockResponse(
                code = 400,
                body = """{"type":"about:blank","title":"Request is invalid.","status":400}""",
            ),
        )

        try {
            dashboard.month("2026-10")
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(400, e.status)
            assertTrue(e.fields.isEmpty()) // domain 400 — no fields{}
        }
    }

    @Test
    fun `authenticated 401 expires the session`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 401, body = ""))

        try {
            dashboard.month("2026-10")
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
        }

        assertEquals(Session.Expired("ana", "u1"), sessionManager.session.value)
    }
}
