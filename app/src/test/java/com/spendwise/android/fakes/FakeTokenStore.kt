package com.spendwise.android.fakes

import com.spendwise.android.core.session.StoredSession
import com.spendwise.android.core.session.TokenStore
import java.time.Instant

/**
 * In-memory [TokenStore] for JVM tests — no Android Keystore required.
 *
 * Mirrors the real store's contract: after [clearToken], [load] returns
 * null (the credential is gone) while the identity hints stay observable —
 * matching how the DataStore impl retains username/userId on disk.
 */
class FakeTokenStore(
    stored: StoredSession? = null,
) : TokenStore {

    /** Storage-failure injection: thrown by [load] / write ops when set. */
    var loadError: Throwable? = null
    var writeError: Throwable? = null
    var clearTokenCalls = 0

    var token: String? = stored?.token
    var expiresAt: Instant? = stored?.expiresAt
    var lastUsername: String? = stored?.username
    var lastUserId: String? = stored?.userId

    override suspend fun load(): StoredSession? {
        loadError?.let { throw it }
        val t = token ?: return null
        val e = expiresAt ?: return null
        return StoredSession(
            token = t,
            expiresAt = e,
            username = lastUsername,
            userId = lastUserId,
        )
    }

    override suspend fun save(token: String, expiresAt: Instant, username: String?, userId: String?) {
        writeError?.let { throw it }
        this.token = token
        this.expiresAt = expiresAt
        this.lastUsername = username
        this.lastUserId = userId
    }

    override suspend fun clearToken() {
        clearTokenCalls++
        writeError?.let { throw it }
        token = null
        expiresAt = null
    }

    override suspend fun clear() {
        writeError?.let { throw it }
        token = null
        expiresAt = null
        lastUsername = null
        lastUserId = null
    }
}
