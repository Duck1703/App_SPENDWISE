package com.spendwise.android.feature.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.TestFormats
import com.spendwise.android.core.designsystem.SpendwiseTheme
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
import androidx.compose.ui.unit.dp
import java.math.BigDecimal

/**
 * Stateless-screen tests for Home (S05): hero §17 states, account
 * carousel, activity panel, empty/error states, callbacks, and the
 * merged account-card semantics.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun account(
        id: String,
        name: String,
        type: AccountType = AccountType.CASH,
        currency: String = "VND",
        balance: String = "1000",
    ) = Account(
        id = id,
        name = name,
        type = type,
        openingBalance = Money(BigDecimal(balance), CurrencyCode(currency)),
        balance = Money(BigDecimal(balance), CurrencyCode(currency)),
    )

    private fun transaction(
        id: String,
        accountId: String = "a1",
        amount: String = "25.50",
        type: TransactionType = TransactionType.EXPENSE,
    ) = Transaction(
        id = id,
        accountId = accountId,
        type = type,
        amount = Money(BigDecimal(amount), CurrencyCode("VND")),
        category = "FOOD",
        sourceRef = "MANUAL:$id",
    )

    private data class Harness(
        var addClicks: Int = 0,
        var openedAccount: String? = null,
        var openedTransaction: String? = null,
        var seeAllClicks: Int = 0,
        var createAccountClicks: Int = 0,
        var importClicks: Int = 0,
        var refreshClicks: Int = 0,
        var retryTotalsClicks: Int = 0,
        var retryActivityClicks: Int = 0,
    )

    private fun screen(state: HomeUiState, harness: Harness = Harness()) {
        composeRule.setContent {
            SpendwiseTheme {
                HomeScreen(
                    state = state,
                    onAddTransaction = { harness.addClicks++ },
                    onOpenAccount = { harness.openedAccount = it },
                    onOpenTransaction = { harness.openedTransaction = it },
                    onSeeAllActivity = { harness.seeAllClicks++ },
                    onImport = { harness.importClicks++ },
                    onCreateAccount = { harness.createAccountClicks++ },
                    onRefresh = { harness.refreshClicks++ },
                    onRetryTotals = { harness.retryTotalsClicks++ },
                    onRetryActivity = { harness.retryActivityClicks++ },
                )
            }
        }
    }

    private fun content(
        accounts: List<Account> = listOf(account("a1", "Wallet")),
        totals: MonthTotals = MonthTotals.Loaded(
            income = BigDecimal("4500"),
            expense = BigDecimal("1200"),
            currency = CurrencyCode("VND"),
        ),
        activity: ActivityState = ActivityState.Items(listOf(transaction("t1"))),
        isRefreshing: Boolean = false,
        refreshError: HomeError? = null,
    ) = HomeUiState.Content(
        accounts = accounts,
        totals = totals,
        activity = activity,
        isRefreshing = isRefreshing,
        refreshError = refreshError,
    )

    @Test
    fun heroLoaded_showsIncomeAndExpenseFigures() {
        screen(content())

        composeRule.onNodeWithText("This month").assertIsDisplayed()
        // Merged figure semantics — figures read as one unit.
        composeRule.onNodeWithContentDescription(
            "Income ${TestFormats.amount("4500", "VND")} VND",
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "Spent ${TestFormats.signedAmount("1200", "VND", credit = false)} VND",
        ).assertIsDisplayed()
        // Currency is announced inside the merged figure CDs — no stray
        // unlabeled "VND" text node should exist alongside them.
        composeRule.onAllNodesWithText("VND").assertCountEquals(0)
    }

    @Test
    fun heroUnavailable_showsTheHonestMixedCurrencyCopy() {
        screen(content(totals = MonthTotals.Unavailable))

        composeRule.onNodeWithText("Combined totals unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Your accounts use different currencies.").assertIsDisplayed()
        // No figure is ever fabricated.
        composeRule.onNodeWithText("Income").assertDoesNotExist()
    }

    @Test
    fun heroFailed_showsInlineRetryThatReachesCallback() {
        val harness = Harness()
        screen(content(totals = MonthTotals.Failed(HomeError.Network)), harness)

        composeRule.onNodeWithText("This month’s summary couldn’t be loaded.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Try again")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, harness.retryTotalsClicks)
    }

    @Test
    fun carousel_listsAccountCardsWithMergedSemantics() {
        screen(
            content(
                accounts = listOf(
                    account("a1", "Wallet", AccountType.CASH, "VND", "1250500"),
                    account("a2", "Checking", AccountType.BANK, "USD", "42.10"),
                ),
            ),
        )

        composeRule.onNodeWithContentDescription(
            "Wallet, Cash, balance ${TestFormats.amount("1250500", "VND")} VND",
        )
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "Checking, Bank, balance ${TestFormats.amount("42.10", "USD")} USD",
        ).assertIsDisplayed()
    }

    @Test
    fun accountCardClick_opensThatAccount() {
        val harness = Harness()
        screen(content(), harness)

        composeRule.onNodeWithContentDescription(
            "Wallet, Cash, balance ${TestFormats.amount("1000", "VND")} VND",
        ).performClick()

        assertEquals("a1", harness.openedAccount)
    }

    @Test
    fun activityRows_renderAndOpenDetail() {
        val harness = Harness()
        screen(
            content(activity = ActivityState.Items(listOf(transaction("t7", amount = "25")))),
            harness,
        )

        composeRule.onNodeWithText("Recent activity").assertIsDisplayed()
        // Merged row semantics: "Expense −25 VND Food on Wallet, Manual".
        composeRule.onNodeWithContentDescription("Expense −25 VND Food on Wallet, Manual")
            .performClick()
        assertEquals("t7", harness.openedTransaction)
    }

    @Test
    fun activityEmpty_showsHonestEmptyCopy() {
        screen(content(activity = ActivityState.Items(emptyList())))

        composeRule.onNodeWithText("No activity yet — record a transaction to see it here.")
            .assertIsDisplayed()
    }

    @Test
    fun activityFailed_showsInlineRetryThatReachesCallback() {
        val harness = Harness()
        screen(content(activity = ActivityState.Failed(HomeError.Server)), harness)

        composeRule.onNodeWithText("Activity couldn’t be loaded.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, harness.retryActivityClicks)
    }

    @Test
    fun seeAll_reachesCallback() {
        val harness = Harness()
        screen(content(), harness)

        composeRule.onNodeWithText("See all")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, harness.seeAllClicks)
    }

    @Test
    fun importQuickCard_reachesCallback() {
        val harness = Harness()
        screen(content(), harness)

        composeRule.onNodeWithContentDescription(
            "Import statement — CSV or TPBank statement PDF",
        )
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, harness.importClicks)
    }

    @Test
    fun emptyState_showsFirstAccountCta() {
        val harness = Harness()
        screen(HomeUiState.Empty, harness)

        composeRule.onNodeWithText("Create your first account").assertIsDisplayed()
        composeRule.onNodeWithText("Add account").performClick()
        assertEquals(1, harness.createAccountClicks)
    }

    @Test
    fun refreshError_showsBannerAndKeepsContent() {
        screen(content(refreshError = HomeError.Network))

        composeRule.onNodeWithText("Couldn’t refresh").assertIsDisplayed()
        composeRule.onNodeWithText("This month").assertIsDisplayed()
    }

    @Test
    fun networkError_showsRetryableCopy() {
        val harness = Harness()
        screen(HomeUiState.Error(HomeError.Network), harness)

        composeRule.onNodeWithText(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .targetContext.getString(com.spendwise.android.R.string.state_network_title),
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, harness.refreshClicks)
    }

    @Test
    fun serverError_showsServerCopyAndRetry() {
        val harness = Harness()
        screen(HomeUiState.Error(HomeError.Server), harness)

        composeRule.onNodeWithText("Something went wrong on our side").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, harness.refreshClicks)
    }

    @Test
    fun loadingState_announcesLoadingSemantics() {
        screen(HomeUiState.Loading)

        composeRule.onNodeWithContentDescription("Loading").assertIsDisplayed()
    }

    @Test
    fun addTransactionFab_reachesCallback() {
        val harness = Harness()
        screen(content(), harness)

        // Merged FAB semantics — labeled in expanded AND collapsed forms.
        composeRule.onNodeWithContentDescription("Add transaction").performClick()
        assertEquals(1, harness.addClicks)
    }
}
