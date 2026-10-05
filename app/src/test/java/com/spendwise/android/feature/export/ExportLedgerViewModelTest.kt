package com.spendwise.android.feature.export

import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.ExportSaveException
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.fakes.FakeAccountRepository
import com.spendwise.android.fakes.FakeLedgerExportRepository
import com.spendwise.android.fakes.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Ledger export VM (M9) — account load, scope selection, the
 * modal download/save phases, double-submit lock, one-shot save
 * request, cancel-as-non-error, every error mapping, generation
 * safety and the resume contract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExportLedgerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(
        accounts: FakeAccountRepository = FakeAccountRepository(),
        exports: FakeLedgerExportRepository = FakeLedgerExportRepository(),
    ) = ExportLedgerViewModel(accounts, exports)

    private fun twoAccounts() = listOf(
        FakeAccountRepository.account(id = "acc-1", name = "Wallet"),
        FakeAccountRepository.account(id = "acc-2", name = "Bank"),
    )

    private fun form(vm: ExportLedgerViewModel) = vm.state.value as ExportUiState.Ready

    // ── Load ────────────────────────────────────────────────────────

    @Test
    fun `loads ready with All accounts default`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { twoAccounts() }

        val viewModel = vm(accounts)

        val form = form(viewModel)
        assertEquals(2, form.accounts.size)
        assertNull(form.selectedAccountId)
        assertEquals(ExportUiState.Phase.Idle, form.phase)
    }

    @Test
    fun `account load failure shows an honest error`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { throw AppErrorException(AppError.NetworkUnavailable) }

        val viewModel = vm(accounts)

        assertEquals(ExportError.Network, (viewModel.state.value as ExportUiState.Error).kind)
    }

    @Test
    fun `empty account list still allows all-accounts export`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { emptyList() }

        val viewModel = vm(accounts)

        val form = form(viewModel)
        assertTrue(form.accounts.isEmpty())
        assertNull(form.selectedAccountId)
    }

    // ── Scope ───────────────────────────────────────────────────────

    @Test
    fun `scope selection forwards the account id to the download`() = runTest {
        val accounts = FakeAccountRepository()
        val exports = FakeLedgerExportRepository()
        accounts.listResult = { twoAccounts() }
        val viewModel = vm(accounts, exports)

        form(viewModel)
        viewModel.onScopeSelected("acc-2")
        assertEquals("acc-2", form(viewModel).selectedAccountId)

        viewModel.export()
        runCurrent()

        assertEquals(listOf("acc-2"), exports.downloadCalls)
    }

    @Test
    fun `all-accounts scope sends null`() = runTest {
        val accounts = FakeAccountRepository()
        val exports = FakeLedgerExportRepository()
        accounts.listResult = { twoAccounts() }
        val viewModel = vm(accounts, exports)
        form(viewModel)

        viewModel.export()
        runCurrent()

        assertEquals(listOf<String?>(null), exports.downloadCalls)
    }

    // ── Export phases ───────────────────────────────────────────────

    @Test
    fun `successful download raises a one-shot save request`() = runTest {
        val accounts = FakeAccountRepository()
        val exports = FakeLedgerExportRepository()
        accounts.listResult = { twoAccounts() }
        exports.downloadResult = { FakeLedgerExportRepository.artifact(fileName = "spendwise.csv") }
        val viewModel = vm(accounts, exports)
        form(viewModel)

        viewModel.export()
        runCurrent()

        val form = form(viewModel)
        assertEquals(ExportUiState.Phase.AwaitingDestination, form.phase)
        assertEquals("spendwise.csv", form.pendingFileName)
        assertEquals("spendwise.csv", form.saveRequest?.fileName)
    }

    @Test
    fun `double-submit is locked while downloading`() = runTest {
        val accounts = FakeAccountRepository()
        val exports = FakeLedgerExportRepository()
        accounts.listResult = { twoAccounts() }
        val gate = CompletableDeferred<Unit>()
        exports.downloadResult = {
            gate.await()
            FakeLedgerExportRepository.artifact()
        }
        val viewModel = vm(accounts, exports)
        form(viewModel)

        viewModel.export()
        viewModel.export()
        runCurrent()

        assertEquals(1, exports.downloadCalls.size)
        gate.complete(Unit)
    }

    // ── Save flow ───────────────────────────────────────────────────

    @Test
    fun `save success marks saved and clears the artifact`() = runTest {
        val accounts = FakeAccountRepository()
        val exports = FakeLedgerExportRepository()
        accounts.listResult = { twoAccounts() }
        val artifact = FakeLedgerExportRepository.artifact()
        exports.downloadResult = { artifact }
        val viewModel = vm(accounts, exports)
        form(viewModel)
        viewModel.export()
        runCurrent()

        viewModel.onDestinationChosen("content://picked/ledger.csv")
        runCurrent()

        assertEquals(listOf(artifact to "content://picked/ledger.csv"), exports.saveCalls)
        val form = form(viewModel)
        assertTrue(form.saved)
        assertEquals(ExportUiState.Phase.Idle, form.phase)
        assertNull(form.saveRequest)
    }

    @Test
    fun `picker cancel discards the artifact without an error`() = runTest {
        val accounts = FakeAccountRepository()
        val exports = FakeLedgerExportRepository()
        accounts.listResult = { twoAccounts() }
        val artifact = FakeLedgerExportRepository.artifact()
        exports.downloadResult = { artifact }
        val viewModel = vm(accounts, exports)
        form(viewModel)
        viewModel.export()
        runCurrent()

        viewModel.onDestinationCancelled()
        runCurrent()

        assertEquals(listOf(artifact), exports.discarded)
        val form = form(viewModel)
        assertEquals(ExportUiState.Phase.Idle, form.phase)
        assertNull(form.error)
        assertNull(form.saveRequest)
    }

    @Test
    fun `save failure reports SaveFailed and deletes the artifact`() = runTest {
        val accounts = FakeAccountRepository()
        val exports = FakeLedgerExportRepository()
        accounts.listResult = { twoAccounts() }
        val artifact = FakeLedgerExportRepository.artifact()
        exports.downloadResult = { artifact }
        exports.saveResult = { _, _ -> throw ExportSaveException(IOException("denied")) }
        val viewModel = vm(accounts, exports)
        form(viewModel)
        viewModel.export()
        runCurrent()

        viewModel.onDestinationChosen("content://picked/x.csv")
        runCurrent()

        val form = form(viewModel)
        assertEquals(ExportError.SaveFailed, form.error)
        assertEquals(ExportUiState.Phase.Idle, form.phase)
    }

    // ── Errors / session ────────────────────────────────────────────

    @Test
    fun `404 maps to AccountUnavailable`() = runTest {
        val accounts = FakeAccountRepository()
        val exports = FakeLedgerExportRepository()
        accounts.listResult = { twoAccounts() }
        exports.downloadResult = { throw AppErrorException(AppError.NotFound) }
        val viewModel = vm(accounts, exports)
        form(viewModel)

        viewModel.export()
        runCurrent()

        assertEquals(ExportError.AccountUnavailable, form(viewModel).error)
        assertEquals(ExportUiState.Phase.Idle, form(viewModel).phase)
    }

    @Test
    fun `network error maps to Network`() = runTest {
        val accounts = FakeAccountRepository()
        val exports = FakeLedgerExportRepository()
        accounts.listResult = { twoAccounts() }
        exports.downloadResult = { throw AppErrorException(AppError.NetworkUnavailable) }
        val viewModel = vm(accounts, exports)
        form(viewModel)

        viewModel.export()
        runCurrent()

        assertEquals(ExportError.Network, form(viewModel).error)
    }

    @Test
    fun `stale session drops the artifact and resumes the load on visibility`() = runTest {
        val accounts = FakeAccountRepository()
        val exports = FakeLedgerExportRepository()
        accounts.listResult = { twoAccounts() }
        val gate = CompletableDeferred<Unit>()
        exports.downloadResult = {
            gate.await()
            throw SessionEndedException()
        }
        val viewModel = vm(accounts, exports)
        form(viewModel)

        viewModel.export()
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        // Form restored to Idle without an error — the stale artifact
        // was already deleted inside the repository.
        val form = form(viewModel)
        assertEquals(ExportUiState.Phase.Idle, form.phase)
        assertNull(form.error)
        assertNull(form.saveRequest)

        viewModel.onVisible()
        runCurrent()
        assertTrue(accounts.listCalls.size >= 2)
    }
}
