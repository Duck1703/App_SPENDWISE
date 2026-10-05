package com.spendwise.android.core.model

import java.time.Instant

/**
 * One-shot import result (PRODUCT §25, BACKEND-CONTRACT §16).
 * The result IS the report — there is no batch list endpoint.
 */
data class ImportBatch(
    val batchId: String,
    val accountId: String,
    val fileName: String,
    val importedAt: Instant,
    val successCount: Int,
    val duplicateCount: Int,
    val failedCount: Int,
    val rows: List<ImportRow>,
) {
    val totalRows: Int get() = rows.size
}

/** Per-row outcome — row numbers are file-relative (CSV: 2-based lines). */
data class ImportRow(
    val rowNumber: Int,
    val status: ImportRowStatus,
    val reason: String?,
    val transactionId: String?,
)

/**
 * `ImportRowStatus` on the wire is `OK|DUPLICATE|FAILED` — an unknown
 * future value maps to [UNKNOWN], never a crash (AccountType pattern).
 */
enum class ImportRowStatus {
    OK,
    DUPLICATE,
    FAILED,
    UNKNOWN,
    ;

    companion object {
        fun of(value: String): ImportRowStatus =
            entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}
