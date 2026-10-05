package com.spendwise.android.feature.accounts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.R
import com.spendwise.android.TestFormats
import com.spendwise.android.core.designsystem.SpendwiseTheme
import com.spendwise.android.core.model.AccountType
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.model.Account
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/** Stateless-screen tests for the accounts list: states, rows, mixed-currency, hero, callbacks. */
@RunWith(AndroidJUnit4::class)
class AccountsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

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

    private data class Harness(
        var opened: String? = null,
        var createClicks: Int = 0,
        var retries: Int = 0,
    )

    private fun screen(state: AccountsUiState, harness: Harness = Harness()) {
        composeRule.setContent {
            SpendwiseTheme {
                AccountsScreen(
                    state = state,
                    onOpenAccount = { harness.opened = it },
                    onCreateAccount = { harness.createClicks++ },
                    onRetry = { harness.retries++ },
                )
            }
        }
    }

    @Test
    fun emptyState_showsIllustrationAndCta() {
        val harness = Harness()
        screen(AccountsUiState.Empty, harness)

        composeRule.onNodeWithText("No accounts yet").assertIsDisplayed()
        composeRule.onNodeWithText("Add your first account").performClick()
        assertEquals(1, harness.createClicks)
    }

    @Test
    fun contentState_listsRowsWithBalances() {
        screen(
            AccountsUiState.Content(
                accounts = listOf(
                    account("a1", "Wallet", AccountType.CASH, "VND", "1250500"),
                    account("a2", "Checking", AccountType.BANK, "USD", "42.10"),
                ),
                mixedCurrencies = true,
            ),
        )

        composeRule.onNodeWithContentDescription(
            "Wallet, Cash, balance ${TestFormats.amount("1250500", "VND")} VND",
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "Checking, Bank, balance ${TestFormats.amount("42.10", "USD")} USD",
        ).assertIsDisplayed()
        // Mixed-currency banner shown; no cross-currency total exists.
        composeRule.onNodeWithText("Multiple currencies").assertIsDisplayed()
        composeRule.onNodeWithText("Totals are never mixed — each balance keeps its own currency.")
            .assertIsDisplayed()
    }

    @Test
    fun singleCurrency_showsAggregatedTotal() {
        screen(
            AccountsUiState.Content(
                accounts = listOf(
                    account("a1", "Wallet", AccountType.CASH, "VND", "1000"),
                    account("a2", "Jar", AccountType.CASH, "VND", "250"),
                ),
                mixedCurrencies = false,
            ),
        )

        composeRule.onNodeWithText("Total across accounts").assertIsDisplayed()
        // Sum rendered in-account-currency (VND → 0 fraction digits).
        composeRule.onNodeWithText(TestFormats.amount("1250", "VND")).assertIsDisplayed()
    }

    @Test
    fun rowClick_opensThatAccount() {
        val harness = Harness()
        screen(
            AccountsUiState.Content(
                accounts = listOf(account("a9", "Wallet")),
                mixedCurrencies = false,
            ),
            harness,
        )

        composeRule.onNodeWithContentDescription(
            "Wallet, Cash, balance ${TestFormats.amount("1000", "VND")} VND",
        ).performClick()

        assertEquals("a9", harness.opened)
    }

    @Test
    fun networkError_showsRetryCopyAndCallback() {
        val harness = Harness()
        screen(AccountsUiState.Error(AccountsError.Network), harness)

        composeRule.onNodeWithText(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.state_network_title),
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, harness.retries)
    }

    @Test
    fun serverError_showsServerCopyAndRetry() {
        val harness = Harness()
        screen(AccountsUiState.Error(AccountsError.Server), harness)

        composeRule.onNodeWithText("Something went wrong on our side").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, harness.retries)
    }

    @Test
    fun accountRows_carryMergedSemantics() {
        screen(
            AccountsUiState.Content(
                accounts = listOf(account("a1", "Wallet", AccountType.CASH, "VND", "1500")),
                mixedCurrencies = false,
            ),
        )

        composeRule.onNodeWithContentDescription(
            "Wallet, Cash, balance ${TestFormats.amount("1500", "VND")} VND",
        ).assertIsDisplayed()
    }

    @Test
    fun addAccountButton_reachesCallback() {
        val harness = Harness()
        screen(
            AccountsUiState.Content(
                accounts = listOf(account("a1", "Wallet")),
                mixedCurrencies = false,
            ),
            harness,
        )

        composeRule.onNodeWithText("Add account").performClick()

        assertEquals(1, harness.createClicks)
    }

    @Test
    fun refreshError_showsBannerAndKeepsRows() {
        screen(
            AccountsUiState.Content(
                accounts = listOf(account("a1", "Wallet")),
                mixedCurrencies = false,
                refreshError = AccountsError.Network,
            ),
        )

        composeRule.onNodeWithText(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.accounts_refresh_error_title),
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "Wallet, Cash, balance ${TestFormats.amount("1000", "VND")} VND",
        ).assertIsDisplayed()
    }

    @Test
    fun loadingState_announcesLoadingSemantics() {
        screen(AccountsUiState.Loading)

        composeRule.onNodeWithContentDescription("Loading").assertIsDisplayed()
    }
}
