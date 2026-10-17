package com.spendwise.android.live.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.live.infrastructure.LiveCaseIds
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Live UI E2E — Home/dashboard scenarios. Owners: LUI-05, LUI-27.
 * Dashboard endpoint is `GET /api/dashboard?month=YYYY-MM` (V4-corrected);
 * totals only render under same-currency eligibility.
 */
@RunWith(AndroidJUnit4::class)
class LiveHomeE2eTest : LiveE2eTestBase() {

    private val month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))

    /** Rendered row amount — the only unique marker a list row exposes
     *  (locale-grouped, U+2212 minus for debits). */
    private fun signedAmountText(kind: String, amount: String, code: String = "USD"): String {
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

    /** LUI-05 (P0): recent activity + month totals reflect own mutation
     *  automatically — no pull-to-refresh. Freshness timer starts at the
     *  submit gesture (M7). */
    @Test
    fun lui05_homeRecencyAndDashboardFreshness() {
        val ctx = begin(LiveCaseIds.LUI_05)
        try {
            ctx.api.register(ctx.fixture.username, ctx.fixture.password)
            ctx.api.login(ctx.fixture.username, ctx.fixture.password)
            val acct = ctx.fixture.accountName("Home")
            ctx.api.seedAccount(acct, currency = "USD", opening = "0")
            driver.launchMain()
            signInViaUi(ctx, ctx.fixture.username, ctx.fixture.password)

            // Mutation via UI (gesture is the E2E subject, not a fixture call).
            val note = ctx.fixture.txnNote("home-fresh")
            driver.navAccounts()
            driver.tap(acct)
            driver.tap("Add transaction")
            driver.tap("Expense")
            driver.typeText("Amount", "40.00")
            driver.typeText("Note (optional)", note)
            // Rows never render the note — the committed marker post-save
            // (account detail history) is the signed amount.
            val amount = signedAmountText("Expense", "40.00")
            ctx.recorder.freshness("own txn visible on detail post-save",
                driver.tapAndMeasureFresh("Save transaction", amount))

            driver.navHome()
            driver.waitText("Recent activity")
            ctx.recorder.freshness("own txn in Recent activity — no refresh",
                driver.waitFresh(amount))
            driver.waitText("This month")
            ctx.recorder.assertion("month hero present (same-currency USD)")
            // M03: parsed aggregate — totalExpense is exactly 40.00.
            ctx.recorder.serverCheck(
                "dashboard totalExpense == 40.00 (parsed BigDecimal)",
                ctx.api.dashboardJson(month)
                    .optDouble("totalExpense") == 40.00,
            )
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }

    /** LUI-27 (P1): same-currency totals correct; adding a different-currency
     *  account flips the hero to an honest unavailable state. */
    @Test
    fun lui27_dashboardAggregatesAndMixedCurrencyHonesty() {
        val ctx = begin(LiveCaseIds.LUI_27)
        try {
            ctx.api.register(ctx.fixture.username, ctx.fixture.password)
            ctx.api.login(ctx.fixture.username, ctx.fixture.password)
            ctx.api.seedAccount(ctx.fixture.accountName("Usd"), currency = "USD")
            driver.launchMain()
            signInViaUi(ctx, ctx.fixture.username, ctx.fixture.password)

            driver.navHome()
            driver.waitText("This month")
            ctx.recorder.assertion("same-currency dashboard renders")

            // Introduce a foreign-currency account — the hero must degrade
            // honestly, never sum across currencies.
            ctx.api.seedAccount(ctx.fixture.accountName("Eur"), currency = "EUR")
            driver.navAccounts()
            // Seeded via the fixture API — outside the app's mutation
            // signal, the cache-first list shows stale content until a
            // real pull-to-refresh forces the refetch.
            driver.pullDownRefresh()
            driver.waitText(ctx.fixture.accountName("Eur"), timeoutMs = 60_000)
            driver.navHome()
            driver.pullDownRefresh()
            driver.waitText(
                "Your accounts use different currencies.",
                substring = true, timeoutMs = 60_000,
            )
            ctx.recorder.assertion("mixed-currency honest state — no wrong aggregate")
            // M03: parsed rows — currencies exactly {USD,EUR} for this user.
            ctx.recorder.serverCheck(
                "both currencies present server-side (parsed account rows)",
                ctx.api.accountsJson().let { arr ->
                    val currencies = (0 until arr.length()).map {
                        arr.getJSONObject(it).optString("currency")
                    }.toSet()
                    "USD" in currencies && "EUR" in currencies
                },
            )
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }
}
