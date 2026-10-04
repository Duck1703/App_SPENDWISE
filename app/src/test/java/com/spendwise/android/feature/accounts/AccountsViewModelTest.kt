package com.spendwise.android.feature.accounts

import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.fakes.FakeAccountRepository
import com.spendwise.android.fakes.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AccountsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads accounts into Content on init`() = runTest {
        val repo = FakeAccountRepository()
        repo.listResult = {
            listOf(FakeAccountRepository.account("a1"), FakeAccountRepository.account("a2"))
        }

        val vm = AccountsViewModel(repo)

        val state = vm.state.value as AccountsUiState.Content
        assertEquals(2, state.accounts.size)
        assertFalse(state.mixedCurrencies)
        assertEquals(listOf(false), repo.listCalls) // cache-first on entry
    }

    @Test
    fun `empty list maps to the honest Empty state`() = runTest {
        val vm = AccountsViewModel(FakeAccountRepository())

        assertEquals(AccountsUiState.Empty, vm.state.value)
    }

    @Test
    fun `distinct currencies raise the mixed flag`() = runTest {
        val repo = FakeAccountRepository()
        repo.listResult = {
            listOf(
                FakeAccountRepository.account("a1", currency = "VND"),
                FakeAccountRepository.account("a2", currency = "USD"),
            )
        }

        val vm = AccountsViewModel(repo)

        assertTrue((vm.state.value as AccountsUiState.Content).mixedCurrencies)
    }

    @Test
    fun `network failure maps to the retryable Network error`() = runTest {
        val repo = FakeAccountRepository()
        repo.listResult = { throw AppErrorException(AppError.NetworkUnavailable) }

        val vm = AccountsViewModel(repo)

        assertEquals(AccountsUiState.Error(AccountsError.Network), vm.state.value)
    }

    @Test
    fun `5xx maps to the Server error`() = runTest {
        val repo = FakeAccountRepository()
        repo.listResult = { throw AppErrorException(AppError.Server(500)) }

        val vm = AccountsViewModel(repo)

        assertEquals(AccountsUiState.Error(AccountsError.Server), vm.state.value)
    }

    @Test
    fun `SessionExpired never reaches the screen — overlay handles it`() = runTest {
        val repo = FakeAccountRepository()
        repo.listResult = { throw AppErrorException(AppError.SessionExpired) }

        val vm = AccountsViewModel(repo)

        // State stays Loading (initial) — not an error surface.
        assertEquals(AccountsUiState.Loading, vm.state.value)
    }

    @Test
    fun `expired mid-load re-runs the load when the screen returns`() = runTest {
        val repo = FakeAccountRepository()
        var call = 0
        repo.listResult = {
            call++
            if (call == 1) throw AppErrorException(AppError.SessionExpired)
            listOf(FakeAccountRepository.account("a1"))
        }
        val vm = AccountsViewModel(repo)

        vm.onVisible() // resume-pop re-composition hook

        val state = vm.state.value as AccountsUiState.Content
        assertEquals(1, state.accounts.size)
        assertEquals(listOf(false, true), repo.listCalls)
    }

    @Test
    fun `expired mid-refresh keeps prior content and clears isRefreshing`() = runTest {
        val repo = FakeAccountRepository()
        repo.listResult = { listOf(FakeAccountRepository.account("a1")) }
        val vm = AccountsViewModel(repo)
        repo.listResult = { throw AppErrorException(AppError.SessionExpired) }

        vm.refresh()

        val state = vm.state.value as AccountsUiState.Content
        assertFalse(state.isRefreshing)
        // No mid-load retry needed — content is already on screen.
        vm.onVisible()
        assertEquals(listOf(false, true), repo.listCalls)
    }

    @Test
    fun `retry after error refetches with refresh=true`() = runTest {
        val repo = FakeAccountRepository()
        var call = 0
        repo.listResult = {
            call++
            if (call == 1) throw AppErrorException(AppError.NetworkUnavailable)
            listOf(FakeAccountRepository.account("a1"))
        }
        val vm = AccountsViewModel(repo)

        vm.refresh()

        val state = vm.state.value as AccountsUiState.Content
        assertEquals(1, state.accounts.size)
        assertEquals(listOf(false, true), repo.listCalls)
    }

    @Test
    fun `refresh keeps content while refetching`() = runTest {
        val repo = FakeAccountRepository()
        repo.listResult = { listOf(FakeAccountRepository.account("a1")) }
        val vm = AccountsViewModel(repo)

        vm.refresh()

        val state = vm.state.value as AccountsUiState.Content
        assertFalse(state.isRefreshing) // settled after refresh completes
        assertEquals(listOf(false, true), repo.listCalls)
    }

    @Test
    fun `refresh failure over content keeps the list and flags refreshError`() = runTest {
        val repo = FakeAccountRepository()
        repo.listResult = { listOf(FakeAccountRepository.account("a1")) }
        val vm = AccountsViewModel(repo)
        repo.listResult = { throw AppErrorException(AppError.NetworkUnavailable) }

        vm.refresh()

        val state = vm.state.value as AccountsUiState.Content
        assertEquals(1, state.accounts.size)
        assertEquals(AccountsError.Network, state.refreshError)
        assertFalse(state.isRefreshing)
        assertEquals(listOf(false, true), repo.listCalls)
    }

    @Test
    fun `successful refresh clears a previous refreshError`() = runTest {
        val repo = FakeAccountRepository()
        repo.listResult = { listOf(FakeAccountRepository.account("a1")) }
        val vm = AccountsViewModel(repo)
        repo.listResult = { throw AppErrorException(AppError.NetworkUnavailable) }
        vm.refresh()
        repo.listResult = { listOf(FakeAccountRepository.account("a1"), FakeAccountRepository.account("a2")) }

        vm.refresh()

        val state = vm.state.value as AccountsUiState.Content
        assertEquals(2, state.accounts.size)
        assertEquals(null, state.refreshError)
    }

    @Test
    fun `initial load failure still surfaces the full error state`() = runTest {
        val repo = FakeAccountRepository()
        repo.listResult = { throw AppErrorException(AppError.Server(500)) }

        val vm = AccountsViewModel(repo)

        assertEquals(AccountsUiState.Error(AccountsError.Server), vm.state.value)
    }
}
