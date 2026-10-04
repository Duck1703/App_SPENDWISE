package com.spendwise.android.feature.auth

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.spendwise.android.R
import com.spendwise.android.core.designsystem.SwSpacing

/**
 * Login form — stateless presentation; all state arrives via [state] and
 * all user intent exits via callbacks, so the screen is Compose-testable
 * without Hilt (AD-021).
 *
 * Resume mode (re-authentication after expiry) hides the create-account
 * cross-link; the preserved stack handles the rest (§13).
 */
@Composable
fun LoginScreen(
    state: LoginUiState,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToRegister: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    AuthScaffold(
        title = stringResource(
            if (state.isResume) R.string.login_title_resume else R.string.login_title,
        ),
        subtitle = stringResource(R.string.login_subtitle),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) {
        if (state.showAccountCreatedBanner) {
            InfoBanner(text = stringResource(R.string.login_banner_account_created))
            Spacer(Modifier.height(SwSpacing.Md))
        }
        state.formError?.let { error ->
            FormErrorBanner(text = error.displayText())
            Spacer(Modifier.height(SwSpacing.Md))
        }

        OutlinedTextField(
            value = state.username,
            onValueChange = onUsernameChanged,
            label = { Text(stringResource(R.string.auth_username_label)) },
            singleLine = true,
            enabled = !state.isSubmitting,
            isError = state.usernameError != null,
            supportingText = state.usernameError?.let { { Text(it.displayText()) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(SwSpacing.Sm))

        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChanged,
            label = { Text(stringResource(R.string.auth_password_label)) },
            singleLine = true,
            enabled = !state.isSubmitting,
            isError = state.passwordError != null,
            supportingText = state.passwordError?.let { { Text(it.displayText()) } },
            visualTransformation = if (state.passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = onTogglePasswordVisibility) {
                    Icon(
                        imageVector = if (state.passwordVisible) {
                            Icons.Filled.VisibilityOff
                        } else {
                            Icons.Filled.Visibility
                        },
                        contentDescription = stringResource(
                            if (state.passwordVisible) {
                                R.string.auth_password_hide
                            } else {
                                R.string.auth_password_show
                            },
                        ),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    onSubmit()
                },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(SwSpacing.Lg))

        Button(
            onClick = {
                keyboardController?.hide()
                onSubmit()
            },
            enabled = !state.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.login_submit))
            }
        }

        if (!state.isResume) {
            Spacer(Modifier.height(SwSpacing.Md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.login_no_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onNavigateToRegister) {
                    Text(stringResource(R.string.login_create_link))
                }
            }
        }
    }
}
