package com.spendwise.android.feature.planning

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.core.designsystem.SpendwiseTheme
import com.spendwise.android.core.model.Account
import com.spendwise.android.core.model.AccountType
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.model.RecurringFrequency
import com.spendwise.android.core.model.RecurringTemplate
import com.spendwise.android.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Recurring screens (S15–S17) — empty/content states, the deactivate
 * confirm dialog, and positive-amount + direction-label rendering.
 */
@RunWith(AndroidJUnit4::class)
class RecurringScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun template(
        id: String = "r1",
        direction: TransactionType = TransactionType.EXPENSE,
        active: Boolean = true,
    ) = RecurringTemplate(
        id = id,
        accountId = "acc-1",
        direction = direction,
        amount = Money(BigDecimal("1200"), CurrencyCode("VND")),
        category = "HOUSING",
        frequency = RecurringFrequency.MONTHLY,
        nextDueDate = LocalDate.parse("2026-11-01"),
        endDate = null,
        active = active,
    )

    @Test
    fun list_emptyShowsEmptyState() {
        composeRule.setContent {
            SpendwiseTheme {
                RecurringListScreen(
                    state = RecurringListState.Empty,
                    onOpenTemplate = {}, onCreate = {}, onRetry = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("No recurring items").assertIsDisplayed()
    }

    @Test
    fun list_rowOpensDetail() {
        var opened: String? = null
        composeRule.setContent {
            SpendwiseTheme {
                RecurringListScreen(
                    state = RecurringListState.Content(
                        templates = listOf(template()),
                        accountNames = mapOf("acc-1" to "Wallet"),
                    ),
                    onOpenTemplate = { opened = it },
                    onCreate = {}, onRetry = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Recurring items").assertIsDisplayed()
        composeRule.waitForIdle()
        // Row exposes a merged content description containing the
        // account label — click the row through it.
        composeRule.onAllNodesWithContentDescription("Wallet", substring = true)[0]
            .performClick()
        assertEquals("r1", opened)
    }

    @Test
    fun detail_activeShowsDeactivate_andDialogConfirms() {
        var deactivateClicks = 0
        composeRule.setContent {
            SpendwiseTheme {
                RecurringDetailScreen(
                    state = RecurringDetailState.Content(
                        template = template(),
                        accountName = "Wallet",
                    ),
                    onDeactivate = { deactivateClicks++ },
                    onEdit = {}, onRetry = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Deactivate").performClick()
        composeRule.onNodeWithText("Deactivate this recurring item?").assertIsDisplayed()
        assertEquals(0, deactivateClicks)

        // Confirm button — last node labelled "Deactivate".
        val nodes = composeRule.onAllNodesWithText("Deactivate").fetchSemanticsNodes()
        composeRule.onAllNodesWithText("Deactivate")[nodes.size - 1].performClick()
        assertEquals(1, deactivateClicks)
    }

    @Test
    fun detail_inactiveShowsDeactivatedNotice_notButton() {
        composeRule.setContent {
            SpendwiseTheme {
                RecurringDetailScreen(
                    state = RecurringDetailState.Content(template = template(active = false)),
                    onDeactivate = {}, onEdit = {}, onRetry = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Deactivated").assertIsDisplayed()
    }

    @Test
    fun detail_notFoundShowsMissingNotice() {
        composeRule.setContent {
            SpendwiseTheme {
                RecurringDetailScreen(
                    state = RecurringDetailState.NotFound,
                    onDeactivate = {}, onEdit = {}, onRetry = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("This recurring item is no longer available.")
            .assertIsDisplayed()
    }

    @Test
    fun create_rendersForm() {
        composeRule.setContent {
            SpendwiseTheme {
                CreateRecurringScreen(
                    // The form only renders once a target account exists.
                    state = CreateRecurringState(
                        optionsLoading = false,
                        accounts = listOf(
                            Account(
                                id = "a1",
                                name = "Wallet",
                                type = AccountType.CASH,
                                openingBalance = Money(BigDecimal("0"), CurrencyCode("VND")),
                                balance = Money(BigDecimal("0"), CurrencyCode("VND")),
                            ),
                        ),
                    ),
                    onDirectionChange = {}, onAccountChange = {}, onAmountChange = {},
                    onCategoryChange = {}, onFrequencyChange = {}, onNextDueDateChange = {},
                    onEndDateChange = {}, onSubmit = {}, onRetryOptions = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("New recurring item").assertIsDisplayed()
        // Long form — the submit sits below the fold.
        composeRule.onNodeWithText("Save recurring item").performScrollTo().assertIsDisplayed()
    }

    // ── Edit (M16 / REQ-BE-012) ─────────────────────────────────────

    private fun editForm(
        template: RecurringTemplate = template(),
        amount: String = "1200",
        category: String? = "HOUSING",
        frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
        nextDueDate: String = "2026-11-01",
        endDate: String = "",
        formError: EditRecurringState.Form.FormError? = null,
    ) = EditRecurringState.Form(
        template = template,
        accountName = "Wallet",
        amount = amount,
        category = category,
        frequency = frequency,
        nextDueDate = nextDueDate,
        endDate = endDate,
        categories = listOf("FOOD", "HOUSING"),
        formError = formError,
    )

    @Test
    fun detail_activeShowsEdit_andClickFiresNavigation() {
        var edited: String? = null
        composeRule.setContent {
            SpendwiseTheme {
                RecurringDetailScreen(
                    state = RecurringDetailState.Content(
                        template = template(),
                        accountName = "Wallet",
                    ),
                    onDeactivate = {},
                    onEdit = { edited = it },
                    onRetry = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Edit").performScrollTo().performClick()
        assertEquals("r1", edited)
    }

    @Test
    fun detail_inactiveHidesTheEditAffordance() {
        composeRule.setContent {
            SpendwiseTheme {
                RecurringDetailScreen(
                    state = RecurringDetailState.Content(template = template(active = false)),
                    onDeactivate = {}, onEdit = {}, onRetry = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Deactivated").assertIsDisplayed()
        composeRule.onAllNodesWithText("Edit").fetchSemanticsNodes().also {
            assertEquals(0, it.size)
        }
    }

    @Test
    fun edit_prefillsFields_andShowsImmutableIdentity() {
        composeRule.setContent {
            SpendwiseTheme {
                EditRecurringScreen(
                    state = editForm(),
                    onAmountChange = {}, onCategoryChange = {}, onFrequencyChange = {},
                    onNextDueDateChange = {}, onEndDateChange = {},
                    onSave = {}, onRetry = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Edit recurring item").assertIsDisplayed()
        // Immutable identity rows are visible, never editable fields.
        composeRule.onNodeWithText(
            "Account, type, and currency can’t be changed.",
        ).assertIsDisplayed()
        // Prefilled editable values.
        composeRule.onNodeWithText("1200").assertIsDisplayed()
        composeRule.onNodeWithText("2026-11-01").assertIsDisplayed()
    }

    @Test
    fun edit_saveStaysDisabledUntilAFieldChanges() {
        var amount = "1200"
        composeRule.setContent {
            SpendwiseTheme {
                EditRecurringScreen(
                    state = editForm(amount = amount),
                    onAmountChange = { amount = it },
                    onCategoryChange = {}, onFrequencyChange = {},
                    onNextDueDateChange = {}, onEndDateChange = {},
                    onSave = {}, onRetry = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Save changes").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun edit_changedFieldEnablesSave_andSaveFires() {
        var saveClicks = 0
        composeRule.setContent {
            SpendwiseTheme {
                EditRecurringScreen(
                    state = editForm(amount = "950.50"),
                    onAmountChange = {}, onCategoryChange = {}, onFrequencyChange = {},
                    onNextDueDateChange = {}, onEndDateChange = {},
                    onSave = { saveClicks++ }, onRetry = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Save changes").performScrollTo().performClick()
        assertEquals(1, saveClicks)
    }

    @Test
    fun edit_formErrorBannerRenders() {
        composeRule.setContent {
            SpendwiseTheme {
                EditRecurringScreen(
                    state = editForm(
                        amount = "999",
                        formError = EditRecurringState.Form.FormError.Ambiguous,
                    ),
                    onAmountChange = {}, onCategoryChange = {}, onFrequencyChange = {},
                    onNextDueDateChange = {}, onEndDateChange = {},
                    onSave = {}, onRetry = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Couldn’t confirm").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun edit_notFoundShowsMissingNotice() {
        composeRule.setContent {
            SpendwiseTheme {
                EditRecurringScreen(
                    state = EditRecurringState.NotFound,
                    onAmountChange = {}, onCategoryChange = {}, onFrequencyChange = {},
                    onNextDueDateChange = {}, onEndDateChange = {},
                    onSave = {}, onRetry = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("This recurring item is no longer available.")
            .assertIsDisplayed()
    }
}
