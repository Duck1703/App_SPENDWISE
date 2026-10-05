package com.spendwise.android.feature.planning

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.core.designsystem.SpendwiseTheme
import com.spendwise.android.core.model.CategorizationRule
import com.spendwise.android.core.model.RuleConditionType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Categorization-rule screens (S21–S23) — priority-ordered list incl.
 * disabled rows, create form, and the soft-disable confirm dialog.
 */
@RunWith(AndroidJUnit4::class)
class RuleScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun rule(id: String = "rl1", enabled: Boolean = true, priority: Int = 1) =
        CategorizationRule(
            id = id,
            conditionType = RuleConditionType.CONTAINS,
            conditionValue = "coffee",
            targetCategory = "FOOD",
            priority = priority,
            enabled = enabled,
        )

    @Test
    fun list_emptyShowsEmptyState() {
        composeRule.setContent {
            SpendwiseTheme {
                RulesScreen(
                    state = RulesListState.Empty,
                    onOpenRule = {}, onCreate = {}, onRetry = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("No rules").assertIsDisplayed()
    }

    @Test
    fun list_disabledRuleShowsInactive() {
        composeRule.setContent {
            SpendwiseTheme {
                RulesScreen(
                    state = RulesListState.Content(
                        rules = listOf(rule(enabled = false)),
                    ),
                    onOpenRule = {}, onCreate = {}, onRetry = {}, onBack = {},
                )
            }
        }
        // Rows are clearAndSetSemantics-merged — status lives only in the CD.
        composeRule.onNodeWithContentDescription("Inactive", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun list_rowOpensDetail() {
        var opened: String? = null
        composeRule.setContent {
            SpendwiseTheme {
                RulesScreen(
                    state = RulesListState.Content(rules = listOf(rule())),
                    onOpenRule = { opened = it },
                    onCreate = {}, onRetry = {}, onBack = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("coffee", substring = true)
            .performClick()
        assertEquals("rl1", opened)
    }

    @Test
    fun detail_enabledShowsDisable_andDialogConfirms() {
        var disableClicks = 0
        composeRule.setContent {
            SpendwiseTheme {
                RuleDetailScreen(
                    state = RuleDetailState.Content(rule = rule()),
                    onDisable = { disableClicks++ },
                    onRetry = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Disable rule").performClick()
        composeRule.onNodeWithText("Disable this rule?").assertIsDisplayed()
        assertEquals(0, disableClicks)
    }

    @Test
    fun detail_disabledRuleShowsInactiveNotice() {
        composeRule.setContent {
            SpendwiseTheme {
                RuleDetailScreen(
                    state = RuleDetailState.Content(rule = rule(enabled = false)),
                    onDisable = {}, onRetry = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Inactive").assertIsDisplayed()
    }

    @Test
    fun create_rendersForm() {
        composeRule.setContent {
            SpendwiseTheme {
                CreateRuleScreen(
                    state = CreateRuleState(optionsLoading = false),
                    onConditionTypeChange = {}, onConditionValueChange = {},
                    onTargetCategoryChange = {}, onPriorityChange = {},
                    onSubmit = {}, onRetryOptions = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("New rule").assertIsDisplayed()
        composeRule.onNodeWithText("Save rule").assertIsDisplayed()
    }
}
