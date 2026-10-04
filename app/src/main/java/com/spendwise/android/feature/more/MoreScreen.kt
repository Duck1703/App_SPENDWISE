package com.spendwise.android.feature.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.spendwise.android.R
import com.spendwise.android.core.designsystem.SwSpacing

/**
 * More hub — M1: placeholder content + the sign-out affordance with a
 * confirmation dialog (§13). Stateless; the route wrapper supplies the
 * ViewModel state.
 */
@Composable
fun MoreScreen(
    state: MoreUiState,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmVisible by remember { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(SwSpacing.Lg),
        ) {
            Text(
                text = stringResource(R.string.more_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(SwSpacing.Sm))
            Text(
                text = stringResource(R.string.more_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(SwSpacing.Lg))
            HorizontalDivider()
            Spacer(Modifier.height(SwSpacing.Lg))
            OutlinedButton(
                onClick = { confirmVisible = true },
                enabled = !state.isSigningOut,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(
                        if (state.isSigningOut) R.string.more_signing_out else R.string.more_sign_out,
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (confirmVisible) {
        AlertDialog(
            onDismissRequest = { confirmVisible = false },
            title = { Text(stringResource(R.string.sign_out_dialog_title)) },
            text = { Text(stringResource(R.string.sign_out_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmVisible = false
                        onSignOut()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.sign_out_dialog_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmVisible = false }) {
                    Text(stringResource(R.string.sign_out_dialog_cancel))
                }
            },
        )
    }
}
