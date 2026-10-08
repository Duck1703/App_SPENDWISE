package com.spendwise.android.feature.transactions

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
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
        var createTransfer: Int = 0,
        var createAccount: Int = 0,
        var retries: Int = 0,
        var refreshes: Int = 0,
        var accountFilter: String? = "unset",
        var typeFilter: TransactionType? = null,
        var categoryFilter: CategorySelection? = null,
        var provenanceFilter: TransactionProvenance? = null,
        var fromFilter: java.time.LocalDate? = null,
        var toFilter: java.time.LocalDate? = null,
        var fromClicks: Int = 0,
        var toClicks: Int = 0,
        var loadMoreCalls: Int = 0,
        var cleared: Int = 0,
        var searchChange: String? = null,
        var searchCleared: Int = 0,
    )

    private fun screen(
        state: TransactionsUiState,
        options: FilterOptions = FilterOptions(
            accounts = listOf(account("acc-1", "Wallet")),
            categories = listOf("FOOD", "TRANSPORT", "HOUSING", "ENTERTAINMENT", "SALARY"),
        ),
        filters: TransactionFilters =
            (state as? TransactionsUiState.Content)?.filters ?: TransactionFilters(),
        searchInput: String = "",
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
                    onCreateTransfer = { harness.createTransfer++ },
                    onCreateAccount = { harness.createAccount++ },
                    onAccountFilter = { harness.accountFilter = it },
                    onTypeFilter = { harness.typeFilter = it },
                    onCategoryFilter = { harness.categoryFilter = it },
                    onProvenanceFilter = { harness.provenanceFilter = it },
                    onFromFilter = { harness.fromFilter = it },
                    onToFilter = { harness.toFilter = it },
                    searchInput = searchInput,
                    onSearchQueryChange = { harness.searchChange = it },
                    onClearSearch = { harness.searchCleared++ },
                    onLoadMore = { harness.loadMoreCalls++ },
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
    fun header_transferActionFiresWithTwoAccounts() {
        val harness = Harness()
        screen(
            TransactionsUiState.Empty,
            options = FilterOptions(
                accounts = listOf(account("acc-1", "Wallet"), account("acc-2", "Savings")),
            ),
            harness = harness,
        )

        composeRule.onNodeWithContentDescription("Transfer between accounts")
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, harness.createTransfer)
    }

    @Test
    fun header_transferActionHidesWithFewerThanTwoAccounts() {
        // Fewer than two accounts — the affordance hides rather than
        // opening a form that can't be completed.
        screen(
            TransactionsUiState.Empty,
            options = FilterOptions(accounts = listOf(account("acc-1", "Wallet"))),
        )
        composeRule.onNodeWithContentDescription("Transfer between accounts")
            .assertDoesNotExist()
    }

    @Test
    fun content_listsRowsAndShowsHeaderAddAction() {
        val harness = Harness()
        screen(
            TransactionsUiState.Content(
                items = listOf(txn("t1"), txn("t2", type = TransactionType.INCOME, category = "SALARY")),
                accountNames = mapOf("acc-1" to "Wallet"),
                filters = TransactionFilters(),
                totalElements = 2,
                hasMore = false,
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
        // Count text + the end-of-ledger marker (hasMore=false).
        composeRule.onNodeWithText("2 transactions").assertIsDisplayed()
        composeRule.onNodeWithText(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.transactions_end_of_list),
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
                totalElements = 1,
                hasMore = false,
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
                totalElements = 1,
                hasMore = false,
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
    fun filterRow_rendersSixChipsInOneRow() {
        screen(
            TransactionsUiState.Content(
                items = listOf(txn("t1")),
                accountNames = emptyMap(),
                filters = TransactionFilters(),
                totalElements = 1,
                hasMore = false,
            ),
        )

        composeRule.onNodeWithContentDescription("Account filter: all").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Type filter: all").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Category filter: all").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Source filter: all").assertIsDisplayed()
        // M15: the From/To date-range chips exist (server-side
        // occurredOn bounds). The chip row scrolls horizontally — a
        // physical 411dp-wide Pixel shows ~4 chips, so scroll the tail
        // into view.
        composeRule.onNodeWithContentDescription("From filter: all")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("To filter: all")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun noMatches_offersClearFilters() {
        val harness = Harness()
        screen(
            TransactionsUiState.Content(
                items = emptyList(),
                accountNames = emptyMap(),
                filters = TransactionFilters(type = TransactionType.EXPENSE),
                totalElements = 0,
                hasMore = false,
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
                totalElements = 1,
                hasMore = false,
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
                totalElements = 2,
                hasMore = false,
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

    // --- M15: paging + date-range ------------------------------------------

    @Test
    fun content_showsServerTotalWhenPartiallyLoaded() {
        screen(
            TransactionsUiState.Content(
                items = listOf(txn("t1")),
                accountNames = emptyMap(),
                filters = TransactionFilters(),
                totalElements = 57,
                hasMore = true,
            ),
        )

        // Server count, not the loaded slice.
        composeRule.onNodeWithText("Showing 1 of 57").assertIsDisplayed()
    }

    @Test
    fun scrollingToEnd_requestsNextPage() {
        val harness = Harness()
        screen(
            TransactionsUiState.Content(
                items = (1..30).map { txn("t$it") },
                accountNames = emptyMap(),
                filters = TransactionFilters(),
                totalElements = 60,
                hasMore = true,
            ),
            harness = harness,
        )

        composeRule.onNode(androidx.compose.ui.test.hasScrollToIndexAction())
            .performScrollToIndex(29)
        composeRule.waitForIdle()
        assertEquals(true, harness.loadMoreCalls > 0)
    }

    @Test
    fun footer_showsSpinnerWhileAppending() {
        screen(
            TransactionsUiState.Content(
                items = listOf(txn("t1")),
                accountNames = emptyMap(),
                filters = TransactionFilters(),
                totalElements = 3,
                hasMore = true,
                loadingMore = true,
            ),
        )

        composeRule.onNodeWithContentDescription("Loading more transactions")
            .assertIsDisplayed()
    }

    @Test
    fun footer_showsInlineErrorOnAppendFailure() {
        screen(
            TransactionsUiState.Content(
                items = listOf(txn("t1")),
                accountNames = emptyMap(),
                filters = TransactionFilters(),
                totalElements = 3,
                hasMore = true,
                moreError = TransactionsError.Network,
            ),
        )

        composeRule.onNodeWithText(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .targetContext.getString(R.string.transactions_more_error_title),
        ).assertIsDisplayed()
    }

    @Test
    fun dateChip_showsSelectedDateAndClears() {
        val harness = Harness()
        screen(
            TransactionsUiState.Content(
                items = listOf(txn("t1")),
                accountNames = emptyMap(),
                filters = TransactionFilters(
                    from = java.time.LocalDate.of(2026, 9, 1),
                ),
                totalElements = 1,
                hasMore = false,
            ),
            harness = harness,
        )

        // Selected chip announces "From filter: <formatted date>" —
        // scrolled into view first (horizontal chip row on a real phone).
        composeRule.onNodeWithContentDescription(
            "From filter: " +
                com.spendwise.android.core.ui.format.DateFormat.format(
                    java.time.LocalDate.of(2026, 9, 1),
                ),
        ).performScrollTo().assertIsDisplayed()
        // The clear affordance nulls the bound.
        composeRule.onNodeWithContentDescription("Clear From date filter")
            .performScrollTo()
            .performClick()
        assertEquals(null, harness.fromFilter)
    }

    @Test
    fun dateChip_opensPickerDialog() {
        screen(
            TransactionsUiState.Content(
                items = listOf(txn("t1")),
                accountNames = emptyMap(),
                filters = TransactionFilters(),
                totalElements = 1,
                hasMore = false,
            ),
        )

        composeRule.onNodeWithContentDescription("To filter: all")
            .performScrollTo()
            .performClick()
        // The Material3 DatePickerDialog opens with confirm/dismiss actions.
        composeRule.waitForIdle()
        composeRule.onNodeWithText(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .targetContext.getString(R.string.import_result_done),
        ).assertIsDisplayed()
    }

    // --- M18: server-side note search ---------------------------------------

    @Test
    fun searchField_rendersWithTalkBackLabelAndPlaceholder() {
        screen(
            TransactionsUiState.Content(
                items = listOf(txn("t1")),
                accountNames = emptyMap(),
                filters = TransactionFilters(),
                totalElements = 1,
                hasMore = false,
            ),
        )

        composeRule.onNodeWithContentDescription("Search transactions")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Search notes…", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun searchField_typingForwardsEveryKeystroke() {
        val harness = Harness()
        screen(
            TransactionsUiState.Content(
                items = listOf(txn("t1")),
                accountNames = emptyMap(),
                filters = TransactionFilters(),
                totalElements = 1,
                hasMore = false,
            ),
            harness = harness,
        )

        // The field is stateless — the VM owns the value and the debounce.
        // What the screen must guarantee: every keystroke reaches the
        // handler so the applied query can follow it.
        composeRule.onNodeWithContentDescription("Search transactions")
            .performTextInput("coffee")
        assertEquals("coffee", harness.searchChange)
    }

    @Test
    fun searchField_clearAffordanceOnlyWithText_andClears() {
        val harness = Harness()
        screen(
            TransactionsUiState.Content(
                items = listOf(txn("t1")),
                accountNames = emptyMap(),
                filters = TransactionFilters(query = "coffee"),
                totalElements = 1,
                hasMore = false,
            ),
            searchInput = "coffee",
            harness = harness,
        )

        composeRule.onNodeWithContentDescription("Clear search")
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, harness.searchCleared)
    }

    @Test
    fun searchField_noClearAffordanceWhenEmpty() {
        screen(
            TransactionsUiState.Content(
                items = listOf(txn("t1")),
                accountNames = emptyMap(),
                filters = TransactionFilters(),
                totalElements = 1,
                hasMore = false,
            ),
        )

        composeRule.onNodeWithContentDescription("Clear search").assertDoesNotExist()
    }
}
