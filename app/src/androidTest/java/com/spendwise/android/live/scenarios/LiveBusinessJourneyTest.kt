package com.spendwise.android.live.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spendwise.android.live.infrastructure.FixtureApi
import com.spendwise.android.live.infrastructure.OwnerControlPlane
import com.spendwise.android.live.infrastructure.SyntheticFixture
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * BJ-01..BJ-22 — the single-owner master business journey (V5 §8).
 * One sequential, cumulative-state pass. Each checkpoint runs only when
 * its mechanism is attested; an unexecuted checkpoint is recorded
 * BLOCKED — and the journey verdict is PARTIAL, never PASS (B5).
 */
@RunWith(AndroidJUnit4::class)
class LiveBusinessJourneyTest : LiveE2eTestBase() {

    @Test
    fun bj_masterJourney() {
        val ctx = begin("BJ-MASTER")
        try {
            val f = ctx.fixture
            val att = ctx.attestation
            val period = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
            val nextDue = LocalDate.now().plusMonths(1).withDayOfMonth(1)
                .format(DateTimeFormatter.ISO_LOCAL_DATE)

            val safReady = att.safFilePaths.isNotEmpty()
            val expiryReady = att.tokenExpiryMechanismApproved &&
                att.tokenControlEndpoint.isNotBlank()
            val killReady = att.processKillPermitted &&
                att.processKillProofPath.isNotBlank()

            var blocked = 0
            var executed = 0
            fun cp(id: String, mechanismMissing: Boolean = false, body: () -> Unit = {}) {
                ctx.recorder.step("checkpoint $id")
                if (mechanismMissing) {
                    blocked++
                    ctx.recorder.note("$id BLOCKED — mechanism not attested")
                    return
                }
                executed++
                body()
                ctx.recorder.assertion("$id complete")
            }

            cp("BJ-01") {
                driver.launchMain()
                ensureSignedOutBoundary(ctx)
                driver.waitText("Spendwise")
                driver.tap("Create account")
                driver.waitText("Create your account")
                driver.typeText("Username", f.username)
                driver.typeText("Password", f.password)
                driver.tap("Create account")
                driver.waitText("Account created — sign in to continue.")
            }
            cp("BJ-02") {
                driver.typeText("Password", f.password)
                driver.tap("Sign in")
                driver.waitCd("Home")
            }

            /** Row's rendered amount text — the only unique marker a list
             *  row exposes (notes never render on rows; B04 + locale-safe). */
            fun signedAmount(kind: String, amount: String, code: String = "USD"): String {
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

            /** Locale-grouped unsigned amount for balance/markers. */
            fun fmt(amount: String): String {
                val df = java.text.DecimalFormat().apply {
                    decimalFormatSymbols = java.text.DecimalFormatSymbols
                        .getInstance(java.util.Locale.getDefault())
                    minimumFractionDigits = 2
                    maximumFractionDigits = 2
                    isGroupingUsed = true
                }
                return df.format(amount.toBigDecimal())
            }

            val acct = f.accountName("Main")
            cp("BJ-03") {
                driver.navAccounts()
                // Empty state: "Add your first account"; populated: "Add account"
                driver.tapAnyOf("Add account", "Add your first account")
                driver.typeText("Account name", acct)
                driver.tap("Currency")
                driver.tap("USD", substring = true)
                driver.typeText("Opening balance", "1000.00")
                driver.tap("Create account")
                driver.waitText(acct)
            }
            val noteOut = f.txnNote("out")
            val noteIn = f.txnNote("in")
            fun addTxn(kind: String, amount: String, note: String) {
                driver.tap(acct)
                driver.tap("Add transaction")
                driver.tap(kind)
                driver.typeText("Amount", amount)
                driver.typeText("Note (optional)", note)
                driver.tap("Save transaction")
                // Post-save pops to account detail — the committed marker
                // is the signed amount (notes never render on rows).
                driver.waitText(signedAmount(kind, amount), substring = true, timeoutMs = 60_000)
                driver.pressBack()
                driver.navAccounts()
            }
            cp("BJ-04") { addTxn("Expense", "25.50", noteOut) }
            cp("BJ-05") { addTxn("Income", "300.00", noteIn) }
            cp("BJ-06") {
                driver.navHome()
                driver.waitText(signedAmount("Expense", "25.50"), substring = true)
                driver.waitText("This month")
                ctx.recorder.freshness("dashboard reflects own mutations",
                    driver.waitFresh("This month"))
            }

            cp("BJ-07") {
                driver.navTransactions()
                driver.tap("Search transactions", substring = true)
                driver.typeText("Search notes…", "in")
                driver.waitText(signedAmount("Income", "300.00"), substring = true)
            }
            val acct2 = f.accountName("Savings")
            cp("BJ-08") {
                driver.navAccounts()
                driver.tapAnyOf("Add account", "Add your first account")
                driver.typeText("Account name", acct2)
                driver.tap("Currency")
                driver.tap("USD", substring = true)
                driver.typeText("Opening balance", "0")
                driver.tap("Create account")
                driver.waitText(acct2)
            }
            cp("BJ-09") {
                // M2: source = 1000 − 25.50 + 300 − 100 = 1174.50; dest = 100.
                // BJ-08 landed on the new account's detail — return to the
                // list before tapping the source row.
                driver.navAccounts()
                driver.tap(acct)
                driver.tap("Transfer")
                driver.selectDropdown("To account", acct2)
                driver.typeText("Amount", "100.00")
                driver.tap("Transfer")
                driver.waitText(fmt("1174.50"), substring = true, timeoutMs = 60_000)
                driver.pressBack()
                driver.tap(acct2)
                driver.waitText(fmt("100.00"), substring = true, timeoutMs = 60_000)
                driver.pressBack()
            }
            cp("BJ-10") {
                driver.tap(acct)
                // The expense row sits below the 2-row viewport — real
                // swipes compose it before the tap.
                driver.scrollToAndTap(signedAmount("Expense", "25.50"))
                driver.tap("Create offsetting entry")
                driver.waitText("Offsetting entry")
                driver.tap("Save transaction")
                // Save pops back to the ORIGINAL txn's detail — the
                // compensating +25.50 income row only renders on the
                // account's history list, one level up, and same-day
                // ordering can push it below the fold — real swipes
                // compose it.
                driver.pressBack()
                driver.scrollToText(signedAmount("Income", "25.50"), timeoutMs = 60_000)
                ctx.recorder.freshness("compensating entry appended — original intact",
                    driver.waitFresh(signedAmount("Expense", "25.50"), substring = true))
                driver.pressBack()
            }

            cp("BJ-11") {
                driver.navMore()
                driver.tapCd("Budgets")
                driver.tapAnyOf("New budget", "Set a budget")
                driver.selectDropdown("Category", "Food")
                driver.typeText("Month", period)
                driver.typeText("Limit", "200.00")
                driver.tap("Save budget")
                driver.waitText("Food")
            }
            cp("BJ-12") {
                driver.navMore()
                driver.tapCd("Recurring items")
                driver.tap("New recurring item", substring = true)
                driver.selectDropdown("Account", acct)
                driver.typeText("Amount", "9.99")
                driver.typeText("Next due date", nextDue)
                driver.tap("Save recurring item")
                driver.waitText(fmt("9.99"), substring = true)
            }
            cp("BJ-13") {
                driver.navMore()
                driver.tapCd("Categorization rules")
                driver.tap("New rule", substring = true)
                driver.typeText("Description text", f.txnNote("kw"))
                driver.selectDropdown("Assign category", "Food")
                driver.typeText("Priority", "1")
                driver.tap("Save rule")
                driver.waitText(f.txnNote("kw"), substring = true)
            }
            cp("BJ-14") {
                driver.navAccounts()
                driver.tap(acct)
                driver.tap("Edit account")
                driver.typeText("Account name", f.accountName("MainRenamed"))
                driver.tap("Save changes")
                driver.waitText(f.accountName("MainRenamed"), substring = true)
                driver.tap("Edit account")
                // The archive control is a Switch + Save — the committed
                // post-mutation marker is the "Archived" badge.
                driver.toggleSwitch(on = true)
                driver.tap("Save changes")
                driver.waitText("Archived")
                driver.pressBack()
            }

            // Mechanism-gated checkpoints — body runs ONLY when attested.
            cp("BJ-15", mechanismMissing = !safReady) {
                val file = "spendwise_${f.tag}.csv"
                driver.navMore()
                driver.tapCd("Import statement")
                driver.waitText("Import into")
                driver.tap("Import into")
                driver.tap(acct2, substring = true)
                driver.tap("Choose file")
                check(driver.safPickFile(file)) { "SAF missing $file" }
                driver.systemTap("Open")
                driver.tap("Import")
                driver.waitText("imported", substring = true, timeoutMs = 120_000)
            }
            cp("BJ-16", mechanismMissing = !safReady) {
                driver.navMore()
                driver.tapCd("Ledger export")
                driver.waitText("Export ledger")
                driver.tap("Export CSV")
                check(driver.systemTapAnyOf("Save", "Lưu", "LƯU")) {
                    "BLOCKED: system Save button missing"
                }
                driver.waitText("Ledger exported", timeoutMs = 120_000)
            }

            cp("BJ-17") { signOutViaUi(ctx) }
            cp("BJ-18") {
                signInViaUi(ctx, f.username, f.password)
                driver.navAccounts()
                driver.waitText(acct2)
            }

            cp("BJ-19", mechanismMissing = !expiryReady) {
                // B05: deterministic control-plane trigger + content-hash
                // rotation of the persisted session — not an mtime note.
                val control = OwnerControlPlane(ctx.recorder, att.tokenControlEndpoint)
                val store = File(
                    InstrumentationRegistry.getInstrumentation()
                        .targetContext.filesDir,
                    "datastore/spendwise_session.preferences_pb",
                )
                val beforeHash = if (store.isFile) store.readBytes()
                    .let { java.security.MessageDigest.getInstance("SHA-256")
                        .digest(it).joinToString("") { b -> "%02x".format(b) } }
                    else "absent"
                ctx.recorder.serverCheck("control plane expired access token",
                    control.call("expire-access", """{"username":"${f.username}"}"""))
                // Tab nav refetches nothing (cache-first VMs) — a real
                // pull-refresh is the guaranteed live call that trips the
                // expiry → renewal path.
                driver.navAccounts()
                driver.pullDownRefresh()
                driver.navHome()
                val deadline = System.currentTimeMillis() + 60_000
                fun hash(): String = if (store.isFile) store.readBytes()
                    .let { java.security.MessageDigest.getInstance("SHA-256")
                        .digest(it).joinToString("") { b -> "%02x".format(b) } }
                    else "absent"
                var now = hash()
                while (now == beforeHash && System.currentTimeMillis() < deadline) {
                    composeRule.waitForIdle(); Thread.sleep(500)
                    now = hash()
                }
                ctx.recorder.serverCheck(
                    "session store content rotated — renewal written",
                    now != beforeHash && now != "absent")
            }
            cp("BJ-20", mechanismMissing = !expiryReady) {
                val control = OwnerControlPlane(ctx.recorder, att.tokenControlEndpoint)
                ctx.recorder.serverCheck("control plane rejected refresh",
                    control.call("reject-refresh", """{"username":"${f.username}"}"""))
                driver.navAccounts()
                driver.pullDownRefresh()
                // The rejected refresh pushes the expired overlay over the
                // shell mid-gesture — it covers the nav bar, so waiting on
                // a nav tap here can never succeed. Wait for the overlay.
                driver.waitText("Session expired", substring = true, timeoutMs = 60_000)
                driver.tap("Sign in again")
                reAuthViaUi(ctx, f.username, f.password)
            }
            cp("BJ-21") {
                // Cross-user boundary inside the journey (M07): sign in as
                // a fresh synthetic user B — A's CURRENT identifiers must be
                // absent on every surface: the RENAMED account (BJ-14), the
                // second account, and the transaction-note markers.
                val aIdentifiers = listOf(
                    f.accountName("MainRenamed"), // acct was renamed in BJ-14
                    acct2,
                    noteOut, noteIn,
                )
                val fB = SyntheticFixture.forCase("BJ-21-B")
                val apiB = FixtureApi(ctx.recorder, att)
                apiB.register(fB.username, fB.password)
                apiB.login(fB.username, fB.password)
                apiB.seedAccount(fB.accountName("B-ACCT"))
                signOutViaUi(ctx)
                signInViaUi(ctx, fB.username, fB.password)
                for (surface in listOf("Accounts", "Home", "Transactions")) {
                    when (surface) {
                        "Accounts" -> driver.navAccounts()
                        "Home" -> driver.navHome()
                        else -> driver.navTransactions()
                    }
                    for (aId in aIdentifiers) {
                        ctx.recorder.check(
                            "B's $surface shows zero A identifier '$aId'",
                            runCatching { driver.assertAbsent(aId, substring = true) }.isSuccess,
                        )
                    }
                }
                // Server-side: B's identity reads zero A account rows.
                ctx.recorder.serverCheck(
                    "B GET /api/accounts parsed rows contain zero A accounts",
                    apiB.accountsJson().let { arr ->
                        (0 until arr.length()).none {
                            arr.getJSONObject(it).optString("name") ==
                                f.accountName("MainRenamed") ||
                                arr.getJSONObject(it).optString("name") == acct2
                        }
                    },
                )
                signOutViaUi(ctx)
                signInViaUi(ctx, f.username, f.password)
                driver.navAccounts()
                driver.waitText(acct2)
            }
            cp("BJ-22", mechanismMissing = !killReady) {
                // B03: host-produced proof must bind to THIS run —
                // run_id match + a real death (after_pid<=0). A bare file
                // or stale proof is not evidence of the journey's death.
                val proofFile = File(att.processKillProofPath)
                ctx.recorder.serverCheck(
                    "kill proof exists at attested path",
                    proofFile.isFile, att.processKillProofPath)
                val proof = JSONObject(proofFile.readText())
                ctx.recorder.serverCheck(
                    "kill proof bound to this run + real death recorded",
                    proof.optString("run_id") == att.runId &&
                        proof.optLong("after_pid", -1) <= 0,
                    "proof=${proof.toString().take(300)}")
                driver.launchMain()
                driver.waitCd("Home", timeoutMs = 90_000)
            }

            ctx.recorder.serverVerify(
                "journey: $executed executed, $blocked mechanism-blocked")
            ctx.finish(if (blocked == 0) "PASS" else "PARTIAL")
        } finally { ctx.recorder.flush() }
    }
}
