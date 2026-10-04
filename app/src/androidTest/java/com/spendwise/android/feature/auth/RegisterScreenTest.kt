package com.spendwise.android.feature.auth

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.core.designsystem.SpendwiseTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegisterScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun screen(initial: RegisterUiState, onSubmit: () -> Unit = {}) =
        mutableStateOf(initial).also { state ->
            composeRule.setContent {
                SpendwiseTheme {
                    RegisterScreen(
                        state = state.value,
                        onUsernameChanged = {},
                        onPasswordChanged = {},
                        onTogglePasswordVisibility = {},
                        onSubmit = onSubmit,
                        onNavigateBack = {},
                        onNavigateToLogin = {},
                    )
                }
            }
        }

    @Test
    fun rendersControlsAndPasswordHint() {
        screen(RegisterUiState())

        composeRule.onNodeWithText("Create your account").assertIsDisplayed()
        composeRule.onNodeWithText("Username").assertIsDisplayed()
        composeRule.onNodeWithText("Password").assertIsDisplayed()
        composeRule.onNodeWithText("At least 8 characters").assertIsDisplayed()
        composeRule.onNodeWithText("Create account").assertIsDisplayed()
    }

    @Test
    fun passwordToggleSwapsIcon() {
        val state = screen(RegisterUiState())

        composeRule.onNodeWithContentDescription("Show password").performClick()
        state.value = state.value.copy(passwordVisible = true)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Hide password").assertIsDisplayed()
    }

    @Test
    fun hedgedDuplicateErrorShowsBanner() {
        screen(RegisterUiState(formError = AuthFormError.UsernamePossiblyTaken))

        composeRule.onNodeWithText(
            "We couldn't create your account. This username may already be taken — try another one.",
        ).assertIsDisplayed()
    }

    @Test
    fun fieldErrorsRenderInline() {
        screen(
            RegisterUiState(
                usernameError = AuthFieldError.Required,
                passwordError = AuthFieldError.TooShort(8),
            ),
        )

        composeRule.onNodeWithText("This field is required").assertIsDisplayed()
        composeRule.onNodeWithText("Use at least 8 characters").assertIsDisplayed()
    }

    @Test
    fun submitReachesCallback() {
        var submitted = 0
        screen(RegisterUiState(), onSubmit = { submitted++ })

        composeRule.onNodeWithText("Create account").performClick()

        assertEquals(1, submitted)
    }
}
