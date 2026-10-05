package com.spendwise.android.feature.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.android.core.model.RuleConditionType
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.CategoryRepository
import com.spendwise.android.data.repository.RuleRepository
import com.spendwise.android.data.repository.SessionEndedException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Rule create (S22). Mirrors backend limits (`conditionValue` ≤ 255,
 * `targetCategory` required, `priority` an integer) so most mistakes
 * surface inline; server `fields{}` errors still win. A duplicate
 * priority is the honest 409 — surfaced on the priority field, not a
 * generic failure. `enabled` is never sent (S22 note — the server
 * default applies).
 *
 * Write safety: submit lock + no auto-retry + ambiguous-outcome copy,
 * same contract as every other write.
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class CreateRuleViewModel @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateRuleState())
    val state: StateFlow<CreateRuleState> = _state.asStateFlow()

    sealed interface Event {
        data object Created : Event
    }

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadOptions()
    }

    fun loadOptions() {
        viewModelScope.launch {
            _state.value = _state.value.copy(optionsLoading = true, optionsError = false)
            try {
                val categories = categoryRepository.list()
                _state.value = _state.value.copy(
                    categories = categories,
                    targetCategory = _state.value.targetCategory
                        ?.takeIf { it in categories }
                        ?: categories.firstOrNull(),
                    optionsLoading = false,
                )
            } catch (e: SessionEndedException) {
                optionsExpired = true
                _state.value = _state.value.copy(optionsLoading = false)
                return@launch
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    optionsExpired = true
                    _state.value = _state.value.copy(optionsLoading = false)
                    return@launch
                }
                _state.value = _state.value.copy(optionsLoading = false, optionsError = true)
            }
        }
    }

    private var optionsExpired = false

    /** Re-runs an aborted options load after a same-user resume. */
    fun onVisible() {
        if (optionsExpired) {
            optionsExpired = false
            loadOptions()
        }
    }

    fun onConditionTypeChange(value: RuleConditionType) {
        if (value != RuleConditionType.CONTAINS && value != RuleConditionType.EQUALS) return
        _state.value = _state.value.copy(conditionType = value, formError = null)
    }

    fun onConditionValueChange(value: String) {
        _state.value = _state.value.copy(conditionValue = value, valueError = null, formError = null)
    }

    fun onTargetCategoryChange(code: String?) {
        _state.value = _state.value.copy(targetCategory = code, categoryError = null, formError = null)
    }

    fun onPriorityChange(value: String) {
        _state.value = _state.value.copy(priority = value, priorityError = null, formError = null)
    }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return

        val valueError = when {
            current.conditionValue.isBlank() -> CreateRuleState.ValueError.Required
            current.conditionValue.length > MAX_CONDITION_VALUE ->
                CreateRuleState.ValueError.TooLong
            else -> null
        }
        val categoryError = if (current.targetCategory == null) {
            CreateRuleState.CategoryError.Required
        } else {
            null
        }
        val trimmedPriority = current.priority.trim()
        val priorityValue = trimmedPriority.toIntOrNull()
        val priorityError = when {
            trimmedPriority.isEmpty() -> CreateRuleState.PriorityError.Required
            priorityValue == null -> CreateRuleState.PriorityError.Invalid
            else -> null
        }
        if (valueError != null || categoryError != null || priorityError != null) {
            _state.value = current.copy(
                valueError = valueError,
                categoryError = categoryError,
                priorityError = priorityError,
            )
            return
        }

        _state.value = current.copy(
            isSubmitting = true,
            valueError = null,
            categoryError = null,
            priorityError = null,
            formError = null,
        )
        viewModelScope.launch {
            try {
                ruleRepository.create(
                    conditionType = current.conditionType,
                    conditionValue = current.conditionValue.trim(),
                    targetCategory = checkNotNull(current.targetCategory),
                    priority = checkNotNull(priorityValue),
                )
                _events.send(Event.Created)
            } catch (e: SessionEndedException) {
                // Dead-generation publish — the response already landed,
                // so the rule almost certainly exists server-side.
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    formError = CreateRuleState.FormError.Ambiguous,
                )
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    _state.value = _state.value.copy(isSubmitting = false)
                    return@launch
                }
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    valueError = e.error.fieldMessage("conditionValue")
                        ?.let { CreateRuleState.ValueError.TooLong },
                    categoryError = e.error.fieldMessage("targetCategory")
                        ?.let { CreateRuleState.CategoryError.Required },
                    // Duplicate (user, priority) — the priority field
                    // carries the conflict honestly.
                    priorityError = e.error.fieldMessage("priority")
                        ?.let { CreateRuleState.PriorityError.Invalid }
                        ?: (e.error as? AppError.Conflict)
                            ?.let { CreateRuleState.PriorityError.Conflict },
                    formError = when (val error = e.error) {
                        is AppError.Conflict -> null
                        AppError.NetworkUnavailable -> CreateRuleState.FormError.Ambiguous
                        is AppError.Server -> CreateRuleState.FormError.Server
                        is AppError.Validation ->
                            if (error.fields.keys.any {
                                    it in setOf("conditionValue", "targetCategory", "priority")
                                }
                            ) {
                                null
                            } else {
                                CreateRuleState.FormError.Generic
                            }
                        else -> CreateRuleState.FormError.Generic
                    },
                )
            }
        }
    }

    private fun AppError.fieldMessage(field: String): String? =
        (this as? AppError.Validation)?.fields?.get(field)

    private companion object {
        const val MAX_CONDITION_VALUE = 255
    }
}
