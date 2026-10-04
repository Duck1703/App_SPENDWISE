package com.spendwise.android.feature.accounts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.core.designsystem.SpendwiseTheme
import com.spendwise.android.core.model.AccountType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Stateless-screen tests for the create-account form (S07). */
@RunWith(AndroidJUnit4::class)
class CreateAccountScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private data class Harness(
        var name: String = "",
        var type: AccountType? = null,
        var currency: String? = null,
        var balance: String = "",
        var submits: Int = 0,
        var backs: Int = 0,
    )

    private fun screen(state: CreateAccountUiState = CreateAccountUiState(), harness: Harness = Harness()) {
        composeRule.setContent {
            SpendwiseTheme {
                CreateAccountScreen(
                    state = state,
                    onNameChange = { harness.name = it },
                    onTypeChange = { harness.type = it },
                    onCurrencyChange = { harness.currency = it },
                    onOpeningBalanceChange = { harness.balance = it },
                    onSubmit = { harness.submits++ },
                    onNavigateBack = { harness.backs++ },
                )
            }
        }
    }

    @Test
    fun rendersAllFormControls() {
        screen()

        composeRule.onNodeWithText("New account").assertIsDisplayed()
        composeRule.onNodeWithText("Account name").assertIsDisplayed()
        composeRule.onNodeWithText("Type").assertIsDisplayed()
        composeRule.onNodeWithText("Currency").assertIsDisplayed()
        composeRule.onNodeWithText("Opening balance").assertIsDisplayed()
        composeRule.onNodeWithText("Create account").assertIsDisplayed()
    }

    @Test
    fun typingAndSubmitReachCallbacks() {
        val harness = Harness()
        screen(harness = harness)

        composeRule.onNodeWithText("Account name").performTextInput("Jar")
        composeRule.onNodeWithText("Opening balance").performTextInput("1500")
        composeRule.onNodeWithText("Create account").performClick()

        assertEquals("Jar", harness.name)
        assertEquals("1500", harness.balance)
        assertEquals(1, harness.submits)
    }

    @Test
    fun typeSelection_reachesCallback() {
        val harness = Harness()
        screen(harness = harness)

        composeRule.onNodeWithText("Bank").performClick()

        assertEquals(AccountType.BANK, harness.type)
    }

    @Test
    fun validationErrors_renderInline() {
        screen(
            CreateAccountUiState(
                nameError = CreateAccountUiState.NameError.Required,
                balanceError = CreateAccountUiState.BalanceError.Negative,
            ),
        )

        composeRule.onNodeWithText("Give the account a name.").assertIsDisplayed()
        composeRule.onNodeWithText("Opening balance can’t be negative.").assertIsDisplayed()
    }

    @Test
    fun serverFieldError_rendersOnTheField() {
        screen(
            CreateAccountUiState(
                nameError = CreateAccountUiState.NameError.Server("already exists"),
            ),
        )

        composeRule.onNodeWithText("already exists").assertIsDisplayed()
    }

    @Test
    fun submitting_locksTheButton() {
        screen(CreateAccountUiState(isSubmitting = true, name = "Jar"))

        composeRule.onNodeWithText("Creating…").assertIsNotEnabled()
    }

    @Test
    fun networkError_showsFormBanner() {
        screen(
            CreateAccountUiState(
                name = "Jar",
                formError = CreateAccountUiState.CreateFormError.Network,
            ),
        )

        composeRule.onNodeWithText("No connection. Check your network and try again.")
            .assertIsDisplayed()
    }

    @Test
    fun currencyDropdown_offersIso4217Options() {
        val harness = Harness()
        screen(harness = harness)

        composeRule.onNodeWithText("Currency").performClick()
        composeRule.waitForIdle()

        // The open ISO set — JDK-provided — includes common codes.
        composeRule.onNodeWithText("USD — US Dollar").assertIsDisplayed()
        composeRule.onNodeWithText("USD — US Dollar").performClick()

        assertEquals("USD", harness.currency)
    }
}
