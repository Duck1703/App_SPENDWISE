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
import com.spendwise.android.app.FoundationPlaceholder
import com.spendwise.android.feature.more.MoreDestination

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
 * Authenticated shell (AD-004/AD-019): the four top-level destinations with
 * a bottom NavigationBar. Tab content is foundation-only in M0 — no FAB,
 * no headers, no feature actions (those arrive with their milestones).
 */
@Composable
fun SpendwiseMainShell(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

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
                FoundationPlaceholder(
                    title = "Home",
                    detail = "Dashboard foundation — content arrives in M4.",
                )
            }
            composable<TransactionsRoute> {
                FoundationPlaceholder(
                    title = "Transactions",
                    detail = "Transaction list foundation — content arrives in M3.",
                )
            }
            composable<AccountsRoute> {
                FoundationPlaceholder(
                    title = "Accounts",
                    detail = "Account list foundation — content arrives in M2.",
                )
            }
            composable<MoreRoute> {
                MoreDestination()
            }
        }
    }
}
