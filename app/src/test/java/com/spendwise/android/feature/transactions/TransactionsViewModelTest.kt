package com.spendwise.android.feature.transactions

import com.spendwise.android.core.model.Transaction
import com.spendwise.android.core.model.TransactionProvenance
import com.spendwise.android.core.model.TransactionType
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.fakes.FakeAccountRepository
import com.spendwise.android.fakes.FakeCategoryRepository
import com.spendwise.android.fakes.FakeTransactionRepository
import com.spendwise.android.fakes.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TransactionsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(
        txns: FakeTransactionRepository = FakeTransactionRepository(),
        accounts: FakeAccountRepository = FakeAccountRepository(),
        cats: FakeCategoryRepository = FakeCategoryRepository(),
    ) = TransactionsViewModel(txns, accounts, cats)

    private fun twoRows() = listOf(
        FakeTransactionRepository.transaction(id = "t1", type = TransactionType.EXPENSE, category = "FOOD"),
        FakeTransactionRepository.transaction(
            id = "t2",
            type = TransactionType.INCOME,
            category = null,
            sourceRef = "IMPORT:CSV:9",
        ),
    )

    @Test
    fun `init loads Content with account names for row labels`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listResult = { _, _, _ -> twoRows() }
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> listOf(FakeAccountRepository.account(id = "acc-1", name = "Wallet")) }

        val viewModel = vm(txns, accounts)

        val state = viewModel.state.value as TransactionsUiState.Content
        assertEquals(2, state.items.size)
        assertEquals("Wallet", state.accountNames["acc-1"])
        assertEquals(1, txns.listCalls.size)
    }

    @Test
    fun `empty server list without filters is the Empty entry state`() = runTest {
        val viewModel = vm()

        assertEquals(TransactionsUiState.Empty, viewModel.state.value)
    }

    @Test
    fun `server filters hit the repository while provenance stays client-side`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listResult = { _, _, _ -> twoRows() }
        val viewModel = vm(txns)

        viewModel.onAccountFilter("a1")
        viewModel.onTypeFilter(TransactionType.EXPENSE)
        viewModel.onCategoryFilter(CategorySelection.Code("FOOD"))
        viewModel.onProvenanceFilter(TransactionProvenance.IMPORTED)

        val call = txns.listCalls.last()
        assertEquals("a1", call.accountId)
        assertEquals(TransactionType.EXPENSE, call.type)
        assertEquals("FOOD", call.category)
        // IMPORTED is never sent — it filters the fetched rows locally.
        val state = viewModel.state.value as TransactionsUiState.Content
        assertEquals(listOf("t2"), state.items.map { it.id })
        assertTrue(state.noMatches.not())
    }

    @Test
    fun `uncategorized selection is a local null-category pass`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listResult = { _, _, _ -> twoRows() }
        val viewModel = vm(txns)

        viewModel.onCategoryFilter(CategorySelection.Uncategorized)

        // Never a server param for the uncategorized view.
        assertEquals(null, txns.listCalls.last().category)
        val state = viewModel.state.value as TransactionsUiState.Content
        assertEquals(listOf("t2"), state.items.map { it.id })
    }

    @Test
    fun `filtered to zero keeps Content with noMatches`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listResult = { _, _, _ -> emptyList() }
        val viewModel = vm(txns)

        viewModel.onTypeFilter(TransactionType.EXPENSE)

        val state = viewModel.state.value as TransactionsUiState.Content
        assertTrue(state.noMatches)
    }

    @Test
    fun `refresh re-runs the identical query — the nonce defeats conflation`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listResult = { _, _, _ -> twoRows() }
        val viewModel = vm(txns)

        viewModel.refresh()

        assertEquals(2, txns.listCalls.size)
    }

    @Test
    fun `refresh failure over live content stays an inline banner`() = runTest {
        val txns = FakeTransactionRepository()
        var fail = false
        txns.listResult = { _, _, _ ->
            if (fail) throw AppErrorException(AppError.NetworkUnavailable) else twoRows()
        }
        val viewModel = vm(txns)

        fail = true
        viewModel.refresh()

        val state = viewModel.state.value as TransactionsUiState.Content
        assertEquals(2, state.items.size)
        assertEquals(TransactionsError.Network, state.refreshError)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun `initial failure maps to the retryable error state`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listResult = { _, _, _ -> throw AppErrorException(AppError.Server(500)) }

        val viewModel = vm(txns)

        assertEquals(
            TransactionsUiState.Error(TransactionsError.Server),
            viewModel.state.value,
        )
    }

    @Test
    fun `a create mutation reloads the current view`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listResult = { _, _, _ -> twoRows() }
        val viewModel = vm(txns)
        assertEquals(1, txns.listCalls.size)

        txns.emitMutation()

        assertEquals(2, txns.listCalls.size)
    }

    @Test
    fun `latest filter wins — a superseded response never publishes`() = runTest {
        val txns = FakeTransactionRepository()
        val gateExpense = CompletableDeferred<List<Transaction>>()
        val incomeRows = listOf(
            FakeTransactionRepository.transaction(id = "t9", type = TransactionType.INCOME, category = "SALARY"),
        )
        var calls = 0
        txns.listResult = { _, type, _ ->
            calls++
            if (type == TransactionType.EXPENSE) gateExpense.await() else incomeRows
        }
        val viewModel = vm(txns)

        viewModel.onTypeFilter(TransactionType.EXPENSE) // parks on the gate
        viewModel.onTypeFilter(TransactionType.INCOME) // supersedes it

        val state = viewModel.state.value as TransactionsUiState.Content
        assertEquals(listOf("t9"), state.items.map { it.id })

        // The parked fetch's cancellation already ran; completing the gate
        // is a no-op — state must remain the latest filter's result.
        gateExpense.complete(twoRows())
        assertEquals(listOf("t9"), (viewModel.state.value as TransactionsUiState.Content).items.map { it.id })
    }

    @Test
    fun `SessionExpired on first load stays Loading and onVisible reloads`() = runTest {
        val txns = FakeTransactionRepository()
        var expired = true
        txns.listResult = { _, _, _ ->
            if (expired) throw AppErrorException(AppError.SessionExpired) else twoRows()
        }
        val viewModel = vm(txns)

        assertTrue(viewModel.state.value is TransactionsUiState.Loading)

        expired = false
        viewModel.onVisible()

        val state = viewModel.state.value as TransactionsUiState.Content
        assertEquals(2, state.items.size)
    }

    @Test
    fun `SessionExpired on the account-name leg still reloads on resume`() = runTest {
        // Regression: list() succeeds but the trailing accountNames()
        // call hits the expired session — the load was still in flight,
        // so onVisible() must re-run it (not leave a stuck skeleton).
        val txns = FakeTransactionRepository()
        txns.listResult = { _, _, _ -> twoRows() }
        val accounts = FakeAccountRepository()
        var expired = true
        accounts.listResult = { _ ->
            if (expired) {
                throw AppErrorException(AppError.SessionExpired)
            } else {
                listOf(FakeAccountRepository.account(id = "acc-1", name = "Wallet"))
            }
        }
        val viewModel = vm(txns, accounts)

        assertTrue(viewModel.state.value is TransactionsUiState.Loading)

        expired = false
        viewModel.onVisible()

        val state = viewModel.state.value as TransactionsUiState.Content
        assertEquals("Wallet", state.accountNames["acc-1"])
    }

    @Test
    fun `SessionExpired during refresh preserves content and stops refreshing`() = runTest {
        val txns = FakeTransactionRepository()
        var expired = false
        txns.listResult = { _, _, _ ->
            if (expired) throw AppErrorException(AppError.SessionExpired) else twoRows()
        }
        val viewModel = vm(txns)

        expired = true
        viewModel.refresh()

        val state = viewModel.state.value as TransactionsUiState.Content
        assertFalse(state.isRefreshing)
        assertEquals(2, state.items.size)
    }

    @Test
    fun `clear filters restores the unfiltered server call`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listResult = { _, _, _ -> twoRows() }
        val viewModel = vm(txns)

        viewModel.onAccountFilter("a1")
        viewModel.clearFilters()

        val call = txns.listCalls.last()
        assertEquals(null, call.accountId)
        assertEquals(null, call.type)
        assertEquals(null, call.category)
    }
}
