package com.spendwise.android.fakes

import com.spendwise.android.data.remote.ImportBatchListItemDto
import com.spendwise.android.data.remote.ImportBatchSummaryDto
import com.spendwise.android.data.remote.ImportsService
import okhttp3.MultipartBody

/**
 * Scripted [ImportsService] — records the accountId + multipart part
 * (filename readable via `part.headers`), returns the canned report or
 * throws the scripted error. [uploadResponder] gates the "network"
 * completion for concurrency/session-boundary tests.
 */
class FakeImportsService : ImportsService {

    var uploadResponse: ImportBatchSummaryDto = summaryDto()
    var uploadError: Throwable? = null
    var uploadResponder: (suspend (accountId: String, file: MultipartBody.Part) -> ImportBatchSummaryDto)? = null

    var listResponse: List<ImportBatchListItemDto> = emptyList()
    var listError: Throwable? = null
    var detailResponse: ImportBatchSummaryDto = summaryDto()
    var detailError: Throwable? = null

    data class UploadCall(val accountId: String, val fileName: String?)

    val uploadCalls = mutableListOf<UploadCall>()
    val listCalls = mutableListOf<Unit>()
    val detailCalls = mutableListOf<String>()

    override suspend fun list(): List<ImportBatchListItemDto> {
        listCalls += Unit
        listError?.let { throw it }
        return listResponse
    }

    override suspend fun detail(batchId: String): ImportBatchSummaryDto {
        detailCalls += batchId
        detailError?.let { throw it }
        return detailResponse
    }

    override suspend fun upload(
        accountId: String,
        file: MultipartBody.Part,
    ): ImportBatchSummaryDto {
        val fileName = file.headers?.get("Content-Disposition")
            ?.let { Regex("filename=\"([^\"]*)\"").find(it)?.groupValues?.get(1) }
        uploadCalls += UploadCall(accountId, fileName)
        uploadResponder?.let { return it(accountId, file) }
        uploadError?.let { throw it }
        return uploadResponse
    }

    companion object {
        fun listItemDto(
            batchId: String = "batch-1",
            accountId: String = "acc-1",
            fileName: String = "statement.csv",
            importedAt: String = "2026-10-04T12:00:00Z",
        ) = ImportBatchListItemDto(
            batchId = batchId,
            accountId = accountId,
            fileName = fileName,
            importedAt = importedAt,
            successCount = 2,
            duplicateCount = 1,
            failedCount = 1,
        )

        fun summaryDto(
            batchId: String = "batch-1",
            accountId: String = "acc-1",
            fileName: String = "statement.csv",
        ) = ImportBatchSummaryDto(
            batchId = batchId,
            accountId = accountId,
            fileName = fileName,
            importedAt = "2026-10-04T12:00:00Z",
            successCount = 2,
            duplicateCount = 1,
            failedCount = 1,
            rows = listOf(
                com.spendwise.android.data.remote.ImportRowDto(
                    rowNumber = 2,
                    status = "OK",
                    reason = null,
                    transactionId = "txn-1",
                ),
                com.spendwise.android.data.remote.ImportRowDto(
                    rowNumber = 3,
                    status = "DUPLICATE",
                    reason = "already imported",
                    transactionId = null,
                ),
                com.spendwise.android.data.remote.ImportRowDto(
                    rowNumber = 4,
                    status = "FAILED",
                    reason = "currency mismatch",
                    transactionId = null,
                ),
                com.spendwise.android.data.remote.ImportRowDto(
                    rowNumber = 5,
                    status = "OK",
                    reason = null,
                    transactionId = "txn-2",
                ),
            ),
        )
    }
}
