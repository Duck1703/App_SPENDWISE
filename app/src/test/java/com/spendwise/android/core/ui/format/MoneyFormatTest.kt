package com.spendwise.android.core.ui.format

import com.spendwise.android.core.model.AccountType
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class MoneyFormatTest {

    private fun money(amount: String, currency: String) =
        Money(BigDecimal(amount), CurrencyCode(currency))

    @Test
    fun `VND renders with zero fraction digits and grouping`() {
        assertEquals(
            "1,250,500",
            MoneyFormat.format(money("1250500", "VND"), Locale.US),
        )
    }

    @Test
    fun `USD renders two fraction digits`() {
        assertEquals(
            "1,250.50",
            MoneyFormat.format(money("1250.5", "USD"), Locale.US),
        )
    }

    @Test
    fun `grouping uses the locale separator`() {
        val de = MoneyFormat.format(money("1250500", "VND"), Locale.GERMANY)
        assertEquals("1.250.500", de)
    }

    // EXT-MIN-2: the decimal separator follows the locale too — a
    // fractional amount in a `,`-decimal locale is unambiguous.

    @Test
    fun `vi-VN fractional amount uses dot grouping and comma decimal`() {
        val vi = Locale.forLanguageTag("vi-VN")
        assertEquals(
            "1.234,56",
            MoneyFormat.format(money("1234.56", "USD"), vi),
        )
    }

    @Test
    fun `de-DE million-scale fractional amount`() {
        assertEquals(
            "1.000.000,50",
            MoneyFormat.format(money("1000000.5", "USD"), Locale.GERMANY),
        )
    }

    @Test
    fun `en-US fractional amount`() {
        assertEquals(
            "1,234.56",
            MoneyFormat.format(money("1234.56", "USD"), Locale.US),
        )
    }

    @Test
    fun `sub-unit fraction in vi-VN`() {
        val vi = Locale.forLanguageTag("vi-VN")
        assertEquals(
            "0,01",
            MoneyFormat.format(money("0.01", "USD"), vi),
        )
    }

    @Test
    fun `excess precision rounds HALF_UP on the BigDecimal text`() {
        assertEquals(
            "10.13",
            MoneyFormat.format(money("10.125", "USD"), Locale.US),
        )
    }

    @Test
    fun `negative amounts keep the sign outside the grouping`() {
        assertEquals(
            "-1,250.00",
            MoneyFormat.format(money("-1250", "USD"), Locale.US),
        )
    }

    @Test
    fun `unknown ISO-shaped codes fall back to two digits`() {
        // Syntactically valid but not JDK-known → open-set rule.
        assertEquals(2, MoneyFormat.fractionDigits(CurrencyCode("ZZZ")))
    }

    @Test
    fun `AccountType maps known values and tolerates unknown ones`() {
        assertEquals(AccountType.CASH, AccountType.fromWire("CASH"))
        assertEquals(AccountType.BANK, AccountType.fromWire("BANK"))
        assertEquals(AccountType.UNKNOWN, AccountType.fromWire("SAVINGS_VAULT"))
        assertEquals(AccountType.UNKNOWN, AccountType.fromWire(null))
    }
}
