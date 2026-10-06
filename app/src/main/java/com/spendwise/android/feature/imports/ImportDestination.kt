package com.spendwise.android.feature.imports

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
private fun ResumeEffect(onVisible: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onVisible()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/**
 * Import (S12–S14, PRODUCT §23–25). Stateful inside one pushed
 * destination: choose → processing → result.
 *
 * - File bytes come from the system document picker (`OpenDocument`,
 *   CSV/PDF + octet-stream acceptors) — no storage permission needed.
 * - While the synchronous upload runs the form renders S13 and
 *   **system back is disabled** (the flow is modal — §23).
 * - `Done` returns to the origin tab; repository mutations already
 *   refreshed every surface.
 * - `ON_RESUME` re-runs an aborted account load after a same-user
 *   resume — never an automatic re-upload.
 */
@Composable
fun ImportDestination(
    onDone: () -> Unit,
    onBack: () -> Unit,
    onCreateAccount: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.onFilePicked(it.toString()) } }

    val uploading = (state as? ImportUiState.Form)?.isUploading == true
    BackHandler(enabled = uploading) { /* S13 is modal — no escape mid-upload. */ }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onVisible()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ImportScreen(
        state = state,
        onAccountSelected = viewModel::onAccountSelected,
        onPickFile = {
            pickFile.launch(
                arrayOf(
                    "text/csv",
                    "text/comma-separated-values",
                    "application/csv",
                    "application/pdf",
                    "application/octet-stream",
                ),
            )
        },
        onFileCleared = viewModel::onFileCleared,
        onSubmit = viewModel::submit,
        onRetry = viewModel::retry,
        onDone = onDone,
        onCreateAccount = onCreateAccount,
        onBack = onBack,
    )
}

/**
 * Import history (M10, REQ-BE-004) — server-truth batch list; a row
 * pushes the per-batch report. ON_RESUME re-runs an aborted load on a
 * same-user resume.
 */
@Composable
fun ImportHistoryDestination(
    onOpenBatch: (String) -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
    viewModel: ImportHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResumeEffect(viewModel::onVisible)
    ImportHistoryScreen(
        state = state,
        onOpenBatch = onOpenBatch,
        onImport = onImport,
        onRetry = viewModel::refresh,
        onBack = onBack,
    )
}

/** Per-batch report (`GET /api/imports/{id}`) — read-only. */
@Composable
fun ImportHistoryDetailDestination(
    onBack: () -> Unit,
    viewModel: ImportHistoryDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResumeEffect(viewModel::onVisible)
    ImportHistoryDetailScreen(
        state = state,
        onRetry = viewModel::refresh,
        onBack = onBack,
    )
}
