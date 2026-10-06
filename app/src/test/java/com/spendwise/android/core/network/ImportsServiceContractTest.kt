package com.spendwise.android.core.network

import com.spendwise.android.core.session.Session
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.remote.ImportsService
import com.spendwise.android.fakes.FakeTokenStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Wire-level contract for `POST /api/imports?accountId=` + multipart
 * `file` (M5, BACKEND-CONTRACT §16 — verified against
 * `ImportController` at backend `8194daa`): exact path, the one real
 * query param, the multipart part name + original filename, the
 * `Location` header contract, 201 full-response decode incl. unknown
 * keys, 400/404/500 → ApiException, and the authenticated-401 →
 * `Session.Expired` path — all through the REAL interceptors.
 */
class ImportsServiceContractTest {

    private val now: Instant = Instant.parse("2026-10-04T12:00:00Z")

    private lateinit var server: MockWebServer
    private lateinit var sessionManager: SessionManager
    private lateinit var imports: ImportsService

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
        imports = retrofit.create(ImportsService::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private suspend fun signIn() =
        sessionManager.onSignedIn("live-token", now.plusSeconds(900), "ana", "u1")

    private fun csvPart(fileName: String = "tpbank.csv"): MultipartBody.Part =
        MultipartBody.Part.createFormData(
            "file",
            fileName,
            "occurredOn,amount,currency,description,sourceTxnId\n2026-10-01,-10,VND,,".toRequestBody(
                "text/csv".toMediaType(),
            ),
        )

    private fun reportBody() = """
        {
          "batchId":"7f8d4f2e-1a2b-4c3d-9e5f-112233445566",
          "accountId":"11111111-2222-3333-4444-555555555555",
          "fileName":"tpbank.csv",
          "importedAt":"2026-10-04T11:58:00Z",
          "successCount":2,
          "duplicateCount":1,
          "failedCount":1,
          "futureField":{"anything":true},
          "rows":[
            {"rowNumber":2,"status":"OK","reason":null,"transactionId":"t-2"},
            {"rowNumber":3,"status":"DUPLICATE","reason":"same sourceTxnId","transactionId":null},
            {"rowNumber":4,"status":"FAILED","reason":"currency mismatch","transactionId":null},
            {"rowNumber":5,"status":"OK","reason":null,"transactionId":"t-5"}
          ]
        }
    """.trimIndent()

    @Test
    fun `upload POSTs api-imports with the accountId query param and a multipart file part`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 201, body = reportBody()))

        imports.upload(
            accountId = "11111111-2222-3333-4444-555555555555",
            file = csvPart("october statement.pdf"),
        )

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/imports", request.url.encodedPath)
        assertEquals(
            setOf("accountId"),
            request.url.queryParameterNames,
        )
        assertEquals(
            "11111111-2222-3333-4444-555555555555",
            request.url.queryParameter("accountId"),
        )
        assertEquals("Bearer live-token", request.headers["Authorization"])
        assertTrue(
            request.headers["Content-Type"]?.startsWith("multipart/form-data") == true,
        )
        val rawBody = request.body?.utf8().orEmpty()
        assertTrue(rawBody.contains("""name="file"; filename="october statement.pdf""""))
        assertTrue(rawBody.contains("occurredOn,amount,currency,description,sourceTxnId"))
    }

    @Test
    fun `upload decodes the 201 report exactly — rows, statuses, counts, no invented total`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 201, body = reportBody()))

        val dto = imports.upload("acc-x", csvPart())

        assertEquals("7f8d4f2e-1a2b-4c3d-9e5f-112233445566", dto.batchId)
        assertEquals("11111111-2222-3333-4444-555555555555", dto.accountId)
        assertEquals("tpbank.csv", dto.fileName)
        assertEquals("2026-10-04T11:58:00Z", dto.importedAt)
        assertEquals(2, dto.successCount)
        assertEquals(1, dto.duplicateCount)
        assertEquals(1, dto.failedCount)
        assertEquals(4, dto.rows.size)
        assertEquals("OK", dto.rows[0].status)
        assertEquals("t-2", dto.rows[0].transactionId)
        assertEquals("DUPLICATE", dto.rows[1].status)
        assertEquals("FAILED", dto.rows[2].status)
        assertEquals("currency mismatch", dto.rows[2].reason)
        assertNull(dto.rows[2].transactionId)
    }

    @Test
    fun `unknown fields and a header-only empty report decode`() = runTest {
        signIn()
        server.enqueue(
            MockResponse(
                code = 201,
                body = """
                    {
                      "batchId":"b0","accountId":"a0","fileName":"empty.csv",
                      "importedAt":"2026-10-04T11:58:00Z",
                      "successCount":0,"duplicateCount":0,"failedCount":0,
                      "rows":[],"newServerField":1
                    }
                """.trimIndent(),
            ),
        )

        val dto = imports.upload("a0", csvPart())

        assertEquals(0, dto.rows.size)
        assertEquals(0, dto.successCount)
    }

    @Test
    fun `400 maps to ApiException with status 400 (unsupported format path)`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 400, body = """{"error":"unsupported file"}"""))

        try {
            imports.upload("acc-1", csvPart("statement.exe"))
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(400, e.status)
        }
    }

    @Test
    fun `404 maps to ApiException with status 404 (foreign account)`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 404, body = """{"error":"not found"}"""))

        try {
            imports.upload("foreign-acc", csvPart())
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(404, e.status)
        }
    }

    @Test
    fun `500 maps to ApiException (structural CSV failure drift — not assumed 400)`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 500, body = """{"error":"boom"}"""))

        try {
            imports.upload("acc-1", csvPart())
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(500, e.status)
        }
    }

    @Test
    fun `401 transitions the session to Expired and never auto-retries`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 401, body = """{"error":"expired"}"""))

        try {
            imports.upload("acc-1", csvPart())
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
        }

        assertTrue(sessionManager.session.value is Session.Expired)
        // One request hit the wire — the non-idempotent POST never replays.
        assertNotNull(server.takeRequest())
        assertTrue(server.requestCount == 1)
    }

    // ── M10 / REQ-BE-004: history list + batch detail ──────────────

    private fun listBody() = """
        [
          {
            "batchId":"b-new","accountId":"a1","fileName":"oct.csv",
            "importedAt":"2026-10-04T11:58:00Z",
            "successCount":10,"duplicateCount":2,"failedCount":1,
            "futureField":true
          },
          {
            "batchId":"b-old","accountId":"a2","fileName":"sep.pdf",
            "importedAt":"2026-09-04T11:58:00Z",
            "successCount":5,"duplicateCount":0,"failedCount":0
          }
        ]
    """.trimIndent()

    @Test
    fun `list GETs api-imports and decodes summaries — never rows`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 200, body = listBody()))

        val items = imports.list()

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/imports", request.url.encodedPath)
        assertEquals("Bearer live-token", request.headers["Authorization"])
        assertEquals(2, items.size)
        assertEquals("b-new", items[0].batchId)
        assertEquals("oct.csv", items[0].fileName)
        assertEquals("2026-10-04T11:58:00Z", items[0].importedAt)
        assertEquals(10, items[0].successCount)
        assertEquals(2, items[0].duplicateCount)
        assertEquals(1, items[0].failedCount)
    }

    @Test
    fun `list empty history decodes as an empty list — 200 brackets, never 404`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 200, body = "[]"))

        assertTrue(imports.list().isEmpty())
    }

    @Test
    fun `list 401 transitions the session to Expired`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 401, body = """{"error":"expired"}"""))

        try {
            imports.list()
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
        }
        assertTrue(sessionManager.session.value is Session.Expired)
    }

    @Test
    fun `detail GETs api-imports-id and decodes the full report`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 200, body = reportBody()))

        val dto = imports.detail("7f8d4f2e-1a2b-4c3d-9e5f-112233445566")

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(
            "/api/imports/7f8d4f2e-1a2b-4c3d-9e5f-112233445566",
            request.url.encodedPath,
        )
        assertEquals(4, dto.rows.size)
        assertEquals("DUPLICATE", dto.rows[1].status)
    }

    @Test
    fun `detail 404 maps to ApiException — foreign batch reads as absent`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 404, body = """{"error":"not found"}"""))

        try {
            imports.detail("not-mine")
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(404, e.status)
        }
    }
}
