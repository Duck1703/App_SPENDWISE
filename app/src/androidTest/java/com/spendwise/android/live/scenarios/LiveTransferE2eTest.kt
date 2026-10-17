package com.spendwise.android.live.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.live.infrastructure.LiveCaseIds
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Live UI E2E — transfers. Owner: LUI-09.
 * `POST /api/transfers` is atomic: two linked legs + both balances.
 */
@RunWith(AndroidJUnit4::class)
class LiveTransferE2eTest : LiveE2eTestBase() {

    @Test
    fun lui09_transferBetweenOwnAccounts() {
        val ctx = begin(LiveCaseIds.LUI_09)
        try {
            ctx.api.register(ctx.fixture.username, ctx.fixture.password)
            ctx.api.login(ctx.fixture.username, ctx.fixture.password)
            val from = ctx.fixture.accountName("From")
            val to = ctx.fixture.accountName("To")
            val fromId = ctx.api.seedAccount(from, currency = "USD", opening = "500.00")
            val toId = ctx.api.seedAccount(to, currency = "USD", opening = "0")
            driver.launchMain()
            signInViaUi(ctx, ctx.fixture.username, ctx.fixture.password)

            driver.navAccounts()
            driver.tap(from)
            driver.tap("Transfer")
            driver.waitText("Transfer money")
            driver.selectDropdown("To account", to)
            driver.typeText("Amount", "100.00")
            ctx.recorder.freshness("source balance reduced on detail",
                driver.tapAndMeasureFresh("Transfer", "400", substring = true))

            driver.pressBack()
            driver.tap(to)
            driver.waitText("100", substring = true, timeoutMs = 60_000)
            ctx.recorder.assertion("destination balance increased")

            // Server truth (M03): parsed legs — TRANSFER_OUT 100.00 on the
            // source account AND TRANSFER_IN 100.00 on the destination.
            val fromRows = ctx.api.transactionRows(fromId)
            val toRows = ctx.api.transactionRows(toId)
            ctx.recorder.serverCheck(
                "source leg TRANSFER_OUT 100.00 USD on the source account",
                fromRows.any {
                    it.optString("type") == "TRANSFER_OUT" &&
                        it.optDouble("amount") == 100.00 &&
                        it.optString("accountId") == fromId
                },
            )
            ctx.recorder.serverCheck(
                "destination leg TRANSFER_IN 100.00 USD on the dest account",
                toRows.any {
                    it.optString("type") == "TRANSFER_IN" &&
                        it.optDouble("amount") == 100.00 &&
                        it.optString("accountId") == toId
                },
            )
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }
}
