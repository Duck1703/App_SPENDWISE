package com.spendwise.android.feature.export

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.core.designsystem.SpendwiseTheme
import com.spendwise.android.core.model.Account
import com.spendwise.android.core.model.AccountType
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** M9 Ledger Export — scope, phases, honest states, a11y labels. */
@RunWith(AndroidJUnit4::class)
class ExportLedgerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private data class Harness(
        var scopePicked: String? = "unset",
        var exportClicks: Int = 0,
        var retryClicks: Int = 0,
        var backClicks: Int = 0,
    )

    private fun twoAccounts() = listOf(
        account(id = "acc-1", name = "Wallet"),
        account(id = "acc-2", name = "Bank"),
    )

    private fun account(
        id: String,
        name: String,
        type: AccountType = AccountType.CASH,
        currency: String = "VND",
        balance: String = "1000.00",
    ) = Account(
        id = id,
        name = name,
        type = type,
        openingBalance = Money(BigDecimal(balance), CurrencyCode(currency)),
        balance = Money(BigDecimal(balance), CurrencyCode(currency)),
    )

    private fun screen(state: ExportUiState, harness: Harness = Harness()) {
        composeRule.setContent {
            SpendwiseTheme {
                ExportLedgerScreen(
                    state = state,
                    onScopeSelected = { harness.scopePicked = it },
                    onExport = { harness.exportClicks++ },
                    onRetry = { harness.retryClicks++ },
                    onBack = { harness.backClicks++ },
                )
            }
        }
    }

    @Test
    fun title_and_subtitle_render() {
        screen(ExportUiState.Ready(accounts = twoAccounts()))

        composeRule.onNodeWithText("Export ledger").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Spendwise generates a CSV of your transactions — you choose where to save it.",
        ).assertIsDisplayed()
    }

    @Test
    fun All_accounts_is_the_default_scope() {
        screen(ExportUiState.Ready(accounts = twoAccounts()))

        composeRule.onNodeWithText("All accounts").assertIsDisplayed()
    }

    @Test
    fun selecting_a_scope_forwards_the_account_id() {
        val harness = Harness()
        screen(ExportUiState.Ready(accounts = twoAccounts()), harness)

        // The anchored field (not its label) expands the menu.
        // The null-scope ("All accounts") → no-param contract is covered
        // by the ViewModel test `all-accounts scope sends null`.
        composeRule.onNodeWithContentDescription("Scope").performClick()
        composeRule.onNodeWithText("Bank").performClick()
        assertEquals("acc-2", harness.scopePicked)
    }

    @Test
    fun export_click_fires_in_idle() {
        val harness = Harness()
        screen(ExportUiState.Ready(accounts = twoAccounts()), harness)

        composeRule.onNodeWithText("Export CSV").assertIsEnabled().performClick()
        assertEquals(1, harness.exportClicks)
    }

    @Test
    fun downloading_phase_locks_the_export_action() {
        screen(
            ExportUiState.Ready(
                accounts = twoAccounts(),
                phase = ExportUiState.Phase.Downloading,
            ),
        )

        composeRule.onNodeWithText("Preparing export…").assertIsDisplayed()
        composeRule.onNodeWithText("Preparing export…").assertIsNotEnabled()
    }

    @Test
    fun saving_phase_shows_the_saving_label() {
        screen(
            ExportUiState.Ready(
                accounts = twoAccounts(),
                phase = ExportUiState.Phase.Saving,
            ),
        )

        composeRule.onNodeWithText("Saving file…").assertIsDisplayed()
    }

    @Test
    fun saved_banner_renders_after_a_completed_write() {
        screen(
            ExportUiState.Ready(accounts = twoAccounts(), saved = true),
        )

        composeRule.onNodeWithText("Ledger exported").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Your CSV was saved to the location you chose.",
        ).assertIsDisplayed()
    }

    @Test
    fun save_failure_shows_the_honest_save_error() {
        screen(
            ExportUiState.Ready(
                accounts = twoAccounts(),
                error = ExportError.SaveFailed,
            ),
        )

        composeRule.onNodeWithText("Export failed").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Couldn’t save the file — try exporting again.",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun account_unavailable_error_renders() {
        screen(
            ExportUiState.Ready(
                accounts = twoAccounts(),
                error = ExportError.AccountUnavailable,
            ),
        )

        composeRule.onNodeWithText("That account is no longer available.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun loading_renders_without_the_export_action() {
        screen(ExportUiState.Loading)

        composeRule.onNodeWithText("Export CSV").assertDoesNotExist()
    }

    @Test
    fun network_error_state_offers_retry() {
        val harness = Harness()
        screen(ExportUiState.Error(ExportError.Network), harness)

        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, harness.retryClicks)
    }

    @Test
    fun back_affordance_fires() {
        val harness = Harness()
        screen(ExportUiState.Ready(accounts = twoAccounts()), harness)

        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, harness.backClicks)
    }
}
