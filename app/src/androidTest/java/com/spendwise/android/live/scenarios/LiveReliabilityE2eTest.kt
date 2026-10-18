package com.spendwise.android.live.scenarios

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.live.infrastructure.LiveCaseIds
import com.spendwise.android.live.infrastructure.OwnerControlPlane
import com.spendwise.android.live.infrastructure.ProbeResult
import com.spendwise.android.live.infrastructure.RunOwnership
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Live UI E2E — reliability/limit scenarios.
 * Owners: LUI-24a, LUI-24b, LUI-24c, LUI-25, LUI-26.
 *
 * LUI-24a/24b/24c are DISTINCT outcomes (V3): 24a = offline-before-send
 * (retry lands exactly once); 24b = ambiguous in-flight loss BEFORE
 * delivery (honest UI, no auto-replay, ≤1 row); 24c = post-commit
 * response loss (upstream committed, response withheld — honest UI, no
 * auto-replay, exactly 1 row). All need owner-approved network
 * mechanisms — absent them, they BLOCK, never fake a PASS.
 */
@RunWith(AndroidJUnit4::class)
class LiveReliabilityE2eTest : LiveE2eTestBase() {

    /** Row's rendered amount text — the only unique marker a list row
     *  exposes (notes never render on rows; B04 + locale-safe). */
    private fun signedAmount(kind: String, amount: String, code: String = "USD"): String {
        val df = java.text.DecimalFormat().apply {
            decimalFormatSymbols = java.text.DecimalFormatSymbols
                .getInstance(java.util.Locale.getDefault())
            minimumFractionDigits = 2
            maximumFractionDigits = 2
            isGroupingUsed = true
        }
        val sign = if (kind == "Income") "+" else "−"
        return "$sign${df.format(amount.toBigDecimal())} $code"
    }

    private fun signedInWithAccount(ctx: Ctx, label: String = "Net"): Pair<String, String> {
        ctx.api.register(ctx.fixture.username, ctx.fixture.password)
        ctx.api.login(ctx.fixture.username, ctx.fixture.password)
        val acct = ctx.fixture.accountName(label)
        val acctId = ctx.api.seedAccount(acct, opening = "1000.00")
        driver.launchMain()
        signInViaUi(ctx, ctx.fixture.username, ctx.fixture.password)
        return acct to acctId
    }

    /** M04 — controlled fault with TRANSPORT-level proof. svc wifi/data
     *  toggles CANNOT produce a transport fault in this topology: the app
     *  reaches the backend via loopback over the adb-reverse USB channel,
     *  which survives wifi/data off (and executeShellCommand is inert on
     *  this device). The attested mechanism is the owner control plane —
     *  `net-down` kills the backend listener, giving a genuine
     *  connect-refused (no HTTP response = TRANSPORT_DOWN); `net-up`
     *  restarts it and returns once healthy. */
    private inner class Fault(private val ctx: Ctx) {
        private val control = OwnerControlPlane(
            ctx.recorder, ctx.attestation.inflightFaultEndpoint,
        )

        fun disableAndVerify() {
            ctx.recorder.serverCheck(
                "control plane stopped the backend listener (net-down)",
                control.call("net-down"),
            )
            val result = ctx.api.probeConnectivity()
            if (result != ProbeResult.TRANSPORT_DOWN) {
                restore()
                ctx.finishBlocked(
                    "network fault ineffective — probe saw $result " +
                        "(an HTTP response still arrives; transport alive)")
            }
            ctx.recorder.step("network down proven at transport level " +
                "(connect refused — no HTTP response on health probe)")
        }

        fun restoreAndVerify() {
            ctx.recorder.serverCheck(
                "control plane restarted the backend (net-up)",
                control.call("net-up"),
            )
            val deadline = System.currentTimeMillis() + 30_000
            var back = ctx.api.probeConnectivity()
            while (back == ProbeResult.TRANSPORT_DOWN &&
                System.currentTimeMillis() < deadline) {
                Thread.sleep(1000)
                back = ctx.api.probeConnectivity()
            }
            if (back == ProbeResult.TRANSPORT_DOWN) {
                ctx.recorder.teardownFailure(
                    "network restore did not recover transport within 30s")
            } else {
                ctx.recorder.step("network restored — transport reachable ($back)")
            }
        }

        fun restore() = runCatching { control.call("net-up") }
    }

    /** LUI-24a (P2): offline BEFORE send → honest error → restore →
     *  a single explicit retry lands exactly one transaction. */
    @Test
    fun lui24a_offlineBeforeSendSingleRetry() {
        val ctx = begin(LiveCaseIds.LUI_24A)
        val fault = Fault(ctx)
        try {
            RunOwnership.requireNetworkFault(ctx.attestation)
            val (acct, acctId) = signedInWithAccount(ctx)
            val note = ctx.fixture.txnNote("24a")

            fault.disableAndVerify()
            driver.navAccounts()
            driver.tap(acct)
            driver.tap("Add transaction")
            driver.tap("Expense")
            driver.typeText("Amount", "7.00")
            driver.typeText("Note (optional)", note)
            driver.tap("Save transaction")
            // The app's honest contract for a transport failure on a
            // non-idempotent financial POST is the conservative ambiguous
            // copy — "couldn't confirm" — never a bare "No connection".
            driver.waitText(
                "check your transactions before trying again",
                substring = true, timeoutMs = 60_000,
            )
            ctx.recorder.assertion("honest transport-failure error — nothing confirmed sent")

            fault.restoreAndVerify()
            // Post-save pops to account detail where the note never
            // renders — the committed marker is the signed amount (B04).
            ctx.recorder.freshness("manual retry lands",
                driver.tapAndMeasureFresh(
                    "Save transaction", signedAmount("Expense", "7.00"),
                    substring = true))
            ctx.recorder.serverCheck(
                "exactly one parsed row landed after single retry",
                ctx.api.countTransactions(acctId, note) == 1,
            )
            ctx.finish("PASS")
        } finally {
            fault.restore()
            ctx.recorder.flush()
        }
    }

    /** LUI-24b (P2): ambiguous in-flight loss → honest ambiguous-save UI,
     *  NO auto-replay; read-only check proves at most one landed (M04).
     *  The in-flight drop is triggered via the owner control plane
     *  (`inflight_fault_endpoint` op `drop-inflight`) — without it the
     *  mid-request loss cannot be scheduled deterministically → BLOCKED. */
    @Test
    fun lui24b_ambiguousInFlightLossNoReplay() {
        val ctx = begin(LiveCaseIds.LUI_24B)
        try {
            RunOwnership.requireNetworkFault(ctx.attestation)
            RunOwnership.requireInFlightFault(ctx.attestation)
            val control = OwnerControlPlane(ctx.recorder, ctx.attestation.inflightFaultEndpoint)
            val (acct, acctId) = signedInWithAccount(ctx)
            val note = ctx.fixture.txnNote("24b")

            // Arm the mechanism BEFORE the gesture — the control plane
            // drops the next in-flight write it observes.
            ctx.recorder.serverCheck(
                "control plane armed in-flight drop for the next request",
                control.call("arm-drop-inflight",
                    """{"username":"${ctx.fixture.username}"}"""),
            )
            driver.navAccounts()
            driver.tap(acct)
            driver.tap("Add transaction")
            driver.tap("Expense")
            driver.typeText("Amount", "7.00")
            driver.typeText("Note (optional)", note)
            driver.tap("Save transaction")
            // Contract text from txn_create_error_ambiguous — only rendered
            // when the request truly died in-flight.
            driver.waitText(
                "check your transactions before trying again",
                substring = true, timeoutMs = 60_000,
            )
            ctx.recorder.assertion("ambiguous-save honest UI; no auto-replay fired")
            ctx.recorder.serverCheck(
                "at most one parsed row landed — no duplicate replay",
                ctx.api.countTransactions(acctId, note) <= 1,
            )
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }

    /** LUI-24c (P1 — ARGUS-02): post-commit response loss. The proxy
     *  forwards the POST, the backend COMMITS (upstream 2xx consumed —
     *  a 2xx is only produced after the transaction commits), then the
     *  response is withheld — the client sees a transport fault on a
     *  write that already landed. Distinct from 24b's pre-delivery drop:
     *  here the row MUST exist (`== 1`, never `<= 1`). The no-replay
     *  oracle is proxy-side: `proxy-stats.txn_posts_seen` counts every
     *  POST /api/transactions that traversed the proxy — a replay would
     *  be a second sighting. Requires host-set `adb reverse tcp:8080
     *  -> tcp:18081` so app traffic traverses the proxy. */
    @Test
    fun lui24c_postCommitResponseLossNoReplay() {
        val ctx = begin(LiveCaseIds.LUI_24C)
        try {
            RunOwnership.requireNetworkFault(ctx.attestation)
            RunOwnership.requireInFlightFault(ctx.attestation)
            val control = OwnerControlPlane(ctx.recorder, ctx.attestation.inflightFaultEndpoint)
            val (acct, acctId) = signedInWithAccount(ctx)
            val note = ctx.fixture.txnNote("24c")

            ctx.recorder.serverCheck(
                "proxy counters reset — baseline zero for the replay oracle",
                control.call("proxy-stats-reset"),
            )
            ctx.recorder.serverCheck(
                "control plane armed post-commit response drop",
                control.call("arm-drop-response",
                    """{"username":"${ctx.fixture.username}"}"""),
            )
            driver.navAccounts()
            driver.tap(acct)
            driver.tap("Add transaction")
            driver.tap("Expense")
            driver.typeText("Amount", "8.50")
            driver.typeText("Note (optional)", note)
            driver.tap("Save transaction")
            // Contract text from txn_create_error_ambiguous — the
            // response died after the commit; only honest copy is rendered.
            driver.waitText(
                "check your transactions before trying again",
                substring = true, timeoutMs = 60_000,
            )
            ctx.recorder.assertion("ambiguous-save honest UI; no auto-replay fired")
            driver.screenshot(ctx.recorder, "ambiguous-ui")

            ctx.recorder.serverCheck(
                "upstream committed — exactly one parsed row exists " +
                    "(post-commit, unlike 24b's <=1 bound)",
                ctx.api.countTransactions(acctId, note) == 1,
            )
            Thread.sleep(8_000) // cover any hypothetical auto-retry window
            val stats = control.get("proxy-stats")?.let { JSONObject(it) }
            ctx.recorder.serverCheck(
                "proxy saw exactly one txn POST + one response drop — " +
                    "no auto-replay fired",
                stats != null && stats.optInt("txn_posts_seen") == 1 &&
                    stats.optInt("response_drops") == 1,
                "stats=${stats?.toString()?.take(200)}",
            )
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }

    /** LUI-25 (P2): semantics smoke — interactive nodes expose actions and
     *  labels. Semantics-only scope; NOT TalkBack/audio verification. */
    @Test
    fun lui25_semanticsSmoke() {
        val ctx = begin(LiveCaseIds.LUI_25)
        try {
            signedInWithAccount(ctx, "Sem")
            driver.navHome()
            // The nav item's OnClick lives on the merged parent; the label
            // (icon cd / text) is an unmerged descendant. The honest
            // semantics claim: a CLICKABLE node exists that carries the
            // label — merged-tree cd lookups miss it entirely.
            for (label in listOf("Home", "Transactions", "Accounts", "More")) {
                composeRule.onNode(
                    hasClickAction() and hasAnyDescendant(
                        hasContentDescription(label) or hasText(label)),
                    useUnmergedTree = true,
                ).assertExists()
            }
            driver.navAccounts()
            driver.waitText(ctx.fixture.accountName("Sem"))
            ctx.recorder.assertion("nav + rows expose semantics labels/click actions")
            ctx.recorder.note("semantics-only scope — no TalkBack/audio claim")
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }

    /** LUI-26 (P2): 200+ seeded ledger — functional tail traversal plus a
     *  measured scroll latency. M02: distinct occurredOn dates make the
     *  oldest row deterministic (bulk-001 = 2020-01-01). M08: the
     *  performance claim is NOT_MEASURED unless the attestation signs a
     *  `performance_threshold_ms` — then it's a fail-hard bound. */
    @Test
    fun lui26_largeLedgerBoundedFetch() {
        val ctx = begin(LiveCaseIds.LUI_26)
        try {
            RunOwnership.requireBulkSeed(ctx.attestation, 200)
            ctx.api.register(ctx.fixture.username, ctx.fixture.password)
            ctx.api.login(ctx.fixture.username, ctx.fixture.password)
            val acct = ctx.fixture.accountName("Bulk")
            val acctId = ctx.api.seedAccount(acct, opening = "0")
            // Notes never render on list rows — the oldest row gets a
            // unique amount so its signed-amount marker is a reachable
            // scroll sentinel (B04); occurredOn stays deterministic.
            val oldest = signedAmount("Expense", "999.99")
            val base = java.time.LocalDate.of(2020, 1, 1)
            for (i in 1..200) {
                ctx.api.seedTransaction(
                    acctId, "EXPENSE", if (i == 1) "999.99" else "1.00", "USD",
                    "FOOD", ctx.fixture.txnNote("bulk-%03d".format(i)),
                    occurredOn = base.plusDays(i.toLong() - 1)
                        .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE),
                )
            }
            ctx.recorder.step(
                "API-SEEDED 200 rows — bulk-001=2020-01-01 (oldest) .. bulk-200 newest")
            driver.launchMain()
            signInViaUi(ctx, ctx.fixture.username, ctx.fixture.password)

            driver.navAccounts()
            driver.tap(acct)
            driver.waitText("Transactions")
            val start = System.currentTimeMillis()
            driver.scrollToText(oldest, timeoutMs = 120_000)
            val elapsed = System.currentTimeMillis() - start
            ctx.recorder.freshness("oldest row bulk-001 reached (paged traversal)", elapsed)
            ctx.recorder.assertion("200-row ledger traversed to oldest sentinel")

            if (RunOwnership.performanceThresholdAttested(ctx.attestation)) {
                ctx.recorder.serverCheck(
                    "scroll-to-tail within attested threshold " +
                        "${ctx.attestation.performanceThresholdMs}ms",
                    elapsed <= ctx.attestation.performanceThresholdMs,
                    "elapsed=$elapsed",
                )
            } else {
                ctx.recorder.note(
                    "performance NOT_MEASURED — no signed " +
                        "performance_threshold_ms; functional traversal only")
            }
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }
}
