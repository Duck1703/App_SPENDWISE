package com.spendwise.android.data

import com.spendwise.android.core.network.ApiException
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.repository.RemoteDashboardRepository
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.fakes.FakeDashboardService
import com.spendwise.android.fakes.FakeTokenStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * Dashboard repository contract (M4): always-fresh fetch, `YYYY-MM`
 * param derivation, error mapping, and the EXT-MAJOR-1 dead-generation
 * publish guard.
 */
class DashboardRepositoryTest {

    private val now: Instant = Instant.parse("2026-10-04T12:00:00Z")

    private fun harness(): Triple<FakeDashboardService, SessionManager, RemoteDashboardRepository> {
        val service = FakeDashboardService()
        val sessionManager = SessionManager(
            tokenStore = FakeTokenStore(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        return Triple(service, sessionManager, RemoteDashboardRepository(service, sessionManager))
    }

    @Test
    fun `month maps the DTO totals and echoes the server month`() = runTest {
        val (service, _, repository) = harness()
        service.monthResponse = FakeDashboardService.monthDto(
            month = "2026-10",
            totalIncome = BigDecimal("4500.00"),
            totalExpense = BigDecimal("1200.50"),
        )

        val month = repository.month(YearMonth.of(2026, 10))

        assertEquals("2026-10", month.month)
        assertEquals(BigDecimal("4500.00"), month.totalIncome)
        assertEquals(BigDecimal("1200.50"), month.totalExpense)
        assertEquals(listOf("2026-10"), service.monthCalls)
    }

    @Test
    fun `month is always-fresh — no session caching`() = runTest {
        val (service, _, repository) = harness()

        repository.month(YearMonth.of(2026, 10))
        repository.month(YearMonth.of(2026, 10))

        assertEquals(2, service.monthCalls.size) // §17: computed per request
    }

    @Test
    fun `default month derives from the device-local clock not the UTC bean`() = runTest {
        val (service, _, repository) = harness()

        repository.month()

        // The fake clock is Oct 4 2026 UTC; the default arg uses
        // YearMonth.now() (device-local). Under the JVM-default zone in
        // CI this is still 2026-10 — the assertion pins the param
        // format, and the repository never uses the injected UTC Clock.
        assertEquals(1, service.monthCalls.size)
        assertTrue(service.monthCalls.single().matches(Regex("\\d{4}-\\d{2}")))
        assertEquals(YearMonth.now().toString(), service.monthCalls.single())
    }

    @Test
    fun `explicit month serializes to YYYY-MM`() = runTest {
        val (service, _, repository) = harness()

        repository.month(YearMonth.of(2027, 1))

        assertEquals(listOf("2027-01"), service.monthCalls)
    }

    @Test
    fun `400 — mixed-currency aggregation — maps to BadRequest`() = runTest {
        val (service, _, repository) = harness()
        service.monthError = ApiException(status = 400)

        try {
            repository.month(YearMonth.of(2026, 10))
            fail("Expected AppErrorException")
        } catch (e: AppErrorException) {
            assertEquals(AppError.BadRequest, e.error)
        }
    }

    @Test
    fun `401 maps to SessionExpired`() = runTest {
        val (service, _, repository) = harness()
        service.monthError = ApiException(status = 401)

        try {
            repository.month(YearMonth.of(2026, 10))
            fail("Expected AppErrorException")
        } catch (e: AppErrorException) {
            assertEquals(AppError.SessionExpired, e.error)
        }
    }

    @Test
    fun `io failure maps to NetworkUnavailable`() = runTest {
        val (service, _, repository) = harness()
        service.monthError = IOException("offline")

        try {
            repository.month(YearMonth.of(2026, 10))
            fail("Expected AppErrorException")
        } catch (e: AppErrorException) {
            assertEquals(AppError.NetworkUnavailable, e.error)
        }
    }

    @Test
    fun `response landing after a generation boundary throws SessionEndedException`() = runTest {
        // EXT-MAJOR-1 privacy gate: the fetch is dispatched under
        // generation N; a sign-out/sign-in advances the generation
        // before the response lands — the dead result must never
        // surface to the new session.
        val (service, sessionManager, repository) = harness()
        sessionManager.onSignedIn("token-a", now.plusSeconds(900), "ana", "u1")
        val gate = CompletableDeferred<com.spendwise.android.data.remote.DashboardMonthDto>()
        service.monthResponder = { gate.await() }

        val result = backgroundScope.async { repository.month(YearMonth.of(2026, 10)) }
        testScheduler.runCurrent() // let the call read generation N and park on the gate

        sessionManager.signOut()
        sessionManager.onSignedIn("token-b", now.plusSeconds(900), "bob", "u2")
        gate.complete(FakeDashboardService.monthDto())

        try {
            result.await()
            fail("Expected SessionEndedException")
        } catch (e: SessionEndedException) {
            // dead-generation publish blocked
        }
    }

    @Test
    fun `same-generation response publishes normally`() = runTest {
        val (service, sessionManager, repository) = harness()
        sessionManager.onSignedIn("token-a", now.plusSeconds(900), "ana", "u1")

        val month = repository.month(YearMonth.of(2026, 10))

        assertEquals(BigDecimal("4500.00"), month.totalIncome)
        assertEquals(listOf("2026-10"), service.monthCalls)
    }

    @Test
    fun `a failed fetch does not poison the next call`() = runTest {
        val (service, _, repository) = harness()
        service.monthError = IOException("offline")

        try {
            repository.month(YearMonth.of(2026, 10))
            fail("Expected AppErrorException")
        } catch (e: AppErrorException) {
            assertEquals(AppError.NetworkUnavailable, e.error)
        }

        service.monthError = null
        service.monthResponse = FakeDashboardService.monthDto(totalIncome = BigDecimal("7"))
        val month = repository.month(YearMonth.of(2026, 10))

        assertEquals(BigDecimal("7"), month.totalIncome)
        assertEquals(2, service.monthCalls.size)
    }

    @Test
    fun `cancelled fetch propagates CancellationException unmangled`() = runTest {
        val (service, _, repository) = harness()
        val gate = CompletableDeferred<com.spendwise.android.data.remote.DashboardMonthDto>()
        service.monthResponder = { gate.await() }

        val job = backgroundScope.launch {
            try {
                repository.month(YearMonth.of(2026, 10))
                fail("Expected CancellationException")
            } catch (e: kotlinx.coroutines.CancellationException) {
                assertTrue(e !is AppErrorException)
            }
        }
        job.cancel()
        job.join()
    }
}
