package com.spendwise.android.core.session

import kotlinx.serialization.json.Json
import java.util.Base64
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Best-effort, NON-AUTHORITATIVE JWT payload reader (ARCHITECTURE §13,
 * backend contract: `sub` = user UUID).
 *
 * Used ONLY for client-side identity continuity (same-user reauth guard).
 * The client never validates the signature and never treats claims as
 * authorization truth — the backend remains authoritative (INV).
 */
object JwtClaims {

    private val json = Json { ignoreUnknownKeys = true }

    fun subjectOf(token: String): String? {
        val parts = token.split('.')
        if (parts.size != 3) return null
        val payload = runCatching {
            String(Base64.getUrlDecoder().decode(parts[1]))
        }.getOrNull() ?: return null
        return runCatching {
            json.parseToJsonElement(payload)
                .jsonObject["sub"]
                ?.jsonPrimitive
                ?.contentOrNull
        }.getOrNull()
    }
}
