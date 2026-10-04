package com.spendwise.android.core.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import java.math.BigDecimal

/**
 * Precise `BigDecimal` JSON serializer (AD-006).
 *
 * Serialization emits an **unquoted JSON number** — matching the backend's
 * JSON-number shape for amounts — so precision and scale round-trip through
 * the wire text instead of going through a binary floating-point type.
 * Deserialization accepts both JSON numbers and quoted decimal strings and
 * preserves the exact textual value.
 */
object BigDecimalSerializer : KSerializer<BigDecimal> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.spendwise.android.BigDecimal", PrimitiveKind.DOUBLE)

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: BigDecimal) {
        if (encoder is JsonEncoder) {
            encoder.encodeJsonElement(JsonUnquotedLiteral(value.toPlainString()))
        } else {
            encoder.encodeString(value.toPlainString())
        }
    }

    override fun deserialize(decoder: Decoder): BigDecimal =
        if (decoder is JsonDecoder) {
            when (val element = decoder.decodeJsonElement()) {
                is JsonPrimitive -> element.toBigDecimal()
                else -> throw SerializationException("Expected a JSON number for BigDecimal, got $element")
            }
        } else {
            BigDecimal(decoder.decodeString())
        }

    private fun JsonPrimitive.toBigDecimal(): BigDecimal =
        try {
            // `content` is the raw JSON text for numbers and the unquoted
            // value for strings — either way it is parsed exactly, never
            // via Double.
            BigDecimal(content)
        } catch (e: NumberFormatException) {
            throw SerializationException("Invalid BigDecimal literal: '$content'", e)
        }
}
