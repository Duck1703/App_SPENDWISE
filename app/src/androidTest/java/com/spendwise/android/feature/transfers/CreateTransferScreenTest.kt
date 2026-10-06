package com.spendwise.android.feature.transfers

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Stateless-screen tests for create transfer (M12/REQ-BE-003). */
@RunWith(AndroidJUnit4::class)
class CreateTransferScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun account(
        id: String = "acc-1",
        name: String = "Wallet",
        currency: String = "USD",
    ) = Account(
        id = id,
        name = name,
        type = AccountType.CASH,
        openingBalance = Money(java.math.BigDecimal("1000.00"), CurrencyCode(currency)),
        balance = Money(java.math.BigDecimal("1250.50"), CurrencyCode(currency)),
    )

    private fun loadedState(
        isSubmitting: Boolean = false,
        formError: CreateTransferUiState.FormError? = null,
    ) = CreateTransferUiState(
        sourceAccountId = "acc-1",
        accounts = listOf(
            account(id = "acc-1", name = "Checking"),
            account(id = "acc-2", name = "Savings"),
        ),
        optionsLoading = false,
        amount = "25.50",
        isSubmitting = isSubmitting,
        formError = formError,
    )

    private data class Harness(
        var source: String? = null,
        var destination: String? = null,
        var amount: String? = null,
        var submits: Int = 0,
        var backs: Int = 0,
        var createAccount: Int = 0,
        var retryOptions: Int = 0,
    )

    private fun screen(state: CreateTransferUiState, harness: Harness = Harness()) {
        composeRule.setContent {
            SpendwiseTheme {
                CreateTransferScreen(
                    state = state,
                    onSourceChange = { harness.source = it },
                    onDestinationChange = { harness.destination = it },
                    onAmountChange = { harness.amount = it },
                    onSubmit = { harness.submits++ },
                    onNavigateBack = { harness.backs++ },
                    onCreateAccount = { harness.createAccount++ },
                    onRetryOptions = { harness.retryOptions++ },
                )
            }
        }
    }

    @Test
    fun form_showsFromToAmountAndActions() {
        screen(loadedState())

        composeRule.onNodeWithText("From account").assertIsDisplayed()
        composeRule.onNodeWithText("To account").assertIsDisplayed()
        composeRule.onNodeWithText("Amount").assertIsDisplayed()
        composeRule.onNodeWithText("Transfer").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performScrollTo().assertIsDisplayed()
        // The selected source shows its name + locked-currency balance.
        composeRule.onNodeWithText("Checking", substring = true).assertIsDisplayed()
    }

    @Test
    fun destinationPicker_neverOffersTheSourceAccount() {
        val harness = Harness()
        screen(loadedState(), harness)

        // SwDropdownField merges the label as the field's content
        // description — open the destination menu.
        composeRule.onNodeWithContentDescription("To account")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        // Only Savings is offered; "Checking" appears exactly once —
        // the FROM field's own selected label — the menu adds no second
        // entry for the source account.
        composeRule.onNodeWithText("Savings", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("Checking", substring = true).assertCountEquals(1)

        composeRule.onNodeWithText("Savings", substring = true).performClick()
        assertEquals("acc-2", harness.destination)
    }

    @Test
    fun submit_andCancel_fireTheirCallbacks() {
        val harness = Harness()
        screen(loadedState(), harness)

        composeRule.onNodeWithText("Transfer").performScrollTo().performClick()
        composeRule.onNodeWithText("Cancel").performScrollTo().performClick()

        assertEquals(1, harness.submits)
        assertEquals(1, harness.backs)
    }

    @Test
    fun submitting_disablesTheSubmitButton() {
        screen(loadedState(isSubmitting = true))

        composeRule.onNodeWithText("Transferring…").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aSingleAccount_showsTheTwoAccountRequirementWithCreateAction() {
        val harness = Harness()
        screen(
            CreateTransferUiState(
                accounts = listOf(account()),
                optionsLoading = false,
            ),
            harness,
        )

        composeRule.onNodeWithText("Two accounts needed").assertIsDisplayed()
        composeRule.onNodeWithText("Create account").performClick()
        assertEquals(1, harness.createAccount)
    }

    @Test
    fun optionsError_showsRetryAndFiresIt() {
        val harness = Harness()
        screen(
            CreateTransferUiState(optionsLoading = false, optionsError = true),
            harness,
        )

        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, harness.retryOptions)
    }

    @Test
    fun crossCurrencyDestination_showsTheClearRejection() {
        screen(
            loadedState().copy(
                destinationAccountId = "acc-2",
                destinationError = CreateTransferUiState.AccountError.CrossCurrency,
            ),
        )

        composeRule.onNodeWithText(
            "Accounts use different currencies — pick a destination that also uses USD.",
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun ambiguousFormError_showsTheHonestCopy() {
        screen(loadedState(formError = CreateTransferUiState.FormError.Ambiguous))

        composeRule.onNodeWithText(
            "We couldn’t confirm whether this transfer was recorded — " +
                "check your transactions before trying again.",
            substring = true,
        ).assertIsDisplayed()
    }
}
