package com.spendwise.android.core.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppErrorMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `400 with fields maps to Validation`() {
        val error = ApiException(status = 400, fields = mapOf("username" to "taken")).toAppError()
        assertEquals(AppError.Validation(mapOf("username" to "taken")), error)
    }

    @Test
    fun `400 without fields maps to BadRequest`() {
        assertEquals(AppError.BadRequest, ApiException(status = 400).toAppError())
    }

    @Test
    fun `401 maps to SessionExpired for authenticated calls`() {
        assertEquals(AppError.SessionExpired, ApiException(status = 401).toAppError())
    }

    @Test
    fun `404 maps to NotFound`() {
        assertEquals(AppError.NotFound, ApiException(status = 404).toAppError())
    }

    @Test
    fun `409 maps to Conflict`() {
        assertEquals(AppError.Conflict, ApiException(status = 409).toAppError())
    }

    @Test
    fun `5xx maps to Server with status preserved`() {
        assertEquals(AppError.Server(503), ApiException(status = 503).toAppError())
    }

    @Test
    fun `unhandled status maps to Unknown`() {
        assertTrue(ApiException(status = 418).toAppError() is AppError.Unknown)
    }

    @Test
    fun `429 maps to Unknown — no invented retry semantics`() {
        // Wave-2 correction: 429 does not hit the Server branch (5xx
        // only) — it surfaces as Unknown → generic error UI. The
        // backend contract defines no rate-limit semantics; the client
        // must not invent one.
        val error = ApiException(status = 429).toAppError()
        assertTrue(error is AppError.Unknown)
    }

    @Test
    fun `problem detail DTO decodes tolerantly and ignores unknown keys`() {
        val body = """{"type":"about:blank","title":"Validation failed","status":400,"detail":"see fields","instance":"/api/x","fields":{"a":"b"},"surprise":42}"""
        val dto = json.decodeFromString<ProblemDetailDto>(body)

        assertEquals("Validation failed", dto.title)
        assertEquals(400, dto.status)
        assertEquals(mapOf("a" to "b"), dto.fields)
    }

    @Test
    fun `problem detail DTO decodes minimal body`() {
        val dto = json.decodeFromString<ProblemDetailDto>("""{"title":"Nope"}""")
        assertEquals("Nope", dto.title)
        assertNull(dto.fields)
    }
}
