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

/**
 * `ImportBatchListItem` (M10 — `GET /api/imports`, REQ-BE-004): the
 * history-list summary shape. It deliberately carries **no `rows`** —
 * row detail stays behind `GET /api/imports/{id}` — so the list never
 * ships every batch's row payload in one response.
 */
@Serializable
data class ImportBatchListItemDto(
    val batchId: String,
    val accountId: String,
    val fileName: String,
    val importedAt: String,
    val successCount: Int,
    val duplicateCount: Int,
    val failedCount: Int,
)

@Serializable
data class ImportRowDto(
    val rowNumber: Int,
    val status: String,
    val reason: String? = null,
    val transactionId: String? = null,
)
