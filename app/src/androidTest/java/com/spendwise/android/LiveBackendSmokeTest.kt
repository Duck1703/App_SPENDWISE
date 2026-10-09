package com.spendwise.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * M21 — live on-device smoke against the REAL Spendwise backend over the LAN.
 *
 * This class is inert in the normal connected suite: every test skips via
 * [assumeTrue] unless the instrumentation argument
 * `spendwise.liveBackend=1` is passed AND `SPENDWISE_API_BASE_URL` points at
 * a real host (not the placeholder). Run it with:
 *
 *   adb shell am instrument -w -e spendwise.liveBackend 1 \
 *     com.spendwise.android.test/androidx.test.runner.AndroidJUnitRunner
 *
 * It exercises the exact wire path the app uses (OkHttp + the backend's real
 * auth/ownership rules) on the physical device's Wi-Fi — no fakes, no
 * MockWebServer, no adb-reverse. Synthetic data only; no secrets are logged.
 */
@RunWith(AndroidJUnit4::class)
class LiveBackendSmokeTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val baseUrl: String
        get() = BuildConfig.SPENDWISE_API_BASE_URL.trimEnd('/')

    private fun liveBackendEnabled(): Boolean =
        InstrumentationRegistry.getArguments()
            .getString("spendwise.liveBackend") == "1" &&
            !baseUrl.contains("example.invalid")

    @Test
    fun healthEndpointReachable() = runBlocking {
        assumeTrue("live smoke disabled", liveBackendEnabled())
        val resp = get("/actuator/health")
        assertEquals(200, resp.code)
        assertTrue(resp.body.string().contains("UP"))
    }

    @Test
    fun unauthenticatedApiCallIsRejected() = runBlocking {
        assumeTrue("live smoke disabled", liveBackendEnabled())
        val resp = get("/api/accounts")
        assertEquals(401, resp.code)
    }

    @Test
    fun registerLoginCreateAccountCreateTransactionReadBack() = runBlocking {
        assumeTrue("live smoke disabled", liveBackendEnabled())

        // Synthetic identity — unique per run, throwaway.
        val username = "m21smoke-${UUID.randomUUID().toString().take(8)}"
        val password = "synthetic-m21-${UUID.randomUUID().toString().take(12)}"

        // Register (201) — real auth path.
        post(
            "/api/auth/register",
            """{"username":"$username","password":"$password"}""",
        ).use { assertEquals(201, it.code) }

        // Login → real JWT pair (token never printed).
        val loginResp = post(
            "/api/auth/login",
            """{"username":"$username","password":"$password"}""",
        )
        assertEquals(200, loginResp.code)
        val tokens = json.parseToJsonElement(loginResp.body.string()).jsonObject
        val access = tokens["accessToken"]!!.jsonPrimitive.content
        assertNotNull(tokens["refreshToken"])

        // Create synthetic account.
        val acctResp = post(
            "/api/accounts",
            """{"name":"M21 Smoke Acct","type":"CASH","currency":"USD","openingBalance":"100.00"}""",
            access,
        )
        assertEquals(201, acctResp.code)
        val acctId = json.parseToJsonElement(acctResp.body.string())
            .jsonObject["id"]!!.jsonPrimitive.content

        // Create synthetic expense.
        val txnResp = post(
            "/api/transactions",
            """{"accountId":"$acctId","type":"EXPENSE","amount":"12.34","currency":"USD","category":"FOOD","description":"m21-live-smoke"}""",
            access,
        )
        assertEquals(201, txnResp.code)

        // Read back — backend-authoritative balance persisted server-side.
        val listResp = get("/api/transactions?accountId=$acctId", access)
        assertEquals(200, listResp.code)
        assertTrue(listResp.body.string().contains("12.34"))

        val acctsResp = get("/api/accounts", access)
        assertEquals(200, acctsResp.code)
        // Server-side math: 100.00 opening − 12.34 expense = 87.66.
        assertTrue(acctsResp.body.string().contains("87.66"))

        // Logout revokes credentials.
        val logoutResp = post("/api/auth/logout", "{}", access)
        assertTrue(logoutResp.code in listOf(200, 204))
    }

    private fun get(path: String, token: String? = null): okhttp3.Response =
        client.newCall(
            Request.Builder()
                .url(baseUrl + path)
                .apply { token?.let { header("Authorization", "Bearer $it") } }
                .build(),
        ).execute()

    private fun post(path: String, body: String, token: String? = null): okhttp3.Response =
        client.newCall(
            Request.Builder()
                .url(baseUrl + path)
                .post(body.toRequestBody("application/json".toMediaType()))
                .apply { token?.let { header("Authorization", "Bearer $it") } }
                .build(),
        ).execute()
}
