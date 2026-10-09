package com.spendwise.android.feature.accounts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.R
import com.spendwise.android.TestFormats
import com.spendwise.android.core.designsystem.SpendwiseTheme
import androidx.compose.ui.test.onNodeWithContentDescription
import com.spendwise.android.core.model.Account
import com.spendwise.android.core.model.AccountType
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.model.Transaction
import com.spendwise.android.core.model.TransactionType
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

    private data class Harness(
        var retries: Int = 0,
        var backs: Int = 0,
        var openedTxn: String? = null,
        var addFor: String? = null,
        var transferFor: String? = null,
        var editFor: String? = null,
        var historyRetries: Int = 0,
    )

    private fun screen(state: AccountDetailUiState, harness: Harness = Harness()) {
        composeRule.setContent {
            SpendwiseTheme {
                AccountDetailScreen(
                    state = state,
                    onRetry = { harness.retries++ },
                    onNavigateBack = { harness.backs++ },
                    onOpenTransaction = { harness.openedTxn = it },
                    onAddTransaction = { harness.addFor = it },
                    onTransfer = { harness.transferFor = it },
                    onEdit = { harness.editFor = it },
                    onRetryHistory = { harness.historyRetries++ },
                )
            }
        }
    }

    private fun account(archived: Boolean = false) = Account(
        id = "a1",
        name = "Everyday cash",
        type = AccountType.CASH,
        openingBalance = Money(BigDecimal("1000.00"), CurrencyCode("VND")),
        balance = Money(BigDecimal("1250.50"), CurrencyCode("VND")),
        archived = archived,
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

        composeRule.onNodeWithText(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.state_network_title),
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, harness.retries)
    }

    // ── M3: account-scoped transaction history ────────────────────────────

    private fun txn(id: String, type: TransactionType = TransactionType.EXPENSE) = Transaction(
        id = id,
        accountId = "a1",
        type = type,
        amount = Money(BigDecimal("25.50"), CurrencyCode("USD")),
        category = "FOOD",
        sourceRef = "MANUAL:$id",
        occurredOn = java.time.LocalDate.of(2026, 10, 4),
    )

    @Test
    fun history_listsRowsAndOpensThem() {
        val harness = Harness()
        screen(
            AccountDetailUiState.Content(
                account(),
                history = AccountDetailUiState.AccountHistory.Items(listOf(txn("t1"))),
            ),
            harness,
        )

        composeRule.onNodeWithText("Transactions").assertIsDisplayed()
        // Merged row CD carries the provenance + date suffix
        // (…, Manual, <localized date>).
        composeRule.onNodeWithContentDescription(
            "Expense ${TestFormats.signedAmount("25.50", "USD", credit = false)} USD " +
                "Food on Everyday cash, Manual, " +
                TestFormats.date(java.time.LocalDate.of(2026, 10, 4)),
        ).performScrollTo().performClick()
        assertEquals("t1", harness.openedTxn)
    }

    @Test
    fun history_addTransactionCarriesThisAccount() {
        val harness = Harness()
        screen(
            AccountDetailUiState.Content(
                account(),
                history = AccountDetailUiState.AccountHistory.Items(emptyList()),
            ),
            harness,
        )

        composeRule.onNodeWithText("No activity yet — record an expense or import a statement.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Add transaction").performClick()
        assertEquals("a1", harness.addFor)
    }

    @Test
    fun transferAction_prefillsThisAccountAsTheSource() {
        val harness = Harness()
        screen(
            AccountDetailUiState.Content(
                account(),
                hasTransferPeer = true,
                history = AccountDetailUiState.AccountHistory.Items(emptyList()),
            ),
            harness,
        )

        composeRule.onNodeWithText("Transfer").performClick()
        assertEquals("a1", harness.transferFor)
    }

    @Test
    fun transferAction_hiddenWhenNoSecondAccountExists() {
        screen(
            AccountDetailUiState.Content(
                account(),
                hasTransferPeer = false,
                history = AccountDetailUiState.AccountHistory.Items(emptyList()),
            ),
        )

        // Scroll the history header into view — "Add transaction" sits
        // in the same row where "Transfer" would be.
        composeRule.onNodeWithText("Add transaction")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Transfer").assertDoesNotExist()
    }

    // ── M19: edit entry + archived presentation ─────────────────────

    @Test
    fun editAction_opensTheEditorForThisAccount() {
        val harness = Harness()
        screen(
            AccountDetailUiState.Content(
                account(),
                history = AccountDetailUiState.AccountHistory.Items(emptyList()),
            ),
            harness,
        )

        composeRule.onNodeWithText("Edit account").performScrollTo().performClick()
        assertEquals("a1", harness.editFor)
    }

    @Test
    fun archived_showsStatusAndHidesEntryActions() {
        // M19 (REQ-BE-006): archived accounts keep ledger/history
        // visible but accept no new entries.
        screen(
            AccountDetailUiState.Content(
                account(archived = true),
                hasTransferPeer = true,
                history = AccountDetailUiState.AccountHistory.Items(listOf(txn("t1"))),
            ),
        )

        composeRule.onNodeWithText("Status").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Archived").performScrollTo().assertIsDisplayed()
        // Entry points are gone; the ledger rows remain readable.
        composeRule.onNodeWithText("Transactions").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Add transaction").assertDoesNotExist()
        composeRule.onNodeWithText("Transfer").assertDoesNotExist()
    }

    @Test
    fun historyError_retriesOnlyTheSection() {
        val harness = Harness()
        screen(
            AccountDetailUiState.Content(
                account(),
                history = AccountDetailUiState.AccountHistory.Error,
            ),
            harness,
        )

        // The account header stays intact — only the section failed.
        composeRule.onNodeWithText("Everyday cash").assertIsDisplayed()
        composeRule.onNodeWithText(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.account_detail_history_error),
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performScrollTo().performClick()
        assertEquals(1, harness.historyRetries)
    }
}
