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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class CreateTransactionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(
        prefillAccountId: String? = null,
        offsetTransactionId: String? = null,
        txns: FakeTransactionRepository = FakeTransactionRepository(),
        accounts: FakeAccountRepository = FakeAccountRepository(),
        cats: FakeCategoryRepository = FakeCategoryRepository(),
    ) = CreateTransactionViewModel(
        savedStateHandle = SavedStateHandle(
            buildMap {
                prefillAccountId?.let { put("accountId", it) }
                offsetTransactionId?.let { put("offsetTransactionId", it) }
            },
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
    fun `archived accounts are excluded from the picker`() = runTest {
        // M19 (REQ-BE-006): archived stays in ledger/balances but cannot
        // receive new entries.
        val accounts = FakeAccountRepository()
        accounts.listResult = {
            listOf(
                FakeAccountRepository.account(id = "acc-dead", archived = true),
                FakeAccountRepository.account(id = "acc-live"),
            )
        }
        val viewModel = vm(prefillAccountId = "acc-dead", accounts = accounts)

        val state = viewModel.state.value
        assertEquals(listOf("acc-live"), state.accounts.map { it.id })
        assertEquals("acc-live", state.accountId)
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
        viewModel.onDescriptionChange("october")

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
        assertEquals("october", call.description)
    }

    @Test
    fun `note input is bounded at the server's 255`() = runTest {
        val viewModel = vm(accounts = accountBacked())
        viewModel.onDescriptionChange("x".repeat(300))
        assertEquals(CreateTransactionUiState.MAX_DESCRIPTION_LENGTH, viewModel.state.value.description.length)
    }

    @Test
    fun `note cap never leaves a dangling surrogate half`() = runTest {
        val viewModel = vm(accounts = accountBacked())
        // 254 ASCII units + one emoji (2 UTF-16 units) = 256; the 255 cap
        // must drop the whole emoji rather than persist a lone high surrogate
        viewModel.onDescriptionChange("a".repeat(254) + "😊")
        val description = viewModel.state.value.description
        assertEquals(254, description.length)
        assertFalse(description.last().isHighSurrogate())
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
        txns.createResult = { _, _, _, _, _, _, _ -> gate.await() }
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
        txns.createResult = { _, _, _, _, _, _, _ ->
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
        txns.createResult = { _, _, _, _, _, _, _ -> throw AppErrorException(AppError.NotFound) }
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
        txns.createResult = { _, _, _, _, _, _, _ -> throw AppErrorException(AppError.NetworkUnavailable) }
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
        txns.createResult = { _, _, _, _, _, _, _ -> throw SessionEndedException() }
        val viewModel = vm(accounts = accountBacked(), txns = txns)
        fillValid(viewModel)

        viewModel.submit()

        val state = viewModel.state.value
        assertFalse(state.isSubmitting)
        assertEquals(CreateTransactionUiState.CreateTxnFormError.Ambiguous, state.formError)
        assertEquals("25.50", state.amount) // draft preserved

        txns.createResult = { _, _, _, _, _, _, _ -> FakeTransactionRepository.transaction(id = "retry-1") }
        viewModel.submit()

        assertEquals(2, txns.createCalls.size)
    }

    // ── M11: user-selectable occurrence date ─────────────────────────────

    @Test
    fun `the initial date is LocalDate now — a date never null in the form`() = runTest {
        val viewModel = vm(accounts = accountBacked())

        assertEquals(LocalDate.now(), viewModel.state.value.occurredOn)
    }

    @Test
    fun `onDateChange updates the draft date`() = runTest {
        val viewModel = vm(accounts = accountBacked())
        val picked = LocalDate.of(2024, 2, 29) // leap-day — no normalization

        viewModel.onDateChange(picked)

        assertEquals(picked, viewModel.state.value.occurredOn)
    }

    @Test
    fun `submitting sends the selected date to the repository`() = runTest {
        val txns = FakeTransactionRepository()
        val viewModel = vm(accounts = accountBacked(), txns = txns)
        fillValid(viewModel)
        viewModel.onDateChange(LocalDate.of(2025, 1, 1))

        viewModel.submit()
        runCurrent()

        assertEquals(LocalDate.of(2025, 1, 1), txns.createCalls.single().occurredOn)
    }

    @Test
    fun `submitting without a picker change sends the default today`() = runTest {
        val txns = FakeTransactionRepository()
        val viewModel = vm(accounts = accountBacked(), txns = txns)
        fillValid(viewModel)

        viewModel.submit()
        runCurrent()

        assertEquals(LocalDate.now(), txns.createCalls.single().occurredOn)
    }

    @Test
    fun `a picked date survives an ambiguous write — draft kept for retry`() = runTest {
        val txns = FakeTransactionRepository()
        txns.createResult = { _, _, _, _, _, _, _ -> throw AppErrorException(AppError.NetworkUnavailable) }
        val viewModel = vm(accounts = accountBacked(), txns = txns)
        fillValid(viewModel)
        viewModel.onDateChange(LocalDate.of(2024, 12, 31))

        viewModel.submit()
        runCurrent()

        val state = viewModel.state.value
        assertEquals(CreateTransactionUiState.CreateTxnFormError.Ambiguous, state.formError)
        assertEquals(LocalDate.of(2024, 12, 31), state.occurredOn)
    }

    // ── M20: offsetting entry (PRODUCT §21) ──────────────────────────

    private fun offsetSource(
        id: String = "txn-src",
        accountId: String = "acc-1",
        type: TransactionType = TransactionType.EXPENSE,
        amount: String = "25.50",
        currency: String = "VND",
        category: String? = "FOOD",
    ) = FakeTransactionRepository().apply {
        findByIdResult = { requested ->
            FakeTransactionRepository.transaction(
                id = requested,
                accountId = accountId,
                type = type,
                amount = amount,
                currency = currency,
                category = category,
            )
        }
    }

    @Test
    fun `offset prefills the inverse type and mirrors account amount category`() = runTest {
        // §21: EXPENSE source → prefilled INCOME, same account/amount/
        // currency(category) — the offset row is a real new transaction.
        val txns = offsetSource(type = TransactionType.EXPENSE, amount = "25.50", category = "FOOD")
        val viewModel = vm(
            offsetTransactionId = "txn-src",
            txns = txns,
            accounts = accountBacked(id = "acc-1"),
        )

        val state = viewModel.state.value
        assertFalse(state.optionsLoading)
        assertEquals(TransactionType.INCOME, state.type) // inverse
        assertEquals("acc-1", state.accountId)
        assertEquals("25.50", state.amount)
        assertEquals("FOOD", state.category)
        assertEquals("txn-src", state.offsetSourceId)
        assertEquals(listOf("txn-src"), txns.findByIdCalls)
    }

    @Test
    fun `an income source prefills expense — the arithmetic inverse both ways`() = runTest {
        val txns = offsetSource(type = TransactionType.INCOME)
        val viewModel = vm(
            offsetTransactionId = "txn-src",
            txns = txns,
            accounts = accountBacked(),
        )

        assertEquals(TransactionType.EXPENSE, viewModel.state.value.type)
    }

    @Test
    fun `an uncategorized source prefills no category`() = runTest {
        val txns = offsetSource(category = null)
        val viewModel = vm(
            offsetTransactionId = "txn-src",
            txns = txns,
            accounts = accountBacked(),
        )

        assertNull(viewModel.state.value.category)
        assertEquals("txn-src", viewModel.state.value.offsetSourceId)
    }

    @Test
    fun `offset wins over a stale accountId route arg`() = runTest {
        val accounts = FakeAccountRepository()
        accounts.listResult = {
            listOf(
                FakeAccountRepository.account(id = "acc-1"),
                FakeAccountRepository.account(id = "acc-2"),
            )
        }
        val txns = offsetSource(accountId = "acc-2")
        val viewModel = vm(
            prefillAccountId = "acc-1",
            offsetTransactionId = "txn-src",
            txns = txns,
            accounts = accounts,
        )

        assertEquals("acc-2", viewModel.state.value.accountId)
    }

    @Test
    fun `a transfer or unknown source refuses the prefill honestly`() = runTest {
        listOf(
            TransactionType.TRANSFER_IN,
            TransactionType.TRANSFER_OUT,
            TransactionType.UNKNOWN,
        ).forEach { type ->
            val txns = offsetSource(type = type)
            val viewModel = vm(
                offsetTransactionId = "txn-src",
                txns = txns,
                accounts = accountBacked(),
            )

            val state = viewModel.state.value
            assertTrue("$type must fail options, not prefill", state.optionsError)
            assertFalse(state.optionsLoading)
            assertNull(state.offsetSourceId)
        }
    }

    @Test
    fun `an archived source account refuses the prefill`() = runTest {
        // The source's account is archived → it never enters the picker,
        // so there is no live account to post the offset against.
        val accounts = FakeAccountRepository()
        accounts.listResult = {
            listOf(
                FakeAccountRepository.account(id = "acc-dead", archived = true),
                FakeAccountRepository.account(id = "acc-live"),
            )
        }
        val txns = offsetSource(accountId = "acc-dead")
        val viewModel = vm(offsetTransactionId = "txn-src", txns = txns, accounts = accounts)

        assertTrue(viewModel.state.value.optionsError)
        assertNull(viewModel.state.value.offsetSourceId)
    }

    @Test
    fun `a missing source row fails options honestly`() = runTest {
        val txns = FakeTransactionRepository()
        txns.findByIdResult = { throw AppErrorException(AppError.NotFound) }
        val viewModel = vm(
            offsetTransactionId = "txn-gone",
            txns = txns,
            accounts = accountBacked(),
        )

        assertTrue(viewModel.state.value.optionsError)
        assertFalse(viewModel.state.value.optionsLoading)
    }

    @Test
    fun `dead-generation on the source lookup arms resume and prefills after`() = runTest {
        // EXT-MAJOR-1: the offset source fetch is generation-guarded like
        // every load — a stale response never publishes, and the same
        // resume contract re-runs the whole load.
        var dead = true
        val txns = FakeTransactionRepository()
        txns.findByIdResult = { id ->
            if (dead) {
                throw SessionEndedException()
            } else {
                FakeTransactionRepository.transaction(id = id, type = TransactionType.EXPENSE)
            }
        }
        val viewModel = vm(
            offsetTransactionId = "txn-src",
            txns = txns,
            accounts = accountBacked(),
        )

        assertFalse(viewModel.state.value.optionsLoading)
        assertNull(viewModel.state.value.offsetSourceId)

        dead = false
        viewModel.onVisible()

        val state = viewModel.state.value
        assertEquals("txn-src", state.offsetSourceId)
        assertEquals(TransactionType.INCOME, state.type)
    }

    @Test
    fun `offset submit posts the inverse entry through the canonical create`() = runTest {
        // The whole slice is an ordinary POST /api/transactions — the
        // repository call carries the prefilled inverse verbatim.
        val txns = offsetSource(type = TransactionType.EXPENSE, amount = "25.50", category = "FOOD")
        val viewModel = vm(
            offsetTransactionId = "txn-src",
            txns = txns,
            accounts = accountBacked(id = "acc-1", currency = "VND"),
        )

        viewModel.submit()
        runCurrent()

        val call = txns.createCalls.single()
        assertEquals("acc-1", call.accountId)
        assertEquals(TransactionType.INCOME, call.type)
        assertEquals(BigDecimal("25.50"), call.amount.amount)
        assertEquals("VND", call.amount.currency.code)
        assertEquals("FOOD", call.category)
    }

    @Test
    fun `a loadOptions re-run refreshes options without clobbering edits`() = runTest {
        // The account-404 picker refresh calls loadOptions() again —
        // edits made after the offset prefill must survive the re-run
        // (the prefill applies exactly once).
        val txns = offsetSource(type = TransactionType.EXPENSE, amount = "25.50", category = "FOOD")
        val viewModel = vm(
            offsetTransactionId = "txn-src",
            txns = txns,
            accounts = accountBacked(),
        )
        viewModel.onAmountChange("10.00")
        viewModel.onTypeChange(TransactionType.EXPENSE) // user flipped the inverse back

        viewModel.loadOptions()

        val state = viewModel.state.value
        assertEquals("10.00", state.amount)
        assertEquals(TransactionType.EXPENSE, state.type)
        assertEquals("txn-src", state.offsetSourceId)
        assertEquals(1, txns.findByIdCalls.size) // the source is not re-fetched
    }

    @Test
    fun `a re-run never substitutes a vanished offset account`() = runTest {
        // EXT-M20-1: the offset banner promises a compensating entry for
        // THE source row — a source account that left the picker must
        // leave the field empty (Required on submit), never silently
        // retarget the first live account. The draft fields survive.
        val accounts = FakeAccountRepository()
        var live = true
        accounts.listResult = {
            if (live) {
                listOf(FakeAccountRepository.account(id = "acc-1"))
            } else {
                listOf(FakeAccountRepository.account(id = "acc-9"))
            }
        }
        val txns = offsetSource(accountId = "acc-1")
        val viewModel = vm(offsetTransactionId = "txn-src", txns = txns, accounts = accounts)
        viewModel.onAmountChange("10.00")

        live = false
        viewModel.loadOptions()

        val state = viewModel.state.value
        assertNull(state.accountId) // dropped — NOT silently retargeted to acc-9
        assertEquals("10.00", state.amount) // draft untouched
        assertEquals("txn-src", state.offsetSourceId)

        // A submit asks for a deliberate choice — nothing posts.
        viewModel.submit()
        runCurrent()
        assertEquals(
            CreateTransactionUiState.AccountError.Required,
            viewModel.state.value.accountError,
        )
        assertTrue(txns.createCalls.isEmpty())
    }

    @Test
    fun `a user-picked live account survives an offset re-run`() = runTest {
        // EXT-M20-1 companion: the no-substitute rule only fires when
        // the account actually vanished — a deliberate user choice that
        // is still a live picker entry must be preserved.
        val accounts = FakeAccountRepository()
        accounts.listResult = {
            listOf(
                FakeAccountRepository.account(id = "acc-1"),
                FakeAccountRepository.account(id = "acc-2"),
            )
        }
        val txns = offsetSource(accountId = "acc-1")
        val viewModel = vm(offsetTransactionId = "txn-src", txns = txns, accounts = accounts)
        viewModel.onAccountChange("acc-2")

        viewModel.loadOptions()

        assertEquals("acc-2", viewModel.state.value.accountId)
    }

    @Test
    fun `a stale picker list cannot smuggle a freshly-archived source account`() = runTest {
        // EXT-M20-2: the picker list is cache-first — an account
        // archived in ANOTHER session stays "live" in the cached list.
        // The offset gate must re-resolve the source account's archived
        // flag authoritatively rather than trusting the stale list.
        val accounts = FakeAccountRepository()
        accounts.listResult = { listOf(FakeAccountRepository.account(id = "acc-1")) } // stale: not archived
        accounts.findByIdResult = { id, refresh ->
            if (refresh) FakeAccountRepository.account(id = id, archived = true)
            else FakeAccountRepository.account(id = id)
        }
        val txns = offsetSource(accountId = "acc-1")
        val viewModel = vm(offsetTransactionId = "txn-src", txns = txns, accounts = accounts)

        val state = viewModel.state.value
        assertTrue(state.optionsError)
        assertNull(state.offsetSourceId)
        // and the authoritative check did run a fresh resolve
        assertTrue(accounts.findByIdCalls.any { it.first == "acc-1" && it.second })
    }

    @Test
    fun `a refused source prefills cleanly on retry when it resolves`() = runTest {
        // Retry from optionsError re-fetches the source (offsetSourceId
        // was never set) and prefills exactly once when it resolves.
        var fail = true
        val txns = FakeTransactionRepository()
        txns.findByIdResult = { id ->
            if (fail) throw AppErrorException(AppError.NetworkUnavailable)
            else FakeTransactionRepository.transaction(id = id, type = TransactionType.INCOME)
        }
        val viewModel = vm(
            offsetTransactionId = "txn-src",
            txns = txns,
            accounts = accountBacked(),
        )
        assertTrue(viewModel.state.value.optionsError)

        fail = false
        viewModel.loadOptions()

        val state = viewModel.state.value
        assertFalse(state.optionsError)
        assertEquals(TransactionType.EXPENSE, state.type)
        assertEquals("txn-src", state.offsetSourceId)
    }

    @Test
    fun `offset mode still locks double-taps and never auto-replays`() = runTest {
        val txns = offsetSource()
        val gate = CompletableDeferred<com.spendwise.android.core.model.Transaction>()
        var createCalls = 0
        txns.createResult = { _, _, _, _, _, _, _ -> createCalls++; gate.await() }
        val viewModel = vm(
            offsetTransactionId = "txn-src",
            txns = txns,
            accounts = accountBacked(),
        )

        viewModel.submit()
        viewModel.submit() // locked while the offset post is in flight

        gate.complete(FakeTransactionRepository.transaction())
        runCurrent()
        assertEquals(1, createCalls)
    }
}

// ── F2: clientRef idempotency ─────────────────────────────────────

class CreateTransactionClientRefTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(
        txns: FakeTransactionRepository = FakeTransactionRepository(),
        accounts: FakeAccountRepository = FakeAccountRepository(),
        cats: FakeCategoryRepository = FakeCategoryRepository(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) = CreateTransactionViewModel(
        savedStateHandle = savedStateHandle,
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

    @Test
    fun `submit sends a generated clientRef`() = runTest {
        val txns = FakeTransactionRepository()
        val viewModel = vm(txns = txns, accounts = accountBacked())
        viewModel.onAmountChange("25.50")

        viewModel.submit()
        runCurrent()

        val ref = txns.createCalls.single().clientRef
        assertNotNull(ref)
        assertTrue(ref!!.isNotBlank())
    }

    @Test
    fun `retry after an ambiguous failure reuses the same clientRef`() = runTest {
        val txns = FakeTransactionRepository()
        val viewModel = vm(txns = txns, accounts = accountBacked())
        viewModel.onAmountChange("25.50")
        txns.createResult = { _, _, _, _, _, _, _ -> throw AppErrorException(AppError.NetworkUnavailable) }

        viewModel.submit()
        runCurrent()
        txns.createResult = { _, _, _, _, _, _, _ -> FakeTransactionRepository.transaction(id = "landed-1") }
        viewModel.submit()
        runCurrent()

        assertEquals(2, txns.createCalls.size)
        assertEquals(txns.createCalls[0].clientRef, txns.createCalls[1].clientRef)
    }

    @Test
    fun `a new ViewModel instance mints a fresh clientRef`() = runTest {
        val firstTxns = FakeTransactionRepository()
        val secondTxns = FakeTransactionRepository()
        val first = vm(txns = firstTxns, accounts = accountBacked())
        val second = vm(txns = secondTxns, accounts = accountBacked())
        first.onAmountChange("25.50")
        second.onAmountChange("25.50")

        first.submit(); runCurrent()
        second.submit(); runCurrent()

        assertNotEquals(firstTxns.createCalls.single().clientRef, secondTxns.createCalls.single().clientRef)
    }

    @Test
    fun `process death keeps the pending intent's clientRef via SavedStateHandle`() = runTest {
        val handle = SavedStateHandle()
        val firstTxns = FakeTransactionRepository()
        val first = vm(txns = firstTxns, accounts = accountBacked(), savedStateHandle = handle)
        first.onAmountChange("25.50")
        firstTxns.createResult = { _, _, _, _, _, _, _ -> throw AppErrorException(AppError.NetworkUnavailable) }
        first.submit()
        runCurrent()

        // Recreated VM (as after process death) inherits the same handle.
        val secondTxns = FakeTransactionRepository()
        val second = vm(txns = secondTxns, accounts = accountBacked(), savedStateHandle = handle)
        second.onAmountChange("25.50")
        second.submit()
        runCurrent()

        assertEquals(firstTxns.createCalls.single().clientRef, secondTxns.createCalls.single().clientRef)
    }

    @Test
    fun `a 409 conflict surfaces the ambiguous form error`() = runTest {
        val txns = FakeTransactionRepository()
        txns.createResult = { _, _, _, _, _, _, _ -> throw AppErrorException(AppError.Conflict) }
        val viewModel = vm(txns = txns, accounts = accountBacked())
        viewModel.onAmountChange("25.50")

        viewModel.submit()
        runCurrent()

        val state = viewModel.state.value
        assertFalse(state.isSubmitting)
        assertEquals(CreateTransactionUiState.CreateTxnFormError.Ambiguous, state.formError)
    }

    @Test
    fun `rapid double taps still send one request with one clientRef`() = runTest {
        val txns = FakeTransactionRepository()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        txns.createResult = { _, _, _, _, _, _, _ -> gate.await(); FakeTransactionRepository.transaction() }
        val viewModel = vm(txns = txns, accounts = accountBacked())
        viewModel.onAmountChange("25.50")

        viewModel.submit()
        viewModel.submit()
        viewModel.submit()
        gate.complete(Unit)
        runCurrent()

        assertEquals(1, txns.createCalls.size)
    }
}
