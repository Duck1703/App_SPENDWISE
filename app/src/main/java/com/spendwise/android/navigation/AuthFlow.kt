package com.spendwise.android.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.spendwise.android.feature.auth.LoginDestination
import com.spendwise.android.feature.auth.RegisterDestination
import com.spendwise.android.feature.auth.WelcomeScreen

/**
 * Unauthenticated graph (AD-004): Welcome → Login / Register.
 *
 * Successful sign-in flips the session to SignedIn — the app root swaps
 * this graph for the authenticated one (auth stack cleared by composition
 * contract, §13). Register success navigates to login with the username
 * prefilled + confirmation banner.
 */
@Composable
fun AuthFlow(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = WelcomeRoute,
    ) {
        composable<WelcomeRoute> {
            WelcomeScreen(
                onSignIn = { navController.navigate(LoginRoute()) },
                onCreateAccount = { navController.navigate(RegisterRoute) },
            )
        }
        composable<LoginRoute> {
            LoginDestination(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRegister = { navController.navigate(RegisterRoute) },
            )
        }
        composable<RegisterRoute> {
            RegisterDestination(
                onRegistered = { username ->
                    navController.navigate(LoginRoute(prefill = username, accountCreated = true)) {
                        popUpTo(WelcomeRoute)
                    }
                },
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLogin = { navController.popBackStack() },
            )
        }
    }
}
