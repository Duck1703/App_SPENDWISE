package com.spendwise.android.data

import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.model.TransactionProvenance
import com.spendwise.android.core.model.TransactionType
import com.spendwise.android.data.remote.TransactionPageDto
import com.spendwise.android.core.network.ApiException
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.repository.RemoteTransactionRepository
import com.spendwise.android.fakes.FakeAccountRepository
import com.spendwise.android.fakes.FakeTransactionsService
import com.spendwise.android.fakes.FakeTokenStore
import com.spendwise.android.fakes.FakeTransfersService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class TransactionRepositoryTest {

    private val now: Instant = Instant.parse("2026-10-04T12:00:00Z")
    private val testDate: LocalDate = LocalDate.of(2026, 9, 15)

    private data class Harness(
        val service: FakeTransactionsService,
        val transfers: FakeTransfersService,
        val accounts: FakeAccountRepository,
        val sessionManager: SessionManager,
        val repository: RemoteTransactionRepository,
    )

    private fun harness(): Harness {
        val service = FakeTransactionsService()
        val transfers = FakeTransfersService()
        val accounts = FakeAccountRepository()
        val sessionManager = SessionManager(
            tokenStore = FakeTokenStore(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        val scope = CoroutineScope(Dispatchers.Unconfined)
        return Harness(
            service,
            transfers,
            accounts,
            sessionManager,
            RemoteTransactionRepository(service, transfers, accounts, sessionManager, scope),
        )
    }

    private fun dto(id: String, type: String? = "EXPENSE", category: String? = "FOOD", ref: String? = "MANUAL:$id") =
        FakeTransactionsService.transactionDto(id = id, type = type, category = category, sourceRef = ref)

    private fun money(amount: String) = Money(BigDecimal(amount), CurrencyCode("VND"))

    @Test
    fun `list maps the contract fields and keeps amounts positive`() = runTest {
        val (service, _, _, _, repository) = harness()
        service.listResponse = listOf(
            dto("t1", type = "EXPENSE"),
            dto("t2", type = "INCOME", category = null, ref = null),
        )

        val list = repository.list()

        assertEquals(2, list.size)
        assertEquals(TransactionType.EXPENSE, list[0].type)
        assertEquals(BigDecimal("25.50"), list[0].amount.amount)
        assertEquals("FOOD", list[0].category)
        assertEquals(TransactionProvenance.MANUAL, list[0].provenance)
        assertEquals(TransactionProvenance.UNKNOWN, list[1].provenance)
        // Server ISO date parses to the same calendar LocalDate.
        assertEquals(LocalDate.of(2026, 10, 4), list[0].occurredOn)
    }

    @Test
    fun `a malformed server date fails loudly — no fabricated date`() = runTest {
        val (service, _, _, _, repository) = harness()
        service.listResponse = listOf(
            FakeTransactionsService.transactionDto(id = "t1", occurredOn = "15/09/2026"),
        )

        try {
            repository.list()
            fail("Expected AppErrorException")
        } catch (e: AppErrorException) {
            assertTrue(e.error is AppError.Unknown)
        }
    }

    @Test
    fun `list passes the three server params verbatim and omits All`() = runTest {
        val (service, _, _, _, repository) = harness()
        repository.list(accountId = "a1", type = TransactionType.INCOME, category = "SALARY")
        repository.list()

        assertEquals(
            FakeTransactionsService.ListCall("a1", "INCOME", "SALARY"),
            service.listCalls[0],
        )
        assertEquals(
            FakeTransactionsService.ListCall(null, null, null),
            service.listCalls[1],
        )
    }

    @Test
    fun `findById resolves from the last fetched list without a request`() = runTest {
        val (service, _, _, _, repository) = harness()
        service.listResponse = listOf(dto("t1"), dto("t7"))
        repository.list(accountId = "a1")

        val found = repository.findById("t7")

        assertEquals("t7", found.id)
        // One filtered fetch only — the lookup hit the warmed cache.
        assertEquals(1, service.listCalls.size)
    }

    @Test
    fun `findById cache miss falls back to one unfiltered fetch`() = runTest {
        val (service, _, _, _, repository) = harness()
        service.listResponse = listOf(dto("t5"))

        val found = repository.findById("t5")

        assertEquals("t5", found.id)
        assertEquals(
            FakeTransactionsService.ListCall(null, null, null),
            service.listCalls.single(),
        )
    }

    @Test
    fun `findById absent after fetch maps to NotFound`() = runTest {
        val (_, _, _, _, repository) = harness()

        try {
            repository.findById("ghost")
            fail("Expected AppErrorException")
        } catch (e: AppErrorException) {
            assertTrue(e.error is AppError.NotFound)
        }
    }

    @Test
    fun `create posts the contract fields and maps the response`() = runTest {
        val (service, _, accounts, _, repository) = harness()
        service.createResponse = dto("new", type = "INCOME", category = "SALARY")

        val created = repository.create(
            accountId = "a1",
            type = TransactionType.INCOME,
            amount = money("900.00"),
            occurredOn = testDate,
            category = "SALARY",
            hint = "october salary",
        )

        assertEquals("new", created.id)
        val call = service.createCalls.single()
        assertEquals("a1", call.accountId)
        assertEquals("INCOME", call.type)
        assertEquals(BigDecimal("900.00"), call.amount)
        assertEquals("VND", call.currency)
        assertEquals("SALARY", call.category)
        assertEquals("october salary", call.description)
        // Exact ISO wire value — LocalDate.toString() is yyyy-MM-dd.
        assertEquals("2026-09-15", call.occurredOn)
        // Balances are server-authoritative — the account cache drops.
        assertEquals(1, accounts.invalidateCalls)
    }

    @Test
    fun `blank hint stays off the wire`() = runTest {
        val (service, _, _, _, repository) = harness()
        service.createResponse = dto("new")

        repository.create("a1", TransactionType.EXPENSE, money("5"), testDate, hint = "   ")

        assertNull(service.createCalls.single().description)
    }

    @Test
    fun `create rejects transfer and unknown types locally — they never reach the wire`() = runTest {
        val (service, _, _, _, repository) = harness()

        for (type in listOf(
            TransactionType.TRANSFER_IN,
            TransactionType.TRANSFER_OUT,
            TransactionType.UNKNOWN,
        )) {
            try {
                repository.create("a1", type, money("5"), testDate)
                fail("Expected IllegalArgumentException for $type")
            } catch (_: IllegalArgumentException) {
            }
        }
        assertTrue(service.createCalls.isEmpty())
    }

    @Test
    fun `successful create emits one mutation and re-lists fresh`() = runTest {
        val (service, _, _, _, repository) = harness()
        service.listResponse = listOf(dto("t1"))
        repository.list()

        val emissions = mutableListOf<Unit>()
        val job = launch { repository.mutations.collect { emissions += Unit } }
        runCurrent() // register the collector before the emit

        service.createResponse = dto("t2")
        repository.create("a1", TransactionType.EXPENSE, money("1"), testDate)
        runCurrent() // let the collector consume the buffered emission
        job.cancel()

        assertEquals(1, emissions.size)
        // The detail cache was invalidated — the next lookup refetches.
        service.listResponse = listOf(dto("t1"), dto("t2"))
        assertEquals("t2", repository.findById("t2").id)
        assertEquals(2, service.listCalls.size)
    }

    @Test
    fun `io failure maps to NetworkUnavailable`() = runTest {
        val (service, _, _, _, repository) = harness()
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
        val (service, _, _, _, repository) = harness()
        service.createError = ApiException(
            status = 400,
            fields = mapOf("amount" to "must be greater than 0"),
        )

        try {
            repository.create("a1", TransactionType.EXPENSE, money("-1"), testDate)
            fail("Expected AppErrorException")
        } catch (e: AppErrorException) {
            assertEquals(
                AppError.Validation(mapOf("amount" to "must be greater than 0")),
                e.error,
            )
        }
    }

    @Test
    fun `unknown-account 404 on create maps to NotFound`() = runTest {
        val (service, _, _, _, repository) = harness()
        service.createError = ApiException(status = 404)

        try {
            repository.create("foreign", TransactionType.EXPENSE, money("5"), testDate)
            fail("Expected AppErrorException")
        } catch (e: AppErrorException) {
            assertTrue(e.error is AppError.NotFound)
        }
    }

    @Test
    fun `unknown wire type decodes as UNKNOWN instead of crashing`() = runTest {
        val (service, _, _, _, repository) = harness()
        service.listResponse = listOf(dto("t1", type = "TRANSFER_OUT"))

        val list = repository.list()

        assertEquals(TransactionType.TRANSFER_OUT, list.single().type)
        service.listResponse = listOf(dto("t2", type = "FUTURE_KIND"))
        assertEquals(TransactionType.UNKNOWN, repository.list().single().type)
    }

    @Test
    fun `unrecognized sourceRef prefixes read as UNKNOWN provenance, never guessed`() = runTest {
        val (service, _, _, _, repository) = harness()
        service.listResponse = listOf(
            dto("t1", ref = "IMPORT:CSV:1"),
            dto("t2", ref = "RECURRING:tmpl:2026-10-04"),
            dto("t3", ref = "TRANSFER:ref:a"),
            dto("t4", ref = "STRANGE:new-thing"),
        )

        assertEquals(
            listOf(
                TransactionProvenance.IMPORTED,
                TransactionProvenance.RECURRING,
                TransactionProvenance.TRANSFER,
                TransactionProvenance.UNKNOWN,
            ),
            repository.list().map { it.provenance },
        )
    }

    // ── M12: POST /api/transfers ────────────────────────────────────

    @Test
    fun `transfer posts the contract fields and maps the paired response`() = runTest {
        val (_, transfers, accounts, _, repository) = harness()

        val outcome = repository.transfer(
            sourceAccountId = "a1",
            destinationAccountId = "a2",
            amount = Money(BigDecimal("25.00"), CurrencyCode("USD")),
        )

        val call = transfers.createCalls.single()
        assertEquals("a1", call.sourceAccountId)
        assertEquals("a2", call.destinationAccountId)
        assertEquals(BigDecimal("25.00"), call.amount)
        assertEquals("USD", call.currency)

        assertEquals("ref-1", outcome.transferRef)
        assertEquals(TransactionType.TRANSFER_OUT, outcome.outTransaction.type)
        assertEquals(TransactionType.TRANSFER_IN, outcome.inTransaction.type)
        assertEquals("TRANSFER:ref-1:OUT", outcome.outTransaction.sourceRef)
        assertEquals(TransactionProvenance.TRANSFER, outcome.outTransaction.provenance)
        assertEquals(TransactionProvenance.TRANSFER, outcome.inTransaction.provenance)
        // The server moved BOTH balances — the account cache drops, and
        // no local arithmetic ever touches canonical balances (INV-012).
        assertEquals(1, accounts.invalidateCalls)
    }

    @Test
    fun `transfer emits one mutation and the next list refetches`() = runTest {
        val (_, _, _, _, repository) = harness()
        repository.list()

        val emissions = mutableListOf<Unit>()
        val job = launch { repository.mutations.collect { emissions += Unit } }
        runCurrent()

        repository.transfer("a1", "a2", money("5"))
        runCurrent()
        job.cancel()

        assertEquals(1, emissions.size)
    }

    @Test
    fun `transfer rejects identical accounts locally — never reaches the wire`() = runTest {
        val (_, transfers, _, _, repository) = harness()

        try {
            repository.transfer("a1", "a1", money("5"))
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        assertTrue(transfers.createCalls.isEmpty())
    }

    @Test
    fun `transfer maps ApiException to AppErrorException`() = runTest {
        val (_, transfers, _, _, repository) = harness()
        transfers.createError = ApiException(
            status = 400,
            fields = mapOf("amount" to "must be positive"),
        )

        try {
            repository.transfer("a1", "a2", money("0"))
            fail("Expected AppErrorException")
        } catch (e: AppErrorException) {
            assertTrue(e.error is AppError.Validation)
        }
    }

    @Test
    fun `transfer io failure maps to NetworkUnavailable`() = runTest {
        val (_, transfers, _, _, repository) = harness()
        transfers.createError = IOException("offline")

        try {
            repository.transfer("a1", "a2", money("5"))
            fail("Expected AppErrorException")
        } catch (e: AppErrorException) {
            assertEquals(AppError.NetworkUnavailable, e.error)
        }
    }

    // ── M15: GET /api/transactions/page ─────────────────────────────

    private fun pageDto(
        content: List<com.spendwise.android.data.remote.TransactionDto>,
        page: Int = 0,
        size: Int = 50,
        total: Long = content.size.toLong(),
        pages: Int = 1,
        hasNext: Boolean = false,
    ) = TransactionPageDto(
        content = content,
        page = page,
        size = size,
        totalElements = total,
        totalPages = pages,
        hasNext = hasNext,
    )

    @Test
    fun `listPage maps the envelope and warms the detail cache`() = runTest {
        val (service, _, _, _, repository) = harness()
        service.pageResponder = {
            pageDto(
                listOf(dto("t1"), dto("t2", type = "INCOME", category = null, ref = "IMPORT:CSV:7")),
                total = 40,
                pages = 20,
                hasNext = true,
            )
        }

        val page = repository.listPage(page = 0)

        assertEquals(2, page.items.size)
        assertEquals("t1", page.items[0].id)
        assertEquals(TransactionProvenance.IMPORTED, page.items[1].provenance)
        assertEquals(40L, page.totalElements)
        assertEquals(20, page.totalPages)
        assertTrue(page.hasNext)
        // Page content feeds findById — no second request.
        assertEquals("t2", repository.findById("t2").id)
        assertEquals(1, service.pageCalls.size)
        assertEquals(0, service.listCalls.size)
    }

    @Test
    fun `listPage passes every filter verbatim — dates serialize as ISO`() = runTest {
        val (service, _, _, _, repository) = harness()

        repository.listPage(
            accountId = "a1",
            type = TransactionType.EXPENSE,
            category = "FOOD",
            uncategorized = false,
            provenance = TransactionProvenance.RECURRING,
            from = LocalDate.of(2026, 9, 1),
            to = LocalDate.of(2026, 9, 30),
            page = 3,
            size = 25,
        )

        val call = service.pageCalls.single()
        assertEquals("a1", call.accountId)
        assertEquals("EXPENSE", call.type)
        assertEquals("FOOD", call.category)
        // uncategorized=false is NOT sent — false is "no filter".
        assertNull(call.uncategorized)
        assertEquals("RECURRING", call.provenance)
        assertEquals("2026-09-01", call.from)
        assertEquals("2026-09-30", call.to)
        assertEquals(3, call.page)
        assertEquals(25, call.size)
    }

    @Test
    fun `listPage uncategorized=true is the only time the flag is sent`() = runTest {
        val (service, _, _, _, repository) = harness()

        repository.listPage(uncategorized = true, page = 0)

        assertEquals(true, service.pageCalls.single().uncategorized)
    }

    @Test
    fun `listPage merges into the cache — earlier pages stay resolvable`() = runTest {
        val (service, _, _, _, repository) = harness()
        service.pageResponder = { call ->
            when (call.page) {
                0 -> pageDto(listOf(dto("t1")), total = 2, hasNext = true)
                else -> pageDto(listOf(dto("t2")), page = 1, total = 2, hasNext = false)
            }
        }

        repository.listPage(page = 0)
        repository.listPage(page = 1)

        // A page-0 row is still resolvable after page 1 merged in.
        assertEquals("t1", repository.findById("t1").id)
        assertEquals("t2", repository.findById("t2").id)
        assertEquals(2, service.pageCalls.size)
        assertEquals(0, service.listCalls.size) // no unfiltered refetch
    }

    @Test
    fun `listPage failure maps errors like the unpaged list`() = runTest {
        val (service, _, _, _, repository) = harness()
        service.pageError = ApiException(status = 400)

        try {
            repository.listPage(page = -1)
            fail("Expected AppErrorException")
        } catch (e: AppErrorException) {
            assertEquals(AppError.BadRequest, e.error)
        }
    }
}
