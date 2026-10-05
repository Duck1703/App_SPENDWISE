package com.spendwise.android.feature.transactions

import androidx.lifecycle.SavedStateHandle
import com.spendwise.android.core.model.TransactionType
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.fakes.FakeAccountRepository
import com.spendwise.android.fakes.FakeTransactionRepository
import com.spendwise.android.fakes.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TransactionDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(
        id: String = "txn-1",
        txns: FakeTransactionRepository = FakeTransactionRepository(),
        accounts: FakeAccountRepository = FakeAccountRepository(),
    ) = TransactionDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("transactionId" to id)),
        transactionRepository = txns,
        accountRepository = accounts,
    )

    @Test
    fun `init resolves the transaction from list data and names the account`() = runTest {
        val txns = FakeTransactionRepository()
        txns.findByIdResult = { id ->
            FakeTransactionRepository.transaction(id = id, accountId = "acc-7")
        }
        val accounts = FakeAccountRepository()
        accounts.findByIdResult = { id, _ -> FakeAccountRepository.account(id = id, name = "Wallet") }

        val viewModel = vm(id = "txn-9", txns = txns, accounts = accounts)

        val state = viewModel.state.value as TransactionDetailUiState.Content
        assertEquals("txn-9", state.transaction.id)
        assertEquals("Wallet", state.accountName)
        assertEquals(listOf("txn-9"), txns.findByIdCalls)
    }

    @Test
    fun `account label failure degrades to null — the detail still renders`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.findByIdResult = { _, _ -> throw AppErrorException(AppError.NotFound) }

        val viewModel = vm(accounts = accounts)

        val state = viewModel.state.value as TransactionDetailUiState.Content
        assertNull(state.accountName)
    }

    @Test
    fun `missing transaction maps to the honest NotFound state`() = runTest {
        val txns = FakeTransactionRepository()
        txns.findByIdResult = { throw AppErrorException(AppError.NotFound) }

        val viewModel = vm(txns = txns)

        assertEquals(TransactionDetailUiState.NotFound, viewModel.state.value)
    }

    @Test
    fun `network failure maps to the retryable Network error`() = runTest {
        val txns = FakeTransactionRepository()
        txns.findByIdResult = { throw AppErrorException(AppError.NetworkUnavailable) }

        val viewModel = vm(txns = txns)

        assertEquals(
            TransactionDetailUiState.Error(TransactionsError.Network),
            viewModel.state.value,
        )
    }

    @Test
    fun `SessionExpired mid-load stays Loading and onVisible reloads`() = runTest {
        val txns = FakeTransactionRepository()
        var expired = true
        txns.findByIdResult = { id ->
            if (expired) {
                throw AppErrorException(AppError.SessionExpired)
            } else {
                FakeTransactionRepository.transaction(id = id, type = TransactionType.INCOME)
            }
        }
        val viewModel = vm(txns = txns)

        assertTrue(viewModel.state.value is TransactionDetailUiState.Loading)

        expired = false
        viewModel.onVisible()

        val state = viewModel.state.value as TransactionDetailUiState.Content
        assertEquals(TransactionType.INCOME, state.transaction.type)
    }
}
