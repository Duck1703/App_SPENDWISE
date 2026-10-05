package com.spendwise.android.data

import com.spendwise.android.core.model.ImportRowStatus
import com.spendwise.android.core.network.ApiException
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.repository.RemoteImportRepository
import com.spendwise.android.data.repository.SelectedStatement
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.data.repository.StatementStore
import com.spendwise.android.data.repository.StatementUnreadableException
import com.spendwise.android.fakes.FakeAccountRepository
import com.spendwise.android.fakes.FakeImportsService
import com.spendwise.android.fakes.FakeStatementStore
import com.spendwise.android.fakes.FakeTokenStore
import com.spendwise.android.fakes.FakeTransactionRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * `RemoteImportRepository` — the contract-level logic: domain mapping,
 * error translation, account-cache invalidation + ledger signal on
 * success, and the generation guard that keeps a stale user's import
 * result from surfacing (INV-015/PD-012).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportRepositoryTest {

    private val now: Instant = Instant.parse("2026-10-04T12:00:00Z")

    private data class Harness(
        val service: FakeImportsService,
        val accounts: FakeAccountRepository,
        val transactions: FakeTransactionRepository,
        val statements: FakeStatementStore,
        val sessionManager: SessionManager,
        val repository: RemoteImportRepository,
    )

    private fun harness(): Harness {
        val service = FakeImportsService()
        val accounts = FakeAccountRepository()
        val transactions = FakeTransactionRepository()
        val statements = FakeStatementStore()
        val sessionManager = SessionManager(
            tokenStore = FakeTokenStore(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        return Harness(
            service,
            accounts,
            transactions,
            statements,
            sessionManager,
            RemoteImportRepository(service, sessionManager, accounts, transactions, statements),
        )
    }

    private suspend fun signIn(h: Harness) =
        h.sessionManager.onSignedIn("token", now.plusSeconds(900), "ana", "u1")

    private fun file(name: String = "statement.csv") = SelectedStatement(
        uri = "content://docs/1",
        displayName = name,
        sizeBytes = 12,
    )

    @Test
    fun `upload maps the report — rows, statuses, unknown status fallback`() = runTest {
        val h = harness()
        signIn(h)
        h.service.uploadResponse = FakeImportsService.summaryDto()

        val batch = h.repository.upload("acc-1", file())

        assertEquals("batch-1", batch.batchId)
        assertEquals("acc-1", batch.accountId)
        assertEquals("statement.csv", batch.fileName)
        assertEquals(Instant.parse("2026-10-04T12:00:00Z"), batch.importedAt)
        assertEquals(2, batch.successCount)
        assertEquals(1, batch.duplicateCount)
        assertEquals(1, batch.failedCount)
        assertEquals(4, batch.totalRows)
        assertEquals(ImportRowStatus.OK, batch.rows[0].status)
        assertEquals(ImportRowStatus.DUPLICATE, batch.rows[1].status)
        assertEquals(ImportRowStatus.FAILED, batch.rows[2].status)
        assertEquals("currency mismatch", batch.rows[2].reason)
        assertNull(batch.rows[2].transactionId)
        // The file went through the multipart seam with its name intact.
        assertEquals("acc-1", h.service.uploadCalls.single().accountId)
        assertEquals("statement.csv", h.service.uploadCalls.single().fileName)
        assertEquals(file(), h.statements.toPartCalls.single())
    }

    @Test
    fun `an unknown row status decodes to UNKNOWN — never a crash`() = runTest {
        val h = harness()
        signIn(h)
        h.service.uploadResponse = FakeImportsService.summaryDto().copy(
            rows = listOf(
                com.spendwise.android.data.remote.ImportRowDto(
                    rowNumber = 2,
                    status = "QUARANTINED",
                    reason = null,
                    transactionId = null,
                ),
            ),
        )

        val batch = h.repository.upload("acc-1", file())

        assertEquals(ImportRowStatus.UNKNOWN, batch.rows.single().status)
    }

    @Test
    fun `success invalidates the account cache and signals the ledger`() = runTest {
        val h = harness()
        signIn(h)

        h.repository.upload("acc-1", file())

        assertEquals(1, h.accounts.invalidateCalls)
        assertEquals(1, h.transactions.notifyLedgerChangedCalls)
    }

    @Test
    fun `400 maps to BadRequest — unsupported file`() = runTest {
        val h = harness()
        signIn(h)
        h.service.uploadError = ApiException(400, "unsupported")

        try {
            h.repository.upload("acc-1", file())
            fail("expected AppErrorException")
        } catch (e: AppErrorException) {
            assertEquals(AppError.BadRequest, e.error)
        }
        assertEquals(0, h.transactions.notifyLedgerChangedCalls)
    }

    @Test
    fun `404 maps to NotFound — foreign or deleted account`() = runTest {
        val h = harness()
        signIn(h)
        h.service.uploadError = ApiException(404, "not found")

        try {
            h.repository.upload("acc-1", file())
            fail("expected AppErrorException")
        } catch (e: AppErrorException) {
            assertEquals(AppError.NotFound, e.error)
        }
    }

    @Test
    fun `500 maps to Server — structural failures drift to 500 honestly`() = runTest {
        val h = harness()
        signIn(h)
        h.service.uploadError = ApiException(500, "boom")

        try {
            h.repository.upload("acc-1", file())
            fail("expected AppErrorException")
        } catch (e: AppErrorException) {
            assertTrue(e.error is AppError.Server)
        }
    }

    @Test
    fun `a dead generation throws SessionEndedException and never signals the ledger`() = runTest {
        val h = harness()
        signIn(h)
        // Gate the "network" so the session boundary crosses mid-flight.
        val gate = CompletableDeferred<com.spendwise.android.data.remote.ImportBatchSummaryDto>()
        h.service.uploadResponder = { _, _ -> gate.await() }

        val deferred = backgroundScope.async {
            h.repository.upload("acc-1", file())
        }
        runCurrent() // the call captured its generation before suspending

        // Sign-out ends generation 1 while the upload is in flight.
        h.sessionManager.signOut()
        gate.complete(FakeImportsService.summaryDto())

        try {
            deferred.await()
            fail("expected SessionEndedException")
        } catch (e: SessionEndedException) {
            // expected — a dead-session result never surfaces
        }
        assertEquals(0, h.transactions.notifyLedgerChangedCalls)
    }

    @Test
    fun `describe delegates to the platform seam`() = runTest {
        val h = harness()
        val uri = "content://docs/9"
        h.statements.describeResult = {
            SelectedStatement(uri = it, displayName = "oct.csv", sizeBytes = 88)
        }

        val described = h.repository.describe(uri)

        assertEquals("oct.csv", described.displayName)
        assertEquals(88, described.sizeBytes)
        assertEquals(uri, h.statements.describeCalls.single())
    }

    @Test
    fun `an unreadable picked file throws StatementUnreadableException — never reaches the wire`() = runTest {
        val h = harness()
        signIn(h)
        val store = object : StatementStore {
            override suspend fun describe(uri: String) = file()
            override fun toPart(file: SelectedStatement): okhttp3.MultipartBody.Part =
                throw java.io.IOException("gone")
        }
        val repository = RemoteImportRepository(
            h.service,
            h.sessionManager,
            h.accounts,
            h.transactions,
            store,
        )

        try {
            repository.upload("acc-1", file())
            fail("expected StatementUnreadableException")
        } catch (e: StatementUnreadableException) {
            // expected
        }
        assertTrue(h.service.uploadCalls.isEmpty())
    }

    @Test
    fun `a malformed importedAt surfaces as Unknown — never a raw defect`() = runTest {
        val h = harness()
        signIn(h)
        h.service.uploadResponse = FakeImportsService.summaryDto().copy(
            importedAt = "not-a-timestamp",
        )

        try {
            h.repository.upload("acc-1", file())
            fail("expected AppErrorException")
        } catch (e: AppErrorException) {
            assertTrue(e.error is AppError.Unknown)
            assertNotNull((e.error as AppError.Unknown).cause)
        }
    }
}
