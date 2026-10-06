package com.spendwise.android.feature.imports

import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.fakes.FakeAccountRepository
import com.spendwise.android.fakes.FakeImportRepository
import com.spendwise.android.fakes.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Import history list (M10) — loading/empty/content/error states,
 * refresh + refresh-error honesty, account-name context, the
 * latest-wins guard, and the EXT-MAJOR-1 resume contract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportHistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(
        imports: FakeImportRepository = FakeImportRepository(),
        accounts: FakeAccountRepository = FakeAccountRepository(),
    ) = ImportHistoryViewModel(imports, accounts)

    @Test
    fun `loads history with account names resolved`() = runTest {
        val imports = FakeImportRepository().apply {
            historyResult = {
                listOf(
                    FakeImportRepository.summary(batchId = "b1", accountId = "acc-1"),
                    FakeImportRepository.summary(batchId = "b2", accountId = "acc-2"),
                )
            }
        }
        val accounts = FakeAccountRepository().apply {
            listResult = { _ ->
                listOf(
                    FakeAccountRepository.account(id = "acc-1", name = "Wallet"),
                    FakeAccountRepository.account(id = "acc-2", name = "Bank"),
                )
            }
        }

        val viewModel = vm(imports, accounts)

        val content = viewModel.state.value as ImportHistoryState.Content
        assertEquals(2, content.items.size)
        assertEquals("Wallet", content.accountNames["acc-1"])
        assertEquals("Bank", content.accountNames["acc-2"])
        assertEquals(1, imports.historyCalls.size)
    }

    @Test
    fun `empty history maps to the Empty state — never an error`() = runTest {
        val viewModel = vm()

        assertTrue(viewModel.state.value is ImportHistoryState.Empty)
    }

    @Test
    fun `a network failure maps to the Network error state and retry recovers`() = runTest {
        val imports = FakeImportRepository().apply {
            historyResult = { throw AppErrorException(AppError.NetworkUnavailable) }
        }
        val viewModel = vm(imports)

        assertEquals(
            ImportHistoryState.Error(ImportHistoryError.Network),
            viewModel.state.value,
        )

        imports.historyResult = { listOf(FakeImportRepository.summary()) }
        viewModel.refresh()

        assertTrue(viewModel.state.value is ImportHistoryState.Content)
    }

    @Test
    fun `a server failure maps to the Server error state`() = runTest {
        val imports = FakeImportRepository().apply {
            historyResult = { throw AppErrorException(AppError.Server(500)) }
        }

        val viewModel = vm(imports)

        assertEquals(
            ImportHistoryState.Error(ImportHistoryError.Server),
            viewModel.state.value,
        )
    }

    @Test
    fun `refresh failure keeps content and surfaces the banner flag`() = runTest {
        val imports = FakeImportRepository().apply {
            historyResult = { listOf(FakeImportRepository.summary()) }
        }
        val viewModel = vm(imports)
        assertTrue(viewModel.state.value is ImportHistoryState.Content)

        imports.historyResult = { throw AppErrorException(AppError.NetworkUnavailable) }
        viewModel.refresh()

        val content = viewModel.state.value as ImportHistoryState.Content
        assertEquals(ImportHistoryError.Network, content.refreshError)
        assertEquals(1, content.items.size)
    }

    @Test
    fun `a dead-generation result is discarded and arms the resume retry`() = runTest {
        val gate = CompletableDeferred<List<com.spendwise.android.core.model.ImportBatchSummary>>()
        val imports = FakeImportRepository().apply {
            historyResult = { gate.await() }
        }
        val viewModel = vm(imports)

        imports.historyResult = { throw SessionEndedException() }
        viewModel.refresh()

        // State is left untouched and the resume hook is armed — a
        // stale user's history never publishes.
        assertTrue(viewModel.state.value is ImportHistoryState.Loading)

        imports.historyResult = { listOf(FakeImportRepository.summary()) }
        viewModel.onVisible()

        assertTrue(viewModel.state.value is ImportHistoryState.Content)
    }

    @Test
    fun `a superseded load can never overwrite a newer result`() = runTest {
        val first = CompletableDeferred<List<com.spendwise.android.core.model.ImportBatchSummary>>()
        var call = 0
        val imports = FakeImportRepository().apply {
            historyResult = {
                if (++call == 1) first.await() else listOf(FakeImportRepository.summary(batchId = "new"))
            }
        }
        val viewModel = vm(imports)
        viewModel.refresh()
        first.complete(listOf(FakeImportRepository.summary(batchId = "stale")))

        val content = viewModel.state.value as ImportHistoryState.Content
        assertEquals("new", content.items.single().batchId)
    }

    @Test
    fun `a committed import elsewhere refetches — never a local insert`() = runTest {
        val imports = FakeImportRepository().apply {
            historyResult = { emptyList() }
        }
        val viewModel = vm(imports)
        assertTrue(viewModel.state.value is ImportHistoryState.Empty)
        val loadsBefore = imports.historyCalls.size

        // Server truth changes: the just-imported batch now exists.
        imports.historyResult = { listOf(FakeImportRepository.summary(batchId = "just-imported")) }
        imports.emitMutation()

        val content = viewModel.state.value as ImportHistoryState.Content
        assertEquals("just-imported", content.items.single().batchId)
        assertTrue(imports.historyCalls.size > loadsBefore)
    }

    @Test
    fun `an account-name lookup failure degrades to empty names — list still renders`() = runTest {
        val imports = FakeImportRepository().apply {
            historyResult = { listOf(FakeImportRepository.summary(accountId = "acc-9")) }
        }
        val accounts = FakeAccountRepository().apply {
            listResult = { _ -> throw AppErrorException(AppError.NetworkUnavailable) }
        }

        val viewModel = vm(imports, accounts)

        val content = viewModel.state.value as ImportHistoryState.Content
        assertEquals(1, content.items.size)
        assertNull(content.accountNames["acc-9"])
    }
}
