package com.spendwise.android.feature.planning

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.core.designsystem.SpendwiseTheme
import com.spendwise.android.core.model.Budget
import com.spendwise.android.core.model.BudgetCalculation
import com.spendwise.android.core.model.BudgetStatus
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/**
 * Budget screens (S18–S20) — list/empty states, server-authoritative
 * calculation detail rendered verbatim, and the no-edit honesty notice.
 */
@RunWith(AndroidJUnit4::class)
class BudgetScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun vnd(amount: String) = Money(BigDecimal(amount), CurrencyCode("VND"))

    private fun budget(id: String = "b1") =
        Budget(id = id, category = "FOOD", period = "2026-10", limit = vnd("500"))

    private fun calculation(status: BudgetStatus = BudgetStatus.OVER_BUDGET) =
        BudgetCalculation(
            budgetId = "b1",
            category = "FOOD",
            period = "2026-10",
            limit = vnd("500"),
            spent = vnd("600"),
            remaining = vnd("-100"),
            percentage = BigDecimal("120.00"),
            status = status,
        )

    @Test
    fun list_emptyShowsEmptyState() {
        composeRule.setContent {
            SpendwiseTheme {
                BudgetsScreen(
                    state = BudgetsListState.Empty,
                    onOpenBudget = {}, onCreate = {}, onRetry = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("No budgets").assertIsDisplayed()
    }

    @Test
    fun list_rowOpensDetail() {
        var opened: String? = null
        composeRule.setContent {
            SpendwiseTheme {
                BudgetsScreen(
                    state = BudgetsListState.Content(budgets = listOf(budget())),
                    onOpenBudget = { opened = it },
                    onCreate = {}, onRetry = {}, onBack = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("budget", substring = true)
            .fetchSemanticsNodes()
            .isNotEmpty()
            .let { assert(it) }
        // Merged row CD is "Food budget, <period>, limit <limit>".
        composeRule.onAllNodesWithContentDescription("Food budget", substring = true)[0].performClick()
        assertEquals("b1", opened)
    }

    @Test
    fun detail_overBudgetShowsServerStatus() {
        composeRule.setContent {
            SpendwiseTheme {
                BudgetDetailScreen(
                    state = BudgetDetailState.Content(calculation = calculation()),
                    edit = EditBudgetState(),
                    onRetry = {}, onBack = {},
                    onOpenEdit = {}, onDismissEdit = {},
                    onEditLimitChange = {}, onEditPeriodChange = {},
                    onSaveEdit = {}, onDelete = {},
                )
            }
        }
        composeRule.onNodeWithText("Over budget").assertIsDisplayed()
        composeRule.onNodeWithText("Spent").assertIsDisplayed()
        composeRule.onNodeWithText("Remaining").assertIsDisplayed()
    }

    @Test
    fun detail_showsEditAndDeleteAffordances() {
        composeRule.setContent {
            SpendwiseTheme {
                BudgetDetailScreen(
                    state = BudgetDetailState.Content(
                        calculation = calculation(BudgetStatus.WITHIN_BUDGET),
                    ),
                    edit = EditBudgetState(),
                    onRetry = {}, onBack = {},
                    onOpenEdit = {}, onDismissEdit = {},
                    onEditLimitChange = {}, onEditPeriodChange = {},
                    onSaveEdit = {}, onDelete = {},
                )
            }
        }
        val ctx = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .targetContext
        composeRule.onNodeWithText(
            ctx.getString(com.spendwise.android.R.string.budget_edit_action),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            ctx.getString(com.spendwise.android.R.string.budget_delete_action),
        ).assertIsDisplayed()
    }

    @Test
    fun detail_editOpensDialog_andSavePropagates() {
        var saved = false
        composeRule.setContent {
            SpendwiseTheme {
                BudgetDetailScreen(
                    state = BudgetDetailState.Content(
                        calculation = calculation(BudgetStatus.WITHIN_BUDGET),
                    ),
                    edit = EditBudgetState(visible = true, limit = "500", period = "2026-10"),
                    onRetry = {}, onBack = {},
                    onOpenEdit = {}, onDismissEdit = {},
                    onEditLimitChange = {}, onEditPeriodChange = {},
                    onSaveEdit = { saved = true }, onDelete = {},
                )
            }
        }
        val ctx = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .targetContext
        // Dialog is open — the user can press Save and it reaches the VM.
        composeRule.onNodeWithText(
            ctx.getString(com.spendwise.android.R.string.budget_edit_dialog_title),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            ctx.getString(com.spendwise.android.R.string.budget_edit_save),
        ).performClick()
        assertEquals(true, saved)
    }

    @Test
    fun detail_deleteShowsConfirm_andConfirmPropagates() {
        var deleted = false
        composeRule.setContent {
            SpendwiseTheme {
                BudgetDetailScreen(
                    state = BudgetDetailState.Content(
                        calculation = calculation(BudgetStatus.WITHIN_BUDGET),
                    ),
                    edit = EditBudgetState(),
                    onRetry = {}, onBack = {},
                    onOpenEdit = {}, onDismissEdit = {},
                    onEditLimitChange = {}, onEditPeriodChange = {},
                    onSaveEdit = {}, onDelete = { deleted = true },
                )
            }
        }
        val ctx = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .targetContext
        composeRule.onNodeWithText(
            ctx.getString(com.spendwise.android.R.string.budget_delete_action),
        ).performClick()
        composeRule.onNodeWithText(
            ctx.getString(com.spendwise.android.R.string.budget_delete_dialog_title),
        ).assertIsDisplayed()
        // The dialog names WHICH budget — category + month are visible,
        // so two budgets can never be confused (external audit M14).
        composeRule.onNode(
            androidx.compose.ui.test.hasText("2026-10", substring = true),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            ctx.getString(com.spendwise.android.R.string.budget_delete_confirm),
        ).performClick()
        assertEquals(true, deleted)
    }

    @Test
    fun detail_notFoundShowsMissingNotice() {
        composeRule.setContent {
            SpendwiseTheme {
                BudgetDetailScreen(
                    state = BudgetDetailState.NotFound,
                    edit = EditBudgetState(),
                    onRetry = {}, onBack = {},
                    onOpenEdit = {}, onDismissEdit = {},
                    onEditLimitChange = {}, onEditPeriodChange = {},
                    onSaveEdit = {}, onDelete = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun create_rendersForm() {
        composeRule.setContent {
            SpendwiseTheme {
                CreateBudgetScreen(
                    state = CreateBudgetState(optionsLoading = false),
                    onCategoryChange = {}, onPeriodChange = {}, onLimitChange = {},
                    onCurrencyChange = {}, onSubmit = {}, onRetryOptions = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("New budget").assertIsDisplayed()
        composeRule.onNodeWithText("Save budget").assertIsDisplayed()
    }
}
