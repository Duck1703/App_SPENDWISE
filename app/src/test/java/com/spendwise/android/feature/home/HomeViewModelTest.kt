package com.spendwise.android.feature.home

import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.fakes.FakeAccountRepository
import com.spendwise.android.fakes.FakeDashboardRepository
import com.spendwise.android.fakes.FakeTransactionRepository
import com.spendwise.android.fakes.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Home (S05) ViewModel — all §17 hero states, section independence,
 * mutation reloads, and the EXT-MAJOR-1 session-resume contract.
 */
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(
        accounts: FakeAccountRepository = FakeAccountRepository(),
        txns: FakeTransactionRepository = FakeTransactionRepository(),
        dashboard: FakeDashboardRepository = FakeDashboardRepository(),
    ) = HomeViewModel(accounts, txns, dashboard)

    private fun oneAccount(currency: String = "VND") = listOf(
        FakeAccountRepository.account(id = "acc-1", name = "Wallet", currency = currency),
    )

    private fun content(vm: HomeViewModel) = vm.state.value as HomeUiState.Content

    // ── §17 hero states ─────────────────────────────────────────────

    @Test
    fun `uniform currency loads labeled month totals`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> oneAccount("VND") }
        val dashboard = FakeDashboardRepository()
        dashboard.monthResult = { FakeDashboardRepository.month(income = "4500.00", expense = "1200.50") }
        val viewModel = vm(accounts, dashboard = dashboard)

        val state = content(viewModel)
        val totals = state.totals as MonthTotals.Loaded
        assertEquals(java.math.BigDecimal("4500.00"), totals.income)
        assertEquals(java.math.BigDecimal("1200.50"), totals.expense)
        assertEquals("VND", totals.currency.code)
        assertEquals(1, dashboard.monthCalls.size)
    }

    @Test
    fun `mixed currencies render the honest unavailable state with no dashboard call`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ ->
            listOf(
                FakeAccountRepository.account(id = "a1", currency = "VND"),
                FakeAccountRepository.account(id = "a2", currency = "USD"),
            )
        }
        val dashboard = FakeDashboardRepository()
        val viewModel = vm(accounts, dashboard = dashboard)

        assertEquals(MonthTotals.Unavailable, content(viewModel).totals)
        assertTrue(dashboard.monthCalls.isEmpty()) // §17: never even asked
    }

    @Test
    fun `dashboard 400 — mixed-type aggregation — renders unavailable`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> oneAccount() }
        val dashboard = FakeDashboardRepository()
        dashboard.monthResult = { throw AppErrorException(AppError.BadRequest) }
        val viewModel = vm(accounts, dashboard = dashboard)

        assertEquals(MonthTotals.Unavailable, content(viewModel).totals)
    }

    @Test
    fun `dashboard 5xx degrades the hero only — carousel and activity intact`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> oneAccount() }
        val txns = FakeTransactionRepository()
        txns.listResult = { _, _, _ -> listOf(FakeTransactionRepository.transaction()) }
        val dashboard = FakeDashboardRepository()
        dashboard.monthResult = { throw AppErrorException(AppError.Server(500)) }
        val viewModel = vm(accounts, txns, dashboard)

        val state = content(viewModel)
        assertEquals(MonthTotals.Failed(HomeError.Server), state.totals)
        assertEquals(1, state.accounts.size)
        assertEquals(1, (state.activity as ActivityState.Items).transactions.size)
    }

    @Test
    fun `dashboard network failure degrades to the network retry state`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> oneAccount() }
        val dashboard = FakeDashboardRepository()
        dashboard.monthResult = { throw AppErrorException(AppError.NetworkUnavailable) }
        val viewModel = vm(accounts, dashboard = dashboard)

        assertEquals(MonthTotals.Failed(HomeError.Network), content(viewModel).totals)
    }

    @Test
    fun `retryTotals re-runs the hero leg only`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> oneAccount() }
        val dashboard = FakeDashboardRepository()
        var fail = true
        dashboard.monthResult = {
            if (fail) throw AppErrorException(AppError.NetworkUnavailable)
            else FakeDashboardRepository.month(income = "7")
        }
        val txns = FakeTransactionRepository()
        val viewModel = vm(accounts, txns, dashboard)
        assertEquals(MonthTotals.Failed(HomeError.Network), content(viewModel).totals)

        fail = false
        viewModel.retryTotals()

        val totals = content(viewModel).totals as MonthTotals.Loaded
        assertEquals(java.math.BigDecimal("7"), totals.income)
        assertEquals(2, dashboard.monthCalls.size)
        assertEquals(1, accounts.listCalls.size) // accounts leg untouched
        assertEquals(1, txns.listCalls.size) // activity leg untouched
    }

    // ── accounts spine ──────────────────────────────────────────────

    @Test
    fun `no accounts is the first-run empty state`() = runTest {
        val viewModel = vm()

        assertEquals(HomeUiState.Empty, viewModel.state.value)
    }

    @Test
    fun `accounts failure on first load is the retryable error state`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> throw AppErrorException(AppError.NetworkUnavailable) }
        val viewModel = vm(accounts)

        assertEquals(HomeUiState.Error(HomeError.Network), viewModel.state.value)
    }

    @Test
    fun `accounts server error maps distinctly from network`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> throw AppErrorException(AppError.Server(503)) }
        val viewModel = vm(accounts)

        assertEquals(HomeUiState.Error(HomeError.Server), viewModel.state.value)
    }

    @Test
    fun `accounts refresh failure over live content stays an inline banner`() = runTest {
        val accounts = FakeAccountRepository()
        var fail = false
        accounts.listResult = { _ ->
            if (fail) throw AppErrorException(AppError.NetworkUnavailable) else oneAccount()
        }
        val viewModel = vm(accounts)

        fail = true
        viewModel.refresh()

        val state = content(viewModel)
        assertEquals(HomeError.Network, state.refreshError)
        assertFalse(state.isRefreshing)
        assertEquals(1, state.accounts.size) // last-good figures preserved
    }

    // ── activity section ────────────────────────────────────────────

    @Test
    fun `activity renders the first five rows of the unfiltered list`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> oneAccount() }
        val txns = FakeTransactionRepository()
        txns.listResult = { _, _, _ ->
            (1..8).map { FakeTransactionRepository.transaction(id = "t$it") }
        }
        val viewModel = vm(accounts, txns)

        val activity = content(viewModel).activity as ActivityState.Items
        assertEquals(5, activity.transactions.size)
        assertEquals("t5", activity.transactions.last().id)
        // The unfiltered call — no account/type/category params.
        val call = txns.listCalls.single()
        assertEquals(null, call.accountId)
        assertEquals(null, call.type)
        assertEquals(null, call.category)
    }

    @Test
    fun `no transactions renders the activity-empty state`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> oneAccount() }
        val viewModel = vm(accounts)

        assertEquals(
            ActivityState.Items(emptyList()),
            content(viewModel).activity,
        )
    }

    @Test
    fun `activity failure degrades the panel only — hero and carousel intact`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> oneAccount() }
        val txns = FakeTransactionRepository()
        txns.listResult = { _, _, _ -> throw AppErrorException(AppError.Server(500)) }
        val viewModel = vm(accounts, txns)

        val state = content(viewModel)
        assertEquals(ActivityState.Failed(HomeError.Server), state.activity)
        assertTrue(state.totals is MonthTotals.Loaded)
        assertEquals(1, state.accounts.size)
    }

    @Test
    fun `retryActivity re-runs the activity leg only`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> oneAccount() }
        val txns = FakeTransactionRepository()
        var fail = true
        txns.listResult = { _, _, _ ->
            if (fail) throw AppErrorException(AppError.NetworkUnavailable)
            else listOf(FakeTransactionRepository.transaction(id = "t9"))
        }
        val dashboard = FakeDashboardRepository()
        val viewModel = vm(accounts, txns, dashboard)
        assertTrue(content(viewModel).activity is ActivityState.Failed)

        fail = false
        viewModel.retryActivity()

        val activity = content(viewModel).activity as ActivityState.Items
        assertEquals("t9", activity.transactions.single().id)
        assertEquals(2, txns.listCalls.size)
        assertEquals(1, dashboard.monthCalls.size) // hero leg untouched
    }

    // ── mutations ───────────────────────────────────────────────────

    @Test
    fun `a transaction mutation reloads every section`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> oneAccount() }
        val txns = FakeTransactionRepository()
        val dashboard = FakeDashboardRepository()
        val viewModel = vm(accounts, txns, dashboard)
        assertEquals(1, txns.listCalls.size)

        txns.emitMutation()

        assertEquals(2, accounts.listCalls.size)
        assertEquals(2, txns.listCalls.size)
        assertEquals(2, dashboard.monthCalls.size)
    }

    @Test
    fun `an account mutation reloads every section`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> oneAccount() }
        val txns = FakeTransactionRepository()
        val viewModel = vm(accounts, txns)

        accounts.emitMutation()

        assertEquals(2, accounts.listCalls.size)
    }

    @Test
    fun `refresh fetches accounts fresh — cache bypassed`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> oneAccount() }
        val viewModel = vm(accounts)

        viewModel.refresh()

        assertEquals(listOf(false, true), accounts.listCalls)
    }

    @Test
    fun `a superseded load never publishes — latest wins`() = runTest {
        // Initial load parks on the accounts gate; a mutation triggers
        // a second load that completes first. The parked load's late
        // response must not clobber the newer content.
        val accounts = FakeAccountRepository()
        val gate = CompletableDeferred<List<com.spendwise.android.core.model.Account>>()
        var calls = 0
        accounts.listResult = { _ ->
            calls++
            if (calls == 1) gate.await()
            else listOf(FakeAccountRepository.account(id = "acc-2", name = "New"))
        }
        val txns = FakeTransactionRepository()
        val viewModel = vm(accounts, txns)

        txns.emitMutation() // load #2 completes with the new account

        var state = content(viewModel)
        assertEquals("acc-2", state.accounts.single().id)

        gate.complete(oneAccount()) // parked load #1's response lands
        state = content(viewModel)
        assertEquals("acc-2", state.accounts.single().id) // suppressed
    }

    // ── session-resume contract (EXT-MAJOR-1) ───────────────────────

    @Test
    fun `SessionExpired mid-accounts stays Loading and reloads on resume`() = runTest {
        val accounts = FakeAccountRepository()
        var expired = true
        accounts.listResult = { _ ->
            if (expired) throw AppErrorException(AppError.SessionExpired) else oneAccount()
        }
        val viewModel = vm(accounts)

        assertTrue(viewModel.state.value is HomeUiState.Loading)

        expired = false
        viewModel.onVisible()

        assertEquals(1, content(viewModel).accounts.size)
        assertEquals(2, accounts.listCalls.size)
    }

    @Test
    fun `SessionExpired on the dashboard leg keeps the hero pending and reloads on resume`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> oneAccount() }
        val dashboard = FakeDashboardRepository()
        var expired = true
        dashboard.monthResult = {
            if (expired) throw AppErrorException(AppError.SessionExpired)
            else FakeDashboardRepository.month()
        }
        val viewModel = vm(accounts, dashboard = dashboard)

        // The leg failure is section-scoped: content stands, hero pending.
        assertTrue(content(viewModel).totals is MonthTotals.Loading)

        expired = false
        viewModel.onVisible()

        assertTrue(content(viewModel).totals is MonthTotals.Loaded)
        assertEquals(2, dashboard.monthCalls.size)
    }

    @Test
    fun `SessionExpired on the activity leg keeps the panel pending and reloads on resume`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> oneAccount() }
        val txns = FakeTransactionRepository()
        var expired = true
        txns.listResult = { _, _, _ ->
            if (expired) throw AppErrorException(AppError.SessionExpired)
            else listOf(FakeTransactionRepository.transaction(id = "t1"))
        }
        val viewModel = vm(accounts, txns)

        assertTrue(content(viewModel).activity is ActivityState.Loading)

        expired = false
        viewModel.onVisible()

        val activity = content(viewModel).activity as ActivityState.Items
        assertEquals(1, activity.transactions.size)
    }

    @Test
    fun `dead-generation mid-load stays Loading and reloads on resume`() = runTest {
        val accounts = FakeAccountRepository()
        var dead = true
        accounts.listResult = { _ ->
            if (dead) throw SessionEndedException() else oneAccount()
        }
        val viewModel = vm(accounts)

        assertTrue(viewModel.state.value is HomeUiState.Loading)

        dead = false
        viewModel.onVisible()

        assertEquals(1, content(viewModel).accounts.size)
    }

    @Test
    fun `dead-generation mid-refresh clears isRefreshing and reloads on resume`() = runTest {
        val accounts = FakeAccountRepository()
        var dead = false
        accounts.listResult = { _ ->
            if (dead) throw SessionEndedException() else oneAccount()
        }
        val viewModel = vm(accounts)

        dead = true
        viewModel.refresh()

        var state = content(viewModel)
        assertFalse(state.isRefreshing)
        assertEquals(1, state.accounts.size)

        dead = false
        viewModel.onVisible()

        state = content(viewModel)
        assertEquals(1, state.accounts.size)
        assertEquals(3, accounts.listCalls.size)
    }

    @Test
    fun `dead-generation on the dashboard leg reloads the hero on resume`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> oneAccount() }
        val dashboard = FakeDashboardRepository()
        var dead = true
        dashboard.monthResult = {
            if (dead) throw SessionEndedException() else FakeDashboardRepository.month()
        }
        val viewModel = vm(accounts, dashboard = dashboard)

        assertTrue(content(viewModel).totals is MonthTotals.Loading)

        dead = false
        viewModel.onVisible()

        assertTrue(content(viewModel).totals is MonthTotals.Loaded)
    }
}
