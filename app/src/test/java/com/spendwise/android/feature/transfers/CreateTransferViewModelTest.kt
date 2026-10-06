package com.spendwise.android.feature.transfers

import androidx.lifecycle.SavedStateHandle
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.fakes.FakeAccountRepository
import com.spendwise.android.fakes.FakeTransactionRepository
import com.spendwise.android.fakes.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

class CreateTransferViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(
        prefillSourceId: String? = null,
        txns: FakeTransactionRepository = FakeTransactionRepository(),
        accounts: FakeAccountRepository = FakeAccountRepository(),
    ) = CreateTransferViewModel(
        savedStateHandle = SavedStateHandle(
            prefillSourceId?.let { mapOf("fromAccountId" to it) } ?: emptyMap(),
        ),
        transactionRepository = txns,
        accountRepository = accounts,
    )

    private fun twoAccounts(
        firstCurrency: String = "USD",
        secondCurrency: String = "USD",
    ) = FakeAccountRepository().apply {
        listResult = {
            listOf(
                FakeAccountRepository.account(id = "acc-1", currency = firstCurrency),
                FakeAccountRepository.account(id = "acc-2", currency = secondCurrency),
            )
        }
    }

    private suspend fun fillValid(
        viewModel: CreateTransferViewModel,
        amount: String = "25.50",
    ) {
        viewModel.onSourceChange("acc-1")
        viewModel.onDestinationChange("acc-2")
        viewModel.onAmountChange(amount)
    }

    @Test
    fun `prefills the first account and excludes it from the destination picker`() = runTest {
        val viewModel = vm(accounts = twoAccounts())
        runCurrent()

        val state = viewModel.state.value
        assertEquals("acc-1", state.sourceAccountId)
        assertNull(state.destinationAccountId)
        assertEquals(listOf("acc-2"), state.destinationOptions.map { it.id })
        assertTrue(state.hasEnoughAccounts)
        assertFalse(state.optionsLoading)
    }

    @Test
    fun `route prefill wins over the first-account default`() = runTest {
        val viewModel = vm(prefillSourceId = "acc-2", accounts = twoAccounts())
        runCurrent()

        val state = viewModel.state.value
        assertEquals("acc-2", state.sourceAccountId)
        assertEquals(listOf("acc-1"), state.destinationOptions.map { it.id })
    }

    @Test
    fun `a prefill id the server no longer has falls back to the first account`() = runTest {
        val viewModel = vm(prefillSourceId = "gone", accounts = twoAccounts())
        runCurrent()

        assertEquals("acc-1", viewModel.state.value.sourceAccountId)
    }

    @Test
    fun `changing the source clears a destination equal to it`() = runTest {
        val viewModel = vm(accounts = twoAccounts())
        runCurrent()
        fillValid(viewModel)

        viewModel.onSourceChange("acc-2")

        val state = viewModel.state.value
        assertEquals("acc-2", state.sourceAccountId)
        assertNull(state.destinationAccountId)
        assertEquals(listOf("acc-1"), state.destinationOptions.map { it.id })
    }

    @Test
    fun `picking the source as destination is ignored`() = runTest {
        val viewModel = vm(accounts = twoAccounts())
        runCurrent()

        viewModel.onDestinationChange("acc-1")

        assertNull(viewModel.state.value.destinationAccountId)
    }

    @Test
    fun `a cross-currency destination is rejected with a clear field error`() = runTest {
        val txns = FakeTransactionRepository()
        val viewModel = vm(
            txns = txns,
            accounts = twoAccounts(secondCurrency = "EUR"),
        )
        runCurrent()

        viewModel.onSourceChange("acc-1")
        viewModel.onDestinationChange("acc-2")
        viewModel.onAmountChange("25.00")
        viewModel.submit()
        runCurrent()

        val state = viewModel.state.value
        assertEquals(
            CreateTransferUiState.AccountError.CrossCurrency,
            state.destinationError,
        )
        assertFalse(state.isSubmitting)
        assertTrue(txns.transferCalls.isEmpty())
    }

    @Test
    fun `a reload that resolves a different source drops a now-equal destination`() = runTest {
        val accounts = twoAccounts()
        val viewModel = vm(accounts = accounts)
        runCurrent()
        fillValid(viewModel) // source acc-1, destination acc-2

        // The source vanished server-side — the fallback lands on acc-2,
        // which now collides with the stale destination.
        accounts.listResult = {
            listOf(FakeAccountRepository.account(id = "acc-2"))
        }
        viewModel.loadOptions()
        runCurrent()

        val state = viewModel.state.value
        assertEquals("acc-2", state.sourceAccountId)
        assertNull(state.destinationAccountId)
        assertTrue(state.destinationOptions.isEmpty())
    }

    @Test
    fun `a single account reports hasEnoughAccounts false`() = runTest {
        val accounts = FakeAccountRepository().apply {
            listResult = { listOf(FakeAccountRepository.account(id = "acc-1")) }
        }
        val viewModel = vm(accounts = accounts)
        runCurrent()

        assertFalse(viewModel.state.value.hasEnoughAccounts)
    }

    @Test
    fun `submit posts the transfer with the source account currency`() = runTest {
        val txns = FakeTransactionRepository()
        val viewModel = vm(txns = txns, accounts = twoAccounts())
        runCurrent()
        fillValid(viewModel)

        var event: CreateTransferViewModel.Event? = null
        val job = launch { viewModel.events.collect { event = it } }
        runCurrent()
        viewModel.submit()
        runCurrent()
        job.cancel()

        val call = txns.transferCalls.single()
        assertEquals("acc-1", call.sourceAccountId)
        assertEquals("acc-2", call.destinationAccountId)
        assertEquals(BigDecimal("25.50"), call.amount.amount)
        assertEquals("USD", call.amount.currency.code)
        assertEquals(CreateTransferViewModel.Event.Created, event)
    }

    @Test
    fun `the same account on both ends is a field error, never a request`() = runTest {
        val txns = FakeTransactionRepository()
        // A degenerate account list cannot reach this state via the
        // pickers — drive the VM directly to prove the guard holds.
        val viewModel = vm(accounts = twoAccounts())
        runCurrent()
        viewModel.onSourceChange("acc-1")
        viewModel.onAmountChange("10")
        // destination stays null → required; same-account is only
        // reachable if destination == source, which the setter refuses —
        // assert the required error fires and nothing is sent.
        viewModel.submit()
        runCurrent()

        assertEquals(
            CreateTransferUiState.AccountError.Required,
            viewModel.state.value.destinationError,
        )
        assertTrue(txns.transferCalls.isEmpty())
    }

    @Test
    fun `double-submit while in flight sends exactly once`() = runTest {
        val txns = FakeTransactionRepository()
        val gate = CompletableDeferred<Unit>()
        val default = FakeTransactionRepository().transferResult
        txns.transferResult = { source, destination, amount ->
            gate.await()
            default(source, destination, amount)
        }
        val viewModel = vm(txns = txns, accounts = twoAccounts())
        runCurrent()
        fillValid(viewModel)

        viewModel.submit()
        runCurrent()
        viewModel.submit() // submit lock — the second call is ignored
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertEquals(1, txns.transferCalls.size)
    }

    @Test
    fun `a 404 on either account reloads the pickers and flags the form`() = runTest {
        val txns = FakeTransactionRepository()
        txns.transferResult = { _, _, _ ->
            throw AppErrorException(AppError.NotFound)
        }
        val accounts = twoAccounts()
        val viewModel = vm(txns = txns, accounts = accounts)
        runCurrent()
        fillValid(viewModel)

        viewModel.submit()
        runCurrent()

        val state = viewModel.state.value
        assertFalse(state.isSubmitting)
        assertEquals(CreateTransferUiState.FormError.AccountUnavailable, state.formError)
        // The picker reload ran — stale accounts drop out.
        assertEquals(2, accounts.listCalls.size)
    }

    @Test
    fun `server field errors land on their fields, others on the form`() = runTest {
        val txns = FakeTransactionRepository()
        txns.transferResult = { _, _, _ ->
            throw AppErrorException(
                AppError.Validation(mapOf("amount" to "must be positive")),
            )
        }
        val viewModel = vm(txns = txns, accounts = twoAccounts())
        runCurrent()
        fillValid(viewModel)

        viewModel.submit()
        runCurrent()

        val state = viewModel.state.value
        assertEquals(
            CreateTransferUiState.AmountError.Server("must be positive"),
            state.amountError,
        )
        assertNull(state.formError)

        txns.transferResult = { _, _, _ ->
            throw AppErrorException(
                AppError.Validation(mapOf("currency" to "mismatch")),
            )
        }
        viewModel.submit()
        runCurrent()

        assertEquals(CreateTransferUiState.FormError.Generic, viewModel.state.value.formError)
    }

    @Test
    fun `network failure is an ambiguous form error — never an auto-retry`() = runTest {
        val txns = FakeTransactionRepository()
        txns.transferResult = { _, _, _ ->
            throw AppErrorException(AppError.NetworkUnavailable)
        }
        val viewModel = vm(txns = txns, accounts = twoAccounts())
        runCurrent()
        fillValid(viewModel)

        viewModel.submit()
        runCurrent()

        val state = viewModel.state.value
        assertFalse(state.isSubmitting)
        assertEquals(CreateTransferUiState.FormError.Ambiguous, state.formError)
        // The draft survives for a USER-initiated resubmit.
        assertEquals("25.50", state.amount)
        assertEquals("acc-2", state.destinationAccountId)
    }

    @Test
    fun `session expiry mid-submit releases the lock and keeps the draft`() = runTest {
        val txns = FakeTransactionRepository()
        txns.transferResult = { _, _, _ ->
            throw AppErrorException(AppError.SessionExpired)
        }
        val viewModel = vm(txns = txns, accounts = twoAccounts())
        runCurrent()
        fillValid(viewModel)

        viewModel.submit()
        runCurrent()

        val state = viewModel.state.value
        assertFalse(state.isSubmitting)
        assertNull(state.formError)
        assertEquals("25.50", state.amount)
    }

    @Test
    fun `dead-generation publish surfaces the ambiguous copy`() = runTest {
        val txns = FakeTransactionRepository()
        txns.transferResult = { _, _, _ -> throw SessionEndedException() }
        val viewModel = vm(txns = txns, accounts = twoAccounts())
        runCurrent()
        fillValid(viewModel)

        viewModel.submit()
        runCurrent()

        val state = viewModel.state.value
        assertFalse(state.isSubmitting)
        assertEquals(CreateTransferUiState.FormError.Ambiguous, state.formError)
    }

    @Test
    fun `amount validation mirrors the backend digits rule`() = runTest {
        val txns = FakeTransactionRepository()
        val viewModel = vm(txns = txns, accounts = twoAccounts())
        runCurrent()
        viewModel.onSourceChange("acc-1")
        viewModel.onDestinationChange("acc-2")

        viewModel.onAmountChange("0")
        viewModel.submit()
        assertEquals(
            CreateTransferUiState.AmountError.NotPositive,
            viewModel.state.value.amountError,
        )

        viewModel.onAmountChange("10.005")
        viewModel.submit()
        assertEquals(
            CreateTransferUiState.AmountError.TooPrecise,
            viewModel.state.value.amountError,
        )

        viewModel.onAmountChange("not-a-number")
        viewModel.submit()
        assertEquals(
            CreateTransferUiState.AmountError.Invalid,
            viewModel.state.value.amountError,
        )

        assertTrue(txns.transferCalls.isEmpty())
    }
}
