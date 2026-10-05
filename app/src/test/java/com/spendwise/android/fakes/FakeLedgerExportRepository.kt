package com.spendwise.android.fakes

import com.spendwise.android.data.repository.LedgerExportArtifact
import com.spendwise.android.data.repository.LedgerExportRepository
import java.io.File
import java.nio.file.Files

/** Scripted [LedgerExportRepository] for ViewModel tests. */
class FakeLedgerExportRepository : LedgerExportRepository {

    var downloadResult: suspend (String?) -> LedgerExportArtifact = { artifact() }
    var saveResult: suspend (LedgerExportArtifact, String) -> Unit = { _, _ -> }

    val downloadCalls = mutableListOf<String?>()
    val saveCalls = mutableListOf<Pair<LedgerExportArtifact, String>>()
    val discarded = mutableListOf<LedgerExportArtifact>()
    var discardOrphansCalls = 0

    override suspend fun download(accountId: String?): LedgerExportArtifact {
        downloadCalls += accountId
        return downloadResult(accountId)
    }

    override suspend fun save(artifact: LedgerExportArtifact, destinationUri: String) {
        saveCalls += artifact to destinationUri
        saveResult(artifact, destinationUri)
    }

    override suspend fun discard(artifact: LedgerExportArtifact) {
        discarded += artifact
    }

    override suspend fun discardOrphans() {
        discardOrphansCalls++
    }

    companion object {
        fun artifact(
            fileName: String = "transactions.csv",
            sizeBytes: Long = 42,
        ): LedgerExportArtifact = LedgerExportArtifact(
            suggestedFileName = fileName,
            sizeBytes = sizeBytes,
            file = File(Files.createTempDirectory("export-vm").toFile(), fileName),
        )
    }
}
