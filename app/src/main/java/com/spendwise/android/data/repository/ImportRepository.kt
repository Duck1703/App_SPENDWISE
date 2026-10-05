package com.spendwise.android.data.repository

import android.content.Context
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.spendwise.android.core.model.ImportBatch
import com.spendwise.android.core.model.ImportRow
import com.spendwise.android.core.model.ImportRowStatus
import com.spendwise.android.core.network.ApiException
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.core.network.toAppError
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.remote.ImportsService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

/**
 * A document the user picked — identity only; bytes stream at upload.
 * [uri] is the picker's content-URI string form — the platform seam
 * parses it back so this layer stays free of `android.net` types.
 */
data class SelectedStatement(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
)

/**
 * Platform seam around `ContentResolver` — describes a picked document
 * and streams it into the multipart `file` part. Injected so the
 * repository's network/error/session logic stays unit-testable.
 */
interface StatementStore {
    suspend fun describe(uri: String): SelectedStatement
    fun toPart(file: SelectedStatement): MultipartBody.Part
}

/**
 * Statement import (PRODUCT §23–25, BACKEND-CONTRACT §16).
 *
 * - The file is **streamed** to the server — the app never parses,
 *   never loads the statement into memory, never inspects content.
 * - The upload is **never auto-retried** (non-idempotent POST at the
 *   transport level too — `retryOnConnectionFailure` is off app-wide);
 *   the honest outcome-ambiguous copy covers interruption because
 *   server dedup makes a manual re-upload safe.
 * - On success the account cache is invalidated and the ledger
 *   mutation signal fires — Home/Transactions reload; the client
 *   computes nothing.
 * - A response landing after a generation boundary throws
 *   [SessionEndedException] — a stale user's import result never
 *   surfaces.
 */
interface ImportRepository {

    /** Display name + byte size of a picked document (no content read). */
    suspend fun describe(uri: String): SelectedStatement

    /** `POST /api/imports?accountId=` + multipart `file` — synchronous. */
    suspend fun upload(accountId: String, file: SelectedStatement): ImportBatch
}

@Singleton
class RemoteImportRepository @Inject constructor(
    private val service: ImportsService,
    private val sessionManager: SessionManager,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val statements: StatementStore,
) : ImportRepository {

    override suspend fun describe(uri: String): SelectedStatement =
        statements.describe(uri)

    override suspend fun upload(accountId: String, file: SelectedStatement): ImportBatch {
        val generation = sessionManager.sessionGeneration
        val part = try {
            statements.toPart(file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A local read failure never reached the network — the UI
            // must not claim an ambiguous server-side outcome.
            throw StatementUnreadableException(e)
        }
        val batch = try {
            service.upload(accountId, part).toDomain()
        } catch (e: ApiException) {
            throw AppErrorException(e.toAppError(), e)
        } catch (e: IOException) {
            throw AppErrorException(AppError.NetworkUnavailable, e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AppErrorException(AppError.Unknown(e), e)
        }
        // The server committed imported rows + balance mutations — drop
        // the account cache and signal the ledger, matching the
        // create() ordering: a dead-generation write never signals into
        // a new session.
        accountRepository.invalidate()
        if (sessionManager.sessionGeneration != generation) throw SessionEndedException()
        transactionRepository.notifyLedgerChanged()
        return batch
    }

    private fun com.spendwise.android.data.remote.ImportBatchSummaryDto.toDomain() =
        ImportBatch(
            batchId = batchId,
            accountId = accountId,
            fileName = fileName,
            importedAt = Instant.parse(importedAt),
            successCount = successCount,
            duplicateCount = duplicateCount,
            failedCount = failedCount,
            rows = rows.map { row ->
                ImportRow(
                    rowNumber = row.rowNumber,
                    status = ImportRowStatus.of(row.status),
                    reason = row.reason,
                    transactionId = row.transactionId,
                )
            },
        )
}

/** The picked file couldn't be opened/read locally — nothing was sent. */
internal class StatementUnreadableException(cause: Throwable) : Exception(cause)

/**
 * `ContentResolver`-backed [StatementStore] — the picked URI's display
 * name carries the extension the server dispatches on, and bytes
 * stream straight through (a statement never lives in memory).
 */
@Singleton
class ContentResolverStatementStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : StatementStore {

    override suspend fun describe(uri: String): SelectedStatement =
        // Provider queries are blocking I/O — never on the UI thread.
        withContext(Dispatchers.IO) {
            val contentUri = uri.toUri()
            var name: String? = null
            var size = -1L
            try {
                context.contentResolver.query(
                    contentUri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        name = cursor.getString(0)
                        if (!cursor.isNull(1)) size = cursor.getLong(1)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Provider oddities degrade to unknown name/size — the
                // upload decides; never crash on a picker result.
            }
            SelectedStatement(
                uri = uri,
                displayName = name ?: contentUri.lastPathSegment?.substringAfterLast('/')
                    ?: "statement",
                sizeBytes = size,
            )
        }

    override fun toPart(file: SelectedStatement): MultipartBody.Part {
        val uri = file.uri.toUri()
        val body = object : RequestBody() {
            override fun contentType() =
                context.contentResolver.getType(uri)?.toMediaTypeOrNull()

            override fun contentLength(): Long = file.sizeBytes

            override fun writeTo(sink: BufferedSink) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    sink.writeAll(input.source())
                } ?: throw IOException("statement is no longer readable")
            }
        }
        return MultipartBody.Part.createFormData("file", file.displayName, body)
    }
}
