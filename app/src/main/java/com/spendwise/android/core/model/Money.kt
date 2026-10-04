package com.spendwise.android.core.model

import java.math.BigDecimal

/**
 * A monetary amount with its currency (AD-007, INV-001/INV-003).
 *
 * `amount` is [BigDecimal] — Float/Double are forbidden for financial values.
 * Every money value carries its currency; there is deliberately no way to
 * combine or compare [Money] across currencies inside this type (INV-003):
 * the backend is authoritative for balances and no client-side aggregation
 * may silently mix currencies.
 */
data class Money(
    val amount: BigDecimal,
    val currency: CurrencyCode,
)
