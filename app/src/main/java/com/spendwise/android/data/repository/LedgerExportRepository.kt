package com.spendwise.android.data.repository

import android.content.Context
import androidx.core.net.toUri
import com.spendwise.android.core.network.ApiException
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.core.network.toAppError
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.remote.TransactionsService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody

/**
 * A downloaded ledger export — a private temp artifact awaiting the
 * user's chosen destination. Opaque by design: the UI sees only the
 * suggested file name and byte size; CSV bytes are never surfaced to
 * Compose, never parsed, never turned into domain objects (the
 * backend-owned `occurredOn` column stays inside the file — BE-001
 * remains gated).
 */
class LedgerExportArtifact internal constructor(
    val suggestedFileName: String,
    val sizeBytes: Long,
    internal val file: File,
) {
    /**
     * Session generation that produced this artifact — bound by the
     * repository in `download()`. `save()` refuses anything whose
     * generation no longer matches, so a stale-session artifact can
     * never be written to a destination.
     */
    internal var boundGeneration: Long = Long.MIN_VALUE
}

/**
 * Platform seam around temp-file + `ContentResolver` streaming —
 * injected so the repository's network/error/session logic stays
 * unit-testable (mirrors `StatementStore` for imports).
 */
interface ExportFileStore {
    /** Stream a network body into an app-private temp file. */
    suspend fun writeTemp(suggestedFileName: String, body: ResponseBody): LedgerExportArtifact

    /** Stream the temp artifact to a SAF destination URI. */
    suspend fun copyTo(artifact: LedgerExportArtifact, destinationUri: String)

    /** Best-effort delete — safe to call twice or on a missing file. */
    suspend fun discard(artifact: LedgerExportArtifact)

    /** Purge every leftover temp export (session boundary, startup). */
    suspend fun discardOrphans()
}

/**
 * Ledger export (M9, PRODUCT decision — server-generated CSV only).
 *
 * - The CSV is **server truth transported**: `GET
 *   /api/transactions/export` streams into `cacheDir`, then into the
 *   user's Storage Access Framework destination. The app buffers no
 *   whole file and writes no CSV itself.
 * - A response landing after a generation boundary is deleted and
 *   reported via [SessionEndedException] — User A's export never
 *   surfaces for User B.
 * - `save()` always deletes the temp artifact (success, failure or
 *   cancellation); `discardOrphans()` sweeps anything left behind.
 */
interface LedgerExportRepository {

    /** Download the export to a private temp artifact. */
    suspend fun download(accountId: String?): LedgerExportArtifact

    /** Copy the artifact to the user's chosen destination, then delete it. */
    suspend fun save(artifact: LedgerExportArtifact, destinationUri: String)

    /** Delete the temp artifact (picker cancel, error, session change). */
    suspend fun discard(artifact: LedgerExportArtifact)

    /** Remove any orphaned temp exports (e.g. after a session boundary). */
    suspend fun discardOrphans()
}

@Singleton
class RemoteLedgerExportRepository @Inject constructor(
    private val service: TransactionsService,
    private val sessionManager: SessionManager,
    private val files: ExportFileStore,
) : LedgerExportRepository {

    override suspend fun download(accountId: String?): LedgerExportArtifact {
        val generation = sessionManager.sessionGeneration
        val response = try {
            service.export(accountId)
        } catch (e: ApiException) {
            throw AppErrorException(e.toAppError(), e)
        } catch (e: IOException) {
            throw AppErrorException(AppError.NetworkUnavailable, e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AppErrorException(AppError.Unknown(e), e)
        }
        // A 2xx is only an export when it carries CSV — never save an
        // HTML/JSON body as a ledger file.
        val body = response.body()
            ?: throw AppErrorException(AppError.Unknown(null))
        val type = body.contentType()
        if (type == null || type.type != "text" || !type.subtype.equals("csv", ignoreCase = true)) {
            runCatching { body.close() }
            throw AppErrorException(AppError.Unknown(null))
        }
        val fileName = LedgerExportFileName.sanitize(response.headers()["Content-Disposition"])
        val artifact = try {
            files.writeTemp(fileName, body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AppErrorException(AppError.Unknown(e), e)
        }
        if (sessionManager.sessionGeneration != generation) {
            // Stale response after a session boundary — delete, never publish.
            files.discard(artifact)
            throw SessionEndedException()
        }
        artifact.boundGeneration = generation
        return artifact
    }

    override suspend fun save(artifact: LedgerExportArtifact, destinationUri: String) {
        // The artifact is bound to the session that produced it — a
        // boundary crossed since the download (expiry, sign-out, a
        // different identity) invalidates it even if the ViewModel
        // somehow survived.
        if (artifact.boundGeneration != sessionManager.sessionGeneration) {
            files.discard(artifact)
            throw SessionEndedException()
        }
        try {
            files.copyTo(artifact, destinationUri)
        } catch (e: Exception) {
            files.discard(artifact)
            if (e is CancellationException) throw e
            // Destination-write failure is a *save* failure — the UI must
            // not claim the export succeeded.
            throw ExportSaveException(e)
        }
        files.discard(artifact)
    }

    override suspend fun discard(artifact: LedgerExportArtifact) {
        files.discard(artifact)
    }

    override suspend fun discardOrphans() {
        files.discardOrphans()
    }
}

/** Streaming the destination copy failed — distinct from download errors. */
internal class ExportSaveException(cause: Throwable) : Exception(cause)

/**
 * Defensive filename for the SAF suggestion — the server header is
 * untrusted metadata: basename only, control characters stripped,
 * `.csv` enforced, bounded length, safe fallback.
 */
internal object LedgerExportFileName {

    const val FALLBACK = "spendwise-ledger.csv"
    private const val MAX_LENGTH = 100

    private val STAR = Regex("""filename\*=(?:UTF-8'')?([^;]+)""", RegexOption.IGNORE_CASE)
    private val QUOTED = Regex("""filename="([^"]*)"""", RegexOption.IGNORE_CASE)
    private val BARE = Regex("""filename=([^;\s]+)""", RegexOption.IGNORE_CASE)
    private val CONTROL = Regex("[\\p{Cntrl}]")

    fun sanitize(contentDisposition: String?): String {
        val header = contentDisposition ?: return FALLBACK
        val raw = STAR.find(header)?.groupValues?.get(1)
            ?: QUOTED.find(header)?.groupValues?.get(1)
            ?: BARE.find(header)?.groupValues?.get(1)
            ?: return FALLBACK
        val base = raw.trim().trim('"')
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(CONTROL, "")
            .trim()
        if (base.isEmpty() || base == "." || base == "..") return FALLBACK
        val withExt = if (base.endsWith(".csv", ignoreCase = true)) base else "$base.csv"
        return if (withExt.length <= MAX_LENGTH) withExt else withExt.takeLast(MAX_LENGTH)
    }
}

/**
 * `ContentResolver`-backed [ExportFileStore] — temp artifacts live in a
 * dedicated `cacheDir/ledger-export/` dir (app-private, excluded from
 * backup, never indexed as media). Every copy is a stream — the CSV
 * is never materialized in memory.
 */
@Singleton
class ContentResolverExportStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ExportFileStore {

    /**
     * Live artifacts owned by an in-flight flow — registered the moment
     * the temp write completes and unregistered only on [discard].
     * [discardOrphans] skips them, so a sweep on screen entry can never
     * delete a download still owned by another screen instance (e.g. a
     * double-pushed route) — only true orphans (process death) whose
     * owners are gone are swept.
     */
    private val liveArtifacts = java.util.concurrent.ConcurrentHashMap.newKeySet<File>()

    override suspend fun writeTemp(
        suggestedFileName: String,
        body: ResponseBody,
    ): LedgerExportArtifact = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, TEMP_DIR).apply { mkdirs() }
        // Register under the same lock the sweep holds — a file must be
        // protected the instant it exists, not only once streaming ends.
        val temp = synchronized(liveArtifacts) {
            File.createTempFile("ledger-", ".csv", dir).also { liveArtifacts.add(it) }
        }
        try {
            body.byteStream().use { input -> input.copyToFile(temp) }
            LedgerExportArtifact(suggestedFileName, temp.length(), temp)
        } catch (e: Exception) {
            // Cancel included — a partial download never survives.
            liveArtifacts.remove(temp)
            temp.delete()
            throw e
        }
    }

    override suspend fun copyTo(artifact: LedgerExportArtifact, destinationUri: String) =
        withContext(Dispatchers.IO) {
            val output = context.contentResolver
                .openOutputStream(destinationUri.toUri())
                ?: throw IOException("destination is not writable")
            output.use { dest -> artifact.copyToStream(dest) }
        }

    override suspend fun discard(artifact: LedgerExportArtifact) = withContext(Dispatchers.IO) {
        liveArtifacts.remove(artifact.file)
        artifact.file.delete()
        Unit
    }

    override suspend fun discardOrphans() = withContext(Dispatchers.IO) {
        synchronized(liveArtifacts) {
            File(context.cacheDir, TEMP_DIR).listFiles()
                ?.forEach { if (it !in liveArtifacts) it.delete() }
        }
        Unit
    }

    private companion object {
        const val TEMP_DIR = "ledger-export"
    }
}

/** Stream a network body into [file] — the single copy, never buffered. */
internal fun InputStream.copyToFile(file: File) {
    file.outputStream().use { out -> copyTo(out) }
}

/** Stream an artifact's bytes into a destination stream. */
internal fun LedgerExportArtifact.copyToStream(dest: java.io.OutputStream) {
    file.inputStream().use { it.copyTo(dest) }
}
