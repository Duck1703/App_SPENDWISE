package com.spendwise.android.core.session

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Generation-stamped in-memory cache for session-scoped protected data
 * (M2 remediation EXT-MAJOR-1; §13 session-boundary hygiene).
 *
 * Correctness lives in the stamp, not in any async signal: every value
 * is stored with the [SessionManager.sessionGeneration] that produced
 * it, and every read/write re-validates against the *current*
 * generation under a mutex. A request started in generation N can
 * therefore never read, overwrite, or merge cache belonging to
 * generation N±k — regardless of coroutine scheduling, dispatcher
 * choice, or whether the `cacheInvalidations` collector has run yet.
 *
 * Usage contract for repositories:
 * - capture [generation] at operation start;
 * - perform the network call OUTSIDE the mutex;
 * - publish results only through [write]/[update] with the captured
 *   generation — a false return means the session ended mid-flight and
 *   the result must be discarded, never surfaced.
 *
 * [invalidate] is a prompt-cleanup optimization called by the
 * invalidation collector; the stamp alone already guarantees isolation
 * if it runs late or never.
 */
class SessionCache<V>(private val sessionManager: SessionManager) {

    private val mutex = Mutex()
    private var stamped: Stamped<V>? = null

    private data class Stamped<V>(val generation: Long, val value: V)

    /** Generation an operation should capture before doing work. */
    fun generation(): Long = sessionManager.sessionGeneration

    /** The cached value only when it belongs to the live generation. */
    suspend fun read(): V? = mutex.withLock {
        stamped
            ?.takeIf { it.generation == sessionManager.sessionGeneration }
            ?.value
    }

    /**
     * Replace the whole cache. Returns false — without writing — when
     * [expectedGeneration] is no longer the live session generation.
     */
    suspend fun write(expectedGeneration: Long, value: V): Boolean = mutex.withLock {
        if (sessionManager.sessionGeneration != expectedGeneration) return@withLock false
        stamped = Stamped(expectedGeneration, value)
        true
    }

    /**
     * Generation-validated read-modify-write (MIN-2: the merge is atomic
     * under the mutex). Returns false without writing when the generation
     * is stale or [transform] yields null.
     */
    suspend fun update(expectedGeneration: Long, transform: (current: V?) -> V?): Boolean =
        mutex.withLock {
            if (sessionManager.sessionGeneration != expectedGeneration) {
                return@withLock false
            }
            val current = stamped
                ?.takeIf { it.generation == expectedGeneration }
                ?.value
            val next = transform(current) ?: return@withLock false
            stamped = Stamped(expectedGeneration, next)
            true
        }

    /** Drop the value (best-effort cleanup — the stamp still guards). */
    suspend fun invalidate() = mutex.withLock {
        stamped = null
    }
}
