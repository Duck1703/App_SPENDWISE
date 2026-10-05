package com.spendwise.android.core.model

import java.math.BigDecimal

/**
 * Server-computed month summary (PRODUCT §17, BACKEND-CONTRACT §22).
 *
 * The totals are bare `BigDecimal` sums — **the server returns no
 * currency**. A label is only derivable client-side under the §17
 * rule: all accounts share one currency → figures display with that
 * code; anything else → the honest "combined totals unavailable"
 * state. The model deliberately carries no currency so nothing can
 * guess one.
 */
data class DashboardMonth(
    /** Server `YYYY-MM` as returned. */
    val month: String,
    val totalIncome: BigDecimal,
    val totalExpense: BigDecimal,
)
