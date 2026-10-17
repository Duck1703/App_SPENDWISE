package com.spendwise.android.live.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.live.infrastructure.LiveCaseIds
import com.spendwise.android.live.infrastructure.RunOwnership
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Live UI E2E — transaction scenarios.
 * Owners: LUI-04, LUI-07, LUI-08, LUI-10, LUI-21.
 * The ledger is append-only: no edit/PATCH exists; "edit" is Offset (C4).
 */
@RunWith(AndroidJUnit4::class)
class LiveTransactionE2eTest : LiveE2eTestBase() {

    /** Register+login+seed account via fixture API; returns (name, id). */
    private fun ctxFor(ctx: Ctx, name: String = "Main"): Pair<String, String> {
        ctx.api.register(ctx.fixture.username, ctx.fixture.password)
        ctx.api.login(ctx.fixture.username, ctx.fixture.password)
        val acct = ctx.fixture.accountName(name)
        val acctId = ctx.api.seedAccount(acct, opening = "1000.00")
        return acct to acctId
    }

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

    /** Fill the create-transaction form from an account's detail screen.
     *  Save pops back to the ACCOUNT detail — the committed freshness
     *  oracle is the new history row's signed-amount marker (notes are
     *  never rendered on rows). */
    private fun createTxnViaUi(
        ctx: Ctx, accountName: String, kind: String, amount: String, note: String,
    ): Long {
        driver.navAccounts()
        driver.tap(accountName)
        driver.tap("Add transaction")
        driver.tap(kind) // "Income" | "Expense"
        driver.typeText("Amount", amount)
        driver.typeText("Note (optional)", note)
        driver.debugDumpFields("TXNFORM")
        ctx.recorder.step("submit $kind $amount note=$note")
        return driver.tapAndMeasureFresh(
            "Save transaction", signedAmount(kind, amount))
    }

    /** LUI-04 (P0): income + expense via UI → history rows + fresh balance. */
    @Test
    fun lui04_createIncomeAndExpenseViaUi() {
        val ctx = begin(LiveCaseIds.LUI_04)
        try {
            val (acct, acctId) = ctxFor(ctx)
            driver.launchMain()
            signInViaUi(ctx, ctx.fixture.username, ctx.fixture.password)

            val noteIn = ctx.fixture.txnNote("in")
            ctx.recorder.freshness("income row visible post-save",
                createTxnViaUi(ctx, acct, "Income", "300.00", noteIn))

            val noteOut = ctx.fixture.txnNote("out")
            ctx.recorder.freshness("expense row visible post-save",
                createTxnViaUi(ctx, acct, "Expense", "25.50", noteOut))

            // Balance is server truth: 1000 + 300 − 25.50 = 1274.50 —
            // grouped per the device locale (vi "1.274,50" / en "1,274.50").
            val bal = java.text.DecimalFormat().apply {
                decimalFormatSymbols = java.text.DecimalFormatSymbols
                    .getInstance(java.util.Locale.getDefault())
                minimumFractionDigits = 2
                maximumFractionDigits = 2
                isGroupingUsed = true
            }.format(1274.5)
            driver.waitText(bal, substring = true)
            ctx.recorder.assertion("balance updated on detail without refresh")
            // Server truth (M03): parsed rows — exact fields, not substring.
            val rows = ctx.api.transactionRows(acctId)
            ctx.recorder.serverCheck(
                "income row persisted with exact fields",
                rows.any {
                    it.optString("description") == noteIn &&
                        it.optString("type") == "INCOME" &&
                        it.optDouble("amount") == 300.00 &&
                        it.optString("currency") == "USD"
                },
            )
            ctx.recorder.serverCheck(
                "expense row persisted with exact fields",
                rows.any {
                    it.optString("description") == noteOut &&
                        it.optString("type") == "EXPENSE" &&
                        it.optDouble("amount") == 25.50 &&
                        it.optString("currency") == "USD"
                },
            )
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }

    /** LUI-07 (P0): mutation → detail → back → Home → Transactions consistent. */
    @Test
    fun lui07_crossScreenConsistencyPostMutation() {
        val ctx = begin(LiveCaseIds.LUI_07)
        try {
            val (acct, _) = ctxFor(ctx)
            driver.launchMain()
            signInViaUi(ctx, ctx.fixture.username, ctx.fixture.password)
            val note = ctx.fixture.txnNote("x7")
            ctx.recorder.freshness("created row visible",
                createTxnViaUi(ctx, acct, "Expense", "10.00", note))
            // The note lives only on the txn detail — on every list surface
            // the row's unique rendered marker is the signed amount.
            val amount = signedAmount("Expense", "10.00")

            driver.navHome()
            ctx.recorder.freshness("Home recent activity shows own mutation",
                driver.waitFresh(amount))
            driver.navTransactions()
            ctx.recorder.freshness("Transactions list shows same row",
                driver.waitFresh(amount))
            driver.navAccounts()
            driver.tap(acct)
            driver.waitText(amount)
            ctx.recorder.assertion("same transaction consistent across 3 surfaces")
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }

    /** LUI-08 (P1): Offset — compensating POST, original immutable. */
    @Test
    fun lui08_offsetTransactionAppendOnly() {
        val ctx = begin(LiveCaseIds.LUI_08)
        try {
            val (acct, acctId) = ctxFor(ctx)
            driver.launchMain()
            signInViaUi(ctx, ctx.fixture.username, ctx.fixture.password)
            val note = ctx.fixture.txnNote("orig")
            ctx.recorder.freshness("created row visible",
                createTxnViaUi(ctx, acct, "Expense", "25.50", note))

            // Save popped back to the ACCOUNT detail's history. Open the
            // original row by its rendered marker — the signed amount.
            driver.tap(signedAmount("Expense", "25.50"))
            driver.scrollToText("Create offsetting entry", timeoutMs = 30_000)
            driver.tap("Create offsetting entry")
            driver.waitText("Offsetting entry")
            ctx.recorder.step("offset form opened — prefilled inverse")
            driver.tap("Save transaction")
            // The offset save pops back to the original's detail — its
            // note field is still intact (append-only: never rewritten).
            driver.waitText(note, substring = true, timeoutMs = 60_000)
            ctx.recorder.assertion("original transaction immutable + compensating entry done")
            // Server truth (M03): parsed rows — EXACTLY 2 (original+offset),
            // original description intact, offset carries the same accountId.
            val rows = ctx.api.transactionRows(acctId)
            ctx.recorder.serverCheck(
                "exactly 2 ledger rows (original + offset)",
                rows.size == 2, "rows=${rows.size}",
            )
            ctx.recorder.serverCheck(
                "original row intact — same description+amount+accountId",
                rows.any {
                    it.optString("description") == note &&
                        it.optDouble("amount") == 25.50 &&
                        it.optString("accountId") == acctId
                },
            )
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }

    /** LUI-10 (P1): search/filter the transaction list. */
    @Test
    fun lui10_searchAndFilterTransactions() {
        val ctx = begin(LiveCaseIds.LUI_10)
        try {
            val (_, acctId) = ctxFor(ctx)
            val hit = ctx.fixture.txnNote("findme")
            val miss = ctx.fixture.txnNote("other")
            ctx.api.seedTransaction(acctId, "EXPENSE", "10.00", "USD", "FOOD", hit)
            ctx.api.seedTransaction(acctId, "EXPENSE", "20.00", "USD", "FOOD", miss)
            driver.launchMain()
            signInViaUi(ctx, ctx.fixture.username, ctx.fixture.password)

            driver.navTransactions()
            driver.typeCd("Search transactions", "findme")
            // Rows never render the note — the visible per-row marker is the
            // signed amount. Search matches by note server-side, so the hit
            // row is the one whose amount is 10.00.
            val hitAmount = signedAmount("Expense", "10.00")
            val missAmount = signedAmount("Expense", "20.00")
            driver.waitText(hitAmount, timeoutMs = 60_000)
            // Filter may be debounced/paginated — wait for the miss row to
            // leave the tree, not a single snapshot.
            ctx.recorder.check("non-matching row hidden by search",
                runCatching {
                    driver.waitTextGone(missAmount, timeoutMs = 60_000)
                }.isSuccess)
            ctx.recorder.serverCheck(
                "both seeded rows exist server-side (parsed descriptions)",
                ctx.api.transactionRows(acctId).let { rows ->
                    rows.any { it.optString("description") == hit } &&
                        rows.any { it.optString("description") == miss }
                },
            )
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }

    /** LUI-21 (P2): double-tap during isSubmitting → ≤1 outgoing create
     *  (M09). `performClick` waits for Compose idleness, so a second
     *  `performClick` can land after the first request already completed —
     *  that proves nothing. This uses REAL UiAutomator input clicks on the
     *  button's screen bounds, back-to-back, while the attested
     *  `request_delay_ms` holds the first request open. Without the delay
     *  mechanism the overlap cannot be proven → BLOCKED, not a weak PASS. */
    @Test
    fun lui21_doubleTapSubmitAtMostOneCreate() {
        val ctx = begin(LiveCaseIds.LUI_21)
        try {
            RunOwnership.requireRequestDelay(ctx.attestation)
            val (acct, acctId) = ctxFor(ctx)
            driver.launchMain()
            signInViaUi(ctx, ctx.fixture.username, ctx.fixture.password)
            val note = ctx.fixture.txnNote("dbl")

            driver.navAccounts()
            driver.tap(acct)
            driver.tap("Add transaction")
            driver.tap("Expense")
            driver.typeText("Amount", "9.99")
            driver.typeText("Note (optional)", note)

            val start = System.nanoTime()
            driver.dismissKeyboard() // reveal the submit button
            driver.realDoubleTap("Save transaction") // real input events — unsynced
            driver.waitCommittedText(signedAmount("Expense", "9.99"),
                timeoutMs = 60_000)
            ctx.recorder.freshness("single created row visible",
                (System.nanoTime() - start) / 1_000_000)

            val count = ctx.api.countTransactions(acctId, note)
            ctx.recorder.serverCheck(
                "exactly one create landed under a real in-flight double-tap",
                count == 1, "count=$count",
            )
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }
}
