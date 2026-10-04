package com.spendwise.android.core.session

import java.time.Instant

/** Persisted session payload — token, expiry, last username only (AD-010). */
data class StoredSession(
    val token: String,
    val expiresAt: Instant,
    val username: String?,
)

interface TokenStore {

    suspend fun load(): StoredSession?

    suspend fun save(token: String, expiresAt: Instant, username: String?)

    /** Drop the credential but keep the last username (resume UX). */
    suspend fun clearToken()

    /** Drop everything (sign-out). */
    suspend fun clear()
}
