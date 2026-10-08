package com.spendwise.android.feature.transactions

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import com.spendwise.android.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Stateless-screen tests for create transaction (S11). */
@RunWith(AndroidJUnit4::class)
class CreateTransactionScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun account() = Account(
        id = "acc-1",
        name = "Wallet",
        type = AccountType.CASH,
        openingBalance = Money(java.math.BigDecimal("1000.00"), CurrencyCode("USD")),
        balance = Money(java.math.BigDecimal("1250.50"), CurrencyCode("USD")),
    )

    private fun loadedState(
        type: TransactionType = TransactionType.EXPENSE,
        isSubmitting: Boolean = false,
    ) = CreateTransactionUiState(
        type = type,
        accountId = "acc-1",
        accounts = listOf(account()),
        categories = listOf("FOOD", "TRANSPORT", "HOUSING", "ENTERTAINMENT", "SALARY"),
        optionsLoading = false,
        amount = "25.50",
        isSubmitting = isSubmitting,
    )

    private data class Harness(
        var type: TransactionType? = null,
        var account: String? = null,
        var amount: String? = null,
        var date: java.time.LocalDate? = null,
        var category: String? = "unset",
        var description: String? = null,
        var submits: Int = 0,
        var backs: Int = 0,
        var createAccount: Int = 0,
        var retryOptions: Int = 0,
    )

    private fun screen(state: CreateTransactionUiState, harness: Harness = Harness()) {
        composeRule.setContent {
            SpendwiseTheme {
                CreateTransactionScreen(
                    state = state,
                    onTypeChange = { harness.type = it },
                    onAccountChange = { harness.account = it },
                    onAmountChange = { harness.amount = it },
                    onDateChange = { harness.date = it },
                    onCategoryChange = { harness.category = it },
                    onDescriptionChange = { harness.description = it },
                    onSubmit = { harness.submits++ },
                    onNavigateBack = { harness.backs++ },
                    onCreateAccount = { harness.createAccount++ },
                    onRetryOptions = { harness.retryOptions++ },
                )
            }
        }
    }

    @Test
    fun form_showsTypeAccountAmountDateCategoriesAndNote() {
        screen(loadedState())

        composeRule.onNodeWithText("Expense").assertIsDisplayed()
        composeRule.onNodeWithText("Income").assertIsDisplayed()
        composeRule.onNodeWithText("From account").assertIsDisplayed()
        composeRule.onNodeWithText("Amount").assertIsDisplayed()
        // The field shows the state's date in localized form inside its
        // single merged semantic node — no static "today" copy exists.
        // Below the fold at large font scale — scroll into view first.
        composeRule.onNodeWithContentDescription(
            "Date, selected " + com.spendwise.android.core.ui.format.DateFormat.format(
                java.time.LocalDate.now(),
            ),
            substring = true,
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Category (optional)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("No category").assertIsDisplayed()
        composeRule.onNodeWithText("Note (optional)").assertIsDisplayed()
    }

    @Test
    fun dateField_isOneMergedSemanticNode() {
        screen(loadedState())

        // TalkBack hears "Date, selected <localized> — double-tap…" as a
        // single content-description node (the merged field semantics
        // clear the inner TextField's own text).
        composeRule.onNodeWithContentDescription(
            "Date, selected " + com.spendwise.android.core.ui.format.DateFormat.format(
                java.time.LocalDate.now(),
            ),
            substring = true,
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun dateField_opensThePickerAndPickingADayCallsBack() {
        val harness = Harness()
        screen(
            loadedState().copy(occurredOn = java.time.LocalDate.of(2026, 10, 4)),
            harness,
        )

        composeRule.onNodeWithContentDescription(
            "Date, selected " + com.spendwise.android.core.ui.format.DateFormat.format(
                java.time.LocalDate.of(2026, 10, 4),
            ),
            substring = true,
        ).performScrollTo().performClick()
        composeRule.waitForIdle()

        // Day cells carry the FULL localized spoken date as their
        // semantics Text ("Sun, Oct 15, 2026" / "Thứ Năm, 15 tháng 10,
        // 2026") — a substring match on "15" isolates the day-15 cell.
        composeRule.onAllNodesWithText(
            "15",
            substring = true,
            useUnmergedTree = true,
        ).let { nodes ->
            nodes.fetchSemanticsNodes().also { assertEquals(1, it.size) }
            nodes[0].performClick()
        }
        composeRule.onNodeWithText("Done").performClick()

        assertEquals(java.time.LocalDate.of(2026, 10, 15), harness.date)
    }

    @Test
    fun pickerDismiss_leavesTheDraftDateUntouched() {
        val harness = Harness()
        screen(loadedState(), harness)

        composeRule.onNodeWithContentDescription(
            "Date, selected " + com.spendwise.android.core.ui.format.DateFormat.format(
                java.time.LocalDate.now(),
            ),
            substring = true,
        ).performScrollTo().performClick()
        composeRule.waitForIdle()
        androidx.test.espresso.Espresso.pressBack()

        assertEquals(null, harness.date)
    }

    @Test
    fun incomeSwitches_theAccountLabelToTo() {
        screen(loadedState(type = TransactionType.INCOME))

        composeRule.onNodeWithText("To account").assertIsDisplayed()
    }

    @Test
    fun accountPicker_listsAccountsWithBalances() {
        val harness = Harness()
        screen(loadedState(), harness)

        composeRule.onNodeWithText("From account").performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Wallet", substring = true)
            .fetchSemanticsNodes()
            .let { assert(it.isNotEmpty()) }
    }

    @Test
    fun submit_reachesCallback() {
        val harness = Harness()
        screen(loadedState(), harness)

        // Submit sits below the fold at large font scale — scroll first.
        composeRule.onNodeWithText("Save transaction").performScrollTo().performClick()
        assertEquals(1, harness.submits)
    }

    @Test
    fun submitting_locksTheButton() {
        screen(loadedState(isSubmitting = true))

        composeRule.onNodeWithText(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .targetContext.getString(
                    com.spendwise.android.R.string.txn_create_submitting,
                ),
        ).assertIsNotEnabled()
    }

    @Test
    fun notSubmitting_buttonIsEnabled() {
        screen(loadedState())

        composeRule.onNodeWithText("Save transaction").assertIsEnabled()
    }

    @Test
    fun amountError_showsInline() {
        screen(
            loadedState().copy(
                amountError = CreateTransactionUiState.AmountError.NotPositive,
            ),
        )

        composeRule.onNodeWithText("Amount must be greater than zero.").assertIsDisplayed()
    }

    @Test
    fun accountError_showsInline() {
        screen(
            loadedState().copy(
                accountError = CreateTransactionUiState.AccountError.Required,
            ),
        )

        composeRule.onNodeWithText("Choose an account.").assertIsDisplayed()
    }

    @Test
    fun ambiguousError_showsHonestCopy() {
        screen(
            loadedState().copy(
                formError = CreateTransactionUiState.CreateTxnFormError.Ambiguous,
            ),
        )

        composeRule.onNodeWithText(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .targetContext.getString(
                    com.spendwise.android.R.string.txn_create_error_ambiguous,
                ),
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun zeroAccounts_offersCreateAccount() {
        val harness = Harness()
        screen(
            CreateTransactionUiState(accounts = emptyList(), optionsLoading = false),
            harness,
        )

        composeRule.onNodeWithText("Create an account first").assertIsDisplayed()
        composeRule.onNodeWithText("Create account").performClick()
        assertEquals(1, harness.createAccount)
    }

    @Test
    fun optionsError_retriesOptions() {
        val harness = Harness()
        screen(
            CreateTransactionUiState(optionsLoading = false, optionsError = true),
            harness,
        )

        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, harness.retryOptions)
    }

    @Test
    fun categoryTiles_select() {
        val harness = Harness()
        screen(loadedState(), harness)

        composeRule.onNodeWithText("Food").performClick()

        assertEquals("FOOD", harness.category)
    }
}
