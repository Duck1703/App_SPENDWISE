package com.spendwise.android.feature.export

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Ledger Export (M9). Stateful inside one pushed destination:
 * scope → download → system save dialog → result.
 *
 * - The destination is written through `CreateDocument("text/csv")`
 *   (SAF) — no storage permission anywhere in the flow.
 * - The picker launches off a one-shot [SaveRequest] carried in UI
 *   state: keyed `LaunchedEffect` never re-triggers on recomposition;
 *   on rotation the request persists (the artifact survives in the
 *   ViewModel) and the destroyed picker is relaunched.
 * - Downloading/Saving are modal — system back is disabled (leaving
 *   mid-copy would make the outcome ambiguous), mirroring import's
 *   S13 contract.
 * - `ON_RESUME` re-runs an aborted account load after a same-user
 *   resume — never an automatic re-download.
 */
@Composable
fun ExportLedgerDestination(
    onBack: () -> Unit,
    viewModel: ExportLedgerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            viewModel.onDestinationChosen(uri.toString())
        } else {
            viewModel.onDestinationCancelled()
        }
    }

    // One-shot picker launch — keyed on the request sequence so a new
    // export opens a new dialog while recompositions never re-launch.
    val saveRequest = (state as? ExportUiState.Ready)?.saveRequest
    LaunchedEffect(saveRequest) {
        saveRequest?.let { saveFile.launch(it.fileName) }
    }

    val ready = state as? ExportUiState.Ready
    val modal = ready != null &&
        (ready.phase == ExportUiState.Phase.Downloading ||
            ready.phase == ExportUiState.Phase.Saving)
    BackHandler(enabled = modal) { /* modal transit — no escape mid-copy. */ }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onVisible()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ExportLedgerScreen(
        state = state,
        onScopeSelected = viewModel::onScopeSelected,
        onExport = viewModel::export,
        onRetry = viewModel::retry,
        onBack = onBack,
    )
}
