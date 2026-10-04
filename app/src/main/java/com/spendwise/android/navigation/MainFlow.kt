package com.spendwise.android.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.spendwise.android.core.session.Session
import com.spendwise.android.feature.auth.LoginDestination
import com.spendwise.android.feature.auth.SessionExpiredScreen

/**
 * Authenticated graph (AD-004, §13): the tab shell plus the
 * session-expired + re-authentication destinations.
 *
 * Session transitions are handled by a single idempotent effect:
 * - `Expired` while the shell is on top → push [SessionExpiredRoute] (the
 *   main stack — and every draft ViewModel on it — stays preserved).
 * - `SignedIn` with the expired overlay on the stack → same-identity resume
 *   pops BOTH the login and expired entries, restoring the prior surface;
 *   a different identity clears everything to the shell root.
 * - `SignedOut` never reaches here — the app root swaps graphs first.
 */
@Composable
fun MainFlow(
    session: Session,
    onSignOut: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    LaunchedEffect(session) {
        when (session) {
            is Session.SignedIn -> {
                val expiredOnStack = runCatching {
                    navController.getBackStackEntry<SessionExpiredRoute>()
                }.isSuccess
                when {
                    expiredOnStack && session.resumedSession ->
                        navController.popBackStack<SessionExpiredRoute>(inclusive = true)
                    expiredOnStack ->
                        navController.navigate(MainShellRoute) {
                            popUpTo(0) { inclusive = true }
                        }
                    else -> Unit
                }
            }
            is Session.Expired -> {
                if (navController.currentDestination?.hasRoute<MainShellRoute>() == true) {
                    navController.navigate(SessionExpiredRoute)
                }
            }
            else -> Unit
        }
    }

    NavHost(
        navController = navController,
        startDestination = MainShellRoute,
    ) {
        composable<MainShellRoute> {
            SpendwiseMainShell()
        }
        composable<SessionExpiredRoute> {
            val expired = session as? Session.Expired
            SessionExpiredScreen(
                username = expired?.username,
                onSignInAgain = {
                    navController.navigate(
                        LoginRoute(resume = true, prefill = expired?.username),
                    )
                },
                onSignOut = onSignOut,
            )
        }
        composable<LoginRoute> {
            LoginDestination(
                onNavigateBack = { navController.popBackStack() },
                // Unreachable: the register cross-link is hidden in resume
                // mode — kept as back for completeness.
                onNavigateToRegister = { navController.popBackStack() },
            )
        }
    }
}
