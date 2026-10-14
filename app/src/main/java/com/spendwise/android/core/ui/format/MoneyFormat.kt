package com.spendwise.android.core.ui.format

import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Money display formatting (HANDOFF §8): locale-grouped amount with the
 * currency's fraction digits; the currency code renders as a separate
 * muted suffix in the UI (never wrapped away from the amount).
 *
 * Precision: [DecimalFormat.format] on a [BigDecimal] is exact decimal
 * formatting — no Float/Double anywhere. Both grouping and decimal
 * separators follow the locale (EXT-MIN-2: en-US "1,234.56", vi-VN
 * "1.234,56") — presentation only, the stored value is untouched.
 *
 * Fraction digits come from the ISO currency (VND→0, USD→2) via
 * `java.util.Currency`, falling back to 2 for codes the JDK does not
 * know (open-set [CurrencyCode] values stay displayable). A currency
 * whose ISO width is narrower than the backend's stored scale (VND:
 * NUMERIC(19,2)) renders a stored fraction in full ("100,50") instead
 * of rounding it away; whole amounts still render at the ISO width.
 */
object MoneyFormat {

    /** Backend storage scale (NUMERIC(19,2)) — the widest fraction a stored amount can carry. */
    private const val STORED_FRACTION_DIGITS = 2

    fun format(money: Money, locale: Locale = Locale.getDefault()): String =
        format(money.amount, money.currency, locale)

    fun format(amount: BigDecimal, currency: CurrencyCode, locale: Locale = Locale.getDefault()): String =
        formatter(locale, displayFractionDigits(amount, currency)).format(amount)

    /** ISO-4217 fraction digits, or 2 when the JDK does not know the code. */
    fun fractionDigits(currency: CurrencyCode): Int =
        runCatching { java.util.Currency.getInstance(currency.code).defaultFractionDigits }
            .getOrDefault(2)
            .let { if (it < 0) 2 else it }

    /**
     * Display width: normally the ISO currency's digits. When that width
     * is narrower than the stored scale (VND→0, JPY→0), a fractional
     * amount renders at the full stored scale instead of being rounded
     * to a wrong-looking integer; a whole amount keeps the ISO width.
     */
    private fun displayFractionDigits(amount: BigDecimal, currency: CurrencyCode): Int {
        val iso = fractionDigits(currency)
        if (iso >= STORED_FRACTION_DIGITS) return iso
        return if (amount.stripTrailingZeros().scale() > 0) STORED_FRACTION_DIGITS else iso
    }

    /**
     * `DecimalFormat` is not thread-safe — a fresh instance per call
     * (formatting is display-path, never hot enough to matter).
     */
    private fun formatter(locale: Locale, fractionDigits: Int): DecimalFormat =
        DecimalFormat().apply {
            decimalFormatSymbols = DecimalFormatSymbols.getInstance(locale)
            minimumFractionDigits = fractionDigits
            maximumFractionDigits = fractionDigits
            isGroupingUsed = true
            groupingSize = 3
            roundingMode = RoundingMode.HALF_UP
        }
}
