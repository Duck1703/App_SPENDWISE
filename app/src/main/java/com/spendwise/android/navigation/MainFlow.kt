package com.spendwise.android.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.spendwise.android.core.session.Session
import com.spendwise.android.feature.accounts.AccountCreateDestination
import com.spendwise.android.feature.accounts.AccountDetailDestination
import com.spendwise.android.feature.auth.LoginDestination
import com.spendwise.android.feature.auth.SessionExpiredScreen
import com.spendwise.android.feature.transactions.TransactionCreateDestination
import com.spendwise.android.feature.transactions.TransactionDetailDestination

/** savedStateHandle flag — post-create landing selects the Transactions tab once. */
private const val KEY_OPEN_TRANSACTIONS_TAB = "spendwise.openTransactionsTab"

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
                // Pushed over whatever protected surface is current — the
                // shell OR a full-screen flow like account create/detail —
                // preserving the whole stack beneath for same-user resume.
                val expiredOnStack = runCatching {
                    navController.getBackStackEntry<SessionExpiredRoute>()
                }.isSuccess
                if (!expiredOnStack) {
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
        composable<MainShellRoute> { entry ->
            val openTransactionsTab by entry.savedStateHandle
                .getStateFlow(KEY_OPEN_TRANSACTIONS_TAB, false)
                .collectAsStateWithLifecycle()
            SpendwiseMainShell(
                onOpenAccount = { id -> navController.navigate(AccountDetailRoute(id)) },
                onCreateAccount = { navController.navigate(AccountCreateRoute) },
                onOpenTransaction = { id ->
                    navController.navigate(TransactionDetailRoute(id))
                },
                onCreateTransaction = { accountId ->
                    navController.navigate(TransactionCreateRoute(accountId))
                },
                openTransactionsTab = openTransactionsTab,
                onConsumePendingTab = {
                    entry.savedStateHandle[KEY_OPEN_TRANSACTIONS_TAB] = false
                },
            )
        }
        composable<AccountCreateRoute> {
            AccountCreateDestination(
                onNavigateBack = { navController.popBackStack() },
                onCreated = { accountId ->
                    navController.navigate(AccountDetailRoute(accountId)) {
                        popUpTo<AccountCreateRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<AccountDetailRoute> {
            AccountDetailDestination(
                onNavigateBack = { navController.popBackStack() },
                onOpenTransaction = { id ->
                    navController.navigate(TransactionDetailRoute(id))
                },
                onAddTransaction = { accountId ->
                    navController.navigate(TransactionCreateRoute(accountId))
                },
            )
        }
        composable<TransactionDetailRoute> {
            TransactionDetailDestination(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable<TransactionCreateRoute> {
            TransactionCreateDestination(
                onNavigateBack = { navController.popBackStack() },
                onCreated = {
                    // Land on the Transactions tab when the shell sits
                    // beneath (tab/Home launches); an account-detail
                    // launch returns to that detail, which reloads via
                    // the repository mutations signal.
                    val previous = navController.previousBackStackEntry
                    if (previous?.destination?.hasRoute<MainShellRoute>() == true) {
                        previous.savedStateHandle[KEY_OPEN_TRANSACTIONS_TAB] = true
                    }
                    navController.popBackStack()
                },
                onCreateAccount = { navController.navigate(AccountCreateRoute) },
            )
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
