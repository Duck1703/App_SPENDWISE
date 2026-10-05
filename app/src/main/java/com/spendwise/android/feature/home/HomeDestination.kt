package com.spendwise.android.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.android.core.designsystem.SwColors

/**
 * Tab destination — Home (S05). Re-runs when this destination
 * re-enters composition — e.g. when a same-user resume pops the
 * expired overlay back off this screen.
 */
@Composable
fun HomeDestination(
    onAddTransaction: () -> Unit,
    onOpenAccount: (String) -> Unit,
    onOpenTransaction: (String) -> Unit,
    onSeeAllActivity: () -> Unit,
    onCreateAccount: () -> Unit,
    onImport: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onVisible() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SwColors.Ground),
    ) {
        HomeScreen(
            state = state,
            onAddTransaction = onAddTransaction,
            onOpenAccount = onOpenAccount,
            onOpenTransaction = onOpenTransaction,
            onSeeAllActivity = onSeeAllActivity,
            onImport = onImport,
            onCreateAccount = onCreateAccount,
            onRefresh = viewModel::refresh,
            onRetryTotals = viewModel::retryTotals,
            onRetryActivity = viewModel::retryActivity,
        )
    }
}
