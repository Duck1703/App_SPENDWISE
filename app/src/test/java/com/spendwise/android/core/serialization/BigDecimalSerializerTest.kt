package com.spendwise.android.core.serialization

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal

class BigDecimalSerializerTest {

    @Serializable
    private data class AmountHolder(
        @Serializable(with = BigDecimalSerializer::class)
        val amount: BigDecimal,
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `serialize emits unquoted JSON number`() {
        val encoded = json.encodeToString(AmountHolder(BigDecimal("0.01")))
        assertEquals("""{"amount":0.01}""", encoded)
    }

    @Test
    fun `serialize integer without decimal point`() {
        val encoded = json.encodeToString(AmountHolder(BigDecimal("100")))
        assertEquals("""{"amount":100}""", encoded)
    }

    @Test
    fun `roundtrip preserves exact value`() {
        val original = BigDecimal("1234567890.0987654321")
        val decoded = json.decodeFromString<AmountHolder>(json.encodeToString(AmountHolder(original)))
        assertEquals(original.toPlainString(), decoded.amount.toPlainString())
    }

    @Test
    fun `roundtrip preserves scale - trailing zeros survive the wire`() {
        val original = BigDecimal("10.50")
        val decoded = json.decodeFromString<AmountHolder>(json.encodeToString(AmountHolder(original)))
        // BigDecimal.equals is scale-sensitive: scale 2 must survive.
        assertEquals(original, decoded.amount)
        assertEquals("10.50", decoded.amount.toPlainString())
    }

    @Test
    fun `decode accepts quoted decimal string`() {
        val decoded = json.decodeFromString<AmountHolder>("""{"amount":"42.125"}""")
        assertEquals(BigDecimal("42.125"), decoded.amount)
    }

    @Test
    fun `decode rejects non-numeric literal`() {
        try {
            json.decodeFromString<AmountHolder>("""{"amount":"abc"}""")
            fail("Expected SerializationException")
        } catch (expected: SerializationException) {
            // expected
        }
    }

    @Test
    fun `serializer never routes through floating point`() {
        // 0.1 is not exactly representable in binary FP; the wire text must
        // carry the literal "0.1" verbatim.
        val encoded = json.encodeToString(AmountHolder(BigDecimal("0.1")))
        assertTrue(encoded.contains("0.1"))
        assertEquals("""{"amount":0.1}""", encoded)
    }
}
