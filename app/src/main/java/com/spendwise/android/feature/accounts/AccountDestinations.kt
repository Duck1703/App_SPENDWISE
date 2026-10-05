package com.spendwise.android.feature.accounts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.android.core.designsystem.SwColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier

/** Tab destination — Accounts list (S06). */
@Composable
fun AccountsDestination(
    onOpenAccount: (String) -> Unit,
    onCreateAccount: () -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Re-runs when this destination re-enters composition — e.g. when a
    // same-user resume pops the expired overlay back off this screen.
    LaunchedEffect(Unit) { viewModel.onVisible() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SwColors.Ground),
    ) {
        AccountsScreen(
            state = state,
            onOpenAccount = onOpenAccount,
            onCreateAccount = onCreateAccount,
            onRetry = viewModel::refresh,
        )
    }
}

/** Pushed destination — create account (S07). Navigates to detail on success. */
@Composable
fun AccountCreateDestination(
    onNavigateBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: CreateAccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CreateAccountViewModel.Event.Created -> onCreated(event.accountId)
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SwColors.Ground)
            .systemBarsPadding(),
    ) {
        CreateAccountScreen(
            state = state,
            onNameChange = viewModel::onNameChange,
            onTypeChange = viewModel::onTypeChange,
            onCurrencyChange = viewModel::onCurrencyChange,
            onOpeningBalanceChange = viewModel::onOpeningBalanceChange,
            onSubmit = viewModel::submit,
            onNavigateBack = onNavigateBack,
        )
    }
}

/** Pushed destination — account detail (S08): header + account history. */
@Composable
fun AccountDetailDestination(
    onNavigateBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onAddTransaction: (String) -> Unit,
    viewModel: AccountDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onVisible() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SwColors.Ground)
            .systemBarsPadding(),
    ) {
        AccountDetailScreen(
            state = state,
            onRetry = viewModel::refresh,
            onNavigateBack = onNavigateBack,
            onOpenTransaction = onOpenTransaction,
            onAddTransaction = onAddTransaction,
            onRetryHistory = viewModel::retryHistory,
        )
    }
}
