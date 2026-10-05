package com.spendwise.android.feature.transactions

import androidx.lifecycle.SavedStateHandle
import com.spendwise.android.core.model.TransactionType
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.fakes.FakeAccountRepository
import com.spendwise.android.fakes.FakeCategoryRepository
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

class CreateTransactionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(
        prefillAccountId: String? = null,
        txns: FakeTransactionRepository = FakeTransactionRepository(),
        accounts: FakeAccountRepository = FakeAccountRepository(),
        cats: FakeCategoryRepository = FakeCategoryRepository(),
    ) = CreateTransactionViewModel(
        savedStateHandle = SavedStateHandle(
            prefillAccountId?.let { mapOf("accountId" to it) } ?: emptyMap(),
        ),
        transactionRepository = txns,
        accountRepository = accounts,
        categoryRepository = cats,
    )

    private fun accountBacked(
        id: String = "acc-1",
        currency: String = "VND",
    ) = FakeAccountRepository().apply {
        listResult = { _ -> listOf(FakeAccountRepository.account(id = id, currency = currency)) }
    }

    private suspend fun fillValid(viewModel: CreateTransactionViewModel) {
        viewModel.onAmountChange("25.50")
    }

    @Test
    fun `defaults to Expense and prefills the first account with locked currency`() = runTest {
        val accounts = accountBacked(id = "acc-9", currency = "USD")
        val viewModel = vm(accounts = accounts)

        val state = viewModel.state.value
        assertEquals(TransactionType.EXPENSE, state.type)
        assertEquals("acc-9", state.accountId)
        assertEquals("USD", state.selectedAccount?.balance?.currency?.code)
        assertEquals(5, state.categories.size)
        assertFalse(state.optionsLoading)
    }

    @Test
    fun `route prefill wins over the first-account default`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = {
            listOf(
                FakeAccountRepository.account(id = "acc-1"),
                FakeAccountRepository.account(id = "acc-2"),
            )
        }
        val viewModel = vm(prefillAccountId = "acc-2", accounts = accounts)

        assertEquals("acc-2", viewModel.state.value.accountId)
    }

    @Test
    fun `unknown prefill falls back — a foreign id is never submitted`() = runTest {
        val accounts = accountBacked(id = "acc-1")
        val viewModel = vm(prefillAccountId = "not-mine", accounts = accounts)

        assertEquals("acc-1", viewModel.state.value.accountId)
    }

    @Test
    fun `transfer types are ignored — the public form only offers income and expense`() = runTest {
        val viewModel = vm(accounts = accountBacked())

        viewModel.onTypeChange(TransactionType.TRANSFER_IN)
        viewModel.onTypeChange(TransactionType.TRANSFER_OUT)
        viewModel.onTypeChange(TransactionType.UNKNOWN)
        viewModel.onTypeChange(TransactionType.INCOME)

        assertEquals(TransactionType.INCOME, viewModel.state.value.type)
    }

    @Test
    fun `submit requires an account and a positive two-fraction amount`() = runTest {
        val noAccounts = vm() // zero accounts → accountId stays null
        noAccounts.submit()

        val blocked = noAccounts.state.value
        assertEquals(CreateTransactionUiState.AccountError.Required, blocked.accountError)
        assertEquals(CreateTransactionUiState.AmountError.Required, blocked.amountError)

        val viewModel = vm(accounts = accountBacked())
        viewModel.onAmountChange("abc")
        viewModel.submit()
        assertEquals(CreateTransactionUiState.AmountError.Invalid, viewModel.state.value.amountError)

        viewModel.onAmountChange("0")
        viewModel.submit()
        assertEquals(CreateTransactionUiState.AmountError.NotPositive, viewModel.state.value.amountError)

        viewModel.onAmountChange("-5")
        viewModel.submit()
        assertEquals(CreateTransactionUiState.AmountError.NotPositive, viewModel.state.value.amountError)

        viewModel.onAmountChange("1.234")
        viewModel.submit()
        assertEquals(CreateTransactionUiState.AmountError.TooPrecise, viewModel.state.value.amountError)

        viewModel.onAmountChange("123456789012345678.00") // 18 integer digits
        viewModel.submit()
        assertEquals(CreateTransactionUiState.AmountError.TooLarge, viewModel.state.value.amountError)

        assertNull(viewModel.state.value.formError)
    }

    @Test
    fun `comma decimal input normalizes and submits BigDecimal-exact`() = runTest {
        val txns = FakeTransactionRepository()
        val viewModel = vm(accounts = accountBacked(currency = "VND"), txns = txns)
        viewModel.onTypeChange(TransactionType.INCOME)
        viewModel.onAmountChange("12,50")
        viewModel.onCategoryChange("SALARY")
        viewModel.onHintChange("october")

        val events = mutableListOf<CreateTransactionViewModel.Event>()
        val job = launch { viewModel.events.collect { events += it } }
        runCurrent() // register the collector
        viewModel.submit()
        runCurrent() // consume the emitted event
        job.cancel()

        assertEquals(listOf(CreateTransactionViewModel.Event.Created), events)
        val call = txns.createCalls.single()
        assertEquals("acc-1", call.accountId)
        assertEquals(TransactionType.INCOME, call.type)
        assertEquals(BigDecimal("12.50"), call.amount.amount)
        assertEquals("VND", call.amount.currency.code)
        assertEquals("SALARY", call.category)
        assertEquals("october", call.hint)
    }

    @Test
    fun `no account picked surfaces the inline required error`() = runTest {
        val accounts = FakeAccountRepository() // zero accounts
        val viewModel = vm(accounts = accounts)
        viewModel.onAmountChange("10")

        viewModel.submit()

        assertEquals(CreateTransactionUiState.AccountError.Required, viewModel.state.value.accountError)
        assertTrue(viewModel.state.value.formError == null)
    }

    @Test
    fun `double-tap during flight sends exactly one create`() = runTest {
        val txns = FakeTransactionRepository()
        val gate = CompletableDeferred<com.spendwise.android.core.model.Transaction>()
        txns.createResult = { _, _, _, _, _ -> gate.await() }
        val viewModel = vm(accounts = accountBacked(), txns = txns)
        fillValid(viewModel)

        viewModel.submit()
        viewModel.submit() // locked — must not post again

        gate.complete(FakeTransactionRepository.transaction())
        assertEquals(1, txns.createCalls.size)
    }

    @Test
    fun `server field errors land on their fields`() = runTest {
        val txns = FakeTransactionRepository()
        txns.createResult = { _, _, _, _, _ ->
            throw AppErrorException(
                AppError.Validation(mapOf("amount" to "must be greater than 0")),
            )
        }
        val viewModel = vm(accounts = accountBacked(), txns = txns)
        fillValid(viewModel)

        viewModel.submit()

        val state = viewModel.state.value
        assertEquals(
            CreateTransactionUiState.AmountError.Server("must be greater than 0"),
            state.amountError,
        )
        assertNull(state.formError)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun `account 404 refreshes the picker and reports honestly`() = runTest {
        val accounts = accountBacked()
        val txns = FakeTransactionRepository()
        txns.createResult = { _, _, _, _, _ -> throw AppErrorException(AppError.NotFound) }
        val viewModel = vm(accounts = accounts, txns = txns)
        fillValid(viewModel)

        viewModel.submit()

        val state = viewModel.state.value
        assertEquals(
            CreateTransactionUiState.CreateTxnFormError.AccountUnavailable,
            state.formError,
        )
        assertEquals(2, accounts.listCalls.size) // options refreshed
    }

    @Test
    fun `network failure is the ambiguous form error — never auto-retried`() = runTest {
        val txns = FakeTransactionRepository()
        txns.createResult = { _, _, _, _, _ -> throw AppErrorException(AppError.NetworkUnavailable) }
        val viewModel = vm(accounts = accountBacked(), txns = txns)
        fillValid(viewModel)

        viewModel.submit()

        val state = viewModel.state.value
        assertEquals(CreateTransactionUiState.CreateTxnFormError.Ambiguous, state.formError)
        assertFalse(state.isSubmitting)
        assertEquals(1, txns.createCalls.size) // no retry happened
    }

    @Test
    fun `options load failure is the retryable options state`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> throw AppErrorException(AppError.Server(503)) }
        val viewModel = vm(accounts = accounts)

        assertTrue(viewModel.state.value.optionsError)
        assertFalse(viewModel.state.value.optionsLoading)

        accounts.listResult = { _ -> listOf(FakeAccountRepository.account(id = "acc-1")) }
        viewModel.loadOptions()

        assertFalse(viewModel.state.value.optionsError)
        assertEquals("acc-1", viewModel.state.value.accountId)
    }

    @Test
    fun `SessionExpired during options load reloads on resume — draft kept`() = runTest {
        val accounts = FakeAccountRepository()
        var expired = true
        accounts.listResult = {
            if (expired) {
                throw AppErrorException(AppError.SessionExpired)
            } else {
                listOf(FakeAccountRepository.account(id = "acc-1"))
            }
        }
        val viewModel = vm(accounts = accounts)
        viewModel.onAmountChange("7.00")

        assertFalse(viewModel.state.value.optionsLoading)
        assertTrue(viewModel.state.value.accounts.isEmpty())
        assertEquals("7.00", viewModel.state.value.amount) // draft intact

        expired = false
        viewModel.onVisible()

        assertEquals("acc-1", viewModel.state.value.accountId)
        assertEquals("7.00", viewModel.state.value.amount)
    }

    @Test
    fun `dead-generation during options load clears the skeleton and reloads on resume`() = runTest {
        // EXT-MAJOR-1 site 6: a dead-generation response must clear
        // optionsLoading and arm the resume retry — not sit on the
        // skeleton forever.
        val accounts = FakeAccountRepository()
        var dead = true
        accounts.listResult = {
            if (dead) {
                throw SessionEndedException()
            } else {
                listOf(FakeAccountRepository.account(id = "acc-1"))
            }
        }
        val viewModel = vm(accounts = accounts)
        viewModel.onAmountChange("7.00")

        assertFalse(viewModel.state.value.optionsLoading)
        assertTrue(viewModel.state.value.accounts.isEmpty())
        assertEquals("7.00", viewModel.state.value.amount)

        dead = false
        viewModel.onVisible()

        assertEquals("acc-1", viewModel.state.value.accountId)
        assertEquals("7.00", viewModel.state.value.amount)
    }

    @Test
    fun `dead-generation submit releases the lock and marks the write ambiguous`() = runTest {
        // EXT-MAJOR-1 site 7: a response landing after the generation
        // boundary throws SessionEndedException — the POST almost
        // certainly committed server-side, so the lock must release
        // AND the outcome must be marked ambiguous (not silently
        // editable). The preserved draft resubmits only by hand.
        val txns = FakeTransactionRepository()
        txns.createResult = { _, _, _, _, _ -> throw SessionEndedException() }
        val viewModel = vm(accounts = accountBacked(), txns = txns)
        fillValid(viewModel)

        viewModel.submit()

        val state = viewModel.state.value
        assertFalse(state.isSubmitting)
        assertEquals(CreateTransactionUiState.CreateTxnFormError.Ambiguous, state.formError)
        assertEquals("25.50", state.amount) // draft preserved

        txns.createResult = { _, _, _, _, _ -> FakeTransactionRepository.transaction(id = "retry-1") }
        viewModel.submit()

        assertEquals(2, txns.createCalls.size)
    }
}
