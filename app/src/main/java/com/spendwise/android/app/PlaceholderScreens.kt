package com.spendwise.android.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.spendwise.android.R
import com.spendwise.android.core.designsystem.SwSpacing

/**
 * Foundation-only placeholders (M0): prove the theme, shell and navigation
 * compile and navigate — intentionally zero feature content.
 */
@Composable
fun FoundationPlaceholder(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(SwSpacing.Lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(SwSpacing.Sm))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun SessionExpiredPlaceholder(
    onSignInAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(SwSpacing.Lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.session_expired_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(SwSpacing.Sm))
            Text(
                text = stringResource(R.string.session_expired_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(SwSpacing.Lg))
            Button(onClick = onSignInAgain) {
                Text(stringResource(R.string.session_expired_sign_in))
            }
        }
    }
}

@Composable
fun WelcomePlaceholder(
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.padding(SwSpacing.Lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(SwSpacing.Sm))
            Text(
                text = stringResource(R.string.welcome_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(SwSpacing.Xl))
            Button(onClick = onSignIn) {
                Text(stringResource(R.string.welcome_sign_in))
            }
            Spacer(Modifier.height(SwSpacing.Sm))
            OutlinedButton(onClick = onCreateAccount) {
                Text(stringResource(R.string.welcome_create_account))
            }
        }
    }
}
