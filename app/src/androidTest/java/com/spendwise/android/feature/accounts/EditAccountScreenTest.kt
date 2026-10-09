package com.spendwise.android.feature.accounts

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString
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

/** Stateless-screen tests for the edit-account form (M19, REQ-BE-006). */
@RunWith(AndroidJUnit4::class)
class EditAccountScreenTest {

    private fun SemanticsNodeInteraction.typeViaSemantics(text: String) =
        performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString(text)) }

    @get:Rule
    val composeRule = createComposeRule()

    private data class Harness(
        var name: String? = null,
        var archived: Boolean? = null,
        var saves: Int = 0,
        var retries: Int = 0,
        var backs: Int = 0,
    )

    private fun screen(state: EditAccountState, harness: Harness = Harness()) {
        composeRule.setContent {
            SpendwiseTheme {
                EditAccountScreen(
                    state = state,
                    onNameChange = { harness.name = it },
                    onArchivedChange = { harness.archived = it },
                    onSave = { harness.saves++ },
                    onRetry = { harness.retries++ },
                    onBack = { harness.backs++ },
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

    private fun form(
        archived: Boolean = false,
        name: String = "Everyday cash",
        isSubmitting: Boolean = false,
        formError: EditAccountState.Form.FormError? = null,
        nameError: EditAccountState.Form.NameError? = null,
    ) = EditAccountState.Form(
        account = account(archived = archived),
        name = name,
        archived = archived,
        nameError = nameError,
        isSubmitting = isSubmitting,
        formError = formError,
    )

    @Test
    fun form_rendersPrefilledNameAndArchiveCopy() {
        screen(form())

        composeRule.onNodeWithText("Edit account").assertIsDisplayed()
        composeRule.onNodeWithText("Archive account").assertIsDisplayed()
        // Honest copy — the ledger and balance survive archiving.
        composeRule.onNodeWithText(
            "Hidden when picking an account for new entries. " +
                "Its history and balance stay — nothing is deleted.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Save changes").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun save_staysDisabledWithNoChanges() {
        screen(form())

        composeRule.onNodeWithText("Save changes").assertIsNotEnabled()
    }

    @Test
    fun typingForwardsToCallback() {
        val harness = Harness()
        screen(form(), harness)

        composeRule.onNodeWithContentDescription("Account name").typeViaSemantics("Renamed")
        assertEquals("Renamed", harness.name)
    }

    @Test
    fun archiveSwitch_forwardsIntent() {
        val harness = Harness()
        screen(form(archived = false), harness)

        composeRule.onNodeWithText("Archive account").assertIsDisplayed()
        composeRule.onNode(androidx.compose.ui.test.isToggleable())
            .assertIsOff()
            .performClick()
        assertEquals(true, harness.archived)
    }

    @Test
    fun archivedForm_showsSwitchOn() {
        screen(form(archived = true))

        composeRule.onNode(androidx.compose.ui.test.isToggleable()).assertIsOn()
    }

    @Test
    fun submitting_disablesSave() {
        screen(form(name = "Renamed", isSubmitting = true))

        composeRule.onNodeWithText("Saving…").assertIsNotEnabled()
        composeRule.onNodeWithText("Cancel").assertIsNotEnabled()
    }

    @Test
    fun conflict_surfacesTheBanner() {
        screen(
            form(
                name = "Renamed",
                formError = EditAccountState.Form.FormError.Conflict,
            ),
        )

        composeRule.onNodeWithText("Changed elsewhere").assertIsDisplayed()
    }

    @Test
    fun ambiguous_surfacesTheBanner() {
        screen(
            form(
                name = "Renamed",
                formError = EditAccountState.Form.FormError.Ambiguous,
            ),
        )

        composeRule.onNodeWithText("Did it save?").assertIsDisplayed()
    }

    @Test
    fun nameError_showsFieldError() {
        screen(form(name = " ", nameError = EditAccountState.Form.NameError.Required))

        composeRule.onNodeWithText("Give the account a name.").assertIsDisplayed()
    }

    @Test
    fun notFound_showsBackToAccounts() {
        val harness = Harness()
        screen(EditAccountState.NotFound, harness)

        composeRule.onNodeWithText("Account not found").assertIsDisplayed()
        composeRule.onNodeWithText("Back to accounts").performClick()
        assertEquals(1, harness.backs)
    }

    @Test
    fun networkError_retries() {
        val harness = Harness()
        screen(EditAccountState.Error(AccountsError.Network), harness)

        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, harness.retries)
    }
}
