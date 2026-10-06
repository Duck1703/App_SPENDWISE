package com.spendwise.android.core.network

import com.spendwise.android.core.session.Session
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.remote.CreateTransferRequestDto
import com.spendwise.android.data.remote.TransfersService
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
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Wire-level contract for `POST /api/transfers` (M12/REQ-BE-003): the
 * exact path + request shape, the `{transferRef, outTransaction,
 * inTransaction}` response parse, bearer propagation, 401 →
 * `Session.Expired`, and 400/404 error mapping — all through the REAL
 * interceptors.
 */
class TransfersServiceContractTest {

    private val now: Instant = Instant.parse("2026-10-04T12:00:00Z")

    private lateinit var server: MockWebServer
    private lateinit var sessionManager: SessionManager
    private lateinit var transfers: TransfersService

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
        transfers = retrofit.create(TransfersService::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private suspend fun signIn() =
        sessionManager.onSignedIn("live-token", now.plusSeconds(900), "ana", "u1")

    private fun transferBody() = """
        {
          "transferRef":"ref-9",
          "outTransaction":{
            "id":"txn-out","accountId":"a1","type":"TRANSFER_OUT",
            "amount":25.00,"currency":"USD","category":null,
            "sourceRef":"TRANSFER:ref-9:OUT","occurredOn":"2026-10-04"
          },
          "inTransaction":{
            "id":"txn-in","accountId":"a2","type":"TRANSFER_IN",
            "amount":25.00,"currency":"USD","category":null,
            "sourceRef":"TRANSFER:ref-9:IN","occurredOn":"2026-10-04"
          }
        }
    """.trimIndent()

    @Test
    fun `create posts the contract body and parses the paired response`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 201, body = transferBody()))

        val response = transfers.create(
            CreateTransferRequestDto(
                sourceAccountId = "a1",
                destinationAccountId = "a2",
                amount = BigDecimal("25.00"),
                currency = "USD",
            ),
        )

        assertEquals("ref-9", response.transferRef)
        assertEquals("TRANSFER_OUT", response.outTransaction.type)
        assertEquals("a1", response.outTransaction.accountId)
        assertEquals("TRANSFER_IN", response.inTransaction.type)
        assertEquals("a2", response.inTransaction.accountId)
        assertEquals("TRANSFER:ref-9:OUT", response.outTransaction.sourceRef)
        assertEquals("2026-10-04", response.outTransaction.occurredOn)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/transfers", request.url.encodedPath)
        assertEquals("Bearer live-token", request.headers["Authorization"])
        // Exact wire shape — no invented fields; BigDecimalSerializer
        // writes the scale-exact amount (25.00, not 25.0).
        assertEquals(
            """{"sourceAccountId":"a1","destinationAccountId":"a2","amount":25.00,"currency":"USD"}""",
            request.body?.utf8(),
        )
    }

    @Test
    fun `a 401 on create drives SessionExpired through the interceptor`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 401, body = ""))

        try {
            transfers.create(
                CreateTransferRequestDto("a1", "a2", BigDecimal("1.00"), "USD"),
            )
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
        }

        assertEquals(Session.Expired("ana", "u1"), sessionManager.session.value)
    }

    @Test
    fun `a 404 surfaces ApiException — foreign or vanished accounts`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 404, body = "{}"))

        try {
            transfers.create(
                CreateTransferRequestDto("a1", "a2", BigDecimal("1.00"), "USD"),
            )
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(404, e.status)
        }
    }

    @Test
    fun `a 400 carries the fields map for form-level mapping`() = runTest {
        signIn()
        server.enqueue(
            MockResponse(
                code = 400,
                body = """{"type":"about:blank","title":"Invalid request","status":400,"fields":{"amount":"must be positive"}}""",
            ),
        )

        try {
            transfers.create(
                CreateTransferRequestDto("a1", "a1", BigDecimal("0"), "USD"),
            )
            throw AssertionError("Expected ApiException")
        } catch (e: ApiException) {
            assertEquals(400, e.status)
            assertEquals("must be positive", e.fields["amount"])
        }
    }

}
