package com.spendwise.android.feature.planning

import androidx.lifecycle.SavedStateHandle
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.model.RecurringFrequency
import com.spendwise.android.core.model.TransactionType
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.fakes.FakeAccountRepository
import com.spendwise.android.fakes.FakeCategoryRepository
import com.spendwise.android.fakes.FakeRecurringRepository
import com.spendwise.android.fakes.MainDispatcherRule
import java.math.BigDecimal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Recurring (S15–S17) ViewModels — list load/empty/error/retry, the
 * create form's validation + double-submit lock + no auto-retry, and
 * the detail deactivate flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecurringViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun accountsWithTwo() = FakeAccountRepository().apply {
        listResult = { _ ->
            listOf(
                FakeAccountRepository.account(id = "acc-1", name = "Wallet", currency = "VND"),
                FakeAccountRepository.account(id = "acc-2", name = "Bank", currency = "USD"),
            )
        }
    }

    // ── List ────────────────────────────────────────────────────────

    @Test
    fun `list shows content with account names resolved`() = runTest {
        val recurring = FakeRecurringRepository().apply {
            listResult = { listOf(FakeRecurringRepository.template(accountId = "acc-1")) }
        }
        val vm = RecurringListViewModel(recurring, accountsWithTwo())
        runCurrent()
        val content = vm.state.value as RecurringListState.Content
        assertEquals(1, content.templates.size)
        assertEquals("Wallet", content.accountNames["acc-1"])
    }

    @Test
    fun `empty list maps to Empty`() = runTest {
        val vm = RecurringListViewModel(FakeRecurringRepository(), FakeAccountRepository())
        runCurrent()
        assertEquals(RecurringListState.Empty, vm.state.value)
    }

    @Test
    fun `io failure maps to Network error and retry recovers`() = runTest {
        val recurring = FakeRecurringRepository().apply {
            listResult = { throw AppErrorException(AppError.NetworkUnavailable) }
        }
        val vm = RecurringListViewModel(recurring, FakeAccountRepository())
        runCurrent()
        assertEquals(RecurringListState.Error(PlanningError.Network), vm.state.value)

        recurring.listResult = { listOf(FakeRecurringRepository.template()) }
        vm.refresh()
        runCurrent()
        assertTrue(vm.state.value is RecurringListState.Content)
    }

    @Test
    fun `session end mid-load publishes nothing`() = runTest {
        val recurring = FakeRecurringRepository().apply {
            listResult = { throw SessionEndedException() }
        }
        val vm = RecurringListViewModel(recurring, FakeAccountRepository())
        runCurrent()
        assertFalse(vm.state.value is RecurringListState.Error)
    }

    // ── Create ──────────────────────────────────────────────────────

    private fun createVm(
        recurring: FakeRecurringRepository = FakeRecurringRepository(),
        accounts: FakeAccountRepository = accountsWithTwo(),
        categories: FakeCategoryRepository = FakeCategoryRepository(),
    ) = CreateRecurringViewModel(recurring, accounts, categories)

    private suspend fun fillValid(vm: CreateRecurringViewModel) {
        vm.onAccountChange("acc-1")
        vm.onDirectionChange(TransactionType.EXPENSE)
        vm.onAmountChange("45.50")
        vm.onFrequencyChange(RecurringFrequency.WEEKLY)
        vm.onNextDueDateChange("2026-10-10")
    }

    @Test
    fun `create validates required fields before any remote call`() = runTest {
        val recurring = FakeRecurringRepository()
        val vm = createVm(recurring)
        vm.loadOptions()
        runCurrent()
        vm.submit()
        runCurrent()
        val s = vm.state.value
        assertTrue(s.amountError != null)
        assertTrue(s.dateError != null)
        assertTrue(recurring.createCalls.isEmpty())
    }

    @Test
    fun `archived accounts are excluded from the template account picker`() = runTest {
        // M19 (REQ-BE-006): a recurring template generates future ledger
        // entries — an archived account cannot be its target.
        val accounts = FakeAccountRepository().apply {
            listResult = { _ ->
                listOf(
                    FakeAccountRepository.account(id = "acc-dead", archived = true),
                    FakeAccountRepository.account(id = "acc-live", currency = "USD"),
                )
            }
        }
        val vm = createVm(accounts = accounts)
        vm.loadOptions()
        runCurrent()

        val state = vm.state.value
        assertEquals(listOf("acc-live"), state.accounts.map { it.id })
        assertEquals("acc-live", state.accountId)
    }

    @Test
    fun `create locks the currency to the selected account`() = runTest {
        val recurring = FakeRecurringRepository()
        val vm = createVm(recurring)
        vm.loadOptions()
        runCurrent()
        vm.onAccountChange("acc-2")
        vm.onAmountChange("10")
        vm.onFrequencyChange(RecurringFrequency.MONTHLY)
        vm.onNextDueDateChange("2026-10-10")
        vm.submit()
        runCurrent()
        assertEquals("USD", recurring.createCalls.single().amount.currency.code)
    }

    @Test
    fun `create rejects non-positive amount without a remote call`() = runTest {
        val recurring = FakeRecurringRepository()
        val vm = createVm(recurring)
        vm.loadOptions()
        runCurrent()
        vm.onAccountChange("acc-1")
        vm.onAmountChange("0")
        vm.onFrequencyChange(RecurringFrequency.MONTHLY)
        vm.onNextDueDateChange("2026-10-10")
        vm.submit()
        runCurrent()
        assertEquals(CreateRecurringState.AmountError.NotPositive, vm.state.value.amountError)
        assertTrue(recurring.createCalls.isEmpty())
    }

    @Test
    fun `end date before next due is rejected client-side`() = runTest {
        val recurring = FakeRecurringRepository()
        val vm = createVm(recurring)
        vm.loadOptions()
        runCurrent()
        vm.onAccountChange("acc-1")
        vm.onAmountChange("10")
        vm.onFrequencyChange(RecurringFrequency.MONTHLY)
        vm.onNextDueDateChange("2026-10-10")
        vm.onEndDateChange("2026-10-01")
        vm.submit()
        runCurrent()
        assertEquals(CreateRecurringState.DateError.EndBeforeNext, vm.state.value.dateError)
        assertTrue(recurring.createCalls.isEmpty())
    }

    @Test
    fun `create emits Created once and locks double submit`() = runTest {
        val gate = CompletableDeferred<com.spendwise.android.core.model.RecurringTemplate>()
        val recurring = FakeRecurringRepository().apply { createResult = { gate.await() } }
        val vm = createVm(recurring)
        vm.loadOptions()
        runCurrent()
        fillValid(vm)

        val events = mutableListOf<CreateRecurringViewModel.Event>()
        val collector = launch { vm.events.collect { events += it } }

        vm.submit()
        runCurrent()
        vm.submit() // locked while the first is in-flight
        runCurrent()
        assertEquals(1, recurring.createCalls.size)

        gate.complete(FakeRecurringRepository.template())
        runCurrent()
        collector.cancel()
        assertEquals(listOf(CreateRecurringViewModel.Event.Created), events)
    }

    @Test
    fun `ambiguous create failure never auto-retries`() = runTest {
        val recurring = FakeRecurringRepository().apply {
            createResult = { throw AppErrorException(AppError.NetworkUnavailable) }
        }
        val vm = createVm(recurring)
        vm.loadOptions()
        runCurrent()
        fillValid(vm)
        vm.submit()
        runCurrent()
        assertEquals(CreateRecurringState.FormError.Ambiguous, vm.state.value.formError)
        assertEquals(1, recurring.createCalls.size)
    }

    // ── Detail ──────────────────────────────────────────────────────

    private fun detailVm(
        recurring: FakeRecurringRepository,
        id: String = "r1",
        accounts: FakeAccountRepository = accountsWithTwo(),
    ) = RecurringDetailViewModel(
        SavedStateHandle(mapOf("templateId" to id)),
        recurring,
        accounts,
    )

    @Test
    fun `detail loads template content`() = runTest {
        val vm = detailVm(FakeRecurringRepository())
        runCurrent()
        assertTrue(vm.state.value is RecurringDetailState.Content)
    }

    @Test
    fun `detail maps missing id to NotFound`() = runTest {
        val recurring = FakeRecurringRepository().apply {
            findResult = { throw AppErrorException(AppError.NotFound) }
        }
        val vm = detailVm(recurring, id = "foreign")
        runCurrent()
        assertEquals(RecurringDetailState.NotFound, vm.state.value)
    }

    @Test
    fun `a mutations emission reloads detail to server truth`() = runTest {
        val recurring = FakeRecurringRepository()
        val vm = detailVm(recurring)
        runCurrent()
        val initial = vm.state.value as RecurringDetailState.Content
        assertEquals(0, initial.template.amount.amount.compareTo(BigDecimal("1200.00")))

        // An edit lands elsewhere (the edit screen); the mutations
        // signal must re-pull the template — server truth, not a
        // locally-patched copy.
        recurring.findResult = {
            FakeRecurringRepository.template(id = it, amount = Money(BigDecimal("999.99"), CurrencyCode("VND")))
        }
        recurring.emitMutation()
        runCurrent()
        val reloaded = vm.state.value as RecurringDetailState.Content
        assertEquals(0, reloaded.template.amount.amount.compareTo(BigDecimal("999.99")))
    }

    @Test
    fun `deactivate updates content to inactive`() = runTest {
        val recurring = FakeRecurringRepository().apply {
            deactivateResult = { id -> FakeRecurringRepository.template(id = id, active = false) }
        }
        val vm = detailVm(recurring)
        runCurrent()
        vm.deactivate()
        runCurrent()
        assertEquals(listOf("r1"), recurring.deactivateCalls)
        val content = vm.state.value as RecurringDetailState.Content
        assertFalse(content.template.active)
    }
}
