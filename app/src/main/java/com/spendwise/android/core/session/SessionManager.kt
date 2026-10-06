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
     * Load the persisted session at startup. Expired tokens are discarded —
     * unless a refresh credential survives (M13): then the session stays
     * [Session.Restoring] with [pendingRenewal] armed so the cold-start
     * renewal (wired by the caller) can resolve it without flashing the
     * signed-out graph. No protected data is shown while unconfirmed.
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
        when {
            stored == null -> _session.value = Session.SignedOut
            !stored.expiresAt.isAfter(clock.instant()) && stored.refreshToken != null -> {
                // M13 cold-start renewal: keep the session in Restoring and arm
                // the pending credential; SessionRefresher resolves it to
                // SignedIn or Expired/SignedOut.
                pendingRenewal = stored
                _session.value = Session.Restoring
            }
            !stored.expiresAt.isAfter(clock.instant()) -> {
                try {
                    tokenStore.clearToken()
                } catch (e: IOException) {
                    // Best-effort cleanup — the expired credential is
                    // re-evaluated on every restore, so retaining it is safe.
                }
                _session.value = Session.SignedOut
            }
            else -> _session.value = Session.SignedIn(
                token = stored.token,
                expiresAt = stored.expiresAt,
                username = stored.username,
                userId = stored.userId,
                refreshToken = stored.refreshToken,
            )
        }
        if (_session.value == Session.SignedOut) emitInvalidation()
    }

    /** Stored session held while a cold-start renewal is in flight (M13).
     *  Read from OkHttp threads — volatile like the generation counter. */
    @Volatile
    private var pendingRenewal: StoredSession? = null

    /** True while [restore] parked the session in [Session.Restoring] behind
     *  a stored refresh credential awaiting resolution. */
    fun hasPendingRenewal(): Boolean =
        _session.value == Session.Restoring && pendingRenewal != null

    /**
     * The renewal credential the [com.spendwise.android.core.session.SessionRefresher]
     * may spend: the live session's refresh token, or the parked cold-start one.
     * `null` once the session is Expired/SignedOut — an expired session never
     * renews on its own.
     */
    fun renewalToken(): String? = when (val current = _session.value) {
        is Session.SignedIn -> current.refreshToken
        Session.Restoring -> pendingRenewal?.refreshToken
        else -> null
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
        refreshToken: String? = null,
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
            refreshToken = refreshToken,
        )
        pendingRenewal = null
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
            refreshToken = refreshToken,
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
     *
     * M13: unconditional expiry is retained for legacy semantics; the network
     * layer prefers [onRequestRejected], which only kills the session when the
     * rejected credential is still the current one AND no refresh credential
     * survives — a 401 on a stale/absent bearer after a concurrent renewal is
     * retryable noise, not session death.
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

    /**
     * M13 401 adjudication. Returns `true` when the session is still usable
     * (the request carried a stale/absent token while a renewal already landed,
     * or a refresh credential still exists to retry) — the caller must surface
     * a retryable error, NOT session expiry. Returns `false` once the session
     * has actually been marked [Session.Expired].
     */
    suspend fun onRequestRejected(usedToken: String?): Boolean {
        val current = _session.value as? Session.SignedIn ?: return false
        val rejectedCurrentToken = usedToken != null && usedToken == current.token
        if (rejectedCurrentToken && current.refreshToken == null) {
            // The live credential itself was rejected and nothing remains to
            // renew with — the session is genuinely dead.
            markExpired(current)
            try {
                tokenStore.clearToken()
            } catch (e: IOException) {
                // Best-effort — restore() re-validates on next boot.
            }
            return false
        }
        return true
    }

    /**
     * The renewal credential was definitively rejected server-side (401/400 on
     * /api/auth/refresh — not a transport failure). The session is dead:
     * SignedIn → Expired (same-user resume preserved), a parked cold-start
     * renewal → Expired with the stored identity hints. Credentials are wiped.
     */
    suspend fun onRenewalRejected() {
        val pending = pendingRenewal
        val current = _session.value
        when {
            current is Session.SignedIn -> markExpired(current)
            pending != null -> {
                _session.value = Session.Expired(
                    username = pending.username,
                    userId = pending.userId,
                )
                emitInvalidation()
            }
            else -> return
        }
        pendingRenewal = null
        try {
            tokenStore.clearToken()
        } catch (e: IOException) {
            // Best-effort — a dead credential re-fails on next use anyway.
        }
    }

    /**
     * A cold-start renewal could not be resolved right now (transport failure —
     * the credential is NOT proven dead, so the stored copy is kept for the
     * next launch). The parked session resolves to Expired so the user lands
     * on the re-auth surface instead of an endless Restoring.
     */
    fun onRenewalUnresolved() {
        val pending = pendingRenewal ?: return
        _session.value = Session.Expired(
            username = pending.username,
            userId = pending.userId,
        )
        pendingRenewal = null
        emitInvalidation()
    }

    /**
     * Apply a refresh response — only to the generation that requested it.
     * A logout or different-identity sign-in between send and response bumps
     * the generation → the late result is discarded (§29) and `false` is
     * returned so the caller treats the refresh as failed.
     */
    suspend fun applyRenewedSession(
        token: String,
        expiresAt: Instant,
        refreshToken: String,
        expectedGeneration: Long,
    ): Boolean {
        if (_sessionGeneration.get() != expectedGeneration) return false
        val pending = pendingRenewal
        val current = _session.value
        val username: String?
        val userId: String?
        val resumed: Boolean
        when {
            current is Session.SignedIn -> {
                username = current.username
                userId = current.userId
                resumed = current.resumedSession
            }
            current == Session.Restoring && pending != null -> {
                username = pending.username
                userId = pending.userId
                resumed = false
            }
            else -> return false
        }
        // Same fail-closed persistence contract as onSignedIn: IOException
        // propagates so a renewal that can't be durably stored is never
        // observed as a live session.
        tokenStore.save(
            token = token,
            expiresAt = expiresAt,
            username = username,
            userId = userId,
            refreshToken = refreshToken,
        )
        pendingRenewal = null
        _session.value = Session.SignedIn(
            token = token,
            expiresAt = expiresAt,
            username = username,
            userId = userId,
            resumedSession = resumed,
            refreshToken = refreshToken,
        )
        return true
    }

    suspend fun signOut() {
        val hadSession = _session.value !is Session.SignedOut
        // In-memory sign-out is authoritative — a failed disk clear must
        // not leave the user believing they are signed out while SignedIn.
        _session.value = Session.SignedOut
        pendingRenewal = null
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
     * Lightweight expiry check (§13): a locally-expired token is never sent.
     * M13: while a renewal credential survives the session stays SignedIn —
     * [SessionRefresher] renews instead of expiring. Only a refresh-less
     * session flips to [Session.Expired] here.
     */
    fun accessToken(): String? {
        val current = _session.value as? Session.SignedIn ?: return null
        if (!current.expiresAt.isAfter(clock.instant())) {
            if (current.refreshToken == null) {
                // In-memory transition only — a locally-expired stored token
                // is already dropped by restore(), no store write needed here.
                markExpired(current)
            }
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
