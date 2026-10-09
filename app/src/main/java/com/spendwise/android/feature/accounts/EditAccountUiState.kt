package com.spendwise.android.feature.accounts

import com.spendwise.android.core.model.Account

/** M19 edit-account screen states (mirrors EditRecurringState). */
sealed interface EditAccountState {
    data object Loading : EditAccountState

    data class Error(val kind: AccountsError) : EditAccountState

    data object NotFound : EditAccountState

    data class Form(
        /** Server truth as loaded — the diff baseline; never mutated. */
        val account: Account,
        /** Working name field (what the user is editing). */
        val name: String,
        /** Working archive intent — applied by the same sparse PATCH. */
        val archived: Boolean,
        val nameError: NameError?,
        val isSubmitting: Boolean,
        val formError: FormError?,
    ) : EditAccountState {

        /** Sparse-patch predicate — nothing differs → nothing to send. */
        val hasChanges: Boolean
            get() = name.trim() != account.name || archived != account.archived

        enum class NameError { Required, Invalid }

        enum class FormError {
            /** Network dropped mid-write — outcome unknown, reload to check. */
            Ambiguous,
            /** 409 — a concurrent write won; reload server truth. */
            Conflict,
            Server,
            Generic,
        }
    }
}
