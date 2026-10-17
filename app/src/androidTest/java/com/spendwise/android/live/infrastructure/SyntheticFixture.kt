package com.spendwise.android.live.infrastructure

import com.spendwise.android.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Synthetic fixture identities + authorized fixture-API client (V5 §6).
 *
 * Every scenario owns its own fixtures — no shared mutable state, no
 * JUnit-order coupling. Names/users carry the run id so the disposable
 * DB can be audited and cleaned by the operator.
 *
 * [FixtureApi] performs raw HTTP ONLY for two sanctioned purposes:
 *   1. Setup/seeding where UI creation is not the test goal (label
 *      `API-SEEDED` — e.g. 51+ history rows for LUI-06/LUI-26).
 *   2. Read-only server verification AFTER a UI mutation — the proof that
 *      a UI-driven request landed; never a substitute for the gesture.
 *
 * Budgets (M01): fixture writes draw down a RUN-WIDE atomic budget —
 * `max_fixture_writes` is signed, shared across every FixtureApi instance,
 * and enforced atomically so parallel helper clients cannot overspend.
 * UI-driven writes are the E2E subject, recorded separately as UI steps —
 * never conflated with fixture traffic in evidence.
 */
class SyntheticFixture(private val caseId: String, private val runTag: String) {

    val tag = "$runTag-$caseId"
    val username = "e2e-${tag.lowercase()}-${UUID.randomUUID().toString().take(8)}"
    val password = "e2e-pw-${UUID.randomUUID().toString().take(16)}"

    // Account names/notes render ellipsized in list rows (~17 chars) —
    // synthetic tags must fit the committed text or assertions can never
    // see them. caseId digits+letters keep per-user uniqueness.
    private val caseShort = caseId.removePrefix("LUI_")
        .replace(Regex("[^A-Za-z0-9]"), "")

    fun accountName(suffix: String) = "$caseShort-${suffix.take(9)}"
    fun txnNote(suffix: String) = "n-$caseShort-${suffix.take(9)}"

    companion object {
        fun forCase(caseId: String): SyntheticFixture =
            SyntheticFixture(caseId, LiveUiE2eGateShim.runTag())
    }
}

/** Indirection so fixtures don't read instrumentation args directly —
 *  the run tag is the authorized run id once gated. */
internal object LiveUiE2eGateShim {
    fun runTag(): String =
        com.spendwise.android.live.LiveUiE2eGate.runId().ifBlank { "no-run" }
}

/** Run-wide write budget (M01): one AtomicInteger per instrumentation
 *  process, configured from the first verified attestation — subsequent
 *  attestations may only tighten it, never raise it. */
internal object RunBudget {
    private val remaining = AtomicInteger(Int.MAX_VALUE)
    private val configured = AtomicBoolean(false)

    fun configure(maxFixtureWrites: Int) {
        if (configured.compareAndSet(false, true)) {
            remaining.set(maxFixtureWrites)
        } else {
            // Tighten-only: a later attestation can reduce, never raise.
            remaining.updateAndGet { minOf(it, maxFixtureWrites) }
        }
    }

    /** Atomic draw-down — shared by every FixtureApi instance in the run. */
    fun spend(): Boolean = remaining.getAndUpdate { if (it > 0) it - 1 else 0 } > 0
}

/** Connectivity probe outcome (M04): only a transport failure counts as
 *  "network down" — an HTTP response of ANY status proves connectivity. */
enum class ProbeResult { REACHABLE, HTTP_RESPONDED, TRANSPORT_DOWN }

/**
 * Minimal fixture/verify client over the same contract the app uses.
 * Constructed only after gate authorization. `token` is kept in memory
 * only and never written to the record.
 */
class FixtureApi(
    private val recorder: EvidenceRecorder,
    private val attestation: ApprovedRunPreflight.Attestation,
    private val baseUrl: String = BuildConfig.SPENDWISE_API_BASE_URL.trimEnd('/'),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private var token: String? = null

    /** Fixture-inventory (M05): entities this client created, emitted at
     *  teardown for owner-side disposal reconciliation. */
    private val createdUsers = mutableListOf<String>()
    private val createdAccounts = mutableListOf<String>()
    private var seededTransactions = 0

    fun inventory(): JSONObject = JSONObject()
        .put("users", JSONArray(createdUsers))
        .put("accounts", JSONArray(createdAccounts))
        .put("seeded_transactions", seededTransactions)

    init { RunBudget.configure(attestation.maxFixtureWrites) }

    private fun req(method: String, path: String, body: String? = null): okhttp3.Response {
        // B2/M01 — write boundary: deny-by-default AND budget-enforced.
        if (method != "GET") {
            check(attestation.mutationCleanupPermitted) {
                "FIXTURE_WRITE_DENIED: $method $path — mutation_cleanup_permissions not granted"
            }
            check(RunBudget.spend()) {
                "FIXTURE_BUDGET_EXCEEDED: run-wide max_fixture_writes spent at $method $path"
            }
        }
        val builder = Request.Builder().url(baseUrl + path)
        token?.let { builder.header("Authorization", "Bearer $it") }
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete()
            else -> builder.method(method, (body ?: "{}").toRequestBody(JSON_MEDIA))
        }
        return client.newCall(builder.build()).execute().also {
            recorder.networkFixture(method, path, it.code)
        }
    }

    fun register(u: String, p: String) {
        val r = req("POST", "/api/auth/register", """{"username":"$u","password":"$p"}""")
        check(r.code == 201) { "fixture register failed: ${r.code}" }
        createdUsers += u
        r.close()
    }

    fun login(u: String, p: String) {
        val r = req("POST", "/api/auth/login", """{"username":"$u","password":"$p"}""")
        check(r.code == 200) { "fixture login failed: ${r.code}" }
        val o = json.parseToJsonElement(r.body.string()).jsonObject
        token = o["accessToken"]!!.jsonPrimitive.content
    }

    /** API-SEEDED account — allowed only when UI creation isn't the goal. */
    fun seedAccount(name: String, currency: String = "USD", type: String = "BANK",
                    opening: String = "0"): String {
        val r = req("POST", "/api/accounts",
            """{"name":"$name","type":"$type","currency":"$currency","openingBalance":"$opening"}""")
        check(r.code == 201) { "seed account failed: ${r.code}" }
        val id = json.parseToJsonElement(r.body.string()).jsonObject["id"]!!.jsonPrimitive.content
        createdAccounts += id
        return id
    }

    /** API-SEEDED transaction. `occurredOn` (ISO yyyy-MM-dd) is REQUIRED
     *  for pagination fixtures (M02): server ordering is
     *  `occurredOn DESC, id DESC`, so distinct dates make the expected
     *  tail deterministic instead of relying on insertion order. */
    fun seedTransaction(
        accountId: String, type: String, amount: String,
        currency: String, category: String, note: String,
        occurredOn: String? = null,
    ) {
        val date = occurredOn?.let { ""","occurredOn":"$it"""" } ?: ""
        val r = req("POST", "/api/transactions",
            """{"accountId":"$accountId","type":"$type","amount":"$amount","currency":"$currency","category":"$category","description":"$note"$date}""")
        check(r.code == 201) { "seed txn failed: ${r.code}" }
        seededTransactions++
        r.close()
    }

    // --- Read-only server verification (post-UI-mutation proof) ---------

    fun getAccounts(): String {
        val r = req("GET", "/api/accounts"); check(r.code == 200); return r.body.string()
    }

    /** Generic read-only verify for any endpoint — fails on non-200. */
    fun get(path: String): String {
        val r = req("GET", path); check(r.code == 200) { "GET $path -> ${r.code}" }
        return r.body.string()
    }

    fun getTransactions(accountId: String): String {
        val r = req("GET", "/api/transactions?accountId=$accountId")
        check(r.code == 200); return r.body.string()
    }

    fun getTransactionPage(accountId: String, page: Int, size: Int = 50): String {
        val r = req("GET", "/api/transactions/page?accountId=$accountId&page=$page&size=$size")
        check(r.code == 200); return r.body.string()
    }

    fun getDashboard(month: String): String {
        val r = req("GET", "/api/dashboard?month=$month")
        check(r.code == 200); return r.body.string()
    }

    // --- Parsed helpers (M03) — schema-specific, no substring counting ----

    fun accountsJson(): JSONArray = JSONArray(getAccounts())

    fun transactionRows(accountId: String): List<JSONObject> =
        JSONArray(getTransactions(accountId)).let { arr ->
            (0 until arr.length()).map { arr.getJSONObject(it) }
        }

    fun transactionPageJson(accountId: String, page: Int, size: Int = 50): JSONObject =
        JSONObject(getTransactionPage(accountId, page, size))

    fun dashboardJson(month: String): JSONObject = JSONObject(getDashboard(month))

    fun countTransactions(accountId: String, note: String): Int =
        transactionRows(accountId).count { it.optString("description") == note }

    fun logout() {
        runCatching { req("POST", "/api/auth/logout", "{}").close() }
    }

    /** M04: transport-level probe — distinguishes "no network" (no HTTP
     *  response at all) from "network up but server unhappy" (any status).
     *  Uses the unauthenticated health endpoint so auth state is irrelevant. */
    fun probeConnectivity(): ProbeResult = try {
        val r = client.newCall(
            Request.Builder().url("$baseUrl/actuator/health").get().build(),
        ).execute()
        recorder.networkFixture("GET", "/actuator/health", r.code)
        r.close()
        ProbeResult.HTTP_RESPONDED
    } catch (e: IOException) {
        recorder.networkFixture("GET", "/actuator/health", -1)
        ProbeResult.TRANSPORT_DOWN
    }

    private companion object {
        val JSON_MEDIA = "application/json".toMediaType()
    }
}

/** B05/M04 — owner-operated control-plane client. The endpoint is a
 *  signed attestation field (`token_control_endpoint` /
 *  `inflight_fault_endpoint`) — a service the owner runs next to the
 *  isolated backend that can deterministically expire tokens, reject
 *  refresh, or drop a request mid-flight. Calls are recorded as
 *  NETWORK_FIXTURE; never secrets, never the app's own traffic. */
class OwnerControlPlane(
    private val recorder: EvidenceRecorder,
    private val endpoint: String,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        // Control ops are inherently slow: expire-access/reject-refresh must
        // outwait the backend JWT TTL before the app can observe the state.
        .readTimeout(150, TimeUnit.SECONDS)
        .build()

    /** `POST <endpoint>/<op>` with a JSON body. Returns true on 2xx —
     *  the mechanism is only "applied" when the control plane confirms. */
    fun call(op: String, body: String = "{}"): Boolean {
        val r = runCatching {
            client.newCall(
                Request.Builder()
                    .url("${endpoint.trimEnd('/')}/$op")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build(),
            ).execute()
        }.getOrNull() ?: return false
        recorder.networkFixture("POST", "control/$op", r.code)
        val ok = r.code in 200..299
        r.close()
        return ok
    }

    /** `GET <endpoint>/<op>` — returns the raw body on 2xx, null
     *  otherwise. Used by the SAF read-back channel (read-saf). */
    fun get(op: String): String? {
        val r = runCatching {
            client.newCall(
                Request.Builder()
                    .url("${endpoint.trimEnd('/')}/$op")
                    .get()
                    .build(),
            ).execute()
        }.getOrNull() ?: return null
        recorder.networkFixture("GET", "control/$op", r.code)
        val body = if (r.code in 200..299) r.body?.string() else null
        r.close()
        return body
    }
}
