package com.spendwise.android.data

import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.model.TransactionType
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.remote.TransactionDto
import com.spendwise.android.data.repository.RemoteTransactionRepository
import com.spendwise.android.fakes.FakeAccountRepository
import com.spendwise.android.fakes.FakeTransactionsService
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
 * M3 session-generation privacy gate for the transaction repository —
 * mirrors the EXT-MAJOR-1 account discipline: a response produced under
 * generation N can never publish into generation N+1, regardless of when
 * the invalidation collector is scheduled. Deterministic — gates only.
 */
class TransactionRepositoryConcurrencyTest {

    private val now: Instant = Instant.parse("2026-10-04T12:00:00Z")
    private val expires: Instant = now.plusSeconds(900)

    private class Harness(
        val service: FakeTransactionsService,
        val accounts: FakeAccountRepository,
        val sessionManager: SessionManager,
        val repository: RemoteTransactionRepository,
        val appScopeScheduler: TestCoroutineScheduler,
    )

    private fun harness(): Harness {
        val service = FakeTransactionsService()
        val accounts = FakeAccountRepository()
        val sessionManager = SessionManager(
            tokenStore = FakeTokenStore(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        val appScopeScheduler = TestCoroutineScheduler()
        val scope = TestScope(appScopeScheduler)
        return Harness(
            service = service,
            accounts = accounts,
            sessionManager = sessionManager,
            repository = RemoteTransactionRepository(service, accounts, sessionManager, scope),
            appScopeScheduler = appScopeScheduler,
        )
    }

    private fun dto(id: String) = FakeTransactionsService.transactionDto(id = id)

    private fun money(amount: String) = Money(BigDecimal(amount), CurrencyCode("VND"))

    // ── stale-response isolation ─────────────────────────────────────────

    @Test
    fun `user A stale list after user B sign-in is never cached or returned`() = runTest {
        val h = harness()
        h.sessionManager.onSignedIn("token-a", expires, "ana", "u1")

        val gate = CompletableDeferred<List<TransactionDto>>()
        h.service.listResponder = { _, _, _ -> gate.await() }
        val staleCall = async { h.repository.list() }
        runCurrent()

        h.sessionManager.signOut()
        h.sessionManager.onSignedIn("token-b", expires, "bob", "u2")

        gate.complete(listOf(dto("a-1")))

        val thrown = runCatching { staleCall.await() }.exceptionOrNull()
        assertTrue(thrown is CancellationException)

        // B's read must hit the network — A's late rows are never visible.
        h.service.listResponder = null
        h.service.listResponse = listOf(dto("b-1"))
        assertEquals(listOf("b-1"), h.repository.list().map { it.id })
        assertEquals(2, h.service.listCalls.size)
    }

    @Test
    fun `cached list read after generation change misses even without the async clear`() = runTest {
        val h = harness()
        h.sessionManager.onSignedIn("token-a", expires, "ana", "u1")
        h.service.listResponse = listOf(dto("a-1"))
        h.repository.list()

        // The invalidation collector's dispatcher never runs in this test —
        // isolation comes from the generation stamp alone.
        h.sessionManager.signOut()
        h.sessionManager.onSignedIn("token-b", expires, "bob", "u2")

        h.service.listResponse = listOf(dto("b-1"))
        assertEquals(listOf("b-1"), h.repository.list().map { it.id })
        assertEquals(2, h.service.listCalls.size)

        // A's rows also can't be looked up by id under B's session.
        val lookup = runCatching { h.repository.findById("a-1") }
        assertTrue(lookup.isFailure)
        assertEquals(3, h.service.listCalls.size) // the unfiltered miss refetch
    }

    @Test
    fun `in-flight create completing after generation advance never emits a mutation`() = runTest {
        val h = harness()
        h.sessionManager.onSignedIn("token-a", expires, "ana", "u1")

        val mutations = mutableListOf<Unit>()
        val collector = async {
            h.repository.mutations.collect { mutations += Unit }
        }
        runCurrent()

        val gate = CompletableDeferred<TransactionDto>()
        h.service.createResponder = { gate.await() }
        val staleCreate = async {
            h.repository.create("a1", TransactionType.EXPENSE, money("9"))
        }
        runCurrent()

        h.sessionManager.signOut()
        h.sessionManager.onSignedIn("token-b", expires, "bob", "u2")

        gate.complete(dto("a-txn"))

        val thrown = runCatching { staleCreate.await() }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
        collector.cancel()

        // B's session never sees A's mutation signal.
        assertTrue(mutations.isEmpty())
    }

    // ── same-generation behavior stays normal ────────────────────────────

    @Test
    fun `same-generation create publishes mutation and invalidates accounts`() = runTest {
        val h = harness()
        h.sessionManager.onSignedIn("token-a", expires, "ana", "u1")
        h.service.createResponse = dto("new")

        val created = h.repository.create("a1", TransactionType.EXPENSE, money("5"))

        assertEquals("new", created.id)
        assertEquals(1, h.accounts.invalidateCalls)
    }

    @Test
    fun `the async invalidation collector still clears eagerly when scheduled`() = runTest {
        val h = harness()
        h.sessionManager.onSignedIn("token-a", expires, "ana", "u1")
        h.service.listResponse = listOf(dto("a-1"))
        h.repository.list()

        h.sessionManager.signOut()
        h.appScopeScheduler.advanceUntilIdle()

        h.sessionManager.onSignedIn("token-b", expires, "bob", "u2")
        h.service.listResponse = listOf(dto("b-1"))
        assertEquals(listOf("b-1"), h.repository.list().map { it.id })
        assertEquals(2, h.service.listCalls.size)
    }
}
