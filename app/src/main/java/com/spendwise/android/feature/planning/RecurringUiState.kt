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

/**
 * Recurring edit (M16, REQ-BE-012) — the loaded template prefilled into
 * an editable form. Account/direction/currency/active are immutable
 * identity (read-only rows); save builds a sparse PATCH of only the
 * fields that differ.
 */
sealed interface EditRecurringState {
    data object Loading : EditRecurringState
    data object NotFound : EditRecurringState
    data class Error(val kind: PlanningError) : EditRecurringState

    data class Form(
        val template: RecurringTemplate,
        val accountName: String? = null,
        val amount: String,
        val category: String? = null,
        val frequency: RecurringFrequency,
        /** ISO `YYYY-MM-DD` raw input. */
        val nextDueDate: String,
        /** ISO `YYYY-MM-DD` raw input, blank = none. */
        val endDate: String,
        val categories: List<String> = emptyList(),
        val isSubmitting: Boolean = false,
        val amountError: AmountError? = null,
        val dateError: DateError? = null,
        val formError: FormError? = null,
    ) : EditRecurringState {
        /**
         * Sparse-patch detection — the save affordance stays disabled
         * until at least one field actually differs from the loaded
         * template. An unparseable amount counts as changed so the
         * field error can surface on submit.
         */
        val hasChanges: Boolean
            get() {
                if (category != template.category) return true
                if (frequency != template.frequency) return true
                if (nextDueDate.trim() != template.nextDueDate.toString()) return true
                if (endDate.trim() != (template.endDate?.toString() ?: "")) return true
                val parsed = amount.trim().replace(',', '.').toBigDecimalOrNull() ?: return true
                return parsed.compareTo(template.amount.amount) != 0
            }

        enum class AmountError { Required, Invalid, NotPositive, TooPrecise, TooLarge }
        enum class DateError { Required, Invalid, EndBeforeNext }

        /** Same contract as create: [Ambiguous] means the server may have committed. */
        enum class FormError { Ambiguous, Server, Generic }
    }
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
