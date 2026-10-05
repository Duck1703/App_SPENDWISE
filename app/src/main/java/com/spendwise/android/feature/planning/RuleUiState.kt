package com.spendwise.android.feature.planning

import com.spendwise.android.core.model.CategorizationRule
import com.spendwise.android.core.model.RuleConditionType

/** Rules list (S21) — loading / error / empty / content (priority order). */
sealed interface RulesListState {
    data object Loading : RulesListState
    data class Error(val kind: PlanningError) : RulesListState
    data object Empty : RulesListState

    data class Content(
        val rules: List<CategorizationRule>,
        val isRefreshing: Boolean = false,
        val refreshError: PlanningError? = null,
    ) : RulesListState
}

/** Rule detail (S23) — content + soft-disable affordance. */
sealed interface RuleDetailState {
    data object Loading : RuleDetailState
    data object NotFound : RuleDetailState
    data class Error(val kind: PlanningError) : RuleDetailState

    data class Content(
        val rule: CategorizationRule,
        val isDisabling: Boolean = false,
        val actionError: PlanningError? = null,
    ) : RuleDetailState
}

/** Rule create (S22) — draft + options + field errors. */
data class CreateRuleState(
    val conditionType: RuleConditionType = RuleConditionType.CONTAINS,
    val conditionValue: String = "",
    val targetCategory: String? = null,
    /** Decimal integer raw input. */
    val priority: String = "",
    val categories: List<String> = emptyList(),
    val optionsLoading: Boolean = true,
    val optionsError: Boolean = false,
    val isSubmitting: Boolean = false,
    val valueError: ValueError? = null,
    val categoryError: CategoryError? = null,
    val priorityError: PriorityError? = null,
    val formError: FormError? = null,
) {
    enum class ValueError { Required, TooLong }
    enum class CategoryError { Required }
    enum class PriorityError { Required, Invalid, Conflict }

    /**
     * [Ambiguous] = the server may have committed — never claimed safe.
     */
    enum class FormError { Ambiguous, Server, Generic }
}
