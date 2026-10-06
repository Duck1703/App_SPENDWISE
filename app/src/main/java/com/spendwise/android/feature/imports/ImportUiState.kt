package com.spendwise.android.feature.imports

import com.spendwise.android.core.model.Account
import com.spendwise.android.core.model.ImportBatch
import com.spendwise.android.data.repository.SelectedStatement

/**
 * Import (S12–S14, PRODUCT §23–25): choose → processing → result —
 * one stateful flow inside a single pushed destination.
 */
sealed interface ImportUiState {

    /** Account list is loading (the picker needs real accounts). */
    data object Loading : ImportUiState

    /** No accounts — an import needs a target account. */
    data object Empty : ImportUiState

    /**
     * S12 choose screen. [isUploading] renders the blocking S13
     * processing surface over the preserved form (the user cannot
     * navigate away — §23 "the screen is modal").
     */
    data class Form(
        val accounts: List<Account>,
        val selectedAccountId: String?,
        val file: SelectedStatement?,
        val isUploading: Boolean = false,
        val error: ImportError? = null,
        /**
         * An interrupted upload whose server-side outcome is unknown —
         * the file may have partially committed. Honest banner per
         * PRODUCT §52/PD-012: re-uploading the same file is safe.
         */
        val ambiguousNotice: Boolean = false,
    ) : ImportUiState

    /** S14 result — the same per-batch report history detail renders. */
    data class Result(val batch: ImportBatch) : ImportUiState

    /** The account list itself failed. */
    data class Error(val kind: ImportError) : ImportUiState
}

enum class ImportError {
    Network,
    Server,
    Generic,
    /** 400 / client-side extension check — file type or layout unsupported. */
    UnsupportedFile,
    /** Client-side byte-size gate — server rejects oversized uploads with 500. */
    FileTooLarge,
    /** 404 — the chosen account is gone. */
    AccountUnavailable,
}
