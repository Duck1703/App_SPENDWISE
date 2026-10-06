package com.spendwise.android

import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.ui.format.DateFormat
import com.spendwise.android.core.ui.format.MoneyFormat
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

/**
 * Locale-aware expected display strings for test assertions.
 *
 * Production renders money and currency labels with `Locale.getDefault()`
 * (EXT-MIN-2 — en-US "1,234.56" vs vi-VN "1.234,56"). Expectations are
 * derived from the same production formatters so assertions stay exact
 * on any device locale instead of baking in en-US separators.
 */
object TestFormats {

    /** Formatted amount digits only — mirrors `MoneyFormat.format`. */
    fun amount(amount: String, code: String): String =
        MoneyFormat.format(BigDecimal(amount), CurrencyCode(code))

    /** `−`/`+` + formatted amount — mirrors `signedAmountText`/row CDs. */
    fun signedAmount(amount: String, code: String, credit: Boolean): String =
        "${if (credit) "+" else "−"}${amount(amount, code)}"

    /** Localized date — mirrors `DateFormat.format` on the device locale. */
    fun date(date: LocalDate): String = DateFormat.format(date)

    /** "USD — US Dollar"-style label — mirrors the ISO currency dropdown. */
    fun currencyOptionLabel(code: String): String = runCatching {
        "$code — ${Currency.getInstance(code).getDisplayName(Locale.getDefault())}"
    }.getOrDefault(code)
}
