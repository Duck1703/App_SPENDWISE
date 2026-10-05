package com.spendwise.android.feature.planning

import androidx.lifecycle.SavedStateHandle
import com.spendwise.android.core.model.BudgetStatus
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.fakes.FakeAccountRepository
import com.spendwise.android.fakes.FakeBudgetRepository
import com.spendwise.android.fakes.FakeCategoryRepository
import com.spendwise.android.fakes.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/**
 * Budget (S18–S20) ViewModels — list states, create validation +
 * double-submit lock + 409 honesty, and the server-authoritative
 * calculation detail (no client recomputation anywhere).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // ── List ────────────────────────────────────────────────────────

    @Test
    fun `list shows content`() = runTest {
        val budgets = FakeBudgetRepository().apply {
            listResult = { listOf(FakeBudgetRepository.budget()) }
        }
        val vm = BudgetsViewModel(budgets)
        runCurrent()
        assertTrue(vm.state.value is BudgetsListState.Content)
    }

    @Test
    fun `empty list maps to Empty`() = runTest {
        val vm = BudgetsViewModel(FakeBudgetRepository())
        runCurrent()
        assertEquals(BudgetsListState.Empty, vm.state.value)
    }

    @Test
    fun `io failure maps to Network and refresh recovers`() = runTest {
        val budgets = FakeBudgetRepository().apply {
            listResult = { throw AppErrorException(AppError.NetworkUnavailable) }
        }
        val vm = BudgetsViewModel(budgets)
        runCurrent()
        assertEquals(BudgetsListState.Error(PlanningError.Network), vm.state.value)
        budgets.listResult = { listOf(FakeBudgetRepository.budget()) }
        vm.refresh()
        runCurrent()
        assertTrue(vm.state.value is BudgetsListState.Content)
    }

    @Test
    fun `session end mid-load publishes nothing`() = runTest {
        val budgets = FakeBudgetRepository().apply {
            listResult = { throw SessionEndedException() }
        }
        val vm = BudgetsViewModel(budgets)
        runCurrent()
        assertFalse(vm.state.value is BudgetsListState.Error)
    }

    // ── Create ──────────────────────────────────────────────────────

    private fun createVm(
        budgets: FakeBudgetRepository = FakeBudgetRepository(),
        accounts: FakeAccountRepository = FakeAccountRepository().apply {
            listResult = { _ ->
                listOf(FakeAccountRepository.account(id = "a1", currency = "VND"))
            }
        },
        categories: FakeCategoryRepository = FakeCategoryRepository().apply {
            listResult = { _ -> listOf("FOOD", "TRANSPORT") }
        },
    ) = CreateBudgetViewModel(budgets, accounts, categories)

    @Test
    fun `create validates required fields before any remote call`() = runTest {
        val budgets = FakeBudgetRepository()
        val vm = createVm(budgets)
        vm.loadOptions()
        runCurrent()
        vm.submit()
        runCurrent()
        assertTrue(vm.state.value.categoryError != null)
        assertTrue(vm.state.value.limitError != null)
        assertTrue(budgets.createCalls.isEmpty())
    }

    @Test
    fun `create rejects malformed period`() = runTest {
        val budgets = FakeBudgetRepository()
        val vm = createVm(budgets)
        vm.loadOptions()
        runCurrent()
        vm.onCategoryChange("FOOD")
        vm.onPeriodChange("2026-13")
        vm.onLimitChange("500")
        vm.onCurrencyChange("VND")
        vm.submit()
        runCurrent()
        assertEquals(CreateBudgetState.PeriodError.Invalid, vm.state.value.periodError)
        assertTrue(budgets.createCalls.isEmpty())
    }

    @Test
    fun `create rejects over-precision limit`() = runTest {
        val budgets = FakeBudgetRepository()
        val vm = createVm(budgets)
        vm.loadOptions()
        runCurrent()
        vm.onCategoryChange("FOOD")
        vm.onPeriodChange("2026-10")
        vm.onLimitChange("1.999")
        vm.onCurrencyChange("VND")
        vm.submit()
        runCurrent()
        assertEquals(CreateBudgetState.LimitError.TooPrecise, vm.state.value.limitError)
        assertTrue(budgets.createCalls.isEmpty())
    }

    @Test
    fun `create emits Created once and locks double submit`() = runTest {
        val gate = CompletableDeferred<com.spendwise.android.core.model.Budget>()
        val budgets = FakeBudgetRepository().apply { createResult = { _, _, _ -> gate.await() } }
        val vm = createVm(budgets)
        vm.loadOptions()
        runCurrent()
        vm.onCategoryChange("FOOD")
        vm.onPeriodChange("2026-10")
        vm.onLimitChange("500")
        vm.onCurrencyChange("VND")

        val events = mutableListOf<CreateBudgetViewModel.Event>()
        val collector = launch { vm.events.collect { events += it } }
        vm.submit()
        runCurrent()
        vm.submit()
        runCurrent()
        assertEquals(1, budgets.createCalls.size)

        gate.complete(FakeBudgetRepository.budget())
        runCurrent()
        collector.cancel()
        assertEquals(listOf(CreateBudgetViewModel.Event.Created), events)
    }

    @Test
    fun `409 duplicate surfaces the Conflict form error`() = runTest {
        val budgets = FakeBudgetRepository().apply {
            createResult = { _, _, _ -> throw AppErrorException(AppError.Conflict) }
        }
        val vm = createVm(budgets)
        vm.loadOptions()
        runCurrent()
        vm.onCategoryChange("FOOD")
        vm.onPeriodChange("2026-10")
        vm.onLimitChange("500")
        vm.onCurrencyChange("VND")
        vm.submit()
        runCurrent()
        assertEquals(CreateBudgetState.FormError.Conflict, vm.state.value.formError)
    }

    @Test
    fun `ambiguous create failure never auto-retries`() = runTest {
        val budgets = FakeBudgetRepository().apply {
            createResult = { _, _, _ -> throw AppErrorException(AppError.NetworkUnavailable) }
        }
        val vm = createVm(budgets)
        vm.loadOptions()
        runCurrent()
        vm.onCategoryChange("FOOD")
        vm.onPeriodChange("2026-10")
        vm.onLimitChange("500")
        vm.onCurrencyChange("VND")
        vm.submit()
        runCurrent()
        assertEquals(CreateBudgetState.FormError.Ambiguous, vm.state.value.formError)
        assertEquals(1, budgets.createCalls.size)
    }

    // ── Detail ──────────────────────────────────────────────────────

    private fun detailVm(budgets: FakeBudgetRepository, id: String = "b1") =
        BudgetDetailViewModel(SavedStateHandle(mapOf("budgetId" to id)), budgets)

    @Test
    fun `detail shows server figures verbatim including OVER_BUDGET`() = runTest {
        val budgets = FakeBudgetRepository().apply {
            calculationResult = {
                FakeBudgetRepository.calculation(
                    id = it,
                    spent = com.spendwise.android.core.model.Money(
                        BigDecimal("600"),
                        com.spendwise.android.core.model.CurrencyCode("VND"),
                    ),
                    remaining = com.spendwise.android.core.model.Money(
                        BigDecimal("-100"),
                        com.spendwise.android.core.model.CurrencyCode("VND"),
                    ),
                    percentage = BigDecimal("120.00"),
                    status = BudgetStatus.OVER_BUDGET,
                )
            }
        }
        val vm = detailVm(budgets)
        runCurrent()
        val content = vm.state.value as BudgetDetailState.Content
        assertEquals(BigDecimal("600"), content.calculation.spent.amount)
        assertEquals(BigDecimal("-100"), content.calculation.remaining.amount)
        assertEquals(BigDecimal("120.00"), content.calculation.percentage)
        assertEquals(BudgetStatus.OVER_BUDGET, content.calculation.status)
    }

    @Test
    fun `detail maps 404 to NotFound`() = runTest {
        val budgets = FakeBudgetRepository().apply {
            calculationResult = { throw AppErrorException(AppError.NotFound) }
        }
        val vm = detailVm(budgets, id = "foreign")
        runCurrent()
        assertEquals(BudgetDetailState.NotFound, vm.state.value)
    }
}
