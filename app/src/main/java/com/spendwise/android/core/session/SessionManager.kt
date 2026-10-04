package com.spendwise.android.core.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for authentication state (AD-011).
 *
 * The persisted session holds only token + expiry + last username — never a
 * password (INV-012). Repositories with session-scoped caches must observe
 * [session] transitions and clear caches so a new identity never sees a
 * prior user's data (ARCHITECTURE §10.4).
 */
@Singleton
class SessionManager @Inject constructor(
    private val tokenStore: TokenStore,
    private val clock: Clock,
) {

    private val _session = MutableStateFlow<Session>(Session.Restoring)
    val session: StateFlow<Session> = _session.asStateFlow()

    /** Load the persisted session at startup. Expired tokens are discarded. */
    suspend fun restore() {
        val stored = tokenStore.load()
        _session.value = when {
            stored == null -> Session.SignedOut
            !stored.expiresAt.isAfter(clock.instant()) -> {
                // Token already dead: keep the last username for resume UX,
                // drop the credential.
                tokenStore.clearToken()
                Session.SignedOut
            }
            else -> Session.SignedIn(
                token = stored.token,
                expiresAt = stored.expiresAt,
                username = stored.username,
            )
        }
    }

    suspend fun onSignedIn(token: String, expiresAt: Instant, username: String?) {
        tokenStore.save(token = token, expiresAt = expiresAt, username = username)
        _session.value = Session.SignedIn(token, expiresAt, username)
    }

    /**
     * Called only when an **authenticated** call returns 401 (ARCHITECTURE
     * §10.2): login/register 401s mean bad credentials and must not reach
     * this path; logout 401s are ignored.
     */
    fun onAuthenticationExpired() {
        val current = _session.value
        if (current is Session.SignedIn) {
            _session.value = Session.Expired(current.username)
        }
    }

    suspend fun signOut() {
        tokenStore.clear()
        _session.value = Session.SignedOut
    }

    /** Current access token for the auth interceptor, or null. */
    fun accessToken(): String? = (_session.value as? Session.SignedIn)?.token
}
