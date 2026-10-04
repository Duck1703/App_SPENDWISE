package com.spendwise.android.fakes

import com.spendwise.android.core.session.StoredSession
import com.spendwise.android.core.session.TokenStore
import java.time.Instant

/**
 * In-memory [TokenStore] for JVM tests — no Android Keystore required.
 *
 * Mirrors the real store's contract: after [clearToken], [load] returns
 * null (the credential is gone) while [lastUsername] stays observable —
 * matching how the DataStore impl retains the username on disk.
 */
class FakeTokenStore(
    stored: StoredSession? = null,
) : TokenStore {

    var token: String? = stored?.token
    var expiresAt: Instant? = stored?.expiresAt
    var lastUsername: String? = stored?.username

    override suspend fun load(): StoredSession? {
        val t = token ?: return null
        val e = expiresAt ?: return null
        return StoredSession(token = t, expiresAt = e, username = lastUsername)
    }

    override suspend fun save(token: String, expiresAt: Instant, username: String?) {
        this.token = token
        this.expiresAt = expiresAt
        this.lastUsername = username
    }

    override suspend fun clearToken() {
        token = null
        expiresAt = null
    }

    override suspend fun clear() {
        token = null
        expiresAt = null
        lastUsername = null
    }
}
