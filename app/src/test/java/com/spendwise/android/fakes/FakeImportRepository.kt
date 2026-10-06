package com.spendwise.android.fakes

import com.spendwise.android.core.model.ImportBatch
import com.spendwise.android.core.model.ImportBatchSummary
import com.spendwise.android.core.model.ImportRow
import com.spendwise.android.core.model.ImportRowStatus
import com.spendwise.android.data.repository.ImportRepository
import com.spendwise.android.data.repository.SelectedStatement
import java.time.Instant

/** Scripted [ImportRepository] for ViewModel tests — records calls, runs configured results. */
class FakeImportRepository : ImportRepository {

    var describeResult: suspend (String) -> SelectedStatement = { uri ->
        SelectedStatement(uri = uri, displayName = "statement.csv", sizeBytes = 4)
    }
    var uploadResult: suspend (accountId: String, file: SelectedStatement) -> ImportBatch =
        { _, _ -> batch() }
    var historyResult: suspend () -> List<ImportBatchSummary> = { emptyList() }
    var batchResult: suspend (String) -> ImportBatch =
        { FakeImportRepository.batch(batchId = it) }

    data class UploadCall(val accountId: String, val file: SelectedStatement)

    val describeCalls = mutableListOf<String>()
    val uploadCalls = mutableListOf<UploadCall>()
    val historyCalls = mutableListOf<Unit>()
    val batchCalls = mutableListOf<String>()

    override suspend fun describe(uri: String): SelectedStatement {
        describeCalls += uri
        return describeResult(uri)
    }

    override suspend fun upload(accountId: String, file: SelectedStatement): ImportBatch {
        uploadCalls += UploadCall(accountId, file)
        return uploadResult(accountId, file)
    }

    override suspend fun history(): List<ImportBatchSummary> {
        historyCalls += Unit
        return historyResult()
    }

    override suspend fun batch(batchId: String): ImportBatch {
        batchCalls += batchId
        return batchResult(batchId)
    }

    companion object {
        fun summary(
            batchId: String = "batch-1",
            accountId: String = "acc-1",
            fileName: String = "statement.csv",
            successCount: Int = 2,
            duplicateCount: Int = 1,
            failedCount: Int = 1,
        ) = ImportBatchSummary(
            batchId = batchId,
            accountId = accountId,
            fileName = fileName,
            importedAt = Instant.parse("2026-10-04T12:00:00Z"),
            successCount = successCount,
            duplicateCount = duplicateCount,
            failedCount = failedCount,
        )
        fun row(
            rowNumber: Int,
            status: ImportRowStatus = ImportRowStatus.OK,
            reason: String? = null,
            transactionId: String? = "txn-$rowNumber",
        ) = ImportRow(
            rowNumber = rowNumber,
            status = status,
            reason = reason,
            transactionId = transactionId,
        )

        fun batch(
            batchId: String = "batch-1",
            accountId: String = "acc-1",
            fileName: String = "statement.csv",
            successCount: Int = 2,
            duplicateCount: Int = 0,
            failedCount: Int = 0,
            rows: List<ImportRow> = listOf(row(2), row(3, transactionId = "txn-3")),
        ) = ImportBatch(
            batchId = batchId,
            accountId = accountId,
            fileName = fileName,
            importedAt = Instant.parse("2026-10-04T12:00:00Z"),
            successCount = successCount,
            duplicateCount = duplicateCount,
            failedCount = failedCount,
            rows = rows,
        )
    }
}
