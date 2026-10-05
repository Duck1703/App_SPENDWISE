package com.spendwise.android.feature.planning

import com.spendwise.android.core.model.Account
import com.spendwise.android.core.model.RecurringFrequency
import com.spendwise.android.core.model.RecurringTemplate
import com.spendwise.android.core.model.TransactionType

/** Shared list/detail error kinds for the planning surfaces. */
enum class PlanningError { Network, Server, Generic }

/** Recurring list (S15) — loading / error / empty / content. */
sealed interface RecurringListState {
    data object Loading : RecurringListState
    data class Error(val kind: PlanningError) : RecurringListState
    data object Empty : RecurringListState

    /** [accountNames] labels rows; [refreshError] drives the inline banner. */
    data class Content(
        val templates: List<RecurringTemplate>,
        val accountNames: Map<String, String> = emptyMap(),
        val isRefreshing: Boolean = false,
        val refreshError: PlanningError? = null,
    ) : RecurringListState
}

/** Recurring detail (S17) — content + soft-delete affordance. */
sealed interface RecurringDetailState {
    data object Loading : RecurringDetailState
    data object NotFound : RecurringDetailState
    data class Error(val kind: PlanningError) : RecurringDetailState

    /**
     * [isDeactivating] locks the confirm action; [actionError] renders a
     * form-level banner when the deactivate call fails.
     */
    data class Content(
        val template: RecurringTemplate,
        val accountName: String? = null,
        val isDeactivating: Boolean = false,
        val actionError: PlanningError? = null,
    ) : RecurringDetailState
}

/** Recurring create (S16) — draft + options + field errors. */
data class CreateRecurringState(
    val direction: TransactionType = TransactionType.EXPENSE,
    val accountId: String? = null,
    val amount: String = "",
    val category: String? = null,
    val frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
    /** ISO `YYYY-MM-DD` raw input. */
    val nextDueDate: String = "",
    /** ISO `YYYY-MM-DD` raw input, blank = none. */
    val endDate: String = "",
    val accounts: List<Account> = emptyList(),
    val categories: List<String> = emptyList(),
    val optionsLoading: Boolean = true,
    val optionsError: Boolean = false,
    val isSubmitting: Boolean = false,
    val accountError: AccountError? = null,
    val amountError: AmountError? = null,
    val dateError: DateError? = null,
    val formError: FormError? = null,
) {
    val selectedAccount: Account? get() = accounts.firstOrNull { it.id == accountId }

    enum class AccountError { Required }
    enum class AmountError { Required, Invalid, NotPositive, TooPrecise, TooLarge }
    enum class DateError { Required, Invalid, EndBeforeNext }

    /**
     * Non-field-level outcomes. [Ambiguous] = the server may have
     * committed (network loss/timeout mid-write, dead-generation) —
     * copy must never claim nothing happened.
     */
    enum class FormError { Ambiguous, Server, Generic, AccountUnavailable }
}
