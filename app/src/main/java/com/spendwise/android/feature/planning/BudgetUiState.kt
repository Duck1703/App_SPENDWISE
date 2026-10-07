package com.spendwise.android.feature.planning

import com.spendwise.android.core.model.Budget
import com.spendwise.android.core.model.BudgetCalculation

/** Budgets list (S18) — loading / error / empty / content. */
sealed interface BudgetsListState {
    data object Loading : BudgetsListState
    data class Error(val kind: PlanningError) : BudgetsListState
    data object Empty : BudgetsListState

    data class Content(
        val budgets: List<Budget>,
        val isRefreshing: Boolean = false,
        val refreshError: PlanningError? = null,
    ) : BudgetsListState
}

/** Budget detail (S20) — the server-derived calculation verbatim. */
sealed interface BudgetDetailState {
    data object Loading : BudgetDetailState
    data object NotFound : BudgetDetailState
    data class Error(val kind: PlanningError) : BudgetDetailState

    data class Content(
        val calculation: BudgetCalculation,
        val isRefreshing: Boolean = false,
        val refreshError: PlanningError? = null,
    ) : BudgetDetailState
}

/**
 * Budget edit dialog (M14, REQ-BE-011): `limit` + `period` are the only
 * correctable fields — category and currency are immutable on the
 * backend, so a wrong category is fixed by delete + recreate. Errors
 * reuse the create-form enums; [Conflict] means the target
 * (category, period) pair is already taken.
 */
data class EditBudgetState(
    val visible: Boolean = false,
    val limit: String = "",
    val period: String = "",
    val isSubmitting: Boolean = false,
    val limitError: CreateBudgetState.LimitError? = null,
    val periodError: CreateBudgetState.PeriodError? = null,
    val formError: FormError? = null,
) {
    enum class FormError { Conflict, Ambiguous, Server, Generic }
}

/** Budget create (S19) — draft + options + field errors. */
data class CreateBudgetState(
    val category: String? = null,
    /** `YYYY-MM` raw input — defaults to the current month. */
    val period: String = "",
    val limit: String = "",
    val currency: String? = null,
    /** Currency options derived from the user's accounts (see VM). */
    val currencies: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val optionsLoading: Boolean = true,
    val optionsError: Boolean = false,
    val isSubmitting: Boolean = false,
    val categoryError: CategoryError? = null,
    val periodError: PeriodError? = null,
    val limitError: LimitError? = null,
    val currencyError: CurrencyError? = null,
    val formError: FormError? = null,
) {
    enum class CategoryError { Required }
    enum class PeriodError { Required, Invalid }
    enum class LimitError { Required, Invalid, NotPositive, TooPrecise, TooLarge }
    enum class CurrencyError { Required }

    /**
     * [Conflict] is the honest 409 — a budget already exists for this
     * category+period and the backend offers no edit (BE-011).
     * [Ambiguous] = the server may have committed — never claimed safe.
     */
    enum class FormError { Conflict, Ambiguous, Server, Generic }
}
