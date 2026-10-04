package com.spendwise.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class MoneyCurrencyTest {

    @Test
    fun `known currency codes are accepted`() {
        assertEquals("VND", CurrencyCode("VND").code)
        assertEquals("USD", CurrencyCode("USD").code)
    }

    @Test
    fun `unknown-but-syntactically-valid codes are supported`() {
        // INV: never crash on a valid code the app does not know.
        assertEquals("XYZ", CurrencyCode("XYZ").code)
        assertEquals("ZZZ", CurrencyCode("ZZZ").code)
    }

    @Test
    fun `invalid currency syntax is rejected`() {
        listOf("", "US", "USDT", "usd", "Us1", "12A", "U D").forEach { invalid ->
            assertThrows("expected rejection of '$invalid'", IllegalArgumentException::class.java) {
                CurrencyCode(invalid)
            }
        }
    }

    @Test
    fun `money carries BigDecimal amount and currency`() {
        val money = Money(BigDecimal("19.99"), CurrencyCode("USD"))
        assertEquals(BigDecimal("19.99"), money.amount)
        assertEquals(CurrencyCode("USD"), money.currency)
    }
}
