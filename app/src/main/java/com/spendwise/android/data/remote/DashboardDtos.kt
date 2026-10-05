package com.spendwise.android.data.remote

import com.spendwise.android.core.serialization.BigDecimalSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * `DashboardMonthResponse` (BACKEND-CONTRACT §22, `DashboardController`
 * at backend `8194daa`):
 * `{month, totalIncome, totalExpense, categoryBreakdown[], accounts[],
 * budgets[]}`.
 *
 * **No currency field exists on any total** — the server returns bare
 * `BigDecimal` sums. The §17 labeling rule derives the display
 * currency from the uniform account-currency precondition, never from
 * this payload.
 *
 * `accounts`/`budgets`/`categoryBreakdown` are decoded for contract
 * fidelity. Home renders totals + account balances only — breakdown
 * and budget cards are the P3A §7 deliberate omission (budget
 * management is M6).
 */
@Serializable
data class DashboardMonthDto(
    val month: String,
    @Serializable(with = BigDecimalSerializer::class)
    val totalIncome: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val totalExpense: BigDecimal,
    val categoryBreakdown: List<CategoryBreakdownItemDto> = emptyList(),
    val accounts: List<AccountDto> = emptyList(),
    val budgets: List<BudgetCalculationDto> = emptyList(),
)

/** `{categoryCode, amount}` — categorized EXPENSE sums, code-sorted. */
@Serializable
data class CategoryBreakdownItemDto(
    val categoryCode: String,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
)

/**
 * `BudgetCalculationResponse`: `{budgetId, category, period, limit,
 * currency, spent, remaining, percentage, status}` —
 * `status ∈ {WITHIN_BUDGET, OVER_BUDGET}`. Server-calculated per the
 * requested month; mismatched-currency rows are silently excluded
 * server-side (BG-013).
 */
@Serializable
data class BudgetCalculationDto(
    val budgetId: String,
    val category: String? = null,
    val period: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val limit: BigDecimal,
    val currency: String,
    @Serializable(with = BigDecimalSerializer::class)
    val spent: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val remaining: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val percentage: BigDecimal,
    val status: String? = null,
)
