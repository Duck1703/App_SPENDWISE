package com.spendwise.android.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.res.stringResource
import com.spendwise.android.R
import com.spendwise.android.app.FoundationPlaceholder
import com.spendwise.android.app.WelcomePlaceholder

/**
 * Auth graph shell (AD-004). The screens are foundation placeholders —
 * real register/login forms are the M1 milestone.
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
            WelcomePlaceholder(
                onSignIn = { navController.navigate(LoginRoute) },
                onCreateAccount = { navController.navigate(RegisterRoute) },
            )
        }
        composable<LoginRoute> {
            FoundationPlaceholder(
                title = stringResource(R.string.placeholder_sign_in_title),
                detail = stringResource(R.string.placeholder_m1_auth),
            )
        }
        composable<RegisterRoute> {
            FoundationPlaceholder(
                title = stringResource(R.string.placeholder_register_title),
                detail = stringResource(R.string.placeholder_m1_auth),
            )
        }
    }
}
