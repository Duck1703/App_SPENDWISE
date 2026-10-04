package com.spendwise.android.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spendwise.android.R
import com.spendwise.android.core.designsystem.SwSpacing

/**
 * Session-expired destination (§13): pushed on top of the preserved main
 * stack after an authenticated 401. "Sign in again" pushes the login
 * destination with the username prefilled; a successful same-identity
 * re-auth pops both entries and restores the prior surface — "You will
 * return to this screen." is the product promise.
 */
@Composable
fun SessionExpiredScreen(
    username: String?,
    onSignInAgain: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The session is dead — browsing the app without re-authenticating is
    // not allowed. System back is consumed; the choices are Sign in or
    // Sign out.
    BackHandler { }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = SwSpacing.Lg, vertical = SwSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = stringResource(R.string.session_expired_icon_cd),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Spacer(Modifier.height(SwSpacing.Lg))
            Text(
                text = stringResource(R.string.session_expired_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(SwSpacing.Sm))
            Text(
                text = stringResource(R.string.session_expired_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (!username.isNullOrBlank()) {
                Spacer(Modifier.height(SwSpacing.Md))
                Text(
                    text = stringResource(R.string.session_expired_signed_in_as, username),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(SwSpacing.Xl))
            Button(
                onClick = onSignInAgain,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(stringResource(R.string.session_expired_sign_in))
            }
            Spacer(Modifier.height(SwSpacing.Sm))
            TextButton(onClick = onSignOut) {
                Text(
                    text = stringResource(R.string.session_expired_sign_out),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
