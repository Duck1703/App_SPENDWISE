package com.spendwise.android.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.spendwise.android.core.session.Session
import com.spendwise.android.navigation.AuthFlow
import com.spendwise.android.navigation.SpendwiseMainShell

/**
 * App root (AD-004): selects the structural shell from session state.
 *
 * M0 scope: [Session.SignedOut] → auth placeholder graph, [Session.SignedIn]
 * → the four-destination main shell, [Session.Expired] → the session-expired
 * placeholder. The real auth flows and session-expired stack preservation
 * are M1 work.
 */
@Composable
fun SpendwiseApp(
    session: Session,
    onSignOut: () -> Unit = {},
) {
    when (session) {
        Session.Restoring -> {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Session.SignedOut -> AuthFlow()

        is Session.SignedIn -> SpendwiseMainShell()

        is Session.Expired -> SessionExpiredPlaceholder(onSignInAgain = onSignOut)
    }
}
