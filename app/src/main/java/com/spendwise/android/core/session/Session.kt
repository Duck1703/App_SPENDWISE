package com.spendwise.android.core.session

import java.time.Instant

/**
 * Explicit session state (AD-011, ARCHITECTURE §13).
 *
 * - [Restoring]: persisted session is being loaded at startup.
 * - [SignedOut]: no usable session.
 * - [SignedIn]: valid token in memory. [resumedSession] is true when the
 *   sign-in was a re-authentication matching the identity of the preceding
 *   [Expired] session — the signal that the preserved back stack may be
 *   resumed (identity-continuity guard, §13).
 * - [Expired]: an authenticated call returned 401; the preserved UI stack
 *   stays in place and re-authentication resumes it.
 */
sealed interface Session {

    data object Restoring : Session

    data object SignedOut : Session

    data class SignedIn(
        val token: String,
        val expiresAt: Instant,
        val username: String?,
        val userId: String?,
        val resumedSession: Boolean = false,
        /**
         * Rotating renewal credential (M13, REQ-BE-002). `null` = legacy
         * pre-M13 session — behaves exactly like M12: access expiry →
         * [Expired] → re-auth. Never leaves the session layer.
         */
        val refreshToken: String? = null,
    ) : Session

    data class Expired(
        val username: String?,
        val userId: String?,
    ) : Session
}
