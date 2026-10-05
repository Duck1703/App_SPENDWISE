package com.spendwise.android.data.remote

import kotlinx.serialization.Serializable

/**
 * `ImportBatchSummaryResponse` (BACKEND-CONTRACT §16, `ImportController`
 * at backend `8194daa`):
 * `{batchId, accountId, fileName, importedAt, successCount,
 * duplicateCount, failedCount, rows:[{rowNumber, status, reason,
 * transactionId}]}`.
 *
 * `status` stays a raw `String` on the wire — the domain maps it to
 * the closed `ImportRowStatus` enum with an UNKNOWN fallback, never a
 * crash on a future value.
 */
@Serializable
data class ImportBatchSummaryDto(
    val batchId: String,
    val accountId: String,
    val fileName: String,
    val importedAt: String,
    val successCount: Int,
    val duplicateCount: Int,
    val failedCount: Int,
    val rows: List<ImportRowDto> = emptyList(),
)

@Serializable
data class ImportRowDto(
    val rowNumber: Int,
    val status: String,
    val reason: String? = null,
    val transactionId: String? = null,
)
