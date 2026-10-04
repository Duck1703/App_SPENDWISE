package com.spendwise.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.app.SpendwiseApp
import com.spendwise.android.core.designsystem.SpendwiseTheme
import com.spendwise.android.core.session.Session
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Thin structural Compose tests (AD-021): session-state shell routing and
 * the four-destination main shell. Foundation placeholders only.
 */
@RunWith(AndroidJUnit4::class)
class SpendwiseAppTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun app(session: Session) {
        composeRule.setContent {
            SpendwiseTheme { SpendwiseApp(session = session) }
        }
    }

    @Test
    fun signedOut_showsWelcome() {
        app(Session.SignedOut)

        composeRule.onNodeWithText("Sign in").assertIsDisplayed()
        composeRule.onNodeWithText("Create account").assertIsDisplayed()
    }

    @Test
    fun signedIn_showsFourTopLevelDestinations() {
        app(Session.SignedIn(token = "t", expiresAt = Instant.now().plusSeconds(900), username = null))

        listOf("Home", "Transactions", "Accounts", "More").forEach { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun signedIn_navigatesBetweenDestinations() {
        app(Session.SignedIn(token = "t", expiresAt = Instant.now().plusSeconds(900), username = null))

        // On Home, only the bottom-bar label reads "Transactions".
        composeRule.onNodeWithText("Transactions").performClick()
        composeRule.waitForIdle()

        // After navigating, the placeholder title joins the nav label.
        val nodes = composeRule.onAllNodesWithText("Transactions").fetchSemanticsNodes()
        assert(nodes.size >= 2) { "Expected nav label + screen title, got ${nodes.size}" }
    }

    @Test
    fun expired_showsSessionExpired() {
        app(Session.Expired(username = null))

        composeRule.onNodeWithText("Session expired").assertIsDisplayed()
        composeRule.onNodeWithText("Sign in again").assertIsDisplayed()
    }
}
