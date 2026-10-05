package com.spendwise.android.data

import com.spendwise.android.core.network.ApiException
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.repository.ExportSaveException
import com.spendwise.android.data.repository.RemoteLedgerExportRepository
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.fakes.FakeExportFileStore
import com.spendwise.android.fakes.FakeTokenStore
import com.spendwise.android.fakes.FakeTransactionsService
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * `RemoteLedgerExportRepository` (M9) — byte-identical streaming,
 * filename propagation, media-type honesty, generation guard (a
 * stale-user artifact is deleted, never surfaced), save/discard
 * lifecycle.
 */
class LedgerExportRepositoryTest {

    private val now: Instant = Instant.parse("2026-10-04T12:00:00Z")

    private data class Harness(
        val service: FakeTransactionsService,
        val files: FakeExportFileStore,
        val sessionManager: SessionManager,
        val repository: RemoteLedgerExportRepository,
    )

    private fun harness(): Harness {
        val service = FakeTransactionsService()
        val files = FakeExportFileStore()
        val sessionManager = SessionManager(
            tokenStore = FakeTokenStore(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        return Harness(service, files, sessionManager, RemoteLedgerExportRepository(service, sessionManager, files))
    }

    private suspend fun signIn(h: Harness) =
        h.sessionManager.onSignedIn("token", now.plusSeconds(900), "ana", "u1")

    private fun csvResponse(
        bytes: ByteArray,
        disposition: String? = "attachment; filename=\"transactions.csv\"",
        mediaType: String = "text/csv",
    ): Response<okhttp3.ResponseBody> {
        val headers = disposition?.let {
            okhttp3.Headers.headersOf("Content-Disposition", it)
        } ?: okhttp3.Headers.headersOf()
        return Response.success(bytes.toResponseBody(mediaType.toMediaType()), headers)
    }

    // ── Download → artifact ─────────────────────────────────────────

    @Test
    fun `download all-accounts omits accountId and yields sanitized server filename`() = runTest {
        val h = harness()
        signIn(h)

        val artifact = h.repository.download(null)

        assertEquals(listOf<String?>(null), h.service.exportCalls)
        assertEquals("transactions.csv", artifact.suggestedFileName)
        assertTrue(artifact.sizeBytes > 0)
    }

    @Test
    fun `download selected account forwards its id`() = runTest {
        val h = harness()
        signIn(h)

        h.repository.download("acc-9")

        assertEquals(listOf("acc-9"), h.service.exportCalls)
    }

    @Test
    fun `downloaded bytes are byte-identical to the server body`() = runTest {
        val h = harness()
        signIn(h)
        // Nasty content: UTF-8 Vietnamese, commas, quotes, CRLF, decimals.
        val raw = ("occurredOn,type,amount,currency,category,sourceRef\r\n" +
            "2026-01-05,EXPENSE,\"1,250.50\",USD,FOOD,tx-1\r\n" +
            "2026-01-06,EXPENSE,\"Tiền cà phê, sáng\",VND,FOOD,tx-2\r\n" +
            "2026-01-07,INCOME,99999999999999999.99,VND,,tx-3\r\n")
            .toByteArray(StandardCharsets.UTF_8)
        h.service.exportResponder = { csvResponse(raw) }

        val artifact = h.repository.download(null)

        assertArrayEquals(raw, artifact.file.readBytes())
    }

    @Test
    fun `non-csv media type is an honest failure — body is not saved`() = runTest {
        val h = harness()
        signIn(h)
        h.service.exportResponder = {
            csvResponse("<html>err</html>".toByteArray(), mediaType = "text/html")
        }

        val e = assertThrows(AppErrorException::class.java) {
            kotlinx.coroutines.runBlocking { h.repository.download(null) }
        }
        assertTrue(e.error is AppError.Unknown)
        assertTrue(h.files.filesLeft().isEmpty())
    }

    @Test
    fun `response without body is an honest failure`() = runTest {
        val h = harness()
        signIn(h)
        h.service.exportResponder = { Response.success(null) }

        val e = assertThrows(AppErrorException::class.java) {
            kotlinx.coroutines.runBlocking { h.repository.download(null) }
        }
        assertTrue(e.error is AppError.Unknown)
    }

    @Test
    fun `404 maps to NotFound`() = runTest {
        val h = harness()
        signIn(h)
        h.service.exportResponder = { throw ApiException(status = 404) }

        val e = assertThrows(AppErrorException::class.java) {
            kotlinx.coroutines.runBlocking { h.repository.download("acc-gone") }
        }
        assertEquals(AppError.NotFound, e.error)
    }

    @Test
    fun `network failure maps to NetworkUnavailable`() = runTest {
        val h = harness()
        signIn(h)
        h.service.exportResponder = { throw IOException("gone") }

        val e = assertThrows(AppErrorException::class.java) {
            kotlinx.coroutines.runBlocking { h.repository.download(null) }
        }
        assertEquals(AppError.NetworkUnavailable, e.error)
    }

    // ── Generation / cross-user safety ──────────────────────────────

    @Test
    fun `stale-generation artifact is deleted and never published`() = runTest {
        val h = harness()
        signIn(h)
        val raw = "occurredOn,type\n2026-01-05,EXPENSE\n".toByteArray()
        h.service.exportResponder = {
            // Session ends mid-flight: a different user signs in.
            h.sessionManager.onSignedIn("token-b", now.plusSeconds(900), "bob", "u2")
            csvResponse(raw)
        }

        assertThrows(SessionEndedException::class.java) {
            kotlinx.coroutines.runBlocking { h.repository.download(null) }
        }
        assertTrue(h.files.filesLeft().isEmpty())
        assertEquals(1, h.files.discarded.size)
    }

    // ── Save → destination ──────────────────────────────────────────

    @Test
    fun `save writes byte-identical content and deletes the temp`() = runTest {
        val h = harness()
        signIn(h)
        val raw = "a,b\n\"x,y\",\"z\"\"\n".toByteArray(StandardCharsets.UTF_8)
        h.service.exportResponder = { csvResponse(raw) }
        val artifact = h.repository.download(null)
        val tempFile = artifact.file

        h.repository.save(artifact, "content://picked/ledger.csv")

        assertArrayEquals(raw, h.files.saved.getValue("content://picked/ledger.csv"))
        assertTrue(!tempFile.exists())
    }

    @Test
    fun `save failure deletes the temp and throws ExportSaveException`() = runTest {
        val h = harness()
        signIn(h)
        h.service.exportResponder = { csvResponse("x\n".toByteArray()) }
        val artifact = h.repository.download(null)
        val tempFile = artifact.file
        h.files.copyError = IOException("provider dead")

        assertThrows(ExportSaveException::class.java) {
            kotlinx.coroutines.runBlocking { h.repository.save(artifact, "content://dead/1") }
        }
        assertTrue(!tempFile.exists())
        assertTrue(h.files.saved.isEmpty())
    }

    @Test
    fun `discard removes the artifact`() = runTest {
        val h = harness()
        signIn(h)
        h.service.exportResponder = { csvResponse("x\n".toByteArray()) }
        val artifact = h.repository.download(null)
        val tempFile = artifact.file

        h.repository.discard(artifact)

        assertTrue(!tempFile.exists())
    }
}
