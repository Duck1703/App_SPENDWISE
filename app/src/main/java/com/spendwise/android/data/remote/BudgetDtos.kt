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

/**
 * `UpdateBudgetRequest` (REQ-BE-011, M14): `{limit?, period?}` — every
 * field optional; a `null` stays at its default and is omitted from the
 * wire (kotlinx `encodeDefaults=false`), so the backend sees a true
 * partial update. Currency and category are immutable by contract — a
 * wrong category is fixed by delete + recreate.
 */
@Serializable
data class UpdateBudgetRequestDto(
    @Serializable(with = BigDecimalSerializer::class)
    val limit: BigDecimal? = null,
    val period: String? = null,
)
