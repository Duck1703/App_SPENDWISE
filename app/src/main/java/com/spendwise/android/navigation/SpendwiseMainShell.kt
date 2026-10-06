package com.spendwise.android.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.spendwise.android.R
import com.spendwise.android.feature.accounts.AccountsDestination
import com.spendwise.android.feature.home.HomeDestination
import com.spendwise.android.feature.more.MoreDestination
import com.spendwise.android.feature.transactions.TransactionsDestination

private data class TopLevelDestination(
    val route: AppRoute,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(HomeRoute, R.string.nav_home, Icons.Default.Home),
    TopLevelDestination(TransactionsRoute, R.string.nav_transactions, Icons.AutoMirrored.Filled.List),
    TopLevelDestination(AccountsRoute, R.string.nav_accounts, Icons.Default.AccountCircle),
    TopLevelDestination(MoreRoute, R.string.nav_more, Icons.Default.MoreVert),
)

/**
 * Authenticated shell (AD-004/AD-019): the four top-level destinations
 * with a bottom NavigationBar.
 *
 * [openTransactionsTab] + [onConsumePendingTab] implement the post-create
 * landing: a successful `TransactionCreateRoute` sets the flag on this
 * entry's savedStateHandle and pops back; the shell then selects the
 * Transactions tab once (single-shot — consumed, never sticky).
 */
@Composable
fun SpendwiseMainShell(
    navController: NavHostController = rememberNavController(),
    onOpenAccount: (accountId: String) -> Unit = {},
    onCreateAccount: () -> Unit = {},
    onOpenTransaction: (transactionId: String) -> Unit = {},
    onCreateTransaction: (accountId: String?) -> Unit = {},
    onCreateTransfer: (accountId: String?) -> Unit = {},
    onOpenImport: () -> Unit = {},
    onOpenImportHistory: () -> Unit = {},
    onOpenRecurring: () -> Unit = {},
    onOpenBudgets: () -> Unit = {},
    onOpenRules: () -> Unit = {},
    onOpenLedgerExport: () -> Unit = {},
    openTransactionsTab: Boolean = false,
    onConsumePendingTab: () -> Unit = {},
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Post-create landing: select the Transactions tab exactly once.
    LaunchedEffect(openTransactionsTab) {
        if (openTransactionsTab) {
            onConsumePendingTab()
            navController.navigate(TransactionsRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                topLevelDestinations.forEach { destination ->
                    val selected = when (destination.route) {
                        HomeRoute -> currentDestination?.hasRoute<HomeRoute>() == true
                        TransactionsRoute -> currentDestination?.hasRoute<TransactionsRoute>() == true
                        AccountsRoute -> currentDestination?.hasRoute<AccountsRoute>() == true
                        MoreRoute -> currentDestination?.hasRoute<MoreRoute>() == true
                        else -> false
                    }
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = stringResource(destination.labelRes),
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<HomeRoute> {
                HomeDestination(
                    onAddTransaction = { onCreateTransaction(null) },
                    onOpenAccount = onOpenAccount,
                    onOpenTransaction = onOpenTransaction,
                    onSeeAllActivity = {
                        navController.navigate(TransactionsRoute) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onImport = onOpenImport,
                    onCreateAccount = onCreateAccount,
                )
            }
            composable<TransactionsRoute> {
                TransactionsDestination(
                    onOpenTransaction = onOpenTransaction,
                    onCreateTransaction = { onCreateTransaction(null) },
                    onCreateTransfer = { onCreateTransfer(null) },
                    onCreateAccount = onCreateAccount,
                )
            }
            composable<AccountsRoute> {
                AccountsDestination(
                    onOpenAccount = onOpenAccount,
                    onCreateAccount = onCreateAccount,
                )
            }
            composable<MoreRoute> {
                MoreDestination(
                    onOpenImport = onOpenImport,
                    onOpenImportHistory = onOpenImportHistory,
                    onOpenRecurring = onOpenRecurring,
                    onOpenBudgets = onOpenBudgets,
                    onOpenRules = onOpenRules,
                    onOpenLedgerExport = onOpenLedgerExport,
                )
            }
        }
    }
}
