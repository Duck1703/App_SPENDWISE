package com.spendwise.android.core.session

import app.cash.turbine.test
import com.spendwise.android.fakes.FakeTokenStore
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SessionManagerTest {

    private val now: Instant = Instant.parse("2026-10-04T12:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val futureExpiry: Instant = now.plusSeconds(900)
    private val pastExpiry: Instant = now.minusSeconds(1)

    private fun manager(store: FakeTokenStore) = SessionManager(tokenStore = store, clock = clock)

    @Test
    fun `initial state is Restoring`() {
        assertEquals(Session.Restoring, manager(FakeTokenStore()).session.value)
    }

    @Test
    fun `restore with no stored session signs out`() = runTest {
        val manager = manager(FakeTokenStore(stored = null))
        manager.restore()
        assertEquals(Session.SignedOut, manager.session.value)
    }

    @Test
    fun `restore with live token signs in`() = runTest {
        val store = FakeTokenStore(
            stored = StoredSession("token-abc", futureExpiry, "ana", "user-1"),
        )
        val manager = manager(store)
        manager.restore()
        assertEquals(
            Session.SignedIn("token-abc", futureExpiry, "ana", "user-1"),
            manager.session.value,
        )
        assertEquals("token-abc", manager.accessToken())
    }

    @Test
    fun `restore with expired token signs out, keeps username, drops credential`() = runTest {
        val store = FakeTokenStore(
            stored = StoredSession("dead-token", pastExpiry, "ana", "user-1"),
        )
        val manager = manager(store)
        manager.restore()

        assertEquals(Session.SignedOut, manager.session.value)
        assertNull(manager.accessToken())
        // Credential dropped (load() is null), identity hints retained.
        assertNull(store.load())
        assertEquals("ana", store.lastUsername)
        assertEquals("user-1", store.lastUserId)
    }

    @Test
    fun `onSignedIn persists and signs in`() = runTest {
        val store = FakeTokenStore()
        val manager = manager(store)
        manager.onSignedIn("token-1", futureExpiry, "ana", "user-1")

        assertEquals(
            Session.SignedIn("token-1", futureExpiry, "ana", "user-1"),
            manager.session.value,
        )
        assertEquals("token-1", store.token)
        assertEquals("user-1", store.lastUserId)
    }

    @Test
    fun `authenticated 401 expires the session but keeps identity`() = runTest {
        val store = FakeTokenStore()
        val manager = manager(store)
        manager.onSignedIn("token-1", futureExpiry, "ana", "user-1")

        manager.onAuthenticationExpired()

        assertEquals(Session.Expired("ana", "user-1"), manager.session.value)
        assertNull(manager.accessToken())
        // Dead credential dropped from the store; identity hints retained.
        assertNull(store.token)
        assertEquals("ana", store.lastUsername)
    }

    @Test
    fun `expired credential cannot resurrect on restart`() = runTest {
        val store = FakeTokenStore()
        val manager = manager(store)
        manager.onSignedIn("dead-token", futureExpiry, "ana", "user-1")
        manager.onAuthenticationExpired()

        val restarted = manager(store)
        restarted.restore()

        // The server-rejected token was dropped — restart signs out.
        assertEquals(Session.SignedOut, restarted.session.value)
    }

    @Test
    fun `expiry signal without a session is a no-op`() = runTest {
        val manager = manager(FakeTokenStore())
        manager.restore()
        manager.onAuthenticationExpired()
        assertEquals(Session.SignedOut, manager.session.value)
    }

    @Test
    fun `expiry signal while already expired is a no-op`() = runTest {
        val manager = manager(FakeTokenStore())
        manager.onSignedIn("token-1", futureExpiry, "ana", "user-1")
        manager.onAuthenticationExpired()

        manager.onAuthenticationExpired()

        assertEquals(Session.Expired("ana", "user-1"), manager.session.value)
    }

    @Test
    fun `accessToken returns null and expires the session when locally past expiry`() = runTest {
        val store = FakeTokenStore()
        val manager = manager(store)
        manager.onSignedIn("token-1", pastExpiry, "ana", "user-1")

        assertNull(manager.accessToken())
        assertEquals(Session.Expired("ana", "user-1"), manager.session.value)
    }

    @Test
    fun `re-authentication with the same identity resumes the session`() = runTest {
        val manager = manager(FakeTokenStore())
        manager.onSignedIn("token-1", futureExpiry, "ana", "user-1")
        manager.onAuthenticationExpired()

        manager.cacheInvalidations.test {
            manager.onSignedIn("token-2", futureExpiry, "ana", "user-1")

            val session = manager.session.value as Session.SignedIn
            assertTrue(session.resumedSession)
            assertEquals("token-2", session.token)
            expectNoEvents()
        }
    }

    @Test
    fun `re-authentication with a different user does not resume and resets caches`() = runTest {
        val manager = manager(FakeTokenStore())
        manager.onSignedIn("token-1", futureExpiry, "ana", "user-1")
        manager.onAuthenticationExpired()

        manager.cacheInvalidations.test {
            manager.onSignedIn("token-9", futureExpiry, "bob", "user-2")

            val session = manager.session.value as Session.SignedIn
            assertFalse(session.resumedSession)
            assertEquals("user-2", session.userId)
            assertEquals(Unit, awaitItem())
        }
    }

    @Test
    fun `cold sign-in (not a resume) does not mark resumed`() = runTest {
        val manager = manager(FakeTokenStore())
        manager.restore()

        manager.cacheInvalidations.test {
            manager.onSignedIn("token-1", futureExpiry, "ana", "user-1")

            val session = manager.session.value as Session.SignedIn
            assertFalse(session.resumedSession)
            assertEquals(Unit, awaitItem())
        }
    }

    @Test
    fun `identity continuity falls back to username when ids are missing`() = runTest {
        val manager = manager(FakeTokenStore())
        manager.onSignedIn("token-1", futureExpiry, "ana", null)
        manager.onAuthenticationExpired()

        manager.onSignedIn("token-2", futureExpiry, "ana", null)

        assertTrue((manager.session.value as Session.SignedIn).resumedSession)
    }

    @Test
    fun `user id wins over username for identity continuity`() = runTest {
        val manager = manager(FakeTokenStore())
        manager.onSignedIn("token-1", futureExpiry, "ana", "user-1")
        manager.onAuthenticationExpired()

        manager.onSignedIn("token-2", futureExpiry, "ana", "user-2")

        assertFalse((manager.session.value as Session.SignedIn).resumedSession)
    }

    @Test
    fun `signOut clears store and signs out`() = runTest {
        val store = FakeTokenStore()
        val manager = manager(store)
        manager.onSignedIn("token-1", futureExpiry, "ana", "user-1")

        manager.cacheInvalidations.test {
            manager.signOut()

            assertEquals(Session.SignedOut, manager.session.value)
            assertNull(store.load())
            assertNull(store.lastUsername)
            assertEquals(Unit, awaitItem())
        }
    }

    @Test
    fun `restart after logout stays signed out`() = runTest {
        val store = FakeTokenStore()
        val manager = manager(store)
        manager.onSignedIn("token-1", futureExpiry, "ana", "user-1")
        manager.signOut()

        val restarted = manager(store)
        restarted.restore()

        assertEquals(Session.SignedOut, restarted.session.value)
    }

    @Test
    fun `restart after login restores the session`() = runTest {
        val store = FakeTokenStore()
        manager(store).onSignedIn("token-1", futureExpiry, "ana", "user-1")

        val restarted = manager(store)
        restarted.restore()

        val session = restarted.session.value as Session.SignedIn
        assertEquals("token-1", session.token)
        assertEquals("user-1", session.userId)
        assertFalse(session.resumedSession)
    }

    @Test
    fun `sign-out then different user leaves no prior identity`() = runTest {
        val store = FakeTokenStore()
        val manager = manager(store)
        manager.onSignedIn("token-1", futureExpiry, "ana", "user-1")
        manager.signOut()

        manager.onSignedIn("token-9", futureExpiry, "bob", "user-2")

        val session = manager.session.value as Session.SignedIn
        assertEquals("user-2", session.userId)
        assertFalse(session.resumedSession)
        assertEquals("user-2", store.lastUserId)
    }

    // --- M1 external-audit EXT-MINOR-1: storage I/O failure fails closed ---

    @Test
    fun `restore with storage read failure fails closed to SignedOut`() = runTest {
        val store = FakeTokenStore(
            stored = StoredSession("token-abc", futureExpiry, "ana", "user-1"),
        )
        store.loadError = IOException("disk read failed")
        val manager = manager(store)

        manager.restore()

        // No crash, never stuck in Restoring, credential not trusted.
        assertEquals(Session.SignedOut, manager.session.value)
        assertNull(manager.accessToken())
        // Credential preserved — a transient read failure must not destroy
        // a possibly-valid session; the next restore retries.
        assertEquals("token-abc", store.token)
    }

    @Test
    fun `restore does not swallow cancellation`() = runTest {
        val store = FakeTokenStore()
        store.loadError = CancellationException()
        val manager = manager(store)

        try {
            manager.restore()
            fail("CancellationException must propagate, not be swallowed")
        } catch (e: CancellationException) {
            // Expected — the narrow IOException guard must not catch this.
        }
    }

    @Test
    fun `signOut with disk clear failure still clears in-memory session`() = runTest {
        val store = FakeTokenStore()
        val manager = manager(store)
        manager.onSignedIn("token-abc", futureExpiry, "ana", "user-1")
        store.writeError = IOException("disk write failed")

        manager.signOut()

        // In-memory sign-out is authoritative — the user is out even if
        // the persisted credential could not be deleted this time.
        assertEquals(Session.SignedOut, manager.session.value)
        assertNull(manager.accessToken())
    }

    @Test
    fun `expiry with disk clear failure still marks session expired`() = runTest {
        val store = FakeTokenStore()
        val manager = manager(store)
        manager.onSignedIn("token-abc", futureExpiry, "ana", "user-1")
        store.writeError = IOException("disk write failed")

        manager.onAuthenticationExpired()

        val session = manager.session.value
        assertTrue(session is Session.Expired)
        assertNull(manager.accessToken())
    }

    @Test
    fun `concurrent authenticated 401 signals resolve to a single Expired state`() = runTest {
        val store = FakeTokenStore()
        val manager = manager(store)
        manager.onSignedIn("token-abc", futureExpiry, "ana", "user-1")

        coroutineScope {
            launch { manager.onAuthenticationExpired() }
            launch { manager.onAuthenticationExpired() }
            launch { manager.onAuthenticationExpired() }
        }

        val session = manager.session.value
        assertTrue(session is Session.Expired)
        assertEquals("user-1", (session as Session.Expired).userId)
        assertNull(manager.accessToken())
    }

    @Test
    fun `sign-in with storage write failure fails closed — no session, no token`() = runTest {
        val store = FakeTokenStore().apply { writeError = IOException("disk full") }
        val manager = manager(store)

        try {
            manager.onSignedIn("token-abc", futureExpiry, "ana", "user-1")
            fail("Expected IOException")
        } catch (e: IOException) {
            // Expected — propagates for the repository to map.
        }

        // Fails closed: no SignedIn state was ever established, and the
        // credential was not persisted.
        assertFalse(manager.session.value is Session.SignedIn)
        assertNull(store.token)
    }

    // ---- M13 (REQ-BE-002): refresh lifecycle ---------------------------------

    @Test
    fun `restore with expired access and live refresh credential stays Restoring`() = runTest {
        val store = FakeTokenStore(
            stored = StoredSession("dead-access", pastExpiry, "ana", "user-1", refreshToken = "rt-1"),
        )
        val manager = manager(store)
        manager.restore()

        // Parked for cold-start renewal — NOT SignedOut, NOT a stale SignedIn.
        assertEquals(Session.Restoring, manager.session.value)
        assertTrue(manager.hasPendingRenewal())
        assertEquals("rt-1", manager.renewalToken())
        assertNull(manager.accessToken())
    }

    @Test
    fun `restore with expired access and NO refresh credential signs out as before`() = runTest {
        val store = FakeTokenStore(
            stored = StoredSession("dead-access", pastExpiry, "ana", "user-1"),
        )
        val manager = manager(store)
        manager.restore()

        assertEquals(Session.SignedOut, manager.session.value)
        assertFalse(manager.hasPendingRenewal())
        assertNull(manager.renewalToken())
    }

    @Test
    fun `onRequestRejected expires only when the live credential was rejected without renewal backup`() = runTest {
        val manager = manager(FakeTokenStore())
        manager.onSignedIn("tok", futureExpiry, "ana", "u1")

        // Stale bearer (a peer renewed first): session survives.
        assertTrue(manager.onRequestRejected("old-tok"))
        assertTrue(manager.session.value is Session.SignedIn)

        // The live credential rejected, no refresh → definitive expiry.
        assertFalse(manager.onRequestRejected("tok"))
        assertEquals(Session.Expired("ana", "u1"), manager.session.value)
    }

    @Test
    fun `onRequestRejected keeps the session when a refresh credential survives`() = runTest {
        val manager = manager(FakeTokenStore())
        manager.onSignedIn("tok", futureExpiry, "ana", "u1", refreshToken = "rt-1")

        // Even the live token being rejected is retryable while a refresh
        // credential remains — the next attempt renews first.
        assertTrue(manager.onRequestRejected("tok"))
        assertTrue(manager.session.value is Session.SignedIn)
    }

    @Test
    fun `applyRenewedSession rotates credentials in place and preserves identity`() = runTest {
        val store = FakeTokenStore()
        val manager = manager(store)
        manager.onSignedIn("tok", futureExpiry, "ana", "u1", refreshToken = "rt-1")
        val generation = manager.sessionGeneration

        assertTrue(
            manager.applyRenewedSession("tok-2", futureExpiry, "rt-2", generation),
        )

        val session = manager.session.value as Session.SignedIn
        assertEquals("tok-2", session.token)
        assertEquals("rt-2", session.refreshToken)
        assertEquals("ana", session.username)
        // Rotation did NOT bump the generation — same session, same caches.
        assertEquals(generation, manager.sessionGeneration)
        assertEquals("rt-2", store.refreshToken)
    }

    @Test
    fun `applyRenewedSession on a stale generation is discarded`() = runTest {
        val store = FakeTokenStore()
        val manager = manager(store)
        manager.onSignedIn("tok", futureExpiry, "ana", "u1", refreshToken = "rt-1")
        manager.signOut() // generation bumps — a late renewal must not apply

        assertFalse(manager.applyRenewedSession("tok-2", futureExpiry, "rt-2", 0L))
        assertEquals(Session.SignedOut, manager.session.value)
        assertNull(store.refreshToken)
    }

    @Test
    fun `renewalToken is null for legacy and signed-out sessions`() = runTest {
        val manager = manager(FakeTokenStore())
        manager.onSignedIn("tok", futureExpiry, "ana", "u1") // no refresh token
        assertNull(manager.renewalToken())
        manager.signOut()
        assertNull(manager.renewalToken())
    }

    @Test
    fun `onSignedIn persists the refresh credential and re-login does not inherit a different user's`() = runTest {
        val store = FakeTokenStore()
        val manager = manager(store)
        manager.onSignedIn("tok", futureExpiry, "ana", "u1", refreshToken = "rt-1")
        assertEquals("rt-1", store.refreshToken)

        manager.signOut()
        assertNull(store.refreshToken) // cleared on sign-out

        manager.onSignedIn("tok-b", futureExpiry, "bo", "u2", refreshToken = "rt-b")
        assertEquals("rt-b", (manager.session.value as Session.SignedIn).refreshToken)
        assertEquals("rt-b", store.refreshToken)
    }
}
