package com.spendwise.android.data

import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.model.RecurringFrequency
import com.spendwise.android.core.model.TransactionType
import com.spendwise.android.core.network.ApiException
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.repository.RemoteRecurringRepository
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.fakes.FakeRecurringService
import com.spendwise.android.fakes.FakeTokenStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * `RemoteRecurringRepository` — request/DTO mapping, ISO date encoding,
 * error translation, generation guard, and deactivate mutation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecurringRepositoryTest {

    private val now: Instant = Instant.parse("2026-10-04T12:00:00Z")

    private data class Harness(
        val service: FakeRecurringService,
        val sessionManager: SessionManager,
        val repository: RemoteRecurringRepository,
    )

    private fun harness(): Harness {
        val service = FakeRecurringService()
        val sessionManager = SessionManager(
            tokenStore = FakeTokenStore(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        return Harness(service, sessionManager, RemoteRecurringRepository(service, sessionManager))
    }

    private suspend fun signIn(h: Harness) =
        h.sessionManager.onSignedIn("token", now.plusSeconds(900), "ana", "u1")

    private suspend fun create(h: Harness) = h.repository.create(
        accountId = "a1",
        direction = TransactionType.EXPENSE,
        amount = Money(BigDecimal("45.50"), CurrencyCode("USD")),
        category = null,
        frequency = RecurringFrequency.WEEKLY,
        nextDueDate = LocalDate.parse("2026-10-10"),
        endDate = null,
    )

    @Test
    fun `create maps params to a wire request with ISO dates and null optionals`() = runTest {
        val h = harness()
        signIn(h)
        create(h)
        val body = h.service.createCalls.single()
        assertEquals("a1", body.accountId)
        assertEquals("EXPENSE", body.direction)
        assertEquals(BigDecimal("45.50"), body.amount)
        assertEquals("USD", body.currency)
        assertNull(body.category)
        assertEquals("WEEKLY", body.frequency)
        assertEquals("2026-10-10", body.nextDueDate)
        assertNull(body.endDate)
    }

    @Test
    fun `list maps DTOs preserving positive amount and direction`() = runTest {
        val h = harness()
        signIn(h)
        h.service.listResult = {
            listOf(
                FakeRecurringService.template(direction = "EXPENSE", amount = BigDecimal("1200")),
                FakeRecurringService.template(
                    id = "r2", direction = "INCOME", amount = BigDecimal("3000"),
                    category = null, frequency = "WEEKLY",
                ),
            )
        }
        val list = h.repository.list()
        assertEquals(TransactionType.EXPENSE, list[0].direction)
        assertEquals(BigDecimal("1200"), list[0].amount.amount)
        assertEquals(TransactionType.INCOME, list[1].direction)
        assertNull(list[1].category)
        assertEquals(RecurringFrequency.WEEKLY, list[1].frequency)
        assertEquals(LocalDate.parse("2026-11-01"), list[1].nextDueDate)
    }

    @Test
    fun `unknown frequency armors to UNKNOWN`() = runTest {
        val h = harness()
        signIn(h)
        h.service.listResult = { listOf(FakeRecurringService.template(frequency = "DAILY")) }
        assertEquals(RecurringFrequency.UNKNOWN, h.repository.list().single().frequency)
    }

    @Test
    fun `deactivate emits the mutation signal`() = runTest {
        val h = harness()
        signIn(h)
        val emissions = mutableListOf<Unit>()
        val collector = async { h.repository.mutations.collect { emissions += Unit } }
        runCurrent() // collector is live before the emit
        h.repository.deactivate("r1")
        runCurrent()
        collector.cancel()
        assertEquals(listOf("r1"), h.service.deactivateCalls)
        assertEquals(1, emissions.size)
    }

    @Test
    fun `a dead-generation create never hits the wire-visible mutation`() = runTest {
        val h = harness()
        signIn(h)
        val gate = CompletableDeferred<com.spendwise.android.data.remote.RecurringTransactionDto>()
        h.service.createResult = { gate.await() }
        val emissions = mutableListOf<Unit>()
        val collector = async { h.repository.mutations.collect { emissions += Unit } }
        val pending = async { create(h) }
        runCurrent()
        h.sessionManager.signOut()
        gate.complete(FakeRecurringService.template())
        runCatching { pending.await() }
        runCurrent()
        collector.cancel()
        assertTrue(emissions.isEmpty())
    }

    @Test
    fun `foreign deactivate surfaces as Unknown (403 has no dedicated AppError)`() = runTest {
        val h = harness()
        signIn(h)
        h.service.deactivateResult = { throw ApiException(403, "Forbidden") }
        val thrown = runCatching { h.repository.deactivate("other-users") }.exceptionOrNull()
        assertTrue((thrown as? AppErrorException)?.error is AppError.Unknown)
    }

    // ── Update (M16 / REQ-BE-012) ───────────────────────────────────

    @Test
    fun `update maps a sparse patch to the wire DTO`() = runTest {
        val h = harness()
        signIn(h)
        h.repository.update(
            id = "r1",
            amount = Money(BigDecimal("950.50"), CurrencyCode("VND")),
            frequency = RecurringFrequency.WEEKLY,
            nextDueDate = LocalDate.parse("2026-11-15"),
            endDate = LocalDate.parse("2027-01-15"),
        )
        val (id, body) = h.service.updateCalls.single()
        assertEquals("r1", id)
        assertEquals(BigDecimal("950.50"), body.amount)
        assertEquals("WEEKLY", body.frequency)
        assertEquals("2026-11-15", body.nextDueDate)
        assertEquals("2027-01-15", body.endDate)
        assertNull(body.category)
        assertEquals(false, body.clearCategory)
        assertEquals(false, body.clearEndDate)
    }

    @Test
    fun `update sends clear flags and no category value when clearing`() = runTest {
        val h = harness()
        signIn(h)
        h.repository.update(id = "r1", clearCategory = true, clearEndDate = true)
        val (_, body) = h.service.updateCalls.single()
        assertEquals(true, body.clearCategory)
        assertEquals(true, body.clearEndDate)
        assertNull(body.category)
        assertNull(body.endDate)
        assertNull(body.amount)
        assertNull(body.frequency)
        assertNull(body.nextDueDate)
    }

    @Test
    fun `update emits the mutation signal`() = runTest {
        val h = harness()
        signIn(h)
        val emissions = mutableListOf<Unit>()
        val collector = async { h.repository.mutations.collect { emissions += Unit } }
        runCurrent()
        h.repository.update(id = "r1", amount = Money(BigDecimal("1"), CurrencyCode("VND")))
        runCurrent()
        collector.cancel()
        assertEquals(1, emissions.size)
    }

    @Test
    fun `a dead-generation update is discarded and emits nothing`() = runTest {
        val h = harness()
        signIn(h)
        val gate = CompletableDeferred<com.spendwise.android.data.remote.RecurringTransactionDto>()
        h.service.updateResult = { _, _ -> gate.await() }
        val emissions = mutableListOf<Unit>()
        val collector = async { h.repository.mutations.collect { emissions += Unit } }
        val pending = async {
            h.repository.update(id = "r1", amount = Money(BigDecimal("1"), CurrencyCode("VND")))
        }
        runCurrent()
        h.sessionManager.signOut()
        gate.complete(FakeRecurringService.template())
        val thrown = runCatching { pending.await() }.exceptionOrNull()
        runCurrent()
        collector.cancel()
        assertTrue(thrown is SessionEndedException)
        assertTrue(emissions.isEmpty())
    }

    @Test
    fun `update rejects an armored frequency before any wire call`() = runTest {
        val h = harness()
        signIn(h)
        val thrown = runCatching {
            h.repository.update(id = "r1", frequency = RecurringFrequency.UNKNOWN)
        }.exceptionOrNull()
        assertTrue(thrown is IllegalArgumentException)
        assertTrue(h.service.updateCalls.isEmpty())
    }

    @Test
    fun `update rejects a value paired with its own clear flag`() = runTest {
        val h = harness()
        signIn(h)
        runCatching {
            h.repository.update(id = "r1", category = "FOOD", clearCategory = true)
        }.also { assertTrue(it.exceptionOrNull() is IllegalArgumentException) }
        runCatching {
            h.repository.update(
                id = "r1",
                endDate = LocalDate.parse("2027-01-01"),
                clearEndDate = true,
            )
        }.also { assertTrue(it.exceptionOrNull() is IllegalArgumentException) }
        assertTrue(h.service.updateCalls.isEmpty())
    }

    @Test
    fun `update maps a 404 to NotFound`() = runTest {
        val h = harness()
        signIn(h)
        h.service.updateResult = { _, _ -> throw ApiException(404, "Not found") }
        val thrown = runCatching {
            h.repository.update(id = "r1", amount = Money(BigDecimal("1"), CurrencyCode("VND")))
        }.exceptionOrNull()
        assertTrue((thrown as? AppErrorException)?.error is AppError.NotFound)
    }

    @Test
    fun `a dead-generation list result is discarded`() = runTest {
        val h = harness()
        signIn(h)
        val gate = CompletableDeferred<List<com.spendwise.android.data.remote.RecurringTransactionDto>>()
        h.service.listResult = { gate.await() }
        val pending = async { h.repository.list() }
        runCurrent()
        h.sessionManager.signOut()
        gate.complete(emptyList())
        val thrown = runCatching { pending.await() }.exceptionOrNull()
        assertTrue(thrown is SessionEndedException)
    }
}
