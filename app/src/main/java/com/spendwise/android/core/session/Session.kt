package com.spendwise.android.core.session

import java.time.Instant

/**
 * Explicit session state (AD-011, ARCHITECTURE §10.1).
 *
 * - [Restoring]: persisted session is being loaded at startup.
 * - [SignedOut]: no usable session.
 * - [SignedIn]: valid token in memory.
 * - [Expired]: an authenticated call returned 401; the preserved UI stack
 *   stays in place and re-authentication resumes it (M1 wires the flow).
 */
sealed interface Session {

    data object Restoring : Session

    data object SignedOut : Session

    data class SignedIn(
        val token: String,
        val expiresAt: Instant,
        val username: String?,
    ) : Session

    data class Expired(val username: String?) : Session
}
