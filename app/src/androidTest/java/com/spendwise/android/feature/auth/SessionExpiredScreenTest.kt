package com.spendwise.android.feature.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.core.designsystem.SpendwiseTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionExpiredScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersExplanationAndActions() {
        var signInClicks = 0
        var signOutClicks = 0
        composeRule.setContent {
            SpendwiseTheme {
                SessionExpiredScreen(
                    username = "ana",
                    onSignInAgain = { signInClicks++ },
                    onSignOut = { signOutClicks++ },
                )
            }
        }

        composeRule.onNodeWithText("Session expired").assertIsDisplayed()
        composeRule.onNodeWithText("Signed in as ana").assertIsDisplayed()

        composeRule.onNodeWithText("Sign in again").performClick()
        composeRule.onNodeWithText("Sign out").performClick()

        assertEquals(1, signInClicks)
        assertEquals(1, signOutClicks)
    }
}
