package com.spendwise.android.feature.accounts

import androidx.lifecycle.SavedStateHandle
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.fakes.FakeAccountRepository
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
    ) = AccountDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("accountId" to id)),
        accountRepository = repo,
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
}
