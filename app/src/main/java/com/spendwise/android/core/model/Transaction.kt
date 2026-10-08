package com.spendwise.android.core.model

import java.time.LocalDate

/**
 * A ledger entry (PRODUCT §19, BACKEND-CONTRACT §11).
 *
 * `amount` is always **positive** — direction lives in [type]
 * (`Transaction.amount` is signed-positive on the backend and the client
 * mirrors that; the UI renders `−`/`+` for display only). [occurredOn]
 * is the server-authoritative economic date (M11/REQ-BE-001) — the
 * client never derives it from device time, import metadata, or any
 * other source; a missing/malformed wire date fails loudly upstream
 * rather than being fabricated here.
 */
data class Transaction(
    val id: String,
    val accountId: String,
    val type: TransactionType,
    val amount: Money,
    /** Server category code (e.g. `FOOD`), or null when uncategorized. */
    val category: String?,
    /** Server-issued provenance string (`MANUAL:`/`IMPORT:`/… prefixes). */
    val sourceRef: String?,
    /** Server-persisted economic date — date-only, no timezone semantics. */
    val occurredOn: LocalDate,
    /**
     * Server-persisted note (M17/REQ-BE-010). Null = no note — every
     * pre-M17 row, recurring-generated rows, and transfer legs.
     * User-authored metadata, never used in any financial computation.
     */
    val description: String? = null,
) {
    /** How this entry reached the ledger — derived from [sourceRef]. */
    val provenance: TransactionProvenance
        get() = TransactionProvenance.fromSourceRef(sourceRef)
}

/**
 * Wire enum is `INCOME`/`EXPENSE`/`TRANSFER_IN`/`TRANSFER_OUT`. A public
 * API client can only create INCOME/EXPENSE, but transfer legs (and future
 * server values) can appear on reads — [UNKNOWN] keeps the ledger
 * decodable instead of crashing (AD-008 open-set rule).
 */
enum class TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER_IN,
    TRANSFER_OUT,
    UNKNOWN;

    /** `true` when the amount renders with a `+` (money in). */
    val isCredit: Boolean
        get() = this == INCOME || this == TRANSFER_IN

    companion object {
        fun fromWire(value: String?): TransactionType =
            entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

/**
 * Entry provenance derived from `sourceRef` prefixes (BACKEND-CONTRACT
 * §10): `MANUAL:<uuid>`, `IMPORT:SRC:…`/`IMPORT:<acct>|…`,
 * `RECURRING:<template>:<date>`, `TRANSFER:<ref>:<leg>`. Anything else is
 * honestly UNKNOWN — never guessed as Manual.
 */
enum class TransactionProvenance {
    MANUAL,
    IMPORTED,
    RECURRING,
    TRANSFER,
    UNKNOWN;

    companion object {
        fun fromSourceRef(sourceRef: String?): TransactionProvenance = when {
            sourceRef == null -> UNKNOWN
            sourceRef.startsWith("MANUAL:") -> MANUAL
            sourceRef.startsWith("IMPORT:") -> IMPORTED
            sourceRef.startsWith("RECURRING:") -> RECURRING
            sourceRef.startsWith("TRANSFER:") -> TRANSFER
            else -> UNKNOWN
        }
    }
}

/**
 * One server page of the ledger (M15/REQ-BE-007 —
 * `GET /api/transactions/page`). [totalElements] is the server's count
 * for the active filter set (always ≥ [items].size); [hasNext] tells the
 * UI whether another page can be requested. Ordering is fixed
 * server-side (`occurredOn DESC, id DESC`).
 */
data class TransactionPage(
    val items: List<Transaction>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
)

/**
 * Result of `POST /api/transfers` (M12/REQ-BE-003): the server-issued
 * linkage key plus both ledger rows it created. [transferRef] is the
 * pair's shared `TRANSFER:<ref>` provenance handle — returned on this
 * response only, never on transaction list reads.
 */
data class TransferOutcome(
    val transferRef: String,
    val outTransaction: Transaction,
    val inTransaction: Transaction,
)
