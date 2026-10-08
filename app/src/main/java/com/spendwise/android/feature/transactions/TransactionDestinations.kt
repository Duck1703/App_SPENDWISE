package com.spendwise.android.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.android.core.designsystem.SwColors

/** Tab destination — Transactions list (S09). */
@Composable
fun TransactionsDestination(
    onOpenTransaction: (String) -> Unit,
    onCreateTransaction: () -> Unit,
    onCreateTransfer: () -> Unit,
    onCreateAccount: () -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val searchInput by viewModel.searchInput.collectAsStateWithLifecycle()
    // Re-runs when this destination re-enters composition — e.g. when a
    // same-user resume pops the expired overlay back off this screen.
    LaunchedEffect(Unit) { viewModel.onVisible() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SwColors.Ground),
    ) {
        TransactionsScreen(
            state = state,
            options = options,
            filters = filters,
            onOpenTransaction = onOpenTransaction,
            onCreateTransaction = onCreateTransaction,
            onCreateTransfer = onCreateTransfer,
            onCreateAccount = onCreateAccount,
            onAccountFilter = viewModel::onAccountFilter,
            onTypeFilter = viewModel::onTypeFilter,
            onCategoryFilter = viewModel::onCategoryFilter,
            onProvenanceFilter = viewModel::onProvenanceFilter,
            onFromFilter = viewModel::onFromFilter,
            onToFilter = viewModel::onToFilter,
            searchInput = searchInput,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onClearSearch = viewModel::clearSearch,
            onLoadMore = viewModel::loadNextPage,
            onClearFilters = viewModel::clearFilters,
            onRefresh = viewModel::refresh,
            onRetry = viewModel::refresh,
        )
    }
}

/** Pushed destination — transaction detail (S10), read-only. */
@Composable
fun TransactionDetailDestination(
    onNavigateBack: () -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onVisible() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SwColors.Ground)
            .systemBarsPadding(),
    ) {
        TransactionDetailScreen(
            state = state,
            onRetry = viewModel::refresh,
            onNavigateBack = onNavigateBack,
        )
    }
}

/** Pushed destination — create transaction (S11); pops back on success. */
@Composable
fun TransactionCreateDestination(
    onNavigateBack: () -> Unit,
    onCreated: () -> Unit,
    onCreateAccount: () -> Unit,
    viewModel: CreateTransactionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onVisible() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                CreateTransactionViewModel.Event.Created -> onCreated()
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SwColors.Ground)
            .systemBarsPadding(),
    ) {
        CreateTransactionScreen(
            state = state,
            onTypeChange = viewModel::onTypeChange,
            onAccountChange = viewModel::onAccountChange,
            onAmountChange = viewModel::onAmountChange,
            onDateChange = viewModel::onDateChange,
            onCategoryChange = viewModel::onCategoryChange,
            onDescriptionChange = viewModel::onDescriptionChange,
            onSubmit = viewModel::submit,
            onNavigateBack = onNavigateBack,
            onCreateAccount = onCreateAccount,
            onRetryOptions = viewModel::loadOptions,
        )
    }
}
