package com.spendwise.android.core.session

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for authentication state (AD-011, §13).
 *
 * The persisted session holds only token + expiry + last username/userId —
 * never a password. [cacheInvalidations] fires on every boundary crossing
 * that must drop session-scoped data (sign-out, expiry, or a new identity);
 * session-scoped repositories collect it and clear caches so one user's
 * data can never reach another session (§13 session-boundary hygiene).
 */
@Singleton
class SessionManager @Inject constructor(
    private val tokenStore: TokenStore,
    private val clock: Clock,
) {

    private val _session = MutableStateFlow<Session>(Session.Restoring)
    val session: StateFlow<Session> = _session.asStateFlow()

    private val _cacheInvalidations = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val cacheInvalidations: SharedFlow<Unit> = _cacheInvalidations.asSharedFlow()

    /** Load the persisted session at startup. Expired tokens are discarded. */
    suspend fun restore() {
        val stored = tokenStore.load()
        _session.value = when {
            stored == null -> Session.SignedOut
            !stored.expiresAt.isAfter(clock.instant()) -> {
                tokenStore.clearToken()
                Session.SignedOut
            }
            else -> Session.SignedIn(
                token = stored.token,
                expiresAt = stored.expiresAt,
                username = stored.username,
                userId = stored.userId,
            )
        }
        if (_session.value == Session.SignedOut) emitInvalidation()
    }

    /**
     * Establish a session after a successful login.
     *
     * Identity continuity (§13): when the previous state is [Session.Expired]
     * and the new identity matches it (stable `userId` first, `username`
     * fallback), [Session.SignedIn.resumedSession] is set so the preserved
     * back stack may be resumed. A differing identity resets session state
     * and fires a cache invalidation — the previous user's preserved
     * navigation must not be exposed.
     */
    suspend fun onSignedIn(
        token: String,
        expiresAt: Instant,
        username: String?,
        userId: String? = JwtClaims.subjectOf(token),
    ) {
        tokenStore.save(
            token = token,
            expiresAt = expiresAt,
            username = username,
            userId = userId,
        )
        val previous = _session.value
        val sameIdentity = (previous as? Session.Expired)?.let { expired ->
            identityMatches(expired, username, userId)
        } == true
        _session.value = Session.SignedIn(
            token = token,
            expiresAt = expiresAt,
            username = username,
            userId = userId,
            resumedSession = sameIdentity,
        )
        if (!sameIdentity) emitInvalidation()
    }

    /**
     * Called only when an **authenticated** call returns 401 (§13): public
     * login/register 401s mean bad credentials and must not reach this path;
     * logout 401s are ignored by the caller.
     *
     * The stored credential is dropped — a server-rejected token must never
     * be resurrected on the next cold start. Identity hints survive for
     * resume UX. Suspending because the store write is async; the network
     * interceptor boundary bridges with runBlocking.
     */
    suspend fun onAuthenticationExpired() {
        val current = _session.value as? Session.SignedIn ?: return
        tokenStore.clearToken()
        markExpired(current)
    }

    suspend fun signOut() {
        val hadSession = _session.value !is Session.SignedOut
        tokenStore.clear()
        _session.value = Session.SignedOut
        if (hadSession) emitInvalidation()
    }

    /**
     * Current access token for the auth interceptor, or null.
     *
     * Lightweight expiry check (§13): a locally-expired token is never sent —
     * the session flips to [Session.Expired] instead, and the server remains
     * the authoritative 401 source for anything the local clock missed.
     */
    fun accessToken(): String? {
        val current = _session.value as? Session.SignedIn ?: return null
        if (!current.expiresAt.isAfter(clock.instant())) {
            // In-memory transition only — a locally-expired stored token is
            // already dropped by restore(), no store write needed here.
            markExpired(current)
            return null
        }
        return current.token
    }

    private fun markExpired(current: Session.SignedIn) {
        _session.value = Session.Expired(
            username = current.username,
            userId = current.userId,
        )
        emitInvalidation()
    }

    private fun identityMatches(
        expired: Session.Expired,
        username: String?,
        userId: String?,
    ): Boolean = when {
        expired.userId != null && userId != null -> expired.userId == userId
        else -> expired.username != null && expired.username == username
    }

    private fun emitInvalidation() {
        _cacheInvalidations.tryEmit(Unit)
    }
}
