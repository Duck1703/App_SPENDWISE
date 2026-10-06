package com.spendwise.android.feature.imports

import androidx.lifecycle.SavedStateHandle
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.fakes.FakeAccountRepository
import com.spendwise.android.fakes.FakeImportRepository
import com.spendwise.android.fakes.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Import batch detail (M10) — the verbatim report render, 404 →
 * honest NotFound (a foreign batch never reads as forbidden), error
 * mapping, and the resume contract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportHistoryDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(
        imports: FakeImportRepository = FakeImportRepository(),
        accounts: FakeAccountRepository = FakeAccountRepository(),
        id: String = "batch-1",
    ) = ImportHistoryDetailViewModel(
        SavedStateHandle(mapOf("batchId" to id)),
        imports,
        accounts,
    )

    @Test
    fun `loads the batch report with the resolved account name`() = runTest {
        val imports = FakeImportRepository().apply {
            batchResult = { FakeImportRepository.batch(batchId = it, accountId = "acc-1") }
        }
        val accounts = FakeAccountRepository().apply {
            listResult = { _ ->
                listOf(FakeAccountRepository.account(id = "acc-1", name = "Wallet"))
            }
        }

        val viewModel = vm(imports, accounts, id = "b-42")

        val content = viewModel.state.value as ImportHistoryDetailState.Content
        assertEquals("b-42", content.batch.batchId)
        assertEquals("Wallet", content.accountName)
        assertEquals(listOf("b-42"), imports.batchCalls)
        assertEquals(2, content.batch.successCount)
        assertEquals(2, content.batch.rows.size)
    }

    @Test
    fun `404 maps to NotFound — a foreign batch reads as absent`() = runTest {
        val imports = FakeImportRepository().apply {
            batchResult = { throw AppErrorException(AppError.NotFound) }
        }

        val viewModel = vm(imports)

        assertEquals(ImportHistoryDetailState.NotFound, viewModel.state.value)
    }

    @Test
    fun `a network failure maps to the Network error state`() = runTest {
        val imports = FakeImportRepository().apply {
            batchResult = { throw AppErrorException(AppError.NetworkUnavailable) }
        }

        val viewModel = vm(imports)

        assertEquals(
            ImportHistoryDetailState.Error(ImportHistoryError.Network),
            viewModel.state.value,
        )
    }

    @Test
    fun `a dead-generation result is discarded and the resume retry reloads`() = runTest {
        val imports = FakeImportRepository().apply {
            batchResult = { throw SessionEndedException() }
        }
        val viewModel = vm(imports)

        assertTrue(viewModel.state.value is ImportHistoryDetailState.Loading)

        imports.batchResult = { FakeImportRepository.batch(batchId = it) }
        viewModel.onVisible()

        assertTrue(viewModel.state.value is ImportHistoryDetailState.Content)
    }

    @Test
    fun `a superseded load can never overwrite a newer result`() = runTest {
        val first = kotlinx.coroutines.CompletableDeferred<com.spendwise.android.core.model.ImportBatch>()
        var call = 0
        val imports = FakeImportRepository().apply {
            batchResult = {
                if (++call == 1) first.await()
                else FakeImportRepository.batch(batchId = "new")
            }
        }
        val viewModel = vm(imports)
        viewModel.refresh()
        first.complete(FakeImportRepository.batch(batchId = "stale"))

        val content = viewModel.state.value as ImportHistoryDetailState.Content
        assertEquals("new", content.batch.batchId)
    }

    @Test
    fun `an account-name lookup failure degrades to null — detail still renders`() = runTest {
        val accounts = FakeAccountRepository().apply {
            listResult = { _ -> throw AppErrorException(AppError.NetworkUnavailable) }
        }

        val viewModel = vm(accounts = accounts)

        val content = viewModel.state.value as ImportHistoryDetailState.Content
        assertNull(content.accountName)
        assertEquals("batch-1", content.batch.batchId)
    }
}
