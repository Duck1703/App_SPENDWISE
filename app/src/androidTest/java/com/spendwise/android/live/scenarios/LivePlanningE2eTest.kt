package com.spendwise.android.live.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.live.infrastructure.LiveCaseIds
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Live UI E2E — planning surfaces (More hub rows use `clearAndSetSemantics`
 * with contentDescription = label, verified in MoreScreen.kt).
 * Owners: LUI-11 (budgets), LUI-12 (recurring), LUI-13 (rules).
 * Server verification uses the real GET routes — no notes-as-assertions.
 */
@RunWith(AndroidJUnit4::class)
class LivePlanningE2eTest : LiveE2eTestBase() {

    private val period = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
    private val nextDue = LocalDate.now().plusMonths(1).withDayOfMonth(1)
        .format(DateTimeFormatter.ISO_LOCAL_DATE)

    private fun signedInCtx(ctx: Ctx): String {
        ctx.api.register(ctx.fixture.username, ctx.fixture.password)
        ctx.api.login(ctx.fixture.username, ctx.fixture.password)
        val acct = ctx.fixture.accountName("Plan")
        ctx.api.seedAccount(acct, currency = "USD", opening = "1000.00")
        driver.launchMain()
        signInViaUi(ctx, ctx.fixture.username, ctx.fixture.password)
        return acct
    }

    /** LUI-11 (P1): budget create → edit → delete lifecycle via UI. */
    @Test
    fun lui11_budgetLifecycle() {
        val ctx = begin(LiveCaseIds.LUI_11)
        try {
            signedInCtx(ctx)
            driver.navMore()
            driver.tapCd("Budgets")
            driver.tapAnyOf("New budget", "Set a budget")
            driver.selectDropdown("Category", "Food")
            driver.typeText("Month", period)
            driver.typeText("Limit", "200.00")
            ctx.recorder.freshness("budget row appears without refresh",
                driver.tapAndMeasureFresh("Save budget", "Food"))

            driver.tap("Food")
            driver.tapCd("Edit budget")
            driver.waitText("Save")
            driver.typeText("Limit", "250.00")
            ctx.recorder.freshness("edited limit reflected",
                driver.tapAndMeasureFresh("Save", "250", substring = true))

            driver.tap("Delete budget", substring = true)
            ctx.recorder.freshness("budget removed from list",
                driver.tapDialogAndMeasureFresh(
                    "Delete", "Delete this budget?", "No budgets", substring = true))

            // M03: parsed list — zero budget rows remain for this user.
            ctx.recorder.serverCheck(
                "server has zero budgets for this user after delete",
                org.json.JSONArray(ctx.api.get("/api/budgets")).length() == 0,
            )
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }

    /** LUI-12 (P1): recurring create → edit → deactivate via UI. */
    @Test
    fun lui12_recurringLifecycle() {
        val ctx = begin(LiveCaseIds.LUI_12)
        try {
            val acct = signedInCtx(ctx)
            driver.navMore()
            driver.tapCd("Recurring items")
            driver.tap("New recurring item", substring = true)
            driver.selectDropdown("Account", acct)
            driver.typeText("Amount", "9.99")
            driver.typeText("Next due date", nextDue)
            // The committed marker is the row's rendered amount — locale
            // decimal separator applies ("−9,99 USD" in vi).
            val df = java.text.DecimalFormat().apply {
                decimalFormatSymbols = java.text.DecimalFormatSymbols
                    .getInstance(java.util.Locale.getDefault())
                minimumFractionDigits = 2
                maximumFractionDigits = 2
                isGroupingUsed = true
            }
            val marker99 = "${df.format(9.99.toBigDecimal())} USD"
            val marker1099 = "${df.format(10.99.toBigDecimal())} USD"
            ctx.recorder.freshness("recurring row appears",
                driver.tapAndMeasureFresh("Save recurring item", marker99))

            driver.tap(marker99, substring = true)
            driver.tap("Edit")
            driver.typeText("Amount", "10.99")
            ctx.recorder.freshness("edited amount reflected",
                driver.tapAndMeasureFresh("Save changes", marker1099))

            driver.tap("Deactivate", substring = true)
            ctx.recorder.freshness("deactivated status shown",
                driver.tapDialogAndMeasureFresh(
                    "Deactivate", "Deactivate this recurring item?",
                    "Deactivated", substring = true))

            // M03: parsed list — the template exists with amount 10.99.
            ctx.recorder.serverCheck(
                "recurring template persisted on server with amount 10.99",
                org.json.JSONArray(ctx.api.get("/api/recurring-transactions")).let { arr ->
                    (0 until arr.length()).any {
                        arr.getJSONObject(it).optDouble("amount") == 10.99
                    }
                },
            )
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }

    /** LUI-13 (P1): categorization rule create → disable via UI. */
    @Test
    fun lui13_ruleLifecycle() {
        val ctx = begin(LiveCaseIds.LUI_13)
        try {
            signedInCtx(ctx)
            val keyword = ctx.fixture.txnNote("kw")
            driver.navMore()
            driver.tapCd("Categorization rules")
            driver.tap("New rule", substring = true)
            driver.typeText("Description text", keyword)
            driver.selectDropdown("Assign category", "Food")
            driver.typeText("Priority", "1")
            // The committed marker must be the saved row — the selected
            // "Food" dropdown value is already a committed node and would
            // satisfy the oracle vacuously before submit.
            ctx.recorder.freshness("rule row appears",
                driver.tapAndMeasureFresh("Save rule", keyword, substring = true))

            // Create pops back to the rules list — "Disable rule" lives on
            // the rule detail; open the row by its keyword-bearing cd.
            driver.tap(keyword, substring = true)
            driver.tap("Disable rule", substring = true)
            ctx.recorder.freshness("rule disabled state shown",
                driver.tapDialogAndMeasureFresh(
                    "Disable", "Disable this rule?", "Disabled", substring = true))

            // M03: parsed list — rule row carries the exact keyword.
            ctx.recorder.serverCheck(
                "rule persisted server-side with the keyword",
                org.json.JSONArray(ctx.api.get("/api/categorization-rules")).let { arr ->
                    (0 until arr.length()).any {
                        arr.getJSONObject(it).toString().contains(keyword)
                    }
                },
            )
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }
}
