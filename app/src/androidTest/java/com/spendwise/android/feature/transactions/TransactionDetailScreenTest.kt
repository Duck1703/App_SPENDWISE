package com.spendwise.android.feature.transactions

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.R
import com.spendwise.android.core.designsystem.SpendwiseTheme
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.model.Transaction
import com.spendwise.android.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/** Stateless-screen tests for transaction detail (S10 — strictly read-only). */
@RunWith(AndroidJUnit4::class)
class TransactionDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun txn(
        type: TransactionType = TransactionType.EXPENSE,
        category: String? = "FOOD",
        sourceRef: String? = "MANUAL:t1",
    ) = Transaction(
        id = "t1",
        accountId = "acc-1",
        type = type,
        amount = Money(BigDecimal("25.50"), CurrencyCode("USD")),
        category = category,
        sourceRef = sourceRef,
    )

    private data class Harness(var retries: Int = 0, var backs: Int = 0)

    private fun screen(state: TransactionDetailUiState, harness: Harness = Harness()) {
        composeRule.setContent {
            SpendwiseTheme {
                TransactionDetailScreen(
                    state = state,
                    onRetry = { harness.retries++ },
                    onNavigateBack = { harness.backs++ },
                )
            }
        }
    }

    @Test
    fun content_showsTypeAmountAccountCurrencySource() {
        screen(TransactionDetailUiState.Content(txn(), accountName = "Wallet"))

        composeRule.onNodeWithText("Food").assertIsDisplayed()
        composeRule.onNodeWithText("−25.50 USD").assertIsDisplayed()
        composeRule.onNodeWithText("Account").assertIsDisplayed()
        composeRule.onNodeWithText("Wallet").assertIsDisplayed()
        composeRule.onNodeWithText("Currency").assertIsDisplayed()
        composeRule.onNodeWithText("USD").assertIsDisplayed()
        composeRule.onNodeWithText("Source").assertIsDisplayed()
        composeRule.onNodeWithText("Manual").assertIsDisplayed()
    }

    @Test
    fun content_isReadOnly_noEditDeleteOrCorrectAffordance() {
        screen(TransactionDetailUiState.Content(txn(), accountName = "Wallet"))

        composeRule.onNodeWithText(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.txn_detail_readonly),
        ).assertIsDisplayed()
        composeRule.onAllNodesWithText("Edit", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Delete", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Correct", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Reverse", substring = true).assertCountEquals(0)
    }

    @Test
    fun income_showsPlusSignAndWord() {
        screen(
            TransactionDetailUiState.Content(
                txn(type = TransactionType.INCOME, category = "SALARY"),
                accountName = "Wallet",
            ),
        )

        composeRule.onNodeWithText("+25.50 USD").assertIsDisplayed()
        composeRule.onNodeWithText("Income").assertIsDisplayed()
    }

    @Test
    fun notFound_offersBackToList() {
        val harness = Harness()
        screen(TransactionDetailUiState.NotFound, harness)

        composeRule.onNodeWithText("Transaction not found").assertIsDisplayed()
        composeRule.onNodeWithText("Back to transactions").performClick()
        assertEquals(1, harness.backs)
    }

    @Test
    fun networkError_retries() {
        val harness = Harness()
        screen(TransactionDetailUiState.Error(TransactionsError.Network), harness)

        composeRule.onNodeWithText(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.state_network_title),
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, harness.retries)
    }
}
