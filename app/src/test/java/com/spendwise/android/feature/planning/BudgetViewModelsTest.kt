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

    // ── Detail edit dialog (M14) ────────────────────────────────────

    @Test
    fun `openEdit seeds the dialog from the server figures`() = runTest {
        val vm = detailVm(FakeBudgetRepository())
        runCurrent()
        vm.openEdit()
        val edit = vm.edit.value
        assertEquals(true, edit.visible)
        assertEquals("500", edit.limit)
        assertEquals("2026-10", edit.period)
    }

    @Test
    fun `saveEdit with invalid inputs sets field errors and never calls`() = runTest {
        val budgets = FakeBudgetRepository()
        val vm = detailVm(budgets)
        runCurrent()
        vm.openEdit()
        vm.onEditLimitChange("abc")
        vm.onEditPeriodChange("not-a-month")
        vm.saveEdit()
        runCurrent()
        assertEquals(CreateBudgetState.LimitError.Invalid, vm.edit.value.limitError)
        assertEquals(CreateBudgetState.PeriodError.Invalid, vm.edit.value.periodError)
        assertTrue(budgets.updateCalls.isEmpty())
    }

    @Test
    fun `saveEdit unchanged inputs close without a request`() = runTest {
        val budgets = FakeBudgetRepository()
        val vm = detailVm(budgets)
        runCurrent()
        vm.openEdit() // prefilled with current values
        vm.saveEdit()
        runCurrent()
        assertTrue(budgets.updateCalls.isEmpty())
        assertFalse(vm.edit.value.visible)
    }

    @Test
    fun `saveEdit sends the patch then reloads the calculation`() = runTest {
        val budgets = FakeBudgetRepository()
        val vm = detailVm(budgets)
        runCurrent()
        vm.openEdit()
        vm.onEditLimitChange("750")
        vm.onEditPeriodChange("2026-11")
        vm.saveEdit()
        runCurrent()
        val (id, limit, period) = budgets.updateCalls.single()
        assertEquals("b1", id)
        assertEquals(BigDecimal("750"), limit?.amount)
        assertEquals("2026-11", period)
        assertFalse(vm.edit.value.visible)
    }

    @Test
    fun `saveEdit conflict surfaces the honest 409 copy`() = runTest {
        val budgets = FakeBudgetRepository().apply {
            updateResult = { _, _, _ -> throw AppErrorException(AppError.Conflict) }
        }
        val vm = detailVm(budgets)
        runCurrent()
        vm.openEdit()
        vm.onEditPeriodChange("2026-12")
        vm.saveEdit()
        runCurrent()
        assertEquals(EditBudgetState.FormError.Conflict, vm.edit.value.formError)
        assertTrue(vm.edit.value.visible) // dialog stays open for correction
    }

    @Test
    fun `saveEdit network drop is ambiguous, never auto-retried`() = runTest {
        val budgets = FakeBudgetRepository().apply {
            updateResult = { _, _, _ -> throw AppErrorException(AppError.NetworkUnavailable) }
        }
        val vm = detailVm(budgets)
        runCurrent()
        vm.openEdit()
        vm.onEditLimitChange("750")
        vm.saveEdit()
        runCurrent()
        assertEquals(EditBudgetState.FormError.Ambiguous, vm.edit.value.formError)
        assertEquals(1, budgets.updateCalls.size)
    }

    @Test
    fun `saveEdit dead-generation is ambiguous, never claimed success`() = runTest {
        val budgets = FakeBudgetRepository().apply {
            updateResult = { _, _, _ ->
                throw com.spendwise.android.data.repository.SessionEndedException()
            }
        }
        val vm = detailVm(budgets)
        runCurrent()
        vm.openEdit()
        vm.onEditLimitChange("750")
        vm.saveEdit()
        runCurrent()
        // The response may have committed server-side — the dialog
        // tells the truth (Ambiguous) and stays open.
        assertEquals(EditBudgetState.FormError.Ambiguous, vm.edit.value.formError)
        assertTrue(vm.edit.value.visible)
        assertEquals(1, budgets.updateCalls.size)
    }

    @Test
    fun `saveEdit period change reload lands the new period calculation`() = runTest {
        var moved = false
        val budgets = FakeBudgetRepository().apply {
            calculationResult = { id ->
                // The server truth moves only when the patch commits.
                FakeBudgetRepository.calculation(
                    id = id,
                    period = if (moved) "2026-11" else "2026-10",
                )
            }
            updateResult = { id, _, p ->
                moved = true
                FakeBudgetRepository.budget(id = id, period = p ?: "2026-10")
            }
        }
        val vm = detailVm(budgets)
        runCurrent()
        vm.openEdit()
        vm.onEditPeriodChange("2026-11")
        vm.saveEdit()
        runCurrent()
        // The PATCH actually ran, then the reload landed the new
        // period's server calculation — stale 2026-10 figures cannot
        // linger on the detail.
        assertEquals(1, budgets.updateCalls.size)
        val content = vm.state.value as BudgetDetailState.Content
        assertEquals("2026-11", content.calculation.period)
    }

    // ── Detail delete (M14) ─────────────────────────────────────────

    @Test
    fun `delete emits the Deleted event once`() = runTest {
        val budgets = FakeBudgetRepository()
        val vm = detailVm(budgets)
        runCurrent()
        val events = mutableListOf<BudgetDetailViewModel.Event>()
        val collector = launch { vm.events.collect { events += it } }
        vm.delete()
        runCurrent()
        assertEquals(listOf("b1"), budgets.deleteCalls)
        assertEquals(listOf(BudgetDetailViewModel.Event.Deleted), events)
        collector.cancel()
    }

    @Test
    fun `delete on an already-gone budget reloads instead of navigating`() = runTest {
        var gone = false
        val budgets = FakeBudgetRepository().apply {
            calculationResult = {
                if (gone) throw AppErrorException(AppError.NotFound)
                else FakeBudgetRepository.calculation(id = it)
            }
            // The delete already landed server-side — the reload must see
            // the budget gone too, so the surface settles on NotFound.
            deleteResult = { gone = true; throw AppErrorException(AppError.NotFound) }
        }
        val vm = detailVm(budgets)
        runCurrent()
        val events = mutableListOf<BudgetDetailViewModel.Event>()
        val collector = launch { vm.events.collect { events += it } }
        vm.delete()
        runCurrent()
        // No Deleted event — the surface reloads into NotFound instead.
        assertTrue(events.isEmpty())
        assertEquals(BudgetDetailState.NotFound, vm.state.value)
        collector.cancel()
    }
}
