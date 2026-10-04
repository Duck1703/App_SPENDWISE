package com.spendwise.android.feature.accounts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.core.designsystem.SpendwiseTheme
import com.spendwise.android.core.model.Account
import com.spendwise.android.core.model.AccountType
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/** Stateless-screen tests for account detail (S08 header scope). */
@RunWith(AndroidJUnit4::class)
class AccountDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private data class Harness(var retries: Int = 0, var backs: Int = 0)

    private fun screen(state: AccountDetailUiState, harness: Harness = Harness()) {
        composeRule.setContent {
            SpendwiseTheme {
                AccountDetailScreen(
                    state = state,
                    onRetry = { harness.retries++ },
                    onNavigateBack = { harness.backs++ },
                )
            }
        }
    }

    private fun account() = Account(
        id = "a1",
        name = "Everyday cash",
        type = AccountType.CASH,
        openingBalance = Money(BigDecimal("1000.00"), CurrencyCode("VND")),
        balance = Money(BigDecimal("1250.50"), CurrencyCode("VND")),
    )

    @Test
    fun content_showsNameBalanceAndFields() {
        screen(AccountDetailUiState.Content(account()))

        composeRule.onNodeWithText("Everyday cash").assertIsDisplayed()
        composeRule.onNodeWithText("Current balance").assertIsDisplayed()
        composeRule.onNodeWithText("Type").assertIsDisplayed()
        composeRule.onNodeWithText("Cash").assertIsDisplayed()
        composeRule.onNodeWithText("Currency").assertIsDisplayed()
        composeRule.onNodeWithText("Opening balance").assertIsDisplayed()
    }

    @Test
    fun notFound_showsHonestStateAndBackAction() {
        val harness = Harness()
        screen(AccountDetailUiState.NotFound, harness)

        composeRule.onNodeWithText("Account not found").assertIsDisplayed()
        composeRule.onNodeWithText("Back to accounts").performClick()
        assertEquals(1, harness.backs)
    }

    @Test
    fun networkError_retries() {
        val harness = Harness()
        screen(AccountDetailUiState.Error(AccountsError.Network), harness)

        composeRule.onNodeWithText("Can’t reach Spendwise").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, harness.retries)
    }
}
