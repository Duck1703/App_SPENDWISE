package com.spendwise.android.core.session

import com.spendwise.android.fakes.FakeTokenStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            stored = StoredSession("token-abc", futureExpiry, "user@x"),
        )
        val manager = manager(store)
        manager.restore()
        assertEquals(Session.SignedIn("token-abc", futureExpiry, "user@x"), manager.session.value)
        assertEquals("token-abc", manager.accessToken())
    }

    @Test
    fun `restore with expired token signs out, keeps username, drops credential`() = runTest {
        val store = FakeTokenStore(
            stored = StoredSession("dead-token", pastExpiry, "user@x"),
        )
        val manager = manager(store)
        manager.restore()

        assertEquals(Session.SignedOut, manager.session.value)
        assertNull(manager.accessToken())
        // Credential dropped (load() is null), last username retained for
        // resume UX.
        assertNull(store.load())
        assertEquals("user@x", store.lastUsername)
    }

    @Test
    fun `onSignedIn persists and signs in`() = runTest {
        val store = FakeTokenStore()
        val manager = manager(store)
        manager.onSignedIn("token-1", futureExpiry, "user@x")

        assertEquals(Session.SignedIn("token-1", futureExpiry, "user@x"), manager.session.value)
        assertEquals("token-1", store.token)
    }

    @Test
    fun `authenticated 401 expires the session`() = runTest {
        val store = FakeTokenStore()
        val manager = manager(store)
        manager.onSignedIn("token-1", futureExpiry, "user@x")

        manager.onAuthenticationExpired()

        assertEquals(Session.Expired("user@x"), manager.session.value)
        assertNull(manager.accessToken())
    }

    @Test
    fun `expiry signal without a session is a no-op`() = runTest {
        val manager = manager(FakeTokenStore())
        manager.restore()
        manager.onAuthenticationExpired()
        assertEquals(Session.SignedOut, manager.session.value)
    }

    @Test
    fun `signOut clears store and signs out`() = runTest {
        val store = FakeTokenStore()
        val manager = manager(store)
        manager.onSignedIn("token-1", futureExpiry, "user@x")

        manager.signOut()

        assertEquals(Session.SignedOut, manager.session.value)
        assertNull(store.load())
    }
}
