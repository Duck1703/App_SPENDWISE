package com.spendwise.android.feature.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Hilt-wired route entry for the More hub (AD-021 seam). */
@Composable
fun MoreDestination(
    onOpenImport: () -> Unit = {},
    onOpenRecurring: () -> Unit = {},
    onOpenBudgets: () -> Unit = {},
    onOpenRules: () -> Unit = {},
) {
    val viewModel: MoreViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MoreScreen(
        state = state,
        onSignOut = viewModel::signOut,
        onOpenImport = onOpenImport,
        onOpenRecurring = onOpenRecurring,
        onOpenBudgets = onOpenBudgets,
        onOpenRules = onOpenRules,
    )
}
