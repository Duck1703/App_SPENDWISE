package com.spendwise.android.core.network

import com.spendwise.android.core.session.Session
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.remote.CategoriesService
import com.spendwise.android.data.remote.CreateTransactionRequestDto
import com.spendwise.android.data.remote.TransactionsService
import com.spendwise.android.fakes.FakeTokenStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
 * Wire-level contract for `GET/POST /api/transactions` and
 * `GET /api/categories` (M3): exact paths, the three real query params,
 * the create request shape, `TransactionResponse` parsing, bearer
 * propagation, and the authenticated-401 → `Session.Expired` path —
 * all through the REAL interceptors.
 */
class TransactionsServiceContractTest {

    private val now: Instant = Instant.parse("2026-10-04T12:00:00Z")

    private lateinit var server: MockWebServer
    private lateinit var sessionManager: SessionManager
    private lateinit var transactions: TransactionsService
    private lateinit var categories: CategoriesService

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
        transactions = retrofit.create(TransactionsService::class.java)
        categories = retrofit.create(CategoriesService::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private suspend fun signIn() =
        sessionManager.onSignedIn("live-token", now.plusSeconds(900), "ana", "u1")

    private fun transactionsBody() = """
        [
          {
            "id":"t1","accountId":"a1","type":"EXPENSE","amount":25.50,
            "currency":"VND","category":"FOOD","sourceRef":"MANUAL:t1",
            "occurredOn":"2026-10-04"
          },
          {
            "id":"t2","accountId":"a1","type":"INCOME","amount":900,
            "currency":"VND","category":null,"sourceRef":"IMPORT:CSV:42",
            "occurredOn":"2026-01-31"
          }
        ]
    """.trimIndent()

    @Test
    fun `list hits GET api-transactions and parses the response shape`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 200, body = transactionsBody()))

        val list = transactions.list()

        assertEquals(2, list.size)
        assertEquals("t1", list[0].id)
        assertEquals("EXPENSE", list[0].type)
        assertEquals(BigDecimal("25.50"), list[0].amount)
        assertEquals("FOOD", list[0].category)
        assertEquals("MANUAL:t1", list[0].sourceRef)
        assertEquals("2026-10-04", list[0].occurredOn)
        assertEquals("2026-01-31", list[1].occurredOn)
        assertNull(list[1].category)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/transactions", request.url.encodedPath)
        assertEquals("Bearer live-token", request.headers["Authorization"])
        assertNull(request.url.query) // no params when all filters are null
    }

    @Test
    fun `list sends exactly the three real server filter params`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 200, body = "[]"))

        transactions.list(accountId = "a1", type = "EXPENSE", category = "FOOD")

        val request = server.takeRequest()
        assertEquals("a1", request.url.queryParameter("accountId"))
        assertEquals("EXPENSE", request.url.queryParameter("type"))
        assertEquals("FOOD", request.url.queryParameter("category"))
    }

    @Test
    fun `list omits unset params — there is no All sentinel on the wire`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 200, body = "[]"))

        transactions.list(type = "INCOME")

        val request = server.takeRequest()
        assertNull(request.url.queryParameter("accountId"))
        assertEquals("INCOME", request.url.queryParameter("type"))
        assertNull(request.url.queryParameter("category"))
        // No invented params anywhere on the wire.
        assertNull(request.url.queryParameter("date"))
        assertNull(request.url.queryParameter("search"))
        assertNull(request.url.queryParameter("page"))
        assertNull(request.url.queryParameter("sourceRef"))
    }

    @Test
    fun `create posts the contract body and parses 201`() = runTest {
        signIn()
        server.enqueue(
            MockResponse(
                code = 201,
                body = """{"id":"t9","accountId":"a1","type":"EXPENSE","amount":25.50,"currency":"VND","category":"FOOD","sourceRef":"MANUAL:t9","occurredOn":"2026-09-15"}""",
            ),
        )

        val created = transactions.create(
            CreateTransactionRequestDto(
                accountId = "a1",
                type = "EXPENSE",
                amount = BigDecimal("25.50"),
                currency = "VND",
                category = "FOOD",
                description = "lunch",
                occurredOn = "2026-09-15",
            ),
        )

        assertEquals("t9", created.id)
        assertEquals("MANUAL:t9", created.sourceRef)
        assertEquals("2026-09-15", created.occurredOn)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/transactions", request.url.encodedPath)
        assertEquals("Bearer live-token", request.headers["Authorization"])
        assertEquals(
            """{"accountId":"a1","type":"EXPENSE","amount":25.50,"currency":"VND","category":"FOOD","description":"lunch","occurredOn":"2026-09-15"}""",
            request.body?.utf8(),
        )
    }

    @Test
    fun `create omits optional fields entirely when unset`() = runTest {
        signIn()
        server.enqueue(
            MockResponse(
                code = 201,
                body = """{"id":"t9","accountId":"a1","type":"INCOME","amount":10,"currency":"VND","category":null,"sourceRef":"MANUAL:t9","occurredOn":"2026-02-29"}""",
            ),
        )

        transactions.create(
            CreateTransactionRequestDto(
                accountId = "a1",
                type = "INCOME",
                amount = BigDecimal("10"),
                currency = "VND",
                occurredOn = "2026-02-29",
            ),
        )

        val body = server.takeRequest().body?.utf8().orEmpty()
        assertTrue("category must be omitted, not null: $body", !body.contains("\"category\""))
        assertTrue("description must be omitted: $body", !body.contains("\"description\""))
        // M11: occurredOn is a required client field — the user-selected
        // date is always on the wire as ISO yyyy-MM-dd.
        assertTrue("occurredOn must always be sent: $body",
            body.contains(""""occurredOn":"2026-02-29""""))
        assertTrue("no time/timezone may ride along: $body", !body.contains("2026-02-29T"))
    }

    @Test
    fun `list tolerates unknown wire types and missing sourceRef`() = runTest {
        signIn()
        server.enqueue(
            MockResponse(
                code = 200,
                body = """[{"id":"t1","accountId":"a1","type":"TRANSFER_IN","amount":5,"currency":"VND","category":null,"occurredOn":"2026-10-04"}]""",
            ),
        )

        val list = transactions.list()

        assertEquals("TRANSFER_IN", list.single().type)
        assertNull(list.single().sourceRef)
    }

    @Test
    fun `list tolerates unknown future fields`() = runTest {
        signIn()
        server.enqueue(
            MockResponse(
                code = 200,
                body = """[{"id":"t1","accountId":"a1","type":"EXPENSE","amount":5,"currency":"VND","category":null,"sourceRef":null,"occurredOn":"2026-10-04"}]""",
            ),
        )

        assertEquals(1, transactions.list().size)
    }

    @Test
    fun `authenticated 401 on list expires the session`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 401, body = ""))

        try {
            transactions.list()
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
        }

        assertEquals(Session.Expired("ana", "u1"), sessionManager.session.value)
    }

    @Test
    fun `create validation error maps structured fields`() = runTest {
        signIn()
        server.enqueue(
            MockResponse(
                code = 400,
                body = """{"type":"about:blank","title":"Validation failed","status":400,"fields":{"amount":"must be greater than 0","accountId":"must not be null"}}""",
            ),
        )

        try {
            transactions.create(
                CreateTransactionRequestDto(
                    "a1", "EXPENSE", BigDecimal("-1"), "VND",
                    occurredOn = "2026-09-15",
                ),
            )
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(400, e.status)
            assertEquals("must be greater than 0", e.fields["amount"])
            assertEquals("must not be null", e.fields["accountId"])
        }
    }

    @Test
    fun `categories returns the value codes`() = runTest {
        signIn()
        server.enqueue(
            MockResponse(
                code = 200,
                body = """[{"value":"FOOD"},{"value":"TRANSPORT"},{"value":"HOUSING"},{"value":"ENTERTAINMENT"},{"value":"SALARY"}]""",
            ),
        )

        val codes = categories.list()

        assertEquals(listOf("FOOD", "TRANSPORT", "HOUSING", "ENTERTAINMENT", "SALARY"), codes.map { it.value })
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/categories", request.url.encodedPath)
        assertEquals("Bearer live-token", request.headers["Authorization"])
    }

    // ── M9: GET /api/transactions/export ────────────────────────────

    private fun csvResponse(
        bytes: ByteArray = "occurredOn,type,amount,currency,category,sourceRef\n2026-01-05,EXPENSE,25.50,VND,FOOD,MANUAL:t1\n".toByteArray(),
    ) = MockResponse(
        code = 200,
        body = String(bytes, Charsets.UTF_8),
        headers = okhttp3.Headers.headersOf(
            "Content-Type", "text/csv",
            "Content-Disposition", "attachment; filename=\"transactions.csv\"",
        ),
    )

    @Test
    fun `export hits GET export with no accountId for all-accounts`() = runTest {
        signIn()
        server.enqueue(csvResponse())

        transactions.export(null).body()!!.byteStream().use { it.readBytes() }

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/transactions/export", request.url.encodedPath)
        assertNull(request.url.query) // all-accounts omits the param entirely
        assertEquals("Bearer live-token", request.headers["Authorization"])
    }

    @Test
    fun `export sends the exact accountId for a selected account`() = runTest {
        signIn()
        server.enqueue(csvResponse())

        transactions.export("acc-9").body()!!.byteStream().use { it.readBytes() }

        val request = server.takeRequest()
        assertEquals("acc-9", request.url.queryParameter("accountId"))
    }

    @Test
    fun `export body arrives byte-identical and Content-Disposition is readable`() = runTest {
        signIn()
        val raw = ("occurredOn,type,amount,currency,category,sourceRef\r\n" +
            "2026-01-05,EXPENSE,\"Tiền, café\",VND,FOOD,t1\r\n")
            .toByteArray(Charsets.UTF_8)
        server.enqueue(csvResponse(raw))

        val response = transactions.export(null)

        assertArrayEquals(raw, response.body()!!.byteStream().use { it.readBytes() })
        assertEquals("attachment; filename=\"transactions.csv\"",
            response.headers()["Content-Disposition"])
        assertEquals("text/csv", response.body()!!.contentType()?.toString())
    }

    @Test
    fun `export 404 surfaces ApiException for a foreign account`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 404, body = """{"title":"Not Found"}"""))

        try {
            transactions.export("acc-foreign")
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(404, e.status)
        }
    }

    @Test
    fun `export 401 expires the session`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 401))

        try {
            transactions.export(null)
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
        }
        assertEquals(
            Session.Expired::class,
            sessionManager.session.value::class,
        )
    }

    @Test
    fun `export 500 surfaces ApiException`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 500))

        try {
            transactions.export(null)
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(500, e.status)
        }
    }

    // ── M15: GET /api/transactions/page ─────────────────────────────

    private fun pageBody() = """
        {
          "content": [
            {
              "id":"t1","accountId":"a1","type":"EXPENSE","amount":25.50,
              "currency":"VND","category":"FOOD","sourceRef":"MANUAL:t1",
              "occurredOn":"2026-10-04"
            }
          ],
          "page": 0,
          "size": 50,
          "totalElements": 57,
          "totalPages": 2,
          "hasNext": true
        }
    """.trimIndent()

    @Test
    fun `page hits GET api-transactions-page and parses the envelope`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 200, body = pageBody()))

        val result = transactions.page(page = 0, size = 50)

        assertEquals(1, result.content.size)
        assertEquals("t1", result.content[0].id)
        assertEquals(0, result.page)
        assertEquals(50, result.size)
        assertEquals(57L, result.totalElements)
        assertEquals(2, result.totalPages)
        assertEquals(true, result.hasNext)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/transactions/page", request.url.encodedPath)
        assertEquals("Bearer live-token", request.headers["Authorization"])
        assertEquals("0", request.url.queryParameter("page"))
        assertEquals("50", request.url.queryParameter("size"))
    }

    @Test
    fun `page sends every filter as an exact wire parameter`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 200, body = pageBody()))

        transactions.page(
            accountId = "a1",
            type = "EXPENSE",
            category = "FOOD",
            uncategorized = true,
            provenance = "IMPORTED",
            from = "2026-09-01",
            to = "2026-09-30",
            page = 2,
            size = 25,
        )

        val request = server.takeRequest()
        assertEquals("a1", request.url.queryParameter("accountId"))
        assertEquals("EXPENSE", request.url.queryParameter("type"))
        assertEquals("FOOD", request.url.queryParameter("category"))
        assertEquals("true", request.url.queryParameter("uncategorized"))
        assertEquals("IMPORTED", request.url.queryParameter("provenance"))
        assertEquals("2026-09-01", request.url.queryParameter("from"))
        assertEquals("2026-09-30", request.url.queryParameter("to"))
        assertEquals("2", request.url.queryParameter("page"))
        assertEquals("25", request.url.queryParameter("size"))
    }

    @Test
    fun `page omits unset filters — nulls never reach the wire`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 200, body = pageBody()))

        transactions.page(page = 0, size = 50)

        val request = server.takeRequest()
        assertNull(request.url.queryParameter("accountId"))
        assertNull(request.url.queryParameter("type"))
        assertNull(request.url.queryParameter("category"))
        assertNull(request.url.queryParameter("uncategorized"))
        assertNull(request.url.queryParameter("provenance"))
        assertNull(request.url.queryParameter("from"))
        assertNull(request.url.queryParameter("to"))
        // Only the two required paging params exist.
        assertEquals(
            setOf("page", "size"),
            request.url.queryParameterNames,
        )
    }

    @Test
    fun `page uncategorized=false is omitted — false is not a filter`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 200, body = pageBody()))

        // The repository only sends the flag when the user selected it;
        // the service contract is that null means "no param".
        transactions.page(uncategorized = null, page = 0, size = 50)

        assertNull(server.takeRequest().url.queryParameter("uncategorized"))
    }

    @Test
    fun `page 401 expires the session`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 401))

        try {
            transactions.page(page = 0, size = 50)
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
        }
        assertEquals(
            Session.Expired::class,
            sessionManager.session.value::class,
        )
    }

    @Test
    fun `page 400 surfaces ApiException for validation failures`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 400, body = """{"title":"from must not be after to"}"""))

        try {
            transactions.page(page = 0, size = 50)
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(400, e.status)
        }
    }
}
