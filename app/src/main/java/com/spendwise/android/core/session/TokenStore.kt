package com.spendwise.android.core.session

import java.time.Instant

/** Persisted session payload — token, expiry, last username/userId only (AD-010). */
data class StoredSession(
    val token: String,
    val expiresAt: Instant,
    val username: String?,
    val userId: String?,
)

interface TokenStore {

    suspend fun load(): StoredSession?

    suspend fun save(token: String, expiresAt: Instant, username: String?, userId: String?)

    /** Drop the credential but keep identity hints (resume UX). */
    suspend fun clearToken()

    /** Drop everything (sign-out). */
    suspend fun clear()
}
