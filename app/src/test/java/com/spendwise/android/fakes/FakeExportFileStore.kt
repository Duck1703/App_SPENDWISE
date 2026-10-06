package com.spendwise.android.fakes

import com.spendwise.android.data.repository.ExportFileStore
import com.spendwise.android.data.repository.LedgerExportArtifact
import com.spendwise.android.data.repository.copyToFile
import com.spendwise.android.data.repository.copyToStream
import java.io.File
import java.nio.file.Files
import okhttp3.ResponseBody

/**
 * [ExportFileStore] over a real JVM temp dir — exercises the same
 * streaming helpers (`copyToFile` / `copyToStream`) the Android
 * `ContentResolverExportStore` uses, so byte-identity is proven
 * through the production code path.
 */
class FakeExportFileStore : ExportFileStore {

    private val dir: File = Files.createTempDirectory("export-test").toFile()

    /** Mirrors the production live-artifact registry (sweep skips live files). */
    private val live = java.util.concurrent.ConcurrentHashMap.newKeySet<File>()

    /** Scripted failure — destination write blows up. */
    var copyError: Throwable? = null

    /** Destination URI → bytes written (proves what the user received). */
    val saved = mutableMapOf<String, ByteArray>()
    val discarded = mutableListOf<File>()
    var discardOrphansCalls = 0

    override suspend fun writeTemp(
        suggestedFileName: String,
        body: ResponseBody,
    ): LedgerExportArtifact {
        val temp = File.createTempFile("ledger-", ".csv", dir).also { live.add(it) }
        try {
            body.byteStream().use { it.copyToFile(temp) }
            return LedgerExportArtifact(suggestedFileName, temp.length(), temp)
        } catch (e: Exception) {
            live.remove(temp)
            temp.delete()
            throw e
        }
    }

    override suspend fun copyTo(artifact: LedgerExportArtifact, destinationUri: String) {
        copyError?.let { throw it }
        // The destination is a real stream — uri string is just the key.
        val sink = java.io.ByteArrayOutputStream()
        artifact.copyToStream(sink)
        saved[destinationUri] = sink.toByteArray()
    }

    override suspend fun discard(artifact: LedgerExportArtifact) {
        live.remove(artifact.file)
        discarded += artifact.file
        artifact.file.delete()
    }

    override suspend fun discardOrphans() {
        discardOrphansCalls++
        dir.listFiles()?.forEach { if (it !in live) it.delete() }
    }

    fun filesLeft(): List<File> = dir.listFiles()?.toList() ?: emptyList()
}
