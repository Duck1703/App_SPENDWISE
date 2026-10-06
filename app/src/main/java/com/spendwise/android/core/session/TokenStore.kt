package com.spendwise.android.core.session

import java.time.Instant

/** Persisted session payload — credentials, expiry, last username/userId (AD-010).
 *  [refreshToken] is absent on pre-M13 blobs — reads as `null`, legacy behavior. */
data class StoredSession(
    val token: String,
    val expiresAt: Instant,
    val username: String?,
    val userId: String?,
    val refreshToken: String? = null,
) {
    // Credential material — redacted like Session.SignedIn.
    override fun toString(): String =
        "StoredSession(token=***, expiresAt=$expiresAt, username=$username, " +
            "userId=$userId, refreshToken=${if (refreshToken != null) "***" else "null"})"
}

interface TokenStore {

    suspend fun load(): StoredSession?

    suspend fun save(
        token: String,
        expiresAt: Instant,
        username: String?,
        userId: String?,
        refreshToken: String? = null,
    )

    /** Drop every credential but keep identity hints (resume UX). */
    suspend fun clearToken()

    /** Drop everything (sign-out). */
    suspend fun clear()
}
