package com.spendwise.android.feature.accounts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.R
import com.spendwise.android.TestFormats
import com.spendwise.android.core.designsystem.SpendwiseTheme
import com.spendwise.android.core.model.AccountType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Stateless-screen tests for the create-account form (S07). */
@RunWith(AndroidJUnit4::class)
class CreateAccountScreenTest {

    private fun SemanticsNodeInteraction.typeViaSemantics(text: String) =
        performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString(text)) }

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

        // Field nodes carry contentDescription = label; the visible label is a
        // separate Text node with no input actions. SetText is invoked
        // directly (IME path is unreliable on headless emulator hosts).
        composeRule.onNodeWithContentDescription("Account name").typeViaSemantics("Jar")
        composeRule.onNodeWithContentDescription("Opening balance").typeViaSemantics("1500")
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
        composeRule.onNodeWithText(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.account_error_balance_negative),
        ).assertIsDisplayed()
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

        // Network loss on create is deliberately "ambiguous" copy (MIN-4 —
        // the POST may already have landed server-side).
        composeRule.onNodeWithText(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.account_create_error_ambiguous),
        ).assertIsDisplayed()
    }

    @Test
    fun currencyDropdown_offersIso4217Options() {
        val harness = Harness()
        screen(harness = harness)

        // The clickable anchor is the field (contentDescription = "Currency"),
        // not the visible label Text.
        composeRule.onNodeWithContentDescription("Currency").performClick()
        composeRule.waitForIdle()

        // The open ISO set — JDK-provided — includes common codes. "USD" sits
        // deep in the scrollable menu — scroll before asserting. The label
        // uses the locale display name ("US Dollar" en / "Đô-la Mỹ" vi).
        composeRule.onNodeWithText(TestFormats.currencyOptionLabel("USD"))
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(TestFormats.currencyOptionLabel("USD")).performClick()

        assertEquals("USD", harness.currency)
    }
}
