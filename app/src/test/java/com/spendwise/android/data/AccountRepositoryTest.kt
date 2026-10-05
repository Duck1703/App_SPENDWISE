package com.spendwise.android.data

import com.spendwise.android.core.network.ApiException
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.repository.RemoteAccountRepository
import com.spendwise.android.fakes.FakeAccountsService
import com.spendwise.android.fakes.FakeTokenStore
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
import java.time.ZoneOffset

class AccountRepositoryTest {

    private val now: Instant = Instant.parse("2026-10-04T12:00:00Z")

    private fun harness(): Triple<FakeAccountsService, SessionManager, RemoteAccountRepository> {
        val service = FakeAccountsService()
        val sessionManager = SessionManager(
            tokenStore = FakeTokenStore(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        // Repository needs the app scope at construction — a plain scope is
        // fine: invalidation signals are tryEmit'd (never lost).
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.Dispatchers.Unconfined,
        )
        return Triple(service, sessionManager, RemoteAccountRepository(service, sessionManager, scope))
    }

    private fun cash(id: String, balance: String = "10.00") = FakeAccountsService.accountDto(
        id = id,
        name = "Wallet $id",
        type = "CASH",
        currency = "VND",
        openingBalance = BigDecimal(balance),
        balance = BigDecimal(balance),
    )

    @Test
    fun `list caches the session result and serves repeats without network`() = runTest {
        val (service, _, repository) = harness()
        service.listResponse = listOf(cash("a1"), cash("a2"))

        val first = repository.list()
        val second = repository.list()

        assertEquals(2, first.size)
        assertEquals(1, service.listCalls)
        assertEquals("a2", second[1].id)
        assertEquals(BigDecimal("10.00"), first[0].balance.amount)
    }

    @Test
    fun `list refresh bypasses the cache`() = runTest {
        val (service, _, repository) = harness()
        service.listResponse = listOf(cash("a1"))
        repository.list()
        service.listResponse = listOf(cash("a1"), cash("a2"))

        val refreshed = repository.list(refresh = true)

        assertEquals(2, service.listCalls)
        assertEquals(2, refreshed.size)
    }

    @Test
    fun `findById serves from the list cache without a request`() = runTest {
        val (service, _, repository) = harness()
        service.listResponse = listOf(cash("a1"))
        repository.list()

        val account = repository.findById("a1")

        assertEquals("a1", account.id)
        assertEquals(0, service.getByIdCalls.size)
    }

    @Test
    fun `findById cache miss falls back to GET and merges into cache`() = runTest {
        val (service, _, repository) = harness()
        service.listResponse = listOf(cash("a1"))
        service.getByIdResponse = cash("a2")
        repository.list()

        val account = repository.findById("a2")

        assertEquals(listOf("a2"), service.getByIdCalls)
        // The fetched account is now part of the session cache.
        assertEquals(setOf("a1", "a2"), repository.list().map { it.id }.toSet())
        assertEquals(1, service.listCalls)
    }

    @Test
    fun `create posts the contract fields and extends the cache`() = runTest {
        val (service, _, repository) = harness()
        service.listResponse = listOf(cash("a1"))
        service.createResponse = cash("new", balance = "1500.50")
        repository.list()

        val created = repository.create(
            name = "Jar",
            type = com.spendwise.android.core.model.AccountType.CASH,
            openingBalance = com.spendwise.android.core.model.Money(
                BigDecimal("1500.50"),
                com.spendwise.android.core.model.CurrencyCode("VND"),
            ),
        )

        assertEquals("new", created.id)
        val call = service.createCalls.single()
        assertEquals("Jar", call.name)
        assertEquals("CASH", call.type)
        assertEquals(BigDecimal("1500.50"), call.openingBalance)
        assertEquals("VND", call.currency)
        assertEquals(setOf("a1", "new"), repository.list().map { it.id }.toSet())
        assertEquals(1, service.listCalls)
    }

    @Test
    fun `create emits one mutation signal — Home surfaces reload`() = runTest {
        // M4: the create→mutations contract lets balance-bearing
        // surfaces (Home carousel) refresh without polling.
        val (service, _, repository) = harness()
        service.createResponse = cash("new", balance = "1500.50")
        val emissions = mutableListOf<Unit>()
        val job = backgroundScope.launch {
            repository.mutations.collect { emissions += it }
        }
        testScheduler.runCurrent() // attach the collector before the emit

        repository.create(
            name = "Jar",
            type = com.spendwise.android.core.model.AccountType.CASH,
            openingBalance = com.spendwise.android.core.model.Money(
                BigDecimal("1500.50"),
                com.spendwise.android.core.model.CurrencyCode("VND"),
            ),
        )

        testScheduler.runCurrent() // pump the collector
        assertEquals(1, emissions.size)
        job.cancel()
    }

    @Test
    fun `session invalidation drops the cache so list refetches`() = runTest {
        val (service, sessionManager, repository) = harness()
        sessionManager.onSignedIn("t1", now.plusSeconds(900), "ana", "u1")
        service.listResponse = listOf(cash("a1"))
        repository.list()

        sessionManager.onAuthenticationExpired()

        repository.list()
        assertEquals(2, service.listCalls)
    }

    @Test
    fun `404 maps to NotFound`() = runTest {
        val (service, _, repository) = harness()
        service.getByIdError = ApiException(status = 404)

        try {
            repository.findById("ghost")
            fail("Expected AppErrorException")
        } catch (e: AppErrorException) {
            assertTrue(e.error is AppError.NotFound)
        }
    }

    @Test
    fun `io failure maps to NetworkUnavailable`() = runTest {
        val (service, _, repository) = harness()
        service.listError = IOException("offline")

        try {
            repository.list()
            fail("Expected AppErrorException")
        } catch (e: AppErrorException) {
            assertEquals(AppError.NetworkUnavailable, e.error)
        }
    }

    @Test
    fun `400 with fields maps to Validation`() = runTest {
        val (service, _, repository) = harness()
        service.createError = ApiException(
            status = 400,
            fields = mapOf("name" to "must not be blank"),
        )

        try {
            repository.create(
                " ",
                com.spendwise.android.core.model.AccountType.CASH,
                com.spendwise.android.core.model.Money(
                    BigDecimal.ZERO,
                    com.spendwise.android.core.model.CurrencyCode("VND"),
                ),
            )
            fail("Expected AppErrorException")
        } catch (e: AppErrorException) {
            assertEquals(AppError.Validation(mapOf("name" to "must not be blank")), e.error)
        }
    }

    @Test
    fun `unknown type on the wire maps to UNKNOWN instead of crashing`() = runTest {
        val (service, _, repository) = harness()
        service.listResponse = listOf(cash("a1").copy(type = "SAVINGS_VAULT"))

        val accounts = repository.list()

        assertEquals(
            com.spendwise.android.core.model.AccountType.UNKNOWN,
            accounts.single().type,
        )
    }

    @Test
    fun `invalid currency on the wire maps to Unknown error`() = runTest {
        val (service, _, repository) = harness()
        service.listResponse = listOf(cash("a1").copy(currency = "vnd"))

        try {
            repository.list()
            fail("Expected AppErrorException")
        } catch (e: AppErrorException) {
            assertTrue(e.error is AppError.Unknown)
        }
    }
}
