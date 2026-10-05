package com.spendwise.android.core.model

import java.time.LocalDate

/**
 * A recurring-transaction template (PRODUCT §30 S15–S17, backend
 * `8194daa`). Generation is server-owned (`RecurringGenerationService`
 * sweeps due templates into `RECURRING:`-sourced ledger rows); this
 * model is the template only — the client never computes occurrences.
 */
data class RecurringTemplate(
    val id: String,
    val accountId: String,
    /** INCOME or EXPENSE on create; UNKNOWN armors future wire values. */
    val direction: TransactionType,
    val amount: Money,
    /** Server category code, or null when the template is uncategorized. */
    val category: String?,
    val frequency: RecurringFrequency,
    val nextDueDate: LocalDate,
    val endDate: LocalDate?,
    /** `false` after `DELETE /{id}` — deactivation, not deletion. */
    val active: Boolean,
)

/**
 * Closed wire enum today (`WEEKLY`/`MONTHLY`); UNKNOWN keeps a future
 * frequency decodable (AD-008).
 */
enum class RecurringFrequency {
    WEEKLY,
    MONTHLY,
    UNKNOWN;

    companion object {
        fun fromWire(value: String?): RecurringFrequency =
            entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}
