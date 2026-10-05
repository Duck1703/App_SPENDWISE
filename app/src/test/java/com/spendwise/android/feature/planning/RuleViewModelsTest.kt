package com.spendwise.android.feature.planning

import androidx.lifecycle.SavedStateHandle
import com.spendwise.android.core.model.RuleConditionType
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.fakes.FakeCategoryRepository
import com.spendwise.android.fakes.FakeRuleRepository
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

/**
 * Categorization-rule (S21–S23) ViewModels — list order is the server's
 * own, create validation + double-submit lock + 409 honesty, and the
 * detail soft-disable flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RuleViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // ── List ────────────────────────────────────────────────────────

    @Test
    fun `list preserves server order including disabled rules`() = runTest {
        val rules = FakeRuleRepository().apply {
            listResult = {
                listOf(
                    FakeRuleRepository.rule(id = "a", priority = 1, enabled = true),
                    FakeRuleRepository.rule(id = "b", priority = 2, enabled = false),
                )
            }
        }
        val vm = RulesViewModel(rules)
        runCurrent()
        val content = vm.state.value as RulesListState.Content
        assertEquals(listOf("a", "b"), content.rules.map { it.id })
        assertFalse(content.rules[1].enabled)
    }

    @Test
    fun `empty list maps to Empty`() = runTest {
        val vm = RulesViewModel(FakeRuleRepository())
        runCurrent()
        assertEquals(RulesListState.Empty, vm.state.value)
    }

    @Test
    fun `io failure maps to Network and refresh recovers`() = runTest {
        val rules = FakeRuleRepository().apply {
            listResult = { throw AppErrorException(AppError.NetworkUnavailable) }
        }
        val vm = RulesViewModel(rules)
        runCurrent()
        assertEquals(RulesListState.Error(PlanningError.Network), vm.state.value)
        rules.listResult = { listOf(FakeRuleRepository.rule()) }
        vm.refresh()
        runCurrent()
        assertTrue(vm.state.value is RulesListState.Content)
    }

    @Test
    fun `session end mid-load publishes nothing`() = runTest {
        val rules = FakeRuleRepository().apply {
            listResult = { throw SessionEndedException() }
        }
        val vm = RulesViewModel(rules)
        runCurrent()
        assertFalse(vm.state.value is RulesListState.Error)
    }

    // ── Create ──────────────────────────────────────────────────────

    private fun createVm(
        rules: FakeRuleRepository = FakeRuleRepository(),
        categories: FakeCategoryRepository = FakeCategoryRepository().apply {
            listResult = { _ -> listOf("FOOD", "TRANSPORT") }
        },
    ) = CreateRuleViewModel(rules, categories)

    private fun fillValid(vm: CreateRuleViewModel) {
        vm.onConditionTypeChange(RuleConditionType.CONTAINS)
        vm.onConditionValueChange("uber")
        vm.onTargetCategoryChange("TRANSPORT")
        vm.onPriorityChange("3")
    }

    @Test
    fun `create validates required fields before any remote call`() = runTest {
        val rules = FakeRuleRepository()
        val vm = createVm(rules)
        vm.loadOptions()
        runCurrent()
        vm.submit()
        runCurrent()
        val s = vm.state.value
        assertTrue(s.valueError != null)
        assertTrue(s.priorityError != null)
        assertTrue(rules.createCalls.isEmpty())
    }

    @Test
    fun `create rejects blank condition value`() = runTest {
        val rules = FakeRuleRepository()
        val vm = createVm(rules)
        vm.loadOptions()
        runCurrent()
        vm.onConditionTypeChange(RuleConditionType.CONTAINS)
        vm.onConditionValueChange("   ")
        vm.onTargetCategoryChange("FOOD")
        vm.onPriorityChange("1")
        vm.submit()
        runCurrent()
        assertTrue(vm.state.value.valueError != null)
        assertTrue(rules.createCalls.isEmpty())
    }

    @Test
    fun `create rejects non-integer priority`() = runTest {
        val rules = FakeRuleRepository()
        val vm = createVm(rules)
        vm.loadOptions()
        runCurrent()
        vm.onConditionTypeChange(RuleConditionType.CONTAINS)
        vm.onConditionValueChange("x")
        vm.onTargetCategoryChange("FOOD")
        vm.onPriorityChange("abc")
        vm.submit()
        runCurrent()
        assertTrue(vm.state.value.priorityError != null)
        assertTrue(rules.createCalls.isEmpty())
    }

    @Test
    fun `create emits Created once and locks double submit`() = runTest {
        val gate = CompletableDeferred<com.spendwise.android.core.model.CategorizationRule>()
        val rules = FakeRuleRepository().apply { createResult = { gate.await() } }
        val vm = createVm(rules)
        vm.loadOptions()
        runCurrent()
        fillValid(vm)

        val events = mutableListOf<CreateRuleViewModel.Event>()
        val collector = launch { vm.events.collect { events += it } }
        vm.submit()
        runCurrent()
        vm.submit()
        runCurrent()
        assertEquals(1, rules.createCalls.size)

        gate.complete(FakeRuleRepository.rule())
        runCurrent()
        collector.cancel()
        assertEquals(listOf(CreateRuleViewModel.Event.Created), events)
    }

    @Test
    fun `409 duplicate priority lands on the priority field`() = runTest {
        val rules = FakeRuleRepository().apply {
            createResult = { throw AppErrorException(AppError.Conflict) }
        }
        val vm = createVm(rules)
        vm.loadOptions()
        runCurrent()
        fillValid(vm)
        vm.submit()
        runCurrent()
        assertEquals(CreateRuleState.PriorityError.Conflict, vm.state.value.priorityError)
    }

    @Test
    fun `ambiguous create failure never auto-retries`() = runTest {
        val rules = FakeRuleRepository().apply {
            createResult = { throw AppErrorException(AppError.NetworkUnavailable) }
        }
        val vm = createVm(rules)
        vm.loadOptions()
        runCurrent()
        fillValid(vm)
        vm.submit()
        runCurrent()
        assertEquals(CreateRuleState.FormError.Ambiguous, vm.state.value.formError)
        assertEquals(1, rules.createCalls.size)
    }

    // ── Detail ──────────────────────────────────────────────────────

    private fun detailVm(rules: FakeRuleRepository, id: String = "rl1") =
        RuleDetailViewModel(SavedStateHandle(mapOf("ruleId" to id)), rules)

    @Test
    fun `detail loads content`() = runTest {
        val vm = detailVm(FakeRuleRepository())
        runCurrent()
        assertTrue(vm.state.value is RuleDetailState.Content)
    }

    @Test
    fun `detail maps missing id to NotFound`() = runTest {
        val rules = FakeRuleRepository().apply {
            findResult = { throw AppErrorException(AppError.NotFound) }
        }
        val vm = detailVm(rules, id = "foreign")
        runCurrent()
        assertEquals(RuleDetailState.NotFound, vm.state.value)
    }

    @Test
    fun `disable marks the rule inactive`() = runTest {
        val rules = FakeRuleRepository().apply {
            disableResult = { id -> FakeRuleRepository.rule(id = id, enabled = false) }
        }
        val vm = detailVm(rules)
        runCurrent()
        vm.disable()
        runCurrent()
        assertEquals(listOf("rl1"), rules.disableCalls)
        val content = vm.state.value as RuleDetailState.Content
        assertFalse(content.rule.enabled)
    }
}
