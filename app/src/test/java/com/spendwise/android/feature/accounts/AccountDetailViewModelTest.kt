package com.spendwise.android.feature.accounts

import androidx.lifecycle.SavedStateHandle
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.fakes.FakeAccountRepository
import com.spendwise.android.fakes.FakeTransactionRepository
import com.spendwise.android.fakes.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AccountDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        id: String = "acc-1",
        repo: FakeAccountRepository = FakeAccountRepository(),
        txns: FakeTransactionRepository = FakeTransactionRepository(),
    ) = AccountDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("accountId" to id)),
        accountRepository = repo,
        transactionRepository = txns,
    )

    @Test
    fun `loads the account into Content on init`() = runTest {
        val repo = FakeAccountRepository()
        repo.findByIdResult = { id, _ -> FakeAccountRepository.account(id = id, name = "Jar") }

        val vm = viewModel(id = "acc-7", repo = repo)

        val state = vm.state.value as AccountDetailUiState.Content
        assertEquals("acc-7", state.account.id)
        assertEquals("Jar", state.account.name)
        assertEquals(listOf("acc-7" to false), repo.findByIdCalls)
    }

    @Test
    fun `404 maps to the honest NotFound state`() = runTest {
        val repo = FakeAccountRepository()
        repo.findByIdResult = { _, _ -> throw AppErrorException(AppError.NotFound) }

        val vm = viewModel(repo = repo)

        assertEquals(AccountDetailUiState.NotFound, vm.state.value)
    }

    @Test
    fun `network failure maps to the retryable Network error`() = runTest {
        val repo = FakeAccountRepository()
        repo.findByIdResult = { _, _ -> throw AppErrorException(AppError.NetworkUnavailable) }

        val vm = viewModel(repo = repo)

        assertEquals(
            AccountDetailUiState.Error(AccountsError.Network),
            vm.state.value,
        )
    }

    @Test
    fun `refresh re-requests with refresh=true`() = runTest {
        val repo = FakeAccountRepository()
        val vm = viewModel(repo = repo)

        vm.refresh()

        assertEquals(listOf("acc-1" to false, "acc-1" to true), repo.findByIdCalls)
    }

    @Test
    fun `SessionExpired leaves state alone — overlay handles it`() = runTest {
        val repo = FakeAccountRepository()
        repo.findByIdResult = { _, _ -> throw AppErrorException(AppError.SessionExpired) }

        val vm = viewModel(repo = repo)

        assertTrue(vm.state.value is AccountDetailUiState.Loading)
    }

    @Test
    fun `expired mid-load re-runs the fetch when the screen returns`() = runTest {
        val repo = FakeAccountRepository()
        var call = 0
        repo.findByIdResult = { id, _ ->
            call++
            if (call == 1) throw AppErrorException(AppError.SessionExpired)
            FakeAccountRepository.account(id = id)
        }
        val vm = viewModel(repo = repo)

        vm.onVisible() // resume-pop re-composition hook

        val state = vm.state.value as AccountDetailUiState.Content
        assertEquals("acc-1", state.account.id)
        assertEquals(listOf("acc-1" to false, "acc-1" to true), repo.findByIdCalls)
    }

    // ── M3: account-scoped history section ────────────────────────────────

    @Test
    fun `content carries the account-scoped history`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listResult = { _, _, _ ->
            listOf(FakeTransactionRepository.transaction(id = "t1", accountId = "acc-1"))
        }

        val vm = viewModel(txns = txns)

        val state = vm.state.value as AccountDetailUiState.Content
        val history = state.history as AccountDetailUiState.AccountHistory.Items
        assertEquals(listOf("t1"), history.transactions.map { it.id })
        // Scoped to this account via the real server filter param.
        assertEquals("acc-1", txns.listCalls.single().accountId)
    }

    @Test
    fun `history failure degrades the section, never the header`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listResult = { _, _, _ -> throw AppErrorException(AppError.Server(500)) }

        val vm = viewModel(txns = txns)

        val state = vm.state.value as AccountDetailUiState.Content
        assertEquals("acc-1", state.account.id)
        assertEquals(AccountDetailUiState.AccountHistory.Error, state.history)
    }

    @Test
    fun `history retry reloads only the section`() = runTest {
        val txns = FakeTransactionRepository()
        var fail = true
        txns.listResult = { _, _, _ ->
            if (fail) {
                throw AppErrorException(AppError.NetworkUnavailable)
            } else {
                listOf(FakeTransactionRepository.transaction(id = "t1"))
            }
        }
        val repo = FakeAccountRepository()
        val vm = viewModel(repo = repo, txns = txns)

        fail = false
        vm.retryHistory()

        val state = vm.state.value as AccountDetailUiState.Content
        assertTrue(state.history is AccountDetailUiState.AccountHistory.Items)
        // The account itself was not re-requested.
        assertEquals(1, repo.findByIdCalls.size)
    }

    @Test
    fun `dead-generation mid-load arms the resume retry — no stuck skeleton`() = runTest {
        // EXT-MAJOR-1: the dead-generation publish must arm the same
        // resume-retry as the 401 path.
        val repo = FakeAccountRepository()
        var dead = true
        repo.findByIdResult = { id, _ ->
            if (dead) {
                throw SessionEndedException()
            } else {
                FakeAccountRepository.account(id = id)
            }
        }
        val vm = viewModel(repo = repo)

        assertTrue(vm.state.value is AccountDetailUiState.Loading)

        dead = false
        vm.onVisible()

        val state = vm.state.value as AccountDetailUiState.Content
        assertEquals("acc-1", state.account.id)
        assertEquals(listOf("acc-1" to false, "acc-1" to true), repo.findByIdCalls)
    }

    @Test
    fun `dead-generation on history marks the section for resume retry`() = runTest {
        val repo = FakeAccountRepository()
        val txns = FakeTransactionRepository()
        var dead = true
        txns.listResult = { _, _, _ ->
            if (dead) {
                throw SessionEndedException()
            } else {
                listOf(FakeTransactionRepository.transaction(id = "t1"))
            }
        }
        val vm = viewModel(repo = repo, txns = txns)

        // Header published; the section stayed pending on the dead session.
        var state = vm.state.value as AccountDetailUiState.Content
        assertTrue(state.history is AccountDetailUiState.AccountHistory.Loading)

        dead = false
        vm.onVisible()

        state = vm.state.value as AccountDetailUiState.Content
        assertTrue(state.history is AccountDetailUiState.AccountHistory.Items)
        assertEquals(2, txns.listCalls.size)
        // The header itself was not refetched — the section retried alone.
        assertEquals(1, repo.findByIdCalls.size)
    }

    @Test
    fun `a create mutation reloads account and history`() = runTest {
        val repo = FakeAccountRepository()
        val txns = FakeTransactionRepository()
        val vm = viewModel(repo = repo, txns = txns)

        txns.emitMutation()

        assertEquals(listOf("acc-1" to false, "acc-1" to true), repo.findByIdCalls)
        assertEquals(2, txns.listCalls.size)
    }
}
