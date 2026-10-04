package com.spendwise.android.core.ui.format

import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Money display formatting (HANDOFF §8): grouped amount with the currency's
 * fraction digits; the currency code renders as a separate muted suffix in
 * the UI (never wrapped away from the amount).
 *
 * Precision: works on [BigDecimal] text only — no Float/Double anywhere.
 * Fraction digits come from the ISO currency (VND→0, USD→2) via
 * `java.util.Currency`, falling back to 2 for codes the JDK does not know
 * (open-set [CurrencyCode] values stay displayable).
 */
object MoneyFormat {

    fun format(money: Money, locale: Locale = Locale.getDefault()): String =
        format(money.amount, money.currency, locale)

    fun format(amount: BigDecimal, currency: CurrencyCode, locale: Locale = Locale.getDefault()): String {
        val digits = fractionDigits(currency)
        val scaled = amount.setScale(digits, RoundingMode.HALF_UP)
        return groupIntegerPart(scaled.toPlainString(), DecimalFormatSymbols.getInstance(locale).groupingSeparator)
    }

    /** ISO-4217 fraction digits, or 2 when the JDK does not know the code. */
    fun fractionDigits(currency: CurrencyCode): Int =
        runCatching { java.util.Currency.getInstance(currency.code).defaultFractionDigits }
            .getOrDefault(2)
            .let { if (it < 0) 2 else it }

    private fun groupIntegerPart(plain: String, separator: Char): String {
        val negative = plain.startsWith('-')
        val unsigned = if (negative) plain.substring(1) else plain
        val dot = unsigned.indexOf('.')
        val intPart = if (dot < 0) unsigned else unsigned.substring(0, dot)
        val fracPart = if (dot < 0) "" else unsigned.substring(dot)
        val grouped = buildString {
            intPart.forEachIndexed { index, c ->
                if (index > 0 && (intPart.length - index) % 3 == 0) append(separator)
                append(c)
            }
        }
        return (if (negative) "-" else "") + grouped + fracPart
    }
}
