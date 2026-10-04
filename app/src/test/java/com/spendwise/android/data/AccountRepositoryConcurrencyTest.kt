package com.spendwise.android.data

import com.spendwise.android.core.model.AccountType
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.remote.AccountDto
import com.spendwise.android.data.repository.RemoteAccountRepository
import com.spendwise.android.fakes.FakeAccountsService
import com.spendwise.android.fakes.FakeTokenStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.coroutines.cancellation.CancellationException

/**
 * EXT-MAJOR-1 privacy gate — deterministic session-generation tests.
 *
 * The invalidation collector is parked on a SEPARATE scheduler
 * ([appScopeScheduler]) that is never advanced unless the test chooses —
 * so every assertion here proves isolation through the generation stamp
 * alone, never through collector timing. No sleeps; all ordering is
 * controlled with `CompletableDeferred` gates.
 */
class AccountRepositoryConcurrencyTest {

    private val now: Instant = Instant.parse("2026-10-04T12:00:00Z")
    private val expires: Instant = now.plusSeconds(900)

    private class Harness(
        val service: FakeAccountsService,
        val sessionManager: SessionManager,
        val repository: RemoteAccountRepository,
        val appScopeScheduler: TestCoroutineScheduler,
    )

    private fun harness(): Harness {
        val service = FakeAccountsService()
        val sessionManager = SessionManager(
            tokenStore = FakeTokenStore(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        // The collector scope gets its own scheduler — it only runs when
        // the test explicitly advances it, so the async clear can be
        // held back arbitrarily long.
        val appScopeScheduler = TestCoroutineScheduler()
        val scope = TestScope(appScopeScheduler)
        return Harness(
            service = service,
            sessionManager = sessionManager,
            repository = RemoteAccountRepository(service, sessionManager, scope),
            appScopeScheduler = appScopeScheduler,
        )
    }

    private fun dto(id: String, balance: String = "10.00") = FakeAccountsService.accountDto(
        id = id,
        name = "Acct $id",
        type = "CASH",
        currency = "VND",
        openingBalance = BigDecimal(balance),
        balance = BigDecimal(balance),
    )

    private fun money(amount: String) = Money(BigDecimal(amount), CurrencyCode("VND"))

    // ── §10: the stale-response case ────────────────────────────────────

    @Test
    fun `user A stale list response after user B sign-in is never cached or returned`() =
        runTest {
            val h = harness()
            h.sessionManager.onSignedIn("token-a", expires, "ana", "u1")

            val gate = CompletableDeferred<List<AccountDto>>()
            h.service.listResponder = { gate.await() }
            val staleCall = async { h.repository.list() }
            runCurrent() // the request is now in-flight, parked on the gate

            h.sessionManager.signOut() // generation advances synchronously
            h.sessionManager.onSignedIn("token-b", expires, "bob", "u2")

            // A's response lands after B's session is live.
            gate.complete(listOf(dto("a-1"), dto("a-2")))

            val thrown = runCatching { staleCall.await() }.exceptionOrNull()
            assertTrue(thrown is CancellationException)

            // B's first read must hit the network for B's data — never
            // see A's cached or late-arriving accounts.
            h.service.listResponder = null
            h.service.listResponse = listOf(dto("b-1"))
            assertEquals(listOf("b-1"), h.repository.list().map { it.id })
            assertEquals(2, h.service.listCalls)
        }

    // ── §11: the delayed-clear case ─────────────────────────────────────

    @Test
    fun `cache read after generation change but before async clear misses`() = runTest {
        val h = harness()
        h.sessionManager.onSignedIn("token-a", expires, "ana", "u1")
        h.service.listResponse = listOf(dto("a-1"))
        h.repository.list()
        assertEquals(1, h.service.listCalls)

        // Generation advances; the collector's dispatcher is NOT run —
        // the async clear literally never executes during this test.
        h.sessionManager.signOut()
        h.sessionManager.onSignedIn("token-b", expires, "bob", "u2")

        h.service.listResponse = listOf(dto("b-1"))
        val result = h.repository.list()

        assertEquals(listOf("b-1"), result.map { it.id })
        assertEquals(2, h.service.listCalls)
    }

    // ── §13.3: refresh racing a create merge stays coherent ──────────────

    @Test
    fun `refresh racing a create merge leaves one coherent cache`() = runTest {
        val h = harness()
        h.sessionManager.onSignedIn("token-a", expires, "ana", "u1")
        h.service.listResponse = listOf(dto("a-1"))
        h.repository.list()

        val gate = CompletableDeferred<List<AccountDto>>()
        h.service.listResponder = { gate.await() }
        val refresh = async { h.repository.list(refresh = true) }
        runCurrent()

        // The create lands mid-refresh and merges onto the old cache.
        h.service.createResponse = dto("new")
        h.repository.create("Jar", AccountType.CASH, money("5"))

        // The refresh completes and replaces the whole cache.
        gate.complete(listOf(dto("a-1"), dto("a-2")))
        val refreshed = refresh.await()

        assertEquals(listOf("a-1", "a-2"), refreshed.map { it.id })
        // Coherent: a subsequent read serves exactly the refreshed list —
        // no torn merge, no lost update inside the cache itself.
        assertEquals(listOf("a-1", "a-2"), h.repository.list().map { it.id })
        assertEquals(2, h.service.listCalls)
    }

    // ── §13.4: cache access during logout ────────────────────────────────

    @Test
    fun `in-flight list during logout is discarded and cache cannot repopulate`() = runTest {
        val h = harness()
        h.sessionManager.onSignedIn("token-a", expires, "ana", "u1")

        val gate = CompletableDeferred<List<AccountDto>>()
        h.service.listResponder = { gate.await() }
        val staleCall = async { h.repository.list() }
        runCurrent()

        h.sessionManager.signOut()
        gate.complete(listOf(dto("a-1")))

        val thrown = runCatching { staleCall.await() }.exceptionOrNull()
        assertTrue(thrown is CancellationException)

        // A fresh read sees an empty cache and goes to the network.
        h.service.listResponder = null
        h.service.listResponse = listOf(dto("x-1"))
        assertEquals(listOf("x-1"), h.repository.list().map { it.id })
        assertEquals(2, h.service.listCalls)
    }

    @Test
    fun `in-flight detail fetch during sign-out never publishes`() = runTest {
        val h = harness()
        h.sessionManager.onSignedIn("token-a", expires, "ana", "u1")

        val gate = CompletableDeferred<AccountDto>()
        h.service.getByIdResponder = { gate.await() }
        val staleCall = async { h.repository.findById("a-9") }
        runCurrent()

        h.sessionManager.signOut()
        gate.complete(dto("a-9"))

        val thrown = runCatching { staleCall.await() }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
    }

    // ── §13.6: repeated invalidation ─────────────────────────────────────

    @Test
    fun `repeated session transitions never resurrect a prior cache`() = runTest {
        val h = harness()

        h.sessionManager.onSignedIn("t1", expires, "ana", "u1")
        h.service.listResponse = listOf(dto("a-1"))
        h.repository.list()
        assertEquals(1, h.service.listCalls)

        h.sessionManager.signOut()
        h.sessionManager.onSignedIn("t2", expires, "bob", "u2")
        h.service.listResponse = listOf(dto("b-1"))
        assertEquals(listOf("b-1"), h.repository.list().map { it.id })

        h.sessionManager.signOut()
        h.sessionManager.onSignedIn("t3", expires, "cid", "u3")
        h.service.listResponse = listOf(dto("c-1"))
        assertEquals(listOf("c-1"), h.repository.list().map { it.id })

        // Three sessions → three network lists; no stale hits.
        assertEquals(3, h.service.listCalls)
    }

    // ── §13.7/13.8: same-generation behavior stays normal ────────────────

    @Test
    fun `same generation serves cache and merges creates`() = runTest {
        val h = harness()
        h.sessionManager.onSignedIn("token-a", expires, "ana", "u1")
        h.service.listResponse = listOf(dto("a-1"))
        h.repository.list()

        h.service.createResponse = dto("new")
        val created = h.repository.create("Jar", AccountType.CASH, money("5"))
        assertEquals("new", created.id)

        // Same generation: cache serves, no extra network.
        assertEquals(setOf("a-1", "new"), h.repository.list().map { it.id }.toSet())
        assertEquals(1, h.service.listCalls)
    }

    @Test
    fun `same-generation create publishes its result`() = runTest {
        val h = harness()
        h.sessionManager.onSignedIn("token-a", expires, "ana", "u1")
        h.service.createResponse = dto("new", balance = "12.50")

        val created = h.repository.create("Jar", AccountType.BANK, money("12.50"))

        assertEquals("new", created.id)
        assertEquals(BigDecimal("12.50"), created.balance.amount)
    }

    // ── collector independence: the async clear still helps, unneeded ────

    @Test
    fun `the async invalidation collector still clears eagerly when scheduled`() = runTest {
        val h = harness()
        h.sessionManager.onSignedIn("token-a", expires, "ana", "u1")
        h.service.listResponse = listOf(dto("a-1"))
        h.repository.list()

        h.sessionManager.signOut()
        // Now actually let the collector run — the clear is prompt cleanup.
        h.appScopeScheduler.advanceUntilIdle()

        h.service.listResponse = listOf(dto("b-1"))
        h.sessionManager.onSignedIn("token-b", expires, "bob", "u2")
        assertEquals(listOf("b-1"), h.repository.list().map { it.id })
        assertEquals(2, h.service.listCalls)
    }

    @Test
    fun `generation advances synchronously on every invalidating transition`() = runTest {
        val h = harness()
        val g0 = h.sessionManager.sessionGeneration

        h.sessionManager.onSignedIn("token-a", expires, "ana", "u1")
        val g1 = h.sessionManager.sessionGeneration
        h.sessionManager.signOut()
        val g2 = h.sessionManager.sessionGeneration
        h.sessionManager.onSignedIn("token-b", expires, "bob", "u2")
        val g3 = h.sessionManager.sessionGeneration

        assertTrue(g1 > g0)
        assertTrue(g2 > g1)
        assertTrue(g3 > g2)
    }
}
