package com.spendwise.android.feature.export

import com.spendwise.android.core.model.Account

/**
 * Ledger export (M9): choose scope → download → system save dialog →
 * saved/failed. One stateful flow inside a pushed destination — mirrors
 * the Import flow's shape (S12-style form, modal transit phases).
 */
sealed interface ExportUiState {

    /** Account list is loading (the scope picker needs real accounts). */
    data object Loading : ExportUiState

    /** The account list itself failed. */
    data class Error(val kind: ExportError) : ExportUiState

    /** Export form — [selectedAccountId] `null` means "All accounts". */
    data class Ready(
        val accounts: List<Account>,
        val selectedAccountId: String? = null,
        val phase: Phase = Phase.Idle,
        /** Server-suggested file name once the artifact is downloaded. */
        val pendingFileName: String? = null,
        /** Non-null while the SAF picker should be launched — one-shot. */
        val saveRequest: SaveRequest? = null,
        /** Honest "the file was saved" marker — set only after the copy finished. */
        val saved: Boolean = false,
        val error: ExportError? = null,
    ) : ExportUiState

    enum class Phase {
        /** Form is interactive; Export CSV is enabled. */
        Idle,

        /** GET in flight — modal, no back, no duplicate submit. */
        Downloading,

        /** Artifact ready; system save dialog is (about to be) open. */
        AwaitingDestination,

        /** Destination chosen — streaming copy in flight (modal). */
        Saving,
    }
}

/** One-shot save-dialog launch request — consumed once by the destination. */
data class SaveRequest(val seq: Int, val fileName: String)

enum class ExportError {
    Network,
    Server,
    Generic,
    /** 404 — the chosen account is gone. */
    AccountUnavailable,
    /** Downloaded fine but the destination write failed. */
    SaveFailed,
}
