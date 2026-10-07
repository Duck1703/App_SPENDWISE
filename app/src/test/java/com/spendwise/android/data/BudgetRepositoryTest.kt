package com.spendwise.android.data

import com.spendwise.android.core.model.BudgetStatus
import com.spendwise.android.core.network.ApiException
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.repository.RemoteBudgetRepository
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.fakes.FakeBudgetsService
import com.spendwise.android.fakes.FakeTokenStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * `RemoteBudgetRepository` — domain mapping, error translation,
 * generation guard (a stale-session response never surfaces), and the
 * create→mutations signal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetRepositoryTest {

    private val now: Instant = Instant.parse("2026-10-04T12:00:00Z")

    private data class Harness(
        val service: FakeBudgetsService,
        val sessionManager: SessionManager,
        val repository: RemoteBudgetRepository,
    )

    private fun harness(): Harness {
        val service = FakeBudgetsService()
        val sessionManager = SessionManager(
            tokenStore = FakeTokenStore(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        return Harness(service, sessionManager, RemoteBudgetRepository(service, sessionManager))
    }

    private suspend fun signIn(h: Harness) =
        h.sessionManager.onSignedIn("token", now.plusSeconds(900), "ana", "u1")

    @Test
    fun `list maps rows to domain`() = runTest {
        val h = harness()
        signIn(h)
        h.service.listResult = {
            listOf(FakeBudgetsService.budget(id = "b1", limit = BigDecimal("500.00"), currency = "VND"))
        }
        val budgets = h.repository.list()
        assertEquals("b1", budgets.single().id)
        assertEquals(BigDecimal("500.00"), budgets.single().limit.amount)
        assertEquals("VND", budgets.single().limit.currency.code)
        assertEquals(1, h.service.listCalls.size)
    }

    @Test
    fun `calculation maps server figures verbatim including status`() = runTest {
        val h = harness()
        signIn(h)
        h.service.calculationResult = {
            FakeBudgetsService.calculation(
                spent = BigDecimal("600"), remaining = BigDecimal("-100"),
                percentage = BigDecimal("120.00"), status = "OVER_BUDGET",
            )
        }
        val calc = h.repository.calculation("b1")
        assertEquals(BigDecimal("600"), calc.spent.amount)
        assertEquals(BigDecimal("-100"), calc.remaining.amount)
        assertEquals(BigDecimal("120.00"), calc.percentage)
        assertEquals(BudgetStatus.OVER_BUDGET, calc.status)
    }

    @Test
    fun `unknown status armors to UNKNOWN instead of crashing`() = runTest {
        val h = harness()
        signIn(h)
        h.service.calculationResult = { FakeBudgetsService.calculation(status = "NEAR_LIMIT") }
        assertEquals(BudgetStatus.UNKNOWN, h.repository.calculation("b1").status)
    }

    @Test
    fun `409 surfaces as Conflict`() = runTest {
        val h = harness()
        signIn(h)
        h.service.createResult = { throw ApiException(409, "Conflict") }
        assertThrows(AppErrorException::class.java) {
            kotlinx.coroutines.runBlocking {
                h.repository.create("FOOD", "2026-10", Money(BigDecimal.TEN, CurrencyCode("VND")))
            }
        }
    }

    @Test
    fun `a dead-generation list result is discarded`() = runTest {
        val h = harness()
        signIn(h)
        val gate = CompletableDeferred<List<com.spendwise.android.data.remote.BudgetDto>>()
        h.service.listResult = { gate.await() }
        val pending = async { h.repository.list() }
        runCurrent()
        h.sessionManager.signOut()
        gate.complete(listOf(FakeBudgetsService.budget()))
        val thrown = runCatching { pending.await() }.exceptionOrNull()
        assertTrue(thrown is SessionEndedException)
    }

    @Test
    fun `a dead-generation create never emits the mutation`() = runTest {
        val h = harness()
        signIn(h)
        val gate = CompletableDeferred<com.spendwise.android.data.remote.BudgetDto>()
        h.service.createResult = { gate.await() }
        val emissions = mutableListOf<Unit>()
        val collector = async { h.repository.mutations.collect { emissions += Unit } }
        val pending = async {
            h.repository.create("FOOD", "2026-10", Money(BigDecimal.TEN, CurrencyCode("VND")))
        }
        runCurrent()
        h.sessionManager.signOut()
        gate.complete(FakeBudgetsService.budget())
        runCatching { pending.await() }
        runCurrent()
        collector.cancel()
        assertTrue(emissions.isEmpty())
    }

    @Test
    fun `404 on calculation surfaces NotFound`() = runTest {
        val h = harness()
        signIn(h)
        h.service.calculationResult = { throw ApiException(404, "Not found") }
        val thrown = runCatching { h.repository.calculation("x") }.exceptionOrNull()
        assertTrue((thrown as? AppErrorException)?.error is AppError.NotFound)
    }

    // ── M14 (REQ-BE-011) lifecycle mutations ─────────────────────────

    @Test
    fun `update sends exactly the provided fields and emits mutations`() = runTest {
        val h = harness()
        signIn(h)
        h.service.updateResult = { id, body ->
            FakeBudgetsService.budget(id = id, period = body.period ?: "x", limit = body.limit ?: BigDecimal.ONE)
        }
        val emissions = mutableListOf<Unit>()
        val collector = async { h.repository.mutations.collect { emissions += Unit } }
        runCurrent()

        val updated = h.repository.update("b1", limit = null, period = "2026-11")
        runCurrent()

        assertEquals("2026-11", updated.period)
        val (id, body) = h.service.updateCalls.single()
        assertEquals("b1", id)
        assertEquals(null, body.limit)
        assertEquals("2026-11", body.period)
        assertEquals(1, emissions.size)
        collector.cancel()
    }

    @Test
    fun `update with no fields is rejected before any call`() = runTest {
        val h = harness()
        signIn(h)
        val thrown = runCatching { h.repository.update("b1", limit = null, period = null) }
            .exceptionOrNull()
        assertTrue(thrown is IllegalArgumentException)
        assertTrue(h.service.updateCalls.isEmpty())
    }

    @Test
    fun `update maps conflict and not-found errors`() = runTest {
        val h = harness()
        signIn(h)
        h.service.updateResult = { _, _ -> throw ApiException(409, "Conflict") }
        assertTrue(
            (runCatching {
                h.repository.update("b1", Money(BigDecimal.ONE, CurrencyCode("VND")), null)
            }.exceptionOrNull() as? AppErrorException)?.error is AppError.Conflict,
        )
        h.service.updateResult = { _, _ -> throw ApiException(404, "Not found") }
        assertTrue(
            (runCatching {
                h.repository.update("b1", Money(BigDecimal.ONE, CurrencyCode("VND")), null)
            }.exceptionOrNull() as? AppErrorException)?.error is AppError.NotFound,
        )
    }

    @Test
    fun `a dead-generation update never emits the mutation`() = runTest {
        val h = harness()
        signIn(h)
        val gate = CompletableDeferred<com.spendwise.android.data.remote.BudgetDto>()
        h.service.updateResult = { _, _ -> gate.await() }
        val emissions = mutableListOf<Unit>()
        val collector = async { h.repository.mutations.collect { emissions += Unit } }
        val pending = async {
            h.repository.update("b1", Money(BigDecimal.ONE, CurrencyCode("VND")), null)
        }
        runCurrent()
        h.sessionManager.signOut()
        gate.complete(FakeBudgetsService.budget())
        runCatching { pending.await() }
        runCurrent()
        collector.cancel()
        assertTrue(emissions.isEmpty())
    }

    @Test
    fun `delete emits mutations and maps 404`() = runTest {
        val h = harness()
        signIn(h)
        val emissions = mutableListOf<Unit>()
        val collector = async { h.repository.mutations.collect { emissions += Unit } }
        runCurrent()

        h.repository.delete("b1")
        runCurrent()
        assertEquals(listOf("b1"), h.service.deleteCalls)
        assertEquals(1, emissions.size)

        h.service.deleteResult = { throw ApiException(404, "Not found") }
        val thrown = runCatching { h.repository.delete("b1") }.exceptionOrNull()
        assertTrue((thrown as? AppErrorException)?.error is AppError.NotFound)
        collector.cancel()
    }

    @Test
    fun `a dead-generation delete never emits the mutation`() = runTest {
        val h = harness()
        signIn(h)
        val gate = CompletableDeferred<Unit>()
        h.service.deleteResult = { gate.await() }
        val emissions = mutableListOf<Unit>()
        val collector = async { h.repository.mutations.collect { emissions += Unit } }
        val pending = async { h.repository.delete("b1") }
        runCurrent()
        h.sessionManager.signOut()
        gate.complete(Unit)
        runCatching { pending.await() }
        runCurrent()
        collector.cancel()
        assertTrue(emissions.isEmpty())
    }
}
