package com.spendwise.android.core.session

import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
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

    /**
     * Monotonic session/cache generation (M2 remediation EXT-MAJOR-1).
     *
     * Incremented **synchronously** inside [emitInvalidation] — before the
     * async signal is ever emitted — on every boundary that invalidates
     * session-scoped data (sign-out, expiry, a different identity). A
     * generation captured at the start of a repository operation can
     * therefore never alias a later session, and a stale write/read is
     * detected by comparison alone — no reliance on collector timing,
     * dispatcher ordering, or ViewModel disposal. `@Volatile` via
     * [AtomicLong]: reads/writes are ordered across threads and two
     * concurrent invalidations can never collapse into one.
     */
    private val _sessionGeneration = AtomicLong(0L)

    /** Current session generation; safe to read from any thread. */
    val sessionGeneration: Long
        get() = _sessionGeneration.get()

    /**
     * Load the persisted session at startup. Expired tokens are discarded.
     *
     * A storage read failure (e.g. an `IOException` from disk — distinct
     * from protobuf corruption, which the store handles upstream) fails
     * closed to [Session.SignedOut] without deleting the credential: the
     * failure may be transient and the next restore may succeed. Only
     * `IOException` is caught — cancellation propagates and programmer
     * defects must surface, not be masked (M1 audit EXT-MINOR-1).
     */
    suspend fun restore() {
        val stored = try {
            tokenStore.load()
        } catch (e: IOException) {
            null
        }
        _session.value = when {
            stored == null -> Session.SignedOut
            !stored.expiresAt.isAfter(clock.instant()) -> {
                try {
                    tokenStore.clearToken()
                } catch (e: IOException) {
                    // Best-effort cleanup — the expired credential is
                    // re-evaluated on every restore, so retaining it is safe.
                }
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
        // A persistence failure must fail closed — callers never observe a
        // SignedIn state whose durable copy does not exist (EXT-MIN-4).
        // IOException propagates for the repository to map; cancellation
        // and defects propagate untouched.
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
        // In-memory transition first — expiry must hold even if the disk
        // cleanup fails. A surviving stored token is re-checked by
        // restore() and rejected again by the next server 401.
        markExpired(current)
        try {
            tokenStore.clearToken()
        } catch (e: IOException) {
            // Best-effort — see above.
        }
    }

    suspend fun signOut() {
        val hadSession = _session.value !is Session.SignedOut
        // In-memory sign-out is authoritative — a failed disk clear must
        // not leave the user believing they are signed out while SignedIn.
        _session.value = Session.SignedOut
        if (hadSession) emitInvalidation()
        try {
            tokenStore.clear()
        } catch (e: IOException) {
            // Best-effort — residual risk is a stored token surviving to
            // the next boot, where restore() re-validates expiry and the
            // server re-401s anything already blacklisted.
        }
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
        // Synchronous generation advance FIRST — a subsequent session's
        // first cache access is already safe even if this async signal
        // is never scheduled (EXT-MAJOR-1).
        _sessionGeneration.incrementAndGet()
        _cacheInvalidations.tryEmit(Unit)
    }
}
