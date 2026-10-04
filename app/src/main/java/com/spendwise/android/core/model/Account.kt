package com.spendwise.android.core.model

/**
 * An account the user's money sits in (PRODUCT §18, BACKEND-CONTRACT §
 * accounts). `balance` is the **server-authoritative** stored balance —
 * the client never recomputes it from transactions (INV-012).
 */
data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val openingBalance: Money,
    val balance: Money,
)

/**
 * Backend `AccountType` is a closed enum on the wire (`CASH`/`BANK`), but a
 * future server could emit a value this build does not know — UNKNOWN keeps
 * such rows decodable instead of crashing the list (§15 unknown-enum rule).
 */
enum class AccountType {
    CASH,
    BANK,
    UNKNOWN;

    companion object {
        fun fromWire(value: String?): AccountType =
            entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}
