package com.spendwise.android.feature.auth

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.core.designsystem.SpendwiseTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stateless-screen tests: rendering, validation display, password toggle,
 * submit lock, and error banners for the login form.
 */
@RunWith(AndroidJUnit4::class)
class LoginScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private data class Harness(
        var submitted: Int = 0,
        var usernameValue: String = "",
        var passwordValue: String = "",
        var toggled: Int = 0,
    )

    private fun screen(initial: LoginUiState, harness: Harness = Harness()) =
        mutableStateOf(initial).also { state ->
            composeRule.setContent {
                SpendwiseTheme {
                    LoginScreen(
                        state = state.value,
                        onUsernameChanged = { harness.usernameValue = it },
                        onPasswordChanged = { harness.passwordValue = it },
                        onTogglePasswordVisibility = { harness.toggled++ },
                        onSubmit = { harness.submitted++ },
                        onNavigateBack = {},
                        onNavigateToRegister = {},
                    )
                }
            }
        }

    @Test
    fun rendersAllControls() {
        screen(LoginUiState())

        composeRule.onNodeWithText("Username").assertIsDisplayed()
        composeRule.onNodeWithText("Password").assertIsDisplayed()
        composeRule.onNodeWithText("Sign in").assertIsDisplayed()
        composeRule.onNodeWithText("Show password").assertIsDisplayed()
    }

    @Test
    fun typingAndSubmitReachCallbacks() {
        val harness = Harness()
        screen(LoginUiState(), harness)

        composeRule.onNodeWithText("Username").performTextInput("ana")
        composeRule.onNodeWithText("Password").performTextInput("secret")
        composeRule.onNodeWithText("Sign in").performClick()

        assertEquals("ana", harness.usernameValue)
        assertEquals("secret", harness.passwordValue)
        assertEquals(1, harness.submitted)
    }

    @Test
    fun passwordVisibilityToggle_swapsIconAndSemantics() {
        val state = screen(LoginUiState())

        composeRule.onNodeWithText("Password").performTextInput("secret")
        composeRule.onNodeWithContentDescription("Show password").performClick()

        state.value = state.value.copy(passwordVisible = true)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Hide password").assertIsDisplayed()
    }

    @Test
    fun submitting_locksControlsAndShowsSpinner() {
        screen(LoginUiState(isSubmitting = true))

        composeRule.onNodeWithText("Username").assertIsNotEnabled()
        composeRule.onNodeWithText("Password").assertIsNotEnabled()
        // While submitting, the button shows a spinner instead of the label.
        composeRule.onNodeWithText("Sign in").assertDoesNotExist()
    }

    @Test
    fun fieldErrorsRenderInline() {
        screen(
            LoginUiState(
                usernameError = AuthFieldError.Required,
                passwordError = AuthFieldError.Server("must not be blank"),
            ),
        )

        val nodes = composeRule.onAllNodesWithText("This field is required").fetchSemanticsNodes()
        assertTrue(nodes.isNotEmpty())
        composeRule.onNodeWithText("must not be blank").assertIsDisplayed()
    }

    @Test
    fun invalidCredentialsShowsBanner() {
        screen(LoginUiState(formError = AuthFormError.InvalidCredentials))

        composeRule.onNodeWithText("Incorrect username or password.").assertIsDisplayed()
    }

    @Test
    fun accountCreatedBannerShowsAfterRegister() {
        screen(LoginUiState(showAccountCreatedBanner = true))

        composeRule.onNodeWithText("Account created — sign in to continue.")
            .assertIsDisplayed()
    }

    @Test
    fun resumeModeHidesCreateAccountLink() {
        screen(LoginUiState(isResume = true))

        composeRule.onNodeWithText("Create one").assertDoesNotExist()
        composeRule.onNodeWithText("Sign in again").assertIsDisplayed()
    }
}
