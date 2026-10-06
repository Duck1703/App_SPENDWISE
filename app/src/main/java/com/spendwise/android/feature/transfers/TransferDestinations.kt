package com.spendwise.android.feature.transfers

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

/** Pushed destination — create transfer (M12); pops back on success. */
@Composable
fun TransferCreateDestination(
    onNavigateBack: () -> Unit,
    onCreated: () -> Unit,
    onCreateAccount: () -> Unit,
    viewModel: CreateTransferViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onVisible() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                CreateTransferViewModel.Event.Created -> onCreated()
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SwColors.Ground)
            .systemBarsPadding(),
    ) {
        CreateTransferScreen(
            state = state,
            onSourceChange = viewModel::onSourceChange,
            onDestinationChange = viewModel::onDestinationChange,
            onAmountChange = viewModel::onAmountChange,
            onSubmit = viewModel::submit,
            onNavigateBack = onNavigateBack,
            onCreateAccount = onCreateAccount,
            onRetryOptions = viewModel::loadOptions,
        )
    }
}
