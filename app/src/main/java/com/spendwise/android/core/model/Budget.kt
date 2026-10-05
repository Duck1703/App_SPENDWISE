package com.spendwise.android.core.model

import java.math.BigDecimal

/**
 * A user budget (PRODUCT §30 S18–S20, BACKEND-CONTRACT — verified at
 * backend `8194daa`). `period` is the server-shipped `YYYY-MM` key;
 * `limit` is the stored budget amount. Budgets are create/read-only —
 * no update or delete endpoint exists (BE-011).
 */
data class Budget(
    val id: String,
    /** Server category code (e.g. `FOOD`). */
    val category: String,
    /** `YYYY-MM` period key. */
    val period: String,
    val limit: Money,
)

/**
 * `GET /api/budgets/{id}/calculation` — spent/remaining/percentage/
 * status are derived server-side from the ledger at read time; the
 * client renders them verbatim and never recomputes (INV-012).
 * `percentage` arrives scale-2 HALF_UP of limit — kept [BigDecimal].
 */
data class BudgetCalculation(
    val budgetId: String,
    val category: String,
    val period: String,
    val limit: Money,
    val spent: Money,
    val remaining: Money,
    val percentage: BigDecimal,
    val status: BudgetStatus,
)

/**
 * Wire enum is `WITHIN_BUDGET`/`OVER_BUDGET` (closed today). UNKNOWN
 * keeps a future status decodable instead of crashing the detail
 * surface (AD-008 open-set rule).
 */
enum class BudgetStatus {
    WITHIN_BUDGET,
    OVER_BUDGET,
    UNKNOWN;

    companion object {
        fun fromWire(value: String?): BudgetStatus =
            entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}
