package com.spendwise.android.live.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.live.infrastructure.LiveCaseIds
import com.spendwise.android.live.infrastructure.RunOwnership
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Live UI E2E — account scenarios. Owners: LUI-03, LUI-06, LUI-14.
 */
@RunWith(AndroidJUnit4::class)
class LiveAccountE2eTest : LiveE2eTestBase() {

    /** LUI-03 (P0): create account via UI → row appears without refresh. */
    @Test
    fun lui03_createAccountViaUi() {
        val ctx = begin(LiveCaseIds.LUI_03)
        try {
            ctx.api.register(ctx.fixture.username, ctx.fixture.password)
            driver.launchMain()
            signInViaUi(ctx, ctx.fixture.username, ctx.fixture.password)

            val name = ctx.fixture.accountName("Wallet")
            driver.navAccounts()
            driver.tapAnyOf("Add account", "Add your first account")
            driver.waitText("Account name")
            driver.typeText("Account name", name)
            driver.tapCd("Currency")
            driver.tap("USD", substring = true)
            driver.typeText("Opening balance", "250.00")
            val ms = driver.tapAndMeasureFresh("Create account", name)
            ctx.recorder.freshness("new account row visible without refresh", ms)
            ctx.api.login(ctx.fixture.username, ctx.fixture.password)
            ctx.recorder.serverCheck(
                "POST /api/accounts persisted the new account",
                ctx.api.getAccounts().contains(name),
            )
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }

    /** LUI-06 (P0): account with ≥51 seeded rows → page 1 actually appends.
     *  M02 fix: server ordering is `occurredOn DESC, id DESC` — NOT
     *  insertion order — so seeds carry DISTINCT dates (r00 = 2020-01-01
     *  oldest … r51 = 2020-02-22 newest). The expected tail is then
     *  deterministic AND verified against the parsed page envelope. */
    @Test
    fun lui06_accountHistoryPageTwoLoads() {
        val ctx = begin(LiveCaseIds.LUI_06)
        try {
            RunOwnership.requireBulkSeed(ctx.attestation, 52)
            ctx.api.register(ctx.fixture.username, ctx.fixture.password)
            ctx.api.login(ctx.fixture.username, ctx.fixture.password)
            val acctName = ctx.fixture.accountName("Deep")
            val acctId = ctx.api.seedAccount(acctName, opening = "0")
            val oldest = ctx.fixture.txnNote("r00")
            val secondOldest = ctx.fixture.txnNote("r01")
            val newest = ctx.fixture.txnNote("r51")
            // Rows never render the note — the visible unique marker is the
            // signed amount ("−{locale-formatted} USD"), derived exactly the
            // way SwTransactions builds it (U+2212 minus + DecimalFormat).
            val df = java.text.DecimalFormat().apply {
                decimalFormatSymbols = java.text.DecimalFormatSymbols
                    .getInstance(java.util.Locale.getDefault())
                minimumFractionDigits = 2
                maximumFractionDigits = 2
                isGroupingUsed = true
            }
            val oldestUi = "−${df.format(1)} USD"
            val secondOldestUi = "−${df.format(2)} USD"
            val day0 = java.time.LocalDate.of(2020, 1, 1)
            for (i in 0..51) {
                ctx.api.seedTransaction(
                    acctId, "EXPENSE", "%d.00".format(i + 1), "USD", "FOOD",
                    ctx.fixture.txnNote("r%02d".format(i)),
                    occurredOn = day0.plusDays(i.toLong()).toString(), // distinct → deterministic order
                )
            }
            ctx.recorder.step(
                "API-SEEDED 52 rows — r00=2020-01-01 (oldest) .. r51=2020-02-21 (newest)")

            driver.launchMain()
            signInViaUi(ctx, ctx.fixture.username, ctx.fixture.password)
            driver.navAccounts()
            driver.tap(acctName)
            driver.waitText("Transactions")
            // Newest-first list: scroll to the OLDEST row — only reachable
            // if page 1 loaded and appended without duplicates or gaps.
            val start = System.currentTimeMillis()
            driver.scrollToText(oldestUi, timeoutMs = 120_000)
            ctx.recorder.freshness("oldest page-2 sentinel r00 visible",
                System.currentTimeMillis() - start)
            driver.waitText(secondOldestUi, timeoutMs = 30_000)
            ctx.recorder.assertion("page-1 rows r00+r01 appended")

            // M03: parsed envelope checks — exact ids, counts, boundaries.
            val page0 = ctx.api.transactionPageJson(acctId, page = 0)
            val page1 = ctx.api.transactionPageJson(acctId, page = 1)
            ctx.recorder.serverCheck("page0 size=50 (full first page)",
                page0.getJSONArray("content").length() == 50)
            ctx.recorder.serverCheck("page0 newest is r51 (occurredOn 2020-02-21)",
                page0.getJSONArray("content").getJSONObject(0)
                    .optString("description") == newest)
            ctx.recorder.serverCheck("page0 hasNext=true → deeper page exists",
                page0.getBoolean("hasNext"))
            ctx.recorder.serverCheck(
                "page1 holds exactly the 2 oldest sentinels r01+r00, ordered",
                page1.getJSONArray("content").let { c ->
                    c.length() == 2 &&
                        c.getJSONObject(0).optString("description") == secondOldest &&
                        c.getJSONObject(1).optString("description") == oldest
                },
            )
            ctx.recorder.serverCheck("page1 hasNext=false — tail reached",
                !page1.getBoolean("hasNext"))
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }

    /** LUI-14 (P1): rename → archive → unarchive via UI. */
    @Test
    fun lui14_renameArchiveUnarchiveAccount() {
        val ctx = begin(LiveCaseIds.LUI_14)
        try {
            ctx.api.register(ctx.fixture.username, ctx.fixture.password)
            ctx.api.login(ctx.fixture.username, ctx.fixture.password)
            val original = ctx.fixture.accountName("Orig")
            ctx.api.seedAccount(original)
            driver.launchMain()
            signInViaUi(ctx, ctx.fixture.username, ctx.fixture.password)

            driver.navAccounts()
            driver.tap(original)
            driver.waitText("Edit account")
            driver.tap("Edit account")
            val renamed = ctx.fixture.accountName("Renamed")
            driver.typeText("Account name", renamed)
            val msRename = driver.tapAndMeasureFresh("Save changes", renamed)
            ctx.recorder.freshness("rename reflected on detail without refresh", msRename)

            driver.tap("Edit account")
            // The archive control is a Switch + Save — toggle it on, then
            // submit; the badge is the committed post-mutation marker.
            driver.toggleSwitch(on = true)
            val msArchive = driver.tapAndMeasureFresh(
                "Save changes", "Archived", substring = true)
            ctx.recorder.freshness("archive state shown", msArchive)

            driver.tap("Edit account")
            driver.toggleSwitch(on = false)
            driver.tap("Save changes")
            driver.waitTextGone("Archived")
            ctx.recorder.assertion("unarchive reflected — Archived badge gone")
            ctx.recorder.serverCheck(
                "renamed account persisted on server",
                ctx.api.getAccounts().contains(renamed),
            )
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }
}
