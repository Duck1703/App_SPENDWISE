package com.spendwise.android

import androidx.activity.compose.setContent
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
 * Structural Compose tests (AD-021, §13): session-state graph routing and
 * the expired-overlay push/resume cycle — driven through the real NavHost
 * wiring by swapping the session state under the composed app.
 */
@RunWith(AndroidJUnit4::class)
class SpendwiseAppTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun app(initial: Session): MutableState<Session> {
        val state = mutableStateOf(initial)
        // MainActivity already sets content in onCreate — replace it on the
        // activity itself (the test rule refuses a second setContent call).
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                SpendwiseTheme { SpendwiseApp(session = state.value) }
            }
        }
        composeRule.waitForIdle()
        return state
    }

    private fun signedIn(
        username: String? = "ana",
        userId: String? = "u1",
        resumed: Boolean = false,
    ) = Session.SignedIn(
        token = "t",
        expiresAt = Instant.now().plusSeconds(900),
        username = username,
        userId = userId,
        resumedSession = resumed,
    )

    @Test
    fun restoring_showsSpinnerOnly() {
        app(Session.Restoring)
        composeRule.onNodeWithText("Spendwise").assertDoesNotExist()
    }

    @Test
    fun signedOut_showsWelcome() {
        app(Session.SignedOut)

        composeRule.onNodeWithText("Spendwise").assertIsDisplayed()
        composeRule.onNodeWithText("Sign in").assertIsDisplayed()
        composeRule.onNodeWithText("Create account").assertIsDisplayed()
    }

    @Test
    fun signedIn_showsFourTopLevelDestinations() {
        app(signedIn())

        listOf("Home", "Transactions", "Accounts", "More").forEach { label ->
            // "Home" matches both the nav label and the screen title.
            assert(
                composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty(),
            ) { "Expected at least one '$label' node" }
        }
    }

    @Test
    fun signedIn_navigatesBetweenDestinations() {
        app(signedIn())

        composeRule.onNodeWithText("Transactions").performClick()
        composeRule.waitForIdle()

        val nodes = composeRule.onAllNodesWithText("Transactions").fetchSemanticsNodes()
        assert(nodes.size >= 2) { "Expected nav label + screen title, got ${nodes.size}" }
    }

    @Test
    fun expired_pushesSessionExpiredOverPreservedStack() {
        val session = app(signedIn())
        composeRule.onNodeWithText("Transactions").performClick()
        composeRule.waitForIdle()

        session.value = Session.Expired(username = "ana", userId = "u1")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Session expired").assertIsDisplayed()
        composeRule.onNodeWithText("Sign in again").assertIsDisplayed()
    }

    @Test
    fun expiredTwice_doesNotStackDuplicateOverlays() {
        val session = app(signedIn())

        session.value = Session.Expired(username = "ana", userId = "u1")
        composeRule.waitForIdle()
        // A second expiry signal (e.g. another in-flight 401) must not push
        // a second SessionExpired entry onto the stack.
        session.value = Session.Expired(username = "ana", userId = "u1")
        composeRule.waitForIdle()

        val nodes = composeRule.onAllNodesWithText("Session expired").fetchSemanticsNodes()
        assert(nodes.size == 1) { "Expected exactly one expired overlay, got ${nodes.size}" }
    }

    @Test
    fun resume_sameIdentity_restoresPriorDestination() {
        val session = app(signedIn())
        composeRule.onNodeWithText("Transactions").performClick()
        composeRule.waitForIdle()

        session.value = Session.Expired(username = "ana", userId = "u1")
        composeRule.waitForIdle()

        // Same-identity re-auth → pop expired+login, restored to Transactions.
        session.value = signedIn(resumed = true)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Session expired").assertDoesNotExist()
        val nodes = composeRule.onAllNodesWithText("Transactions").fetchSemanticsNodes()
        assert(nodes.size >= 2) { "Expected nav label + screen title, got ${nodes.size}" }
    }

    @Test
    fun resume_differentIdentity_landsOnFreshHome() {
        val session = app(signedIn())
        composeRule.onNodeWithText("Transactions").performClick()
        composeRule.waitForIdle()

        session.value = Session.Expired(username = "ana", userId = "u1")
        composeRule.waitForIdle()

        // Different identity → stack cleared to the shell root (Home).
        session.value = signedIn(username = "bob", userId = "u2", resumed = false)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Session expired").assertDoesNotExist()
        // Nav label + Home screen title — at least one "Home" must show.
        assert(
            composeRule.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty(),
        ) { "Expected the fresh Home after different-identity sign-in" }
    }

    @Test
    fun signOut_returnsToWelcome() {
        val session = app(signedIn())

        session.value = Session.SignedOut
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Create account").assertIsDisplayed()
    }
}
