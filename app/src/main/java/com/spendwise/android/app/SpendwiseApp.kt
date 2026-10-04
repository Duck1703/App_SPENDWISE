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
import com.spendwise.android.navigation.MainFlow

/**
 * App root (AD-004): selects the graph from session state.
 *
 * - [Session.Restoring] → launch spinner while the store is read.
 * - [Session.SignedOut] → unauthenticated graph (Welcome/Login/Register).
 * - [Session.SignedIn] / [Session.Expired] → the SAME authenticated-graph
 *   branch — sharing one composition slot is what keeps the main back
 *   stack (and draft ViewModels) alive while the session-expired
 *   destination is pushed on top (§13).
 *
 * Logout and sign-out-from-expired both funnel through [onSignOut];
 * session→SignedOut swaps graphs to the auth root automatically.
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

        is Session.SignedIn, is Session.Expired -> MainFlow(
            session = session,
            onSignOut = onSignOut,
        )
    }
}
