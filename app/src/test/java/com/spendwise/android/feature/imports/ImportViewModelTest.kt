package com.spendwise.android.feature.imports

import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.SelectedStatement
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.data.repository.StatementUnreadableException
import com.spendwise.android.fakes.FakeAccountRepository
import com.spendwise.android.fakes.FakeImportRepository
import com.spendwise.android.fakes.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Import (S12–S14) ViewModel — account load states, file gates,
 * the synchronous modal upload, every error mapping, the
 * ambiguous-outcome honesty contract (PD-012), the double-submit
 * guard, and the EXT-MAJOR-1 resume path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(
        accounts: FakeAccountRepository = FakeAccountRepository(),
        imports: FakeImportRepository = FakeImportRepository(),
    ) = ImportViewModel(accounts, imports)

    private fun twoAccounts() = listOf(
        FakeAccountRepository.account(id = "acc-1", name = "Wallet", currency = "VND"),
        FakeAccountRepository.account(id = "acc-2", name = "Bank", currency = "USD"),
    )

    private fun form(vm: ImportViewModel) = vm.state.value as ImportUiState.Form

    private fun uri() = "content://docs/42"

    private fun describe(name: String = "statement.csv", size: Long = 4): (suspend (String) -> SelectedStatement) =
        { u -> SelectedStatement(uri = u, displayName = name, sizeBytes = size) }

    // ── Account load ────────────────────────────────────────────────

    @Test
    fun `loads the form with the first account preselected`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> twoAccounts() }

        val viewModel = vm(accounts)

        val form = form(viewModel)
        assertEquals(2, form.accounts.size)
        assertEquals("acc-1", form.selectedAccountId)
        assertNull(form.file)
        assertFalse(form.isUploading)
    }

    @Test
    fun `empty accounts produce the create-first state`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> emptyList() }

        val viewModel = vm(accounts)

        assertTrue(viewModel.state.value is ImportUiState.Empty)
    }

    @Test
    fun `account load failure maps to the error state and retry recovers`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> throw AppErrorException(AppError.NetworkUnavailable) }

        val viewModel = vm(accounts)
        assertEquals(
            ImportError.Network,
            (viewModel.state.value as ImportUiState.Error).kind,
        )

        accounts.listResult = { _ -> twoAccounts() }
        viewModel.retry()
        assertEquals("acc-1", form(viewModel).selectedAccountId)
    }

    // ── File gates ──────────────────────────────────────────────────

    @Test
    fun `picking a csv or pdf stores the file and other extensions are rejected`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> twoAccounts() }
        val imports = FakeImportRepository()
        val viewModel = vm(accounts, imports)

        imports.describeResult = describe("statement.exe")
        viewModel.onFilePicked(uri())
        assertNull(form(viewModel).file)
        assertEquals(ImportError.UnsupportedFile, form(viewModel).error)

        imports.describeResult = describe("Statement.CSV")
        viewModel.onFilePicked(uri())
        assertEquals("Statement.CSV", form(viewModel).file?.displayName)
        assertNull(form(viewModel).error)
    }

    @Test
    fun `an oversized file is rejected client-side before upload`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> twoAccounts() }
        val imports = FakeImportRepository()
        val viewModel = vm(accounts, imports)

        imports.describeResult = describe(size = 1_048_577L)
        viewModel.onFilePicked(uri())

        assertNull(form(viewModel).file)
        assertEquals(ImportError.FileTooLarge, form(viewModel).error)
        assertTrue(imports.uploadCalls.isEmpty())
    }

    @Test
    fun `a file exactly at the cap is accepted`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> twoAccounts() }
        val imports = FakeImportRepository()
        val viewModel = vm(accounts, imports)

        imports.describeResult = describe(size = 1_048_576L)
        viewModel.onFilePicked(uri())

        assertEquals("statement.csv", form(viewModel).file?.displayName)
    }

    @Test
    fun `clearing the file resets the pick without touching the account`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> twoAccounts() }
        val viewModel = vm(accounts, FakeImportRepository())

        viewModel.onAccountSelected("acc-2")
        viewModel.onFilePicked(uri())
        viewModel.onFileCleared()

        assertNull(form(viewModel).file)
        assertEquals("acc-2", form(viewModel).selectedAccountId)
    }

    @Test
    fun `out-of-order pick completions keep the latest pick`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> twoAccounts() }
        val imports = FakeImportRepository()
        val viewModel = vm(accounts, imports)

        val slow = CompletableDeferred<SelectedStatement>()
        imports.describeResult = { u ->
            if (u == "uri:a") slow.await() else SelectedStatement(u, "b.csv", 10L)
        }
        viewModel.onFilePicked("uri:a")
        runCurrent()
        viewModel.onFilePicked("uri:b")
        runCurrent()
        assertEquals("b.csv", form(viewModel).file?.displayName)

        // The stale pick resolving last must not clobber the newer pick.
        slow.complete(SelectedStatement("uri:a", "a.csv", 10L))
        runCurrent()
        assertEquals("b.csv", form(viewModel).file?.displayName)
        assertNull(form(viewModel).error)
    }

    // ── Submit ──────────────────────────────────────────────────────

    @Test
    fun `submit lands on the result and posts once`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> twoAccounts() }
        val imports = FakeImportRepository()
        val viewModel = vm(accounts, imports)

        viewModel.onFilePicked(uri())
        viewModel.submit()

        val result = viewModel.state.value as ImportUiState.Result
        assertEquals("batch-1", result.batch.batchId)
        assertEquals(1, imports.uploadCalls.size)
        assertEquals("acc-1", imports.uploadCalls.single().accountId)
    }

    @Test
    fun `double-submit while uploading can never issue a second POST`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> twoAccounts() }
        val imports = FakeImportRepository()
        val gate = CompletableDeferred<com.spendwise.android.core.model.ImportBatch>()
        imports.uploadResult = { _, _ -> gate.await() }
        val viewModel = vm(accounts, imports)

        viewModel.onFilePicked(uri())
        viewModel.submit()
        runCurrent()
        assertTrue(form(viewModel).isUploading)

        viewModel.submit()
        viewModel.submit()
        runCurrent()
        assertEquals(1, imports.uploadCalls.size)

        gate.complete(FakeImportRepository.batch())
        runCurrent()
        assertTrue(viewModel.state.value is ImportUiState.Result)
    }

    @Test
    fun `submit without a file is a no-op`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> twoAccounts() }
        val imports = FakeImportRepository()
        val viewModel = vm(accounts, imports)

        viewModel.submit()

        assertTrue(viewModel.state.value is ImportUiState.Form)
        assertTrue(imports.uploadCalls.isEmpty())
    }

    // ── Error mapping ───────────────────────────────────────────────

    @Test
    fun `400 surfaces as unsupported-file — the honest format state`() = runTest {
        val viewModel = submitReturning(
            AppErrorException(AppError.BadRequest),
        )
        assertEquals(ImportError.UnsupportedFile, form(viewModel).error)
        assertFalse(form(viewModel).ambiguousNotice)
        assertFalse(form(viewModel).isUploading)
    }

    @Test
    fun `404 surfaces as account-unavailable`() = runTest {
        val viewModel = submitReturning(
            AppErrorException(AppError.NotFound),
        )
        assertEquals(ImportError.AccountUnavailable, form(viewModel).error)
        assertFalse(form(viewModel).ambiguousNotice)
    }

    @Test
    fun `500 restores the form with the outcome-ambiguous banner`() = runTest {
        val viewModel = submitReturning(
            AppErrorException(AppError.Server(500)),
        )
        assertEquals(ImportError.Server, form(viewModel).error)
        assertTrue(form(viewModel).ambiguousNotice)
        assertFalse(form(viewModel).isUploading)
    }

    @Test
    fun `network drop restores the form — outcome is ambiguous, never claimed failed`() = runTest {
        val viewModel = submitReturning(
            AppErrorException(AppError.NetworkUnavailable),
        )
        assertEquals(ImportError.Network, form(viewModel).error)
        assertTrue(form(viewModel).ambiguousNotice)
    }

    @Test
    fun `an unreadable file surfaces generic — not ambiguous, nothing was sent`() = runTest {
        val viewModel = submitReturning(StatementUnreadableException(RuntimeException("gone")))
        assertEquals(ImportError.Generic, form(viewModel).error)
        assertFalse(form(viewModel).ambiguousNotice)
    }

    @Test
    fun `the failed pick keeps the form usable`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> twoAccounts() }
        val imports = FakeImportRepository()
        imports.describeResult = { throw AppErrorException(AppError.NetworkUnavailable) }
        val viewModel = vm(accounts, imports)

        viewModel.onFilePicked(uri())

        assertEquals(ImportError.Network, form(viewModel).error)
        assertNull(form(viewModel).file)
    }

    // ── Session boundary (EXT-MAJOR-1) ─────────────────────────────

    @Test
    fun `session-expired mid-upload restores the form and resume reloads`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> twoAccounts() }
        val imports = FakeImportRepository()
        var calls = 0
        imports.uploadResult = { _, _ ->
            calls++
            throw AppErrorException(AppError.SessionExpired)
        }
        val viewModel = vm(accounts, imports)

        viewModel.onFilePicked(uri())
        viewModel.submit()

        val form = form(viewModel)
        assertFalse(form.isUploading)
        assertTrue(form.ambiguousNotice)
        assertEquals(1, calls)

        // Same-user resume — the form reloads accounts; no auto-replay.
        viewModel.onVisible()
        assertEquals(1, calls)
        assertFalse((viewModel.state.value as ImportUiState.Form).ambiguousNotice)
    }

    @Test
    fun `a dead-generation upload restores the form and arms resume`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> twoAccounts() }
        val imports = FakeImportRepository()
        imports.uploadResult = { _, _ -> throw SessionEndedException() }
        val viewModel = vm(accounts, imports)

        viewModel.onFilePicked(uri())
        viewModel.submit()

        assertTrue(form(viewModel).ambiguousNotice)
        viewModel.onVisible()
        assertEquals("acc-1", form(viewModel).selectedAccountId)
    }

    @Test
    fun `session-expired account load arms the resume retry`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> throw AppErrorException(AppError.SessionExpired) }
        val viewModel = vm(accounts)
        // Still Loading — the expired overlay owns the surface.
        assertTrue(viewModel.state.value is ImportUiState.Loading)

        accounts.listResult = { _ -> twoAccounts() }
        viewModel.onVisible()
        assertEquals("acc-1", form(viewModel).selectedAccountId)
    }

    // ── helpers ─────────────────────────────────────────────────────

    private fun submitReturning(error: Throwable): ImportViewModel {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> twoAccounts() }
        val imports = FakeImportRepository()
        imports.uploadResult = { _, _ -> throw error }
        val viewModel = vm(accounts, imports)
        viewModel.onFilePicked(uri())
        viewModel.submit()
        return viewModel
    }
}
