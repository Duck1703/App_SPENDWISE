package com.spendwise.android.feature.planning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Planning destinations (S15–S23). Every wrapper observes ON_RESUME so
 * a same-user session resume re-runs an aborted load/options fetch —
 * writes are never auto-replayed.
 */

@Composable
private fun ResumeEffect(onVisible: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onVisible()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
fun RecurringListDestination(
    onOpenTemplate: (String) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
    viewModel: RecurringListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResumeEffect(viewModel::onVisible)
    RecurringListScreen(
        state = state,
        onOpenTemplate = onOpenTemplate,
        onCreate = onCreate,
        onRetry = viewModel::refresh,
        onBack = onBack,
    )
}

@Composable
fun CreateRecurringDestination(
    onCreated: () -> Unit,
    onBack: () -> Unit,
    viewModel: CreateRecurringViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResumeEffect(viewModel::onVisible)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is CreateRecurringViewModel.Event.Created) onCreated()
        }
    }
    CreateRecurringScreen(
        state = state,
        onDirectionChange = viewModel::onDirectionChange,
        onAccountChange = viewModel::onAccountChange,
        onAmountChange = viewModel::onAmountChange,
        onCategoryChange = viewModel::onCategoryChange,
        onFrequencyChange = viewModel::onFrequencyChange,
        onNextDueDateChange = viewModel::onNextDueDateChange,
        onEndDateChange = viewModel::onEndDateChange,
        onSubmit = viewModel::submit,
        onRetryOptions = viewModel::loadOptions,
        onBack = onBack,
    )
}

@Composable
fun RecurringDetailDestination(
    onBack: () -> Unit,
    viewModel: RecurringDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResumeEffect(viewModel::onVisible)
    RecurringDetailScreen(
        state = state,
        onDeactivate = viewModel::deactivate,
        onRetry = viewModel::refresh,
        onBack = onBack,
    )
}

@Composable
fun BudgetsDestination(
    onOpenBudget: (String) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
    viewModel: BudgetsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResumeEffect(viewModel::onVisible)
    BudgetsScreen(
        state = state,
        onOpenBudget = onOpenBudget,
        onCreate = onCreate,
        onRetry = viewModel::refresh,
        onBack = onBack,
    )
}

@Composable
fun CreateBudgetDestination(
    onCreated: () -> Unit,
    onBack: () -> Unit,
    viewModel: CreateBudgetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResumeEffect(viewModel::onVisible)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is CreateBudgetViewModel.Event.Created) onCreated()
        }
    }
    CreateBudgetScreen(
        state = state,
        onCategoryChange = viewModel::onCategoryChange,
        onPeriodChange = viewModel::onPeriodChange,
        onLimitChange = viewModel::onLimitChange,
        onCurrencyChange = viewModel::onCurrencyChange,
        onSubmit = viewModel::submit,
        onRetryOptions = viewModel::loadOptions,
        onBack = onBack,
    )
}

@Composable
fun BudgetDetailDestination(
    onBack: () -> Unit,
    viewModel: BudgetDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val edit by viewModel.edit.collectAsStateWithLifecycle()
    ResumeEffect(viewModel::onVisible)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                BudgetDetailViewModel.Event.Deleted -> onBack()
            }
        }
    }
    BudgetDetailScreen(
        state = state,
        edit = edit,
        onRetry = viewModel::refresh,
        onBack = onBack,
        onOpenEdit = viewModel::openEdit,
        onDismissEdit = viewModel::dismissEdit,
        onEditLimitChange = viewModel::onEditLimitChange,
        onEditPeriodChange = viewModel::onEditPeriodChange,
        onSaveEdit = viewModel::saveEdit,
        onDelete = viewModel::delete,
    )
}

@Composable
fun RulesDestination(
    onOpenRule: (String) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResumeEffect(viewModel::onVisible)
    RulesScreen(
        state = state,
        onOpenRule = onOpenRule,
        onCreate = onCreate,
        onRetry = viewModel::refresh,
        onBack = onBack,
    )
}

@Composable
fun CreateRuleDestination(
    onCreated: () -> Unit,
    onBack: () -> Unit,
    viewModel: CreateRuleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResumeEffect(viewModel::onVisible)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is CreateRuleViewModel.Event.Created) onCreated()
        }
    }
    CreateRuleScreen(
        state = state,
        onConditionTypeChange = viewModel::onConditionTypeChange,
        onConditionValueChange = viewModel::onConditionValueChange,
        onTargetCategoryChange = viewModel::onTargetCategoryChange,
        onPriorityChange = viewModel::onPriorityChange,
        onSubmit = viewModel::submit,
        onRetryOptions = viewModel::loadOptions,
        onBack = onBack,
    )
}

@Composable
fun RuleDetailDestination(
    onBack: () -> Unit,
    viewModel: RuleDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResumeEffect(viewModel::onVisible)
    RuleDetailScreen(
        state = state,
        onDisable = viewModel::disable,
        onRetry = viewModel::refresh,
        onBack = onBack,
    )
}
