package com.spendwise.android.feature.transactions

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.R
import com.spendwise.android.TestFormats
import com.spendwise.android.core.designsystem.SpendwiseTheme
import com.spendwise.android.core.model.Account
import com.spendwise.android.core.model.AccountType
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.model.Transaction
import com.spendwise.android.core.model.TransactionProvenance
import com.spendwise.android.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/** Stateless-screen tests for the transactions list (S09). */
@RunWith(AndroidJUnit4::class)
class TransactionsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun txn(
        id: String,
        type: TransactionType = TransactionType.EXPENSE,
        amount: String = "25.50",
        category: String? = "FOOD",
        sourceRef: String? = "MANUAL:$id",
    ) = Transaction(
        id = id,
        accountId = "acc-1",
        type = type,
        amount = Money(BigDecimal(amount), CurrencyCode("USD")),
        category = category,
        sourceRef = sourceRef,
        occurredOn = java.time.LocalDate.of(2026, 10, 4),
    )

    private fun account(id: String, name: String) = Account(
        id = id,
        name = name,
        type = AccountType.CASH,
        openingBalance = Money(BigDecimal("1000.00"), CurrencyCode("USD")),
        balance = Money(BigDecimal("1250.50"), CurrencyCode("USD")),
    )

    private data class Harness(
        var opened: String? = null,
        var createTxn: Int = 0,
        var createAccount: Int = 0,
        var retries: Int = 0,
        var refreshes: Int = 0,
        var accountFilter: String? = "unset",
        var typeFilter: TransactionType? = null,
        var categoryFilter: CategorySelection? = null,
        var provenanceFilter: TransactionProvenance? = null,
        var cleared: Int = 0,
    )

    private fun screen(
        state: TransactionsUiState,
        options: FilterOptions = FilterOptions(
            accounts = listOf(account("acc-1", "Wallet")),
            categories = listOf("FOOD", "TRANSPORT", "HOUSING", "ENTERTAINMENT", "SALARY"),
        ),
        filters: TransactionFilters =
            (state as? TransactionsUiState.Content)?.filters ?: TransactionFilters(),
        harness: Harness = Harness(),
    ) {
        composeRule.setContent {
            SpendwiseTheme {
                TransactionsScreen(
                    state = state,
                    options = options,
                    filters = filters,
                    onOpenTransaction = { harness.opened = it },
                    onCreateTransaction = { harness.createTxn++ },
                    onCreateAccount = { harness.createAccount++ },
                    onAccountFilter = { harness.accountFilter = it },
                    onTypeFilter = { harness.typeFilter = it },
                    onCategoryFilter = { harness.categoryFilter = it },
                    onProvenanceFilter = { harness.provenanceFilter = it },
                    onClearFilters = { harness.cleared++ },
                    onRefresh = { harness.refreshes++ },
                    onRetry = { harness.retries++ },
                )
            }
        }
    }

    @Test
    fun loading_showsLoadingSemantics() {
        screen(TransactionsUiState.Loading)

        composeRule.onNodeWithContentDescription("Loading").assertIsDisplayed()
    }

    @Test
    fun empty_offersCreateTransactionWhenAccountsExist() {
        val harness = Harness()
        screen(TransactionsUiState.Empty, harness = harness)

        composeRule.onNodeWithText("No transactions yet").assertIsDisplayed()
        composeRule.onNodeWithText("Add transaction").performClick()
        assertEquals(1, harness.createTxn)
    }

    @Test
    fun empty_offersCreateAccountWhenNoneExist() {
        val harness = Harness()
        screen(TransactionsUiState.Empty, options = FilterOptions(), harness = harness)

        composeRule.onNodeWithText("Add your first account").performClick()
        assertEquals(1, harness.createAccount)
    }

    @Test
    fun content_listsRowsAndShowsHeaderAddAction() {
        val harness = Harness()
        screen(
            TransactionsUiState.Content(
                items = listOf(txn("t1"), txn("t2", type = TransactionType.INCOME, category = "SALARY")),
                accountNames = mapOf("acc-1" to "Wallet"),
                filters = TransactionFilters(),
            ),
            harness = harness,
        )

        // Signed, labelled rows — direction carried by sign + word + icon.
        // Rows are clearAndSetSemantics nodes: label text lives inside the
        // merged contentDescription, so assert on the row CD, not Text.
        composeRule.onNodeWithContentDescription(
            "Expense ${TestFormats.signedAmount("25.50", "USD", credit = false)} " +
                "USD Food on Wallet, Manual, " +
                TestFormats.date(java.time.LocalDate.of(2026, 10, 4)),
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "Income ${TestFormats.signedAmount("25.50", "USD", credit = true)} " +
                "USD Salary on Wallet, Manual, " +
                TestFormats.date(java.time.LocalDate.of(2026, 10, 4)),
        ).assertIsDisplayed()
        // Count text + the server-order honesty note.
        composeRule.onNodeWithText("2 transactions").assertIsDisplayed()
        composeRule.onNodeWithText(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.transactions_list_note),
        ).assertIsDisplayed()
        // Header add action, not a FAB.
        composeRule.onNodeWithContentDescription("Add transaction").performClick()
        assertEquals(1, harness.createTxn)
    }

    @Test
    fun rowClick_opensThatTransaction() {
        val harness = Harness()
        screen(
            TransactionsUiState.Content(
                items = listOf(txn("t7")),
                accountNames = mapOf("acc-1" to "Wallet"),
                filters = TransactionFilters(),
            ),
            harness = harness,
        )

        // Merged row semantics — single clickable node (provenance suffix
        // included: MANUAL renders "…, Manual"; M11 appends the server
        // occurrence date last).
        composeRule.onNodeWithContentDescription(
            "Expense ${TestFormats.signedAmount("25.50", "USD", credit = false)} " +
                "USD Food on Wallet, Manual, " +
                com.spendwise.android.core.ui.format.DateFormat.format(
                    java.time.LocalDate.of(2026, 10, 4),
                ),
        ).performClick()
        assertEquals("t7", harness.opened)
    }

    @Test
    fun row_showsTheServerOccurrenceDate() {
        screen(
            TransactionsUiState.Content(
                items = listOf(txn("t7")),
                accountNames = mapOf("acc-1" to "Wallet"),
                filters = TransactionFilters(),
            ),
        )

        // The row merges descendants into one semantics node — the date
        // Text only exists in the unmerged tree.
        composeRule.onNodeWithText(
            com.spendwise.android.core.ui.format.DateFormat.format(
                java.time.LocalDate.of(2026, 10, 4),
            ),
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test
    fun filterRow_rendersFourChipsInOneRow() {
        screen(
            TransactionsUiState.Content(
                items = listOf(txn("t1")),
                accountNames = emptyMap(),
                filters = TransactionFilters(),
            ),
        )

        composeRule.onNodeWithContentDescription("Account filter: all").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Type filter: all").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Category filter: all").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Source filter: all").assertIsDisplayed()
        // No date/search/sort controls exist anywhere on this screen.
        composeRule.onAllNodesWithText("Date", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Search", substring = true).assertCountEquals(0)
    }

    @Test
    fun noMatches_offersClearFilters() {
        val harness = Harness()
        screen(
            TransactionsUiState.Content(
                items = emptyList(),
                accountNames = emptyMap(),
                filters = TransactionFilters(type = TransactionType.EXPENSE),
            ),
            harness = harness,
        )

        composeRule.onNodeWithText("No matches").assertIsDisplayed()
        composeRule.onNodeWithText("Clear filters").performClick()
        assertEquals(1, harness.cleared)
    }

    @Test
    fun refreshError_showsBannerAndKeepsRows() {
        screen(
            TransactionsUiState.Content(
                items = listOf(txn("t1")),
                accountNames = mapOf("acc-1" to "Wallet"),
                filters = TransactionFilters(),
                refreshError = TransactionsError.Network,
            ),
        )

        composeRule.onNodeWithText(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.transactions_refresh_error_title),
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "Expense ${TestFormats.signedAmount("25.50", "USD", credit = false)} " +
                "USD Food on Wallet, Manual, " +
                TestFormats.date(java.time.LocalDate.of(2026, 10, 4)),
        ).assertIsDisplayed()
    }

    @Test
    fun networkError_retries() {
        val harness = Harness()
        screen(TransactionsUiState.Error(TransactionsError.Network), harness = harness)

        composeRule.onNodeWithText(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.state_network_title),
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, harness.retries)
    }

    @Test
    fun provenanceBadge_showsForNonManualRowsOnly() {
        screen(
            TransactionsUiState.Content(
                items = listOf(
                    txn("t1", sourceRef = "IMPORT:CSV:1"),
                    txn("t2", sourceRef = "MANUAL:t2", category = "SALARY"),
                ),
                accountNames = emptyMap(),
                filters = TransactionFilters(),
            ),
        )

        // Imported badge visible inside the merged row CD; the manual row
        // carries a "Manual" provenance suffix instead.
        composeRule.onNodeWithContentDescription(
            "Expense ${TestFormats.signedAmount("25.50", "USD", credit = false)} " +
                "USD Food on Transaction, Imported, " +
                TestFormats.date(java.time.LocalDate.of(2026, 10, 4)),
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "Expense ${TestFormats.signedAmount("25.50", "USD", credit = false)} " +
                "USD Salary on Transaction, Manual, " +
                TestFormats.date(java.time.LocalDate.of(2026, 10, 4)),
        ).assertIsDisplayed()
    }
}
