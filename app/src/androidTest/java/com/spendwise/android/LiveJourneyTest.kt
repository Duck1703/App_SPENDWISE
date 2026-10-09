package com.spendwise.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * M21 - canonical PRODUCT §32 journeys J1-J14 executed from the physical
 * device over real Wi-Fi against the real Spring Boot backend and real
 * PostgreSQL. Same assume-gate as [LiveBackendSmokeTest] - inert in the
 * default hermetic suite.
 *
 * Wire-level evidence per journey: register/login/refresh/logout (J1-J3),
 * account + balance (J4), expense/income with server-side math (J5-J6),
 * list+filter+detail (J7), CSV/PDF import + dedup (J8-J10), dashboard
 * (J11), recurring lifecycle (J12), budget lifecycle (J13), offsetting
 * correction (J14), plus transfer pairing, ownership isolation, Unicode
 * persistence and ledger export extras.
 *
 * Synthetic identity + data only. Tokens and passwords are never logged.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LiveJourneyTest {

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        private val baseUrl: String
            get() = BuildConfig.SPENDWISE_API_BASE_URL.trimEnd('/')

        private lateinit var access: String
        private lateinit var refresh: String
        private lateinit var userBAccess: String
        private lateinit var accountId: String // USD main
        private lateinit var vndAccountId: String // VND for TPBank rows
        private lateinit var cashAccountId: String // for transfer pair
        private val username = "m21j-${UUID.randomUUID().toString().take(8)}"
        private val password = "m21j-${UUID.randomUUID().toString().take(16)}"
        private var expenseId: String = ""
        private var csvBatchId: String = ""
        private var recurringId: String = ""
        private var budgetId: String = ""

        private fun enabled(): Boolean =
            InstrumentationRegistry.getArguments()
                .getString("spendwise.liveBackend") == "1" &&
                !baseUrl.contains("example.invalid")

        private fun get(path: String, token: String? = access): okhttp3.Response =
            client.newCall(
                Request.Builder().url(baseUrl + path)
                    .apply { token?.let { header("Authorization", "Bearer $it") } }.build(),
            ).execute()

        private fun post(path: String, body: String, token: String? = access): okhttp3.Response =
            client.newCall(
                Request.Builder().url(baseUrl + path)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .apply { token?.let { header("Authorization", "Bearer $it") } }.build(),
            ).execute()

        private fun send(req: Request): okhttp3.Response = client.newCall(req).execute()

        private fun multipart(path: String, field: String, fileName: String, bytes: ByteArray,
                              mime: String): okhttp3.Response {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart(field, fileName, bytes.toRequestBody(mime.toMediaType()))
                .build()
            return client.newCall(
                Request.Builder().url(baseUrl + path)
                    .post(body).header("Authorization", "Bearer $access").build(),
            ).execute()
        }

        private fun asset(name: String): ByteArray =
            InstrumentationRegistry.getInstrumentation().context.assets.open(name).readBytes()

        private fun login(u: String, p: String): Pair<String, String> {
            val resp = post(
                "/api/auth/login", """{"username":"$u","password":"$p"}""", token = null)
            assertEquals("login $u", 200, resp.code)
            val o = json.parseToJsonElement(resp.body.string()).jsonObject
            return o["accessToken"]!!.jsonPrimitive.content to
                o["refreshToken"]!!.jsonPrimitive.content
        }

        @JvmStatic
        @BeforeClass
        fun setup() {
            assumeTrue("live backend smoke disabled", enabled())
            val reg = post(
                "/api/auth/register",
                """{"username":"$username","password":"$password"}""", token = null)
            assertEquals("register", 201, reg.code)
            val (at, rt) = login(username, password)
            access = at; refresh = rt
            // USD main account.
            var r = post(
                "/api/accounts",
                """{"name":"M21 Main","type":"BANK","currency":"USD","openingBalance":"1000.00"}""")
            assertEquals(201, r.code)
            accountId = json.parseToJsonElement(r.body.string()).jsonObject["id"]!!.jsonPrimitive.content
            // VND account for TPBank import rows.
            r = post(
                "/api/accounts",
                """{"name":"M21 TPBank","type":"BANK","currency":"VND","openingBalance":"0"}""")
            assertEquals(201, r.code)
            vndAccountId = json.parseToJsonElement(r.body.string()).jsonObject["id"]!!.jsonPrimitive.content
            // Cash account for the transfer pair.
            r = post(
                "/api/accounts",
                """{"name":"M21 Cash","type":"CASH","currency":"USD","openingBalance":"0"}""")
            assertEquals(201, r.code)
            cashAccountId = json.parseToJsonElement(r.body.string()).jsonObject["id"]!!.jsonPrimitive.content
            // Second user for the ownership-isolation check.
            val u2 = "m21j2-${UUID.randomUUID().toString().take(8)}"
            val p2 = "m21j-${UUID.randomUUID().toString().take(16)}"
            assertEquals(201, post(
                "/api/auth/register",
                """{"username":"$u2","password":"$p2"}""", token = null).code)
            userBAccess = login(u2, p2).first
        }

        @JvmStatic
        @AfterClass
        fun teardown() {
            if (::access.isInitialized) post("/api/auth/logout", "{}")
        }
    }

    // J1 - first launch/register/login: covered by @BeforeClass (register 201
    // + login 200 + JWT pair issued). Assert the artifacts exist.
    @Test
    fun j01_registerLoginIssuedTokenPair() {
        assertTrue(access.isNotBlank())
        assertTrue(refresh.isNotBlank())
    }

    // J2 - returning login: a second login with the same credentials succeeds.
    // Re-login at the end so the shared pair is always the freshest session.
    @Test
    fun j02_returningLogin() {
        val (at2, rt2) = login(username, password)
        assertTrue(at2.isNotBlank() && rt2.isNotBlank())
        val pair = login(username, password)
        access = pair.first; refresh = pair.second
    }

    // J3 - session expiry / renewal: dead access token -> 401; the real
    // refresh endpoint rotates the pair and the new token works.
    @Test
    fun j03_expiredThenRenewedSession() = runBlocking {
        val dead = get("/api/accounts", token = "m21-dead-token")
        assertEquals(401, dead.code)
        val renew = post(
            "/api/auth/refresh", """{"refreshToken":"$refresh"}""", token = null)
        assertEquals(200, renew.code)
        val o = json.parseToJsonElement(renew.body.string()).jsonObject
        access = o["accessToken"]!!.jsonPrimitive.content
        refresh = o["refreshToken"]!!.jsonPrimitive.content
        assertEquals(200, get("/api/accounts").code)
    }

    // J4 - first account: created in setup; verify it reads back with the
    // server-held balance.
    @Test
    fun j04_accountCreateAndReadBack() = runBlocking {
        val r = get("/api/accounts")
        assertEquals(200, r.code)
        val body = r.body.string()
        assertTrue(body.contains(accountId))
        assertTrue(body.contains("1000"))
    }

    // J5 - record expense: POST EXPENSE 25.50 -> server balance 1000-25.50.
    @Test
    fun j05_recordExpense() = runBlocking {
        val r = post(
            "/api/transactions",
            """{"accountId":"$accountId","type":"EXPENSE","amount":"25.50","currency":"USD","category":"FOOD","description":"m21 j5"}""")
        assertEquals(201, r.code)
        expenseId = json.parseToJsonElement(r.body.string()).jsonObject["id"]!!.jsonPrimitive.content
        val accts = get("/api/accounts").body.string()
        assertTrue("balance 974.50", accts.contains("974.5"))
    }

    // J6 - record income: POST INCOME 300 -> 974.50+300=1274.50.
    @Test
    fun j06_recordIncome() = runBlocking {
        val r = post(
            "/api/transactions",
            """{"accountId":"$accountId","type":"INCOME","amount":"300.00","currency":"USD","category":"SALARY","description":"m21 j6"}""")
        assertEquals(201, r.code)
        assertTrue(get("/api/accounts").body.string().contains("1274.5"))
    }

    // J7 - inspect transactions: list, account filter, and the M15 paged
    // search endpoint (the backend has no /{id} detail route by design).
    @Test
    fun j07_inspectTransactions() = runBlocking {
        val list = get("/api/transactions?accountId=$accountId")
        assertEquals(200, list.code)
        val body = list.body.string()
        assertTrue(body.contains(expenseId))
        assertTrue(body.contains("25.5"))
        val page = get("/api/transactions/page?accountId=$accountId&q=m21&page=0&size=10")
        assertEquals(200, page.code)
        val pb = page.body.string()
        assertTrue("paged result rows", pb.contains(expenseId))
        assertTrue("page metadata", pb.contains("\"totalElements\""))
    }

    // J8 - CSV import: multipart upload -> batch summary shows 3 imported.
    @Test
    fun j08_importCsv() = runBlocking {
        val r = multipart(
            "/api/imports?accountId=$accountId", "file", "m21-statement.csv",
            asset("m21-statement.csv"), "text/csv")
        assertTrue("import status ${r.code}", r.code in listOf(200, 201, 202))
        csvBatchId = json.parseToJsonElement(r.body.string())
            .jsonObject["batchId"]!!.jsonPrimitive.content
        val sum = get("/api/imports/$csvBatchId")
        assertEquals(200, sum.code)
        val sb = sum.body.string()
        assertTrue("3 imported rows", sb.contains("\"successCount\":3"))
    }

    // J9 - TPBank PDF import: the backend-generated synthetic statement.
    @Test
    fun j09_importTpbankPdf() = runBlocking {
        val r = multipart(
            "/api/imports?accountId=$vndAccountId", "file", "m21-tpbank.pdf",
            asset("m21-tpbank.pdf"), "application/pdf")
        assertTrue("pdf import status ${r.code}", r.code in listOf(200, 201, 202))
        val batchId = json.parseToJsonElement(r.body.string())
            .jsonObject["batchId"]!!.jsonPrimitive.content
        val sum = get("/api/imports/$batchId")
        assertEquals(200, sum.code)
        val sb = sum.body.string()
        // 2 synthetic rows: one debit, one credit - both must land.
        assertTrue("pdf rows imported: $sb", sb.contains("\"successCount\":2"))
    }

    // J10 - re-import duplicate handling: same CSV a second time must
    // produce duplicates, not new transactions.
    @Test
    fun j10_reimportShowsDuplicates() = runBlocking {
        val r = multipart(
            "/api/imports?accountId=$accountId", "file", "m21-statement.csv",
            asset("m21-statement.csv"), "text/csv")
        assertTrue(r.code in listOf(200, 201, 202))
        val batchId = json.parseToJsonElement(r.body.string())
            .jsonObject["batchId"]!!.jsonPrimitive.content
        val sb = get("/api/imports/$batchId").body.string()
        assertTrue("all 3 rows duplicate: $sb", sb.contains("\"duplicateCount\":3"))
    }

    // J11 - month review: dashboard aggregates per-month totals. Oct 2026 is
    // pure USD (requireSingleCurrency rejects mixed months); Sept is pure VND
    // from the TPBank import - two currencies, each internally consistent.
    @Test
    fun j11_monthReviewDashboard() = runBlocking {
        val oct = get("/api/dashboard?month=2026-10")
        assertEquals(200, oct.code)
        val o = json.parseToJsonElement(oct.body.string()).jsonObject
        // Income: 300 manual + 500 csv = 800. Expense: 25.5 + 45 + 12.75 = 83.25.
        fun num(el: kotlinx.serialization.json.JsonElement?) =
            java.math.BigDecimal(el!!.jsonPrimitive.content)
        assertEquals(0, num(o["totalIncome"]).compareTo(java.math.BigDecimal("800")))
        assertEquals(0, num(o["totalExpense"]).compareTo(java.math.BigDecimal("83.25")))
        val sep = get("/api/dashboard?month=2026-09")
        assertEquals(200, sep.code)
        val s = json.parseToJsonElement(sep.body.string()).jsonObject
        // VND from the TPBank PDF: credit 1,000,000 / debit 150,000.
        assertEquals(0, num(s["totalIncome"]).compareTo(java.math.BigDecimal("1000000")))
        assertEquals(0, num(s["totalExpense"]).compareTo(java.math.BigDecimal("150000")))
    }

    // J12 - recurring lifecycle: create -> list -> deactivate.
    @Test
    fun j12_recurringLifecycle() = runBlocking {
        val r = post(
            "/api/recurring-transactions",
            """{"accountId":"$accountId","direction":"EXPENSE","amount":"9.99","currency":"USD","category":"ENTERTAINMENT","frequency":"MONTHLY","nextDueDate":"2026-11-01"}""")
        assertEquals(201, r.code)
        recurringId = json.parseToJsonElement(r.body.string()).jsonObject["id"]!!.jsonPrimitive.content
        val list = get("/api/recurring-transactions")
        assertTrue(list.body.string().contains(recurringId))
        val del = send(
            Request.Builder().url("$baseUrl/api/recurring-transactions/$recurringId")
                .delete().header("Authorization", "Bearer $access").build())
        assertTrue("deactivate ${del.code}", del.code in listOf(200, 204))
    }

    // J13 - budget lifecycle: create -> calculation reflects real spend.
    @Test
    fun j13_budgetLifecycle() = runBlocking {
        val r = post(
            "/api/budgets",
            """{"category":"FOOD","period":"2026-10","limit":"200.00","currency":"USD"}""")
        assertEquals(201, r.code)
        budgetId = json.parseToJsonElement(r.body.string()).jsonObject["id"]!!.jsonPrimitive.content
        val calc = get("/api/budgets/$budgetId/calculation")
        assertEquals(200, calc.code)
        // FOOD spend at this point = the 25.50 manual expense only; the
        // imported CSV rows are uncategorized (no rules for this user), so
        // they correctly do not count toward the FOOD budget.
        val spent = json.parseToJsonElement(calc.body.string())
            .jsonObject["spent"]!!.jsonPrimitive.content
        assertEquals(0, java.math.BigDecimal(spent).compareTo(java.math.BigDecimal("25.5")))
    }

    // J14 - correction via offsetting entry: inverse type, same amount,
    // original row still visible (append-only).
    @Test
    fun j14_offsettingCorrection() = runBlocking {
        val offset = post(
            "/api/transactions",
            """{"accountId":"$accountId","type":"INCOME","amount":"25.50","currency":"USD","category":"FOOD","description":"Offset for m21 j5"}""")
        assertEquals(201, offset.code)
        val list = get("/api/transactions?accountId=$accountId").body.string()
        assertTrue("original remains", list.contains("\"description\":\"m21 j5\""))
        assertTrue("offset visible", list.contains("Offset for m21 j5"))
    }

    // Extra - transfer pair: linked legs on both accounts via transferRef.
    @Test
    fun x01_transferLinkedLegs() = runBlocking {
        val r = post(
            "/api/transfers",
            """{"sourceAccountId":"$accountId","destinationAccountId":"$cashAccountId","amount":"100.00"}""")
        assertEquals(201, r.code)
        val out = get("/api/transactions?accountId=$accountId").body.string()
        val into = get("/api/transactions?accountId=$cashAccountId").body.string()
        assertTrue(out.contains("TRANSFER_OUT"))
        assertTrue(into.contains("TRANSFER_IN"))
    }

    // Extra - ownership isolation: user B cannot read user A's resources.
    // Transactions have no /{id} route; the list endpoint is the foreign
    // access surface - a foreign accountId must read as 404, not data.
    @Test
    fun x02_ownershipIsolation() = runBlocking {
        assertEquals(404, get("/api/accounts/$accountId", token = userBAccess).code)
        assertEquals(
            404,
            get("/api/transactions?accountId=$accountId", token = userBAccess).code,
        )
        assertEquals(
            404,
            get("/api/transactions/page?accountId=$accountId", token = userBAccess).code,
        )
    }

    // Extra - Unicode description round-trips byte-exact.
    @Test
    fun x03_unicodeDescription() = runBlocking {
        val r = post(
            "/api/transactions",
            """{"accountId":"$accountId","type":"EXPENSE","amount":"5.00","currency":"USD","category":"FOOD","description":"Cà phê sáng - ăn uống"}""")
        assertEquals(201, r.code)
        val list = get("/api/transactions?accountId=$accountId").body.string()
        assertTrue(list.contains("Cà phê sáng - ăn uống"))
    }

    // Extra - ledger export endpoint serves real CSV for the account.
    @Test
    fun x04_ledgerExport() = runBlocking {
        val r = get("/api/transactions/export?accountId=$accountId")
        assertEquals(200, r.code)
        val b = r.body.string()
        assertTrue("csv header", b.contains("occurredOn"))
        assertTrue("row present", b.contains("m21 j5"))
    }
}
