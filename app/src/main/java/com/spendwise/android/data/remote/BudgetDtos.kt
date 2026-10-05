package com.spendwise.android.data.remote

import com.spendwise.android.core.serialization.BigDecimalSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * `BudgetResponse` / `CreateBudgetRequest` / `BudgetCalculationResponse`
 * (verified against `BudgetController`, `BudgetResponse`,
 * `BudgetCalculationResponse`, `CreateBudgetRequest` at backend
 * `8194daa`).
 *
 * `period` is the `YYYY-MM` key; `limit`/`spent`/`remaining` are
 * `BigDecimal` over the wire; `percentage` is the server's scale-2
 * HALF_UP figure — displayed verbatim, never recomputed.
 */
@Serializable
data class BudgetDto(
    val id: String,
    val category: String,
    val period: String,
    @Serializable(with = BigDecimalSerializer::class)
    val limit: BigDecimal,
    val currency: String,
)

/** `BudgetCalculationResponse` DTO lives in [DashboardDtos] (M4). */

/**
 * `CreateBudgetRequest`: `{category, period, limit, currency}` —
 * `category` is the server code, `period` is `YYYY-MM`, `limit`
 * strictly positive ≤ `Digits(17,2)`.
 */
@Serializable
data class CreateBudgetRequestDto(
    val category: String,
    val period: String,
    @Serializable(with = BigDecimalSerializer::class)
    val limit: BigDecimal,
    val currency: String,
)
