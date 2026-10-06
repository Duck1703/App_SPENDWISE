package com.spendwise.android.feature.imports

import com.spendwise.android.core.model.ImportBatch
import com.spendwise.android.core.model.ImportBatchSummary

/**
 * Import history (M10, REQ-BE-004): `GET /api/imports` — server-truth
 * batch summaries, newest first, no row payloads.
 */
sealed interface ImportHistoryState {

    data object Loading : ImportHistoryState

    data class Error(val kind: ImportHistoryError) : ImportHistoryState

    /** `200 []` — an empty history is a real state, never an error. */
    data object Empty : ImportHistoryState

    data class Content(
        val items: List<ImportBatchSummary>,
        /** Display-only context — degrades to unknown-account copy. */
        val accountNames: Map<String, String>,
        val isRefreshing: Boolean = false,
        val refreshError: ImportHistoryError? = null,
    ) : ImportHistoryState
}

/** Batch detail (`GET /api/imports/{id}`) — the full per-row report. */
sealed interface ImportHistoryDetailState {
    data object Loading : ImportHistoryDetailState
    data object NotFound : ImportHistoryDetailState
    data class Error(val kind: ImportHistoryError) : ImportHistoryDetailState

    data class Content(
        val batch: ImportBatch,
        val accountName: String?,
        val isRefreshing: Boolean = false,
        val refreshError: ImportHistoryError? = null,
    ) : ImportHistoryDetailState
}

enum class ImportHistoryError { Network, Server, Generic }
