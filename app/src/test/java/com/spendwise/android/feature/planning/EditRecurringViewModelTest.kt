package com.spendwise.android.feature.planning

import androidx.lifecycle.SavedStateHandle
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.model.RecurringFrequency
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.fakes.FakeAccountRepository
import com.spendwise.android.fakes.FakeCategoryRepository
import com.spendwise.android.fakes.FakeRecurringRepository
import com.spendwise.android.fakes.MainDispatcherRule
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
 * `EditRecurringViewModel` (M16, REQ-BE-012) — prefill, sparse-patch
 * assembly, clear flags, validation, submit lock, save error paths,
 * and the stale-load/session races.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditRecurringViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(
        recurring: FakeRecurringRepository,
        id: String = "r1",
        accounts: FakeAccountRepository = FakeAccountRepository().apply {
            findByIdResult = { accountId, _ ->
                FakeAccountRepository.account(id = accountId, name = "Wallet", currency = "VND")
            }
        },
        categories: FakeCategoryRepository = FakeCategoryRepository(),
    ) = EditRecurringViewModel(
        SavedStateHandle(mapOf("templateId" to id)),
        recurring,
        accounts,
        categories,
    )

    private fun form(vm: EditRecurringViewModel) = vm.state.value as EditRecurringState.Form

    // ── Load / prefill ──────────────────────────────────────────────

    @Test
    fun `prefills every editable field from the loaded template`() = runTest {
        val recurring = FakeRecurringRepository().apply {
            findResult = {
                FakeRecurringRepository.template(
                    id = it,
                    amount = Money(BigDecimal("1200.00"), CurrencyCode("VND")),
                    category = "HOUSING",
                    frequency = RecurringFrequency.MONTHLY,
                    nextDueDate = LocalDate.parse("2026-11-01"),
                    endDate = LocalDate.parse("2027-11-01"),
                )
            }
        }
        val viewModel = vm(recurring)
        runCurrent()
        val form = form(viewModel)
        assertEquals("1200.00", form.amount)
        assertEquals("HOUSING", form.category)
        assertEquals(RecurringFrequency.MONTHLY, form.frequency)
        assertEquals("2026-11-01", form.nextDueDate)
        assertEquals("2027-11-01", form.endDate)
        assertEquals("Wallet", form.accountName)
        assertFalse(form.hasChanges)
    }

    @Test
    fun `maps a missing id to NotFound`() = runTest {
        val recurring = FakeRecurringRepository().apply {
            findResult = { throw AppErrorException(AppError.NotFound) }
        }
        val viewModel = vm(recurring, id = "foreign")
        runCurrent()
        assertEquals(EditRecurringState.NotFound, viewModel.state.value)
    }

    @Test
    fun `network failure maps to Network error and retry recovers`() = runTest {
        val recurring = FakeRecurringRepository().apply {
            findResult = { throw AppErrorException(AppError.NetworkUnavailable) }
        }
        val viewModel = vm(recurring)
        runCurrent()
        assertEquals(EditRecurringState.Error(PlanningError.Network), viewModel.state.value)

        recurring.findResult = { FakeRecurringRepository.template(id = it) }
        viewModel.refresh()
        runCurrent()
        assertTrue(viewModel.state.value is EditRecurringState.Form)
    }

    @Test
    fun `an aborted load after session expiry re-runs on resume`() = runTest {
        val recurring = FakeRecurringRepository().apply {
            findResult = { throw AppErrorException(AppError.SessionExpired) }
        }
        val viewModel = vm(recurring)
        runCurrent()
        // Stays Loading — the session overlay owns the failure.
        assertEquals(EditRecurringState.Loading, viewModel.state.value)

        recurring.findResult = { FakeRecurringRepository.template(id = it) }
        viewModel.onVisible()
        runCurrent()
        assertTrue(viewModel.state.value is EditRecurringState.Form)
    }

    @Test
    fun `a stale load failure cannot clobber a newer successful load`() = runTest {
        val recurring = FakeRecurringRepository()
        val first = CompletableDeferred<com.spendwise.android.core.model.RecurringTemplate>()
        var call = 0
        recurring.findResult = {
            call++
            if (call == 1) first.await() else FakeRecurringRepository.template(id = it)
        }
        val viewModel = vm(recurring)
        runCurrent()
        // Second load supersedes the parked first.
        viewModel.refresh()
        runCurrent()
        assertTrue(viewModel.state.value is EditRecurringState.Form)
        // The parked first load now fails — its failure is stale and
        // must not overwrite the newer Form (M15 stale-error lesson).
        first.completeExceptionally(AppErrorException(AppError.NetworkUnavailable))
        runCurrent()
        assertTrue(viewModel.state.value is EditRecurringState.Form)
    }

    @Test
    fun `a stale load success cannot clobber a newer successful load`() = runTest {
        val recurring = FakeRecurringRepository()
        val first = CompletableDeferred<com.spendwise.android.core.model.RecurringTemplate>()
        var call = 0
        recurring.findResult = {
            call++
            if (call == 1) {
                first.await()
            } else {
                FakeRecurringRepository.template(id = it, category = "FOOD")
            }
        }
        val viewModel = vm(recurring)
        runCurrent()
        viewModel.refresh()
        runCurrent()
        assertEquals("FOOD", form(viewModel).category)
        first.complete(FakeRecurringRepository.template(id = "r1", category = "HOUSING"))
        runCurrent()
        // The superseded response is dropped — FOOD stays.
        assertEquals("FOOD", form(viewModel).category)
    }

    // ── hasChanges / save gating ────────────────────────────────────

    @Test
    fun `save with no changes performs no wire call`() = runTest {
        val recurring = FakeRecurringRepository()
        val viewModel = vm(recurring)
        runCurrent()
        viewModel.save()
        runCurrent()
        assertTrue(recurring.updateCalls.isEmpty())
    }

    @Test
    fun `amount change alone produces a sparse patch`() = runTest {
        val recurring = FakeRecurringRepository()
        val viewModel = vm(recurring)
        runCurrent()
        viewModel.onAmountChange("950.50")
        viewModel.save()
        runCurrent()
        val call = recurring.updateCalls.single()
        assertEquals("r1", call.id)
        assertEquals(0, call.amount!!.amount.compareTo(BigDecimal("950.50")))
        assertEquals(CurrencyCode("VND"), call.amount.currency)
        assertNull(call.category)
        assertFalse(call.clearCategory)
        assertNull(call.frequency)
        assertNull(call.nextDueDate)
        assertNull(call.endDate)
        assertFalse(call.clearEndDate)
    }

    @Test
    fun `every changed field reaches the patch`() = runTest {
        val recurring = FakeRecurringRepository()
        val viewModel = vm(recurring)
        runCurrent()
        viewModel.onAmountChange("42.00")
        viewModel.onCategoryChange("FOOD")
        viewModel.onFrequencyChange(RecurringFrequency.WEEKLY)
        viewModel.onNextDueDateChange("2026-12-05")
        viewModel.onEndDateChange("2027-06-05")
        viewModel.save()
        runCurrent()
        val call = recurring.updateCalls.single()
        assertEquals(0, call.amount!!.amount.compareTo(BigDecimal("42.00")))
        assertEquals("FOOD", call.category)
        assertFalse(call.clearCategory)
        assertEquals(RecurringFrequency.WEEKLY, call.frequency)
        assertEquals(LocalDate.parse("2026-12-05"), call.nextDueDate)
        assertEquals(LocalDate.parse("2027-06-05"), call.endDate)
        assertFalse(call.clearEndDate)
    }

    @Test
    fun `removing the category sends clearCategory not a null category`() = runTest {
        val recurring = FakeRecurringRepository()
        val viewModel = vm(recurring)
        runCurrent()
        viewModel.onCategoryChange(null)
        assertTrue(form(viewModel).hasChanges)
        viewModel.save()
        runCurrent()
        val call = recurring.updateCalls.single()
        assertTrue(call.clearCategory)
        assertNull(call.category)
        // Nothing else is sent.
        assertNull(call.amount)
        assertNull(call.frequency)
        assertNull(call.nextDueDate)
        assertNull(call.endDate)
        assertFalse(call.clearEndDate)
    }

    @Test
    fun `emptying the end date sends clearEndDate`() = runTest {
        val recurring = FakeRecurringRepository().apply {
            findResult = {
                FakeRecurringRepository.template(
                    id = it,
                    endDate = LocalDate.parse("2027-11-01"),
                )
            }
        }
        val viewModel = vm(recurring)
        runCurrent()
        assertEquals("2027-11-01", form(viewModel).endDate)
        viewModel.onEndDateChange("")
        assertTrue(form(viewModel).hasChanges)
        viewModel.save()
        runCurrent()
        val call = recurring.updateCalls.single()
        assertTrue(call.clearEndDate)
        assertNull(call.endDate)
    }

    @Test
    fun `changing the category sends the code not a clear`() = runTest {
        val recurring = FakeRecurringRepository()
        val viewModel = vm(recurring)
        runCurrent()
        viewModel.onCategoryChange("FOOD")
        viewModel.save()
        runCurrent()
        val call = recurring.updateCalls.single()
        assertEquals("FOOD", call.category)
        assertFalse(call.clearCategory)
    }

    // ── Validation ──────────────────────────────────────────────────

    @Test
    fun `invalid amount blocks save and never calls the repository`() = runTest {
        val recurring = FakeRecurringRepository()
        val viewModel = vm(recurring)
        runCurrent()
        viewModel.onAmountChange("0")
        viewModel.save()
        runCurrent()
        assertEquals(
            EditRecurringState.Form.AmountError.NotPositive,
            form(viewModel).amountError,
        )
        assertTrue(recurring.updateCalls.isEmpty())
    }

    @Test
    fun `invalid dates block save and never call the repository`() = runTest {
        val recurring = FakeRecurringRepository()
        val viewModel = vm(recurring)
        runCurrent()
        viewModel.onNextDueDateChange("not-a-date")
        viewModel.save()
        runCurrent()
        assertEquals(
            EditRecurringState.Form.DateError.Invalid,
            form(viewModel).dateError,
        )
        assertTrue(recurring.updateCalls.isEmpty())
    }

    @Test
    fun `end date before next due blocks save`() = runTest {
        val recurring = FakeRecurringRepository()
        val viewModel = vm(recurring)
        runCurrent()
        viewModel.onNextDueDateChange("2026-12-01")
        viewModel.onEndDateChange("2026-11-01")
        viewModel.save()
        runCurrent()
        assertEquals(
            EditRecurringState.Form.DateError.EndBeforeNext,
            form(viewModel).dateError,
        )
        assertTrue(recurring.updateCalls.isEmpty())
    }

    // ── Submit lock / outcomes ──────────────────────────────────────

    @Test
    fun `double submit is locked by isSubmitting`() = runTest {
        val recurring = FakeRecurringRepository()
        val gate = CompletableDeferred<com.spendwise.android.core.model.RecurringTemplate>()
        recurring.updateResult = { gate.await() }
        val viewModel = vm(recurring)
        runCurrent()
        viewModel.onAmountChange("999.00")
        viewModel.save()
        runCurrent()
        assertTrue(form(viewModel).isSubmitting)
        viewModel.save()
        runCurrent()
        assertEquals(1, recurring.updateCalls.size)
        gate.complete(FakeRecurringRepository.template())
        runCurrent()
    }

    @Test
    fun `success emits the Saved event`() = runTest {
        val recurring = FakeRecurringRepository()
        val viewModel = vm(recurring)
        runCurrent()
        val event = CompletableDeferred<EditRecurringViewModel.Event>()
        val collector = launch { event.complete(viewModel.events.first()) }
        viewModel.onAmountChange("999.00")
        viewModel.save()
        runCurrent()
        assertEquals(EditRecurringViewModel.Event.Saved, event.await())
        collector.cancel()
    }

    @Test
    fun `a dropped mid-write surfaces Ambiguous`() = runTest {
        val recurring = FakeRecurringRepository().apply {
            updateResult = { throw AppErrorException(AppError.NetworkUnavailable) }
        }
        val viewModel = vm(recurring)
        runCurrent()
        viewModel.onAmountChange("999.00")
        viewModel.save()
        runCurrent()
        assertEquals(
            EditRecurringState.Form.FormError.Ambiguous,
            form(viewModel).formError,
        )
        assertFalse(form(viewModel).isSubmitting)
    }

    @Test
    fun `a dead-generation save surfaces Ambiguous`() = runTest {
        val recurring = FakeRecurringRepository().apply {
            updateResult = { throw SessionEndedException() }
        }
        val viewModel = vm(recurring)
        runCurrent()
        viewModel.onAmountChange("999.00")
        viewModel.save()
        runCurrent()
        assertEquals(
            EditRecurringState.Form.FormError.Ambiguous,
            form(viewModel).formError,
        )
    }

    @Test
    fun `a 404 on save reloads toward NotFound`() = runTest {
        val recurring = FakeRecurringRepository().apply {
            updateResult = { throw AppErrorException(AppError.NotFound) }
            findResult = { throw AppErrorException(AppError.NotFound) }
        }
        val viewModel = vm(recurring)
        // The load also fails with NotFound — drive the Form through a
        // first-successful load, then flip findResult.
        recurring.findResult = { FakeRecurringRepository.template(id = it) }
        viewModel.refresh()
        runCurrent()
        assertTrue(viewModel.state.value is EditRecurringState.Form)
        recurring.findResult = { throw AppErrorException(AppError.NotFound) }
        viewModel.onAmountChange("999.00")
        viewModel.save()
        runCurrent()
        assertEquals(EditRecurringState.NotFound, viewModel.state.value)
    }

    @Test
    fun `server field errors land on their fields`() = runTest {
        val recurring = FakeRecurringRepository().apply {
            updateResult = {
                throw AppErrorException(
                    AppError.Validation(mapOf("amount" to "must be positive")),
                )
            }
        }
        val viewModel = vm(recurring)
        runCurrent()
        viewModel.onAmountChange("999.00")
        viewModel.save()
        runCurrent()
        val form = form(viewModel)
        assertEquals(EditRecurringState.Form.AmountError.Invalid, form.amountError)
        assertFalse(form.isSubmitting)
    }

    @Test
    fun `save during Loading is a no-op`() = runTest {
        val recurring = FakeRecurringRepository()
        val gate = CompletableDeferred<com.spendwise.android.core.model.RecurringTemplate>()
        recurring.findResult = { gate.await() }
        val viewModel = vm(recurring)
        runCurrent()
        viewModel.save()
        runCurrent()
        assertTrue(recurring.updateCalls.isEmpty())
        gate.complete(FakeRecurringRepository.template())
        runCurrent()
    }
}
