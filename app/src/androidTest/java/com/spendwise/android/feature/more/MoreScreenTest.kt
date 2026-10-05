package com.spendwise.android.feature.more

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.core.designsystem.SpendwiseTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoreScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun importRow_launchesImport() {
        var importClicks = 0
        composeRule.setContent {
            SpendwiseTheme {
                MoreScreen(
                    state = MoreUiState(),
                    onSignOut = {},
                    onOpenImport = { importClicks++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Import statement").performClick()
        assertEquals(1, importClicks)
    }

    @Test
    fun signOut_requiresConfirmation() {
        var signOutClicks = 0
        composeRule.setContent {
            SpendwiseTheme {
                MoreScreen(
                    state = MoreUiState(),
                    onSignOut = { signOutClicks++ },
                    onOpenImport = {},
                )
            }
        }

        composeRule.onNodeWithText("Sign out").performClick()
        composeRule.onNodeWithText("Sign out of Spendwise?").assertIsDisplayed()
        assertEquals(0, signOutClicks)

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        assertEquals(0, signOutClicks)
        composeRule.onNodeWithText("Sign out of Spendwise?").assertDoesNotExist()
    }

    @Test
    fun confirmSignOut_firesCallback() {
        var signOutClicks = 0
        composeRule.setContent {
            SpendwiseTheme {
                MoreScreen(
                    state = MoreUiState(),
                    onSignOut = { signOutClicks++ },
                    onOpenImport = {},
                )
            }
        }

        composeRule.onNodeWithText("Sign out").performClick()
        // The dialog's confirm button — last node labelled "Sign out".
        val nodes = composeRule.onAllNodesWithText("Sign out").fetchSemanticsNodes()
        composeRule.onAllNodesWithText("Sign out")[nodes.size - 1].performClick()

        assertEquals(1, signOutClicks)
    }
}
