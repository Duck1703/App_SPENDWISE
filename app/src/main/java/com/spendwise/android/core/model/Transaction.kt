package com.spendwise.android.core.model

/**
 * A ledger entry (PRODUCT §19, BACKEND-CONTRACT §11).
 *
 * `amount` is always **positive** — direction lives in [type]
 * (`Transaction.amount` is signed-positive on the backend and the client
 * mirrors that; the UI renders `−`/`+` for display only). No `occurredOn`:
 * the public REST surface never returns a transaction date, so no date
 * field exists here to be fabricated.
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
