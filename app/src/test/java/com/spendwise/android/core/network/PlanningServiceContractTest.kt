package com.spendwise.android.core.network

import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.remote.BudgetsService
import com.spendwise.android.data.remote.CreateBudgetRequestDto
import com.spendwise.android.data.remote.CreateRecurringRequestDto
import com.spendwise.android.data.remote.CreateRuleRequestDto
import com.spendwise.android.data.remote.RecurringService
import com.spendwise.android.data.remote.RulesService
import com.spendwise.android.data.remote.UpdateBudgetRequestDto
import com.spendwise.android.fakes.FakeTokenStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Wire-level contracts for the three M6 planning endpoints (verified
 * against `BudgetController`, `RecurringTransactionController`,
 * `CategorizationRuleController` at backend `8194daa`): exact paths,
 * request bodies, response decode incl. unknown keys, 400+fields /
 * 404 / 409 → ApiException, DELETE 200+body soft-delete, and the
 * authenticated-401 → `Session.Expired` path — through the REAL
 * interceptors.
 */
class PlanningServiceContractTest {

    private val now: Instant = Instant.parse("2026-10-04T12:00:00Z")

    private lateinit var server: MockWebServer
    private lateinit var sessionManager: SessionManager
    private lateinit var budgets: BudgetsService
    private lateinit var recurring: RecurringService
    private lateinit var rules: RulesService

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
        budgets = retrofit.create(BudgetsService::class.java)
        recurring = retrofit.create(RecurringService::class.java)
        rules = retrofit.create(RulesService::class.java)
    }

    @After
    fun tearDown() = server.close()

    private suspend fun signIn() =
        sessionManager.onSignedIn("live-token", now.plusSeconds(900), "ana", "u1")

    private fun jsonResponse(code: Int, body: String): MockResponse =
        MockResponse.Builder()
            .code(code)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()

    // ── Budgets ─────────────────────────────────────────────────────

    @Test
    fun `budget list decodes and auths`() = runTest {
        signIn()
        server.enqueue(
            jsonResponse(
                200,
                """[{"id":"b1","category":"FOOD","period":"2026-10","limit":500.00,"currency":"VND","serverExtra":true}]""",
            ),
        )
        val list = budgets.list()
        val request = server.takeRequest()
        assertEquals("/api/budgets", request.target)
        assertEquals("GET", request.method)
        assertEquals("Bearer live-token", request.headers["Authorization"])
        assertEquals("FOOD", list.single().category)
        assertEquals(BigDecimal("500.00"), list.single().limit)
        assertEquals("VND", list.single().currency)
    }

    @Test
    fun `budget create sends exact body and decodes 201`() = runTest {
        signIn()
        server.enqueue(
            jsonResponse(
                201,
                """{"id":"b9","category":"TRANSPORT","period":"2026-11","limit":250.5,"currency":"USD"}""",
            ),
        )
        val created = budgets.create(
            CreateBudgetRequestDto(
                category = "TRANSPORT",
                period = "2026-11",
                limit = BigDecimal("250.50"),
                currency = "USD",
            ),
        )
        val request = server.takeRequest()
        assertEquals("/api/budgets", request.target)
        assertEquals("POST", request.method)
        val body = request.body?.utf8().orEmpty()
        assertTrue(body.contains("\"category\":\"TRANSPORT\""))
        assertTrue(body.contains("\"period\":\"2026-11\""))
        assertTrue(body.contains("\"limit\":250.5"))
        assertTrue(body.contains("\"currency\":\"USD\""))
        assertFalse(body.contains("id"))
        assertEquals("b9", created.id)
        assertEquals(BigDecimal("250.5"), created.limit)
    }

    @Test
    fun `budget calculation decodes server figures`() = runTest {
        signIn()
        server.enqueue(
            jsonResponse(
                200,
                """{"budgetId":"b1","category":"FOOD","period":"2026-10","limit":500,"currency":"VND","spent":325.75,"remaining":174.25,"percentage":65.15,"status":"WITHIN_BUDGET","future":null}""",
            ),
        )
        val calc = budgets.calculation("b1")
        assertEquals("/api/budgets/b1/calculation", server.takeRequest().target)
        assertEquals(BigDecimal("325.75"), calc.spent)
        assertEquals(BigDecimal("174.25"), calc.remaining)
        assertEquals(BigDecimal("65.15"), calc.percentage)
        assertEquals("WITHIN_BUDGET", calc.status)
    }

    @Test
    fun `budget 409 surfaces as ApiException conflict`() = runTest {
        signIn()
        server.enqueue(
            jsonResponse(
                409,
                """{"title":"Conflict","status":409,"detail":"The request conflicts with an existing record."}""",
            ),
        )
        try {
            budgets.create(CreateBudgetRequestDto("FOOD", "2026-10", BigDecimal.ONE, "VND"))
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(409, e.status)
            assertEquals(AppError.Conflict, e.toAppError())
        }
    }

    @Test
    fun `budget 400 fields decode as validation`() = runTest {
        signIn()
        server.enqueue(
            jsonResponse(
                400,
                """{"title":"Invalid request","status":400,"detail":"Request is invalid.","fields":{"limit":"must be greater than 0"}}""",
            ),
        )
        try {
            budgets.create(CreateBudgetRequestDto("FOOD", "2026-10", BigDecimal.ZERO, "VND"))
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(400, e.status)
            assertEquals(
                mapOf("limit" to "must be greater than 0"),
                (e.toAppError() as AppError.Validation).fields,
            )
        }
    }

    // ── Budget lifecycle (M14 / REQ-BE-011) ─────────────────────────

    @Test
    fun `budget PATCH sends only the provided fields`() = runTest {
        signIn()
        server.enqueue(
            jsonResponse(
                200,
                """{"id":"b1","category":"FOOD","period":"2026-11","limit":750.00,"currency":"VND"}""",
            ),
        )
        val updated = budgets.update("b1", UpdateBudgetRequestDto(period = "2026-11"))
        val request = server.takeRequest()
        assertEquals("/api/budgets/b1", request.target)
        assertEquals("PATCH", request.method)
        assertEquals("Bearer live-token", request.headers["Authorization"])
        val body = request.body?.utf8().orEmpty()
        assertTrue(body.contains("\"period\":\"2026-11\""))
        // Absent fields stay off the wire — a true partial update, not
        // "limit":null (which the server would read as an explicit null).
        assertFalse("absent limit must be omitted", body.contains("limit"))
        assertEquals(BigDecimal("750.00"), updated.limit)
        assertEquals("2026-11", updated.period)
    }

    @Test
    fun `budget PATCH with both fields sends both`() = runTest {
        signIn()
        server.enqueue(
            jsonResponse(
                200,
                """{"id":"b1","category":"FOOD","period":"2026-12","limit":750.00,"currency":"VND"}""",
            ),
        )
        budgets.update("b1", UpdateBudgetRequestDto(limit = BigDecimal("750.00"), period = "2026-12"))
        val body = server.takeRequest().body?.utf8().orEmpty()
        assertTrue(body.contains("\"limit\":750"))
        assertTrue(body.contains("\"period\":\"2026-12\""))
        // Immutable fields are never sent.
        assertFalse(body.contains("category"))
        assertFalse(body.contains("currency"))
    }

    @Test
    fun `budget PATCH collision surfaces as Conflict`() = runTest {
        signIn()
        server.enqueue(
            jsonResponse(
                409,
                """{"title":"Conflict","status":409,"detail":"The request conflicts with an existing record."}""",
            ),
        )
        try {
            budgets.update("b1", UpdateBudgetRequestDto(period = "2026-10"))
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(409, e.status)
            assertEquals(AppError.Conflict, e.toAppError())
        }
    }

    @Test
    fun `budget PATCH foreign id surfaces as 404`() = runTest {
        signIn()
        server.enqueue(jsonResponse(404, """{"title":"Not found","status":404}"""))
        try {
            budgets.update("foreign", UpdateBudgetRequestDto(limit = BigDecimal.ONE))
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(404, e.status)
            assertEquals(AppError.NotFound, e.toAppError())
        }
    }

    @Test
    fun `budget DELETE hits the exact path and returns 204`() = runTest {
        signIn()
        server.enqueue(jsonResponse(204, ""))
        budgets.delete("b1")
        val request = server.takeRequest()
        assertEquals("/api/budgets/b1", request.target)
        assertEquals("DELETE", request.method)
        assertEquals("Bearer live-token", request.headers["Authorization"])
    }

    @Test
    fun `budget DELETE on an already-deleted id surfaces 404`() = runTest {
        signIn()
        server.enqueue(jsonResponse(404, """{"title":"Not found","status":404}"""))
        try {
            budgets.delete("b1")
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(404, e.status)
            assertEquals(AppError.NotFound, e.toAppError())
        }
    }

    // ── Recurring ───────────────────────────────────────────────────

    @Test
    fun `recurring list decodes templates incl null optionals`() = runTest {
        signIn()
        server.enqueue(
            jsonResponse(
                200,
                """[{"id":"r1","accountId":"a1","direction":"EXPENSE","amount":1200.00,"currency":"VND","category":"HOUSING","frequency":"MONTHLY","nextDueDate":"2026-11-01","endDate":null,"active":true}]""",
            ),
        )
        val list = recurring.list()
        val request = server.takeRequest()
        assertEquals("/api/recurring-transactions", request.target)
        assertEquals("GET", request.method)
        assertEquals("Bearer live-token", request.headers["Authorization"])
        val template = list.single()
        assertEquals("MONTHLY", template.frequency)
        assertEquals("2026-11-01", template.nextDueDate)
        assertNull(template.endDate)
        assertTrue(template.active)
    }

    @Test
    fun `recurring create omits null optionals and sends ISO dates`() = runTest {
        signIn()
        server.enqueue(
            jsonResponse(
                201,
                """{"id":"r9","accountId":"a1","direction":"EXPENSE","amount":45.50,"currency":"USD","category":null,"frequency":"WEEKLY","nextDueDate":"2026-10-10","endDate":null,"active":true}""",
            ),
        )
        recurring.create(
            CreateRecurringRequestDto(
                accountId = "a1",
                direction = "EXPENSE",
                amount = BigDecimal("45.50"),
                currency = "USD",
                category = null,
                frequency = "WEEKLY",
                nextDueDate = "2026-10-10",
                endDate = null,
            ),
        )
        val body = server.takeRequest().body?.utf8().orEmpty()
        assertTrue(body.contains("\"accountId\":\"a1\""))
        assertTrue(body.contains("\"nextDueDate\":\"2026-10-10\""))
        assertFalse("null optionals stay off the wire", body.contains("endDate"))
        assertFalse(body.contains("category"))
    }

    @Test
    fun `recurring DELETE returns the deactivated template in the 200 body`() = runTest {
        signIn()
        server.enqueue(
            jsonResponse(
                200,
                """{"id":"r1","accountId":"a1","direction":"EXPENSE","amount":1200.00,"currency":"VND","category":"HOUSING","frequency":"MONTHLY","nextDueDate":"2026-11-01","endDate":null,"active":false}""",
            ),
        )
        val deactivated = recurring.deactivate("r1")
        val request = server.takeRequest()
        assertEquals("/api/recurring-transactions/r1", request.target)
        assertEquals("DELETE", request.method)
        assertFalse(deactivated.active)
    }

    @Test
    fun `recurring foreign id surfaces as 404 ApiException`() = runTest {
        signIn()
        server.enqueue(
            jsonResponse(404, """{"title":"Not found","status":404,"detail":""}"""),
        )
        try {
            recurring.getById("foreign")
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(404, e.status)
            assertEquals(AppError.NotFound, e.toAppError())
        }
    }

    // ── Rules ───────────────────────────────────────────────────────

    @Test
    fun `rules list decodes server priority order verbatim`() = runTest {
        signIn()
        server.enqueue(
            jsonResponse(
                200,
                """[{"id":"r1","conditionType":"CONTAINS","conditionValue":"coffee","targetCategory":"FOOD","priority":1,"enabled":true},{"id":"r2","conditionType":"EQUALS","conditionValue":"rent","targetCategory":"HOUSING","priority":2,"enabled":false}]""",
            ),
        )
        val list = rules.list()
        assertEquals("/api/categorization-rules", server.takeRequest().target)
        assertEquals(listOf(1, 2), list.map { it.priority })
        assertEquals("CONTAINS", list[0].conditionType)
        assertFalse(list[1].enabled)
    }

    @Test
    fun `rule create never sends enabled`() = runTest {
        signIn()
        server.enqueue(
            jsonResponse(
                201,
                """{"id":"n1","conditionType":"CONTAINS","conditionValue":"uber","targetCategory":"TRANSPORT","priority":3,"enabled":true}""",
            ),
        )
        rules.create(
            CreateRuleRequestDto(
                conditionType = "CONTAINS",
                conditionValue = "uber",
                targetCategory = "TRANSPORT",
                priority = 3,
            ),
        )
        val body = server.takeRequest().body?.utf8().orEmpty()
        assertTrue(body.contains("\"priority\":3"))
        assertFalse("enabled is never sent (S22)", body.contains("enabled"))
    }

    @Test
    fun `rule duplicate priority surfaces 409`() = runTest {
        signIn()
        server.enqueue(
            jsonResponse(
                409,
                """{"title":"Conflict","status":409,"detail":"The request conflicts with an existing record."}""",
            ),
        )
        try {
            rules.create(CreateRuleRequestDto("EQUALS", "x", "FOOD", 1))
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(409, e.status)
            assertEquals(AppError.Conflict, e.toAppError())
        }
    }

    @Test
    fun `rule DELETE returns the disabled rule`() = runTest {
        signIn()
        server.enqueue(
            jsonResponse(
                200,
                """{"id":"r1","conditionType":"CONTAINS","conditionValue":"coffee","targetCategory":"FOOD","priority":1,"enabled":false}""",
            ),
        )
        val disabled = rules.disable("r1")
        val request = server.takeRequest()
        assertEquals("/api/categorization-rules/r1", request.target)
        assertEquals("DELETE", request.method)
        assertFalse(disabled.enabled)
    }

    // ── Shared ──────────────────────────────────────────────────────

    @Test
    fun `401 on a planning call drives Session Expired`() = runTest {
        signIn()
        server.enqueue(jsonResponse(401, """{"title":"Unauthorized","status":401,"detail":""}"""))
        try {
            budgets.list()
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
            assertEquals(AppError.SessionExpired, e.toAppError())
        }
        assertTrue(sessionManager.session.value is com.spendwise.android.core.session.Session.Expired)
    }
}
