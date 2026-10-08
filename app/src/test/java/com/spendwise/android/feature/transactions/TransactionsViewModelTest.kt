package com.spendwise.android.feature.transactions

import com.spendwise.android.core.model.Transaction
import com.spendwise.android.core.model.TransactionPage
import com.spendwise.android.core.model.TransactionProvenance
import com.spendwise.android.core.model.TransactionType
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.fakes.FakeAccountRepository
import com.spendwise.android.fakes.FakeCategoryRepository
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.data.repository.TransactionRepository
import com.spendwise.android.fakes.FakeTransactionRepository
import com.spendwise.android.fakes.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(
        txns: FakeTransactionRepository = FakeTransactionRepository(),
        accounts: FakeAccountRepository = FakeAccountRepository(),
        cats: FakeCategoryRepository = FakeCategoryRepository(),
    ) = TransactionsViewModel(txns, accounts, cats)

    private fun twoRows() = listOf(
        FakeTransactionRepository.transaction(id = "t1", type = TransactionType.EXPENSE, category = "FOOD"),
        FakeTransactionRepository.transaction(
            id = "t2",
            type = TransactionType.INCOME,
            category = null,
            sourceRef = "IMPORT:CSV:9",
        ),
    )

    /**
     * Wrap loaded rows in a one-page server envelope — totalElements
     * defaults to the row count (a full first page), override for
     * multi-page scenarios.
     */
    private fun page(
        items: List<Transaction>,
        page: Int = 0,
        totalElements: Long = items.size.toLong(),
        hasNext: Boolean = false,
        size: Int = TransactionRepository.DEFAULT_PAGE_SIZE,
    ) = TransactionPage(
        items = items,
        page = page,
        size = size,
        totalElements = totalElements,
        totalPages = if (size > 0) ((totalElements + size - 1) / size).toInt() else 0,
        hasNext = hasNext,
    )

    @Test
    fun `init loads Content with account names for row labels`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listPageResult = { page(twoRows()) }
        val accounts = FakeAccountRepository()
        accounts.listResult = { _ -> listOf(FakeAccountRepository.account(id = "acc-1", name = "Wallet")) }

        val viewModel = vm(txns, accounts)

        val state = viewModel.state.value as TransactionsUiState.Content
        assertEquals(2, state.items.size)
        assertEquals("Wallet", state.accountNames["acc-1"])
        assertEquals(1, txns.pageCalls.size)
        assertEquals(0, txns.pageCalls.single().page)
        assertEquals(TransactionRepository.DEFAULT_PAGE_SIZE, txns.pageCalls.single().size)
    }

    @Test
    fun `empty server page without filters is the Empty entry state`() = runTest {
        val viewModel = vm()

        assertEquals(TransactionsUiState.Empty, viewModel.state.value)
    }

    @Test
    fun `every filter selection is a real server parameter`() = runTest {
        // M15: provenance, uncategorized and date bounds all travel on
        // the wire — a client-side pass over page N would hide matches
        // on not-yet-loaded pages.
        val txns = FakeTransactionRepository()
        txns.listPageResult = { page(twoRows()) }
        val viewModel = vm(txns)

        viewModel.onAccountFilter("a1")
        viewModel.onTypeFilter(TransactionType.EXPENSE)
        viewModel.onCategoryFilter(CategorySelection.Code("FOOD"))
        viewModel.onProvenanceFilter(TransactionProvenance.IMPORTED)
        viewModel.onFromFilter(LocalDate.of(2026, 9, 1))
        viewModel.onToFilter(LocalDate.of(2026, 9, 30))

        val call = txns.pageCalls.last()
        assertEquals("a1", call.accountId)
        assertEquals(TransactionType.EXPENSE, call.type)
        assertEquals("FOOD", call.category)
        assertEquals(false, call.uncategorized)
        assertEquals(TransactionProvenance.IMPORTED, call.provenance)
        assertEquals(LocalDate.of(2026, 9, 1), call.from)
        assertEquals(LocalDate.of(2026, 9, 30), call.to)
        val state = viewModel.state.value as TransactionsUiState.Content
        assertEquals(2, state.items.size)
        assertTrue(state.filters.hasDateRange)
    }

    @Test
    fun `uncategorized selection sends the server flag, not a category`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listPageResult = { page(twoRows()) }
        val viewModel = vm(txns)

        viewModel.onCategoryFilter(CategorySelection.Uncategorized)

        val call = txns.pageCalls.last()
        assertEquals(true, call.uncategorized)
        assertNull(call.category)
    }

    @Test
    fun `filtered to zero keeps Content with noMatches`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listPageResult = { page(emptyList(), totalElements = 0) }
        val viewModel = vm(txns)

        viewModel.onTypeFilter(TransactionType.EXPENSE)

        val state = viewModel.state.value as TransactionsUiState.Content
        assertTrue(state.noMatches)
        assertEquals(0L, state.totalElements)
        assertFalse(state.hasMore)
    }

    @Test
    fun `refresh re-runs the identical query — the nonce defeats conflation`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listPageResult = { page(twoRows()) }
        val viewModel = vm(txns)

        viewModel.refresh()

        assertEquals(2, txns.pageCalls.size)
    }

    @Test
    fun `refresh failure over live content stays an inline banner`() = runTest {
        val txns = FakeTransactionRepository()
        var fail = false
        txns.listPageResult = {
            if (fail) throw AppErrorException(AppError.NetworkUnavailable) else page(twoRows())
        }
        val viewModel = vm(txns)

        fail = true
        viewModel.refresh()

        val state = viewModel.state.value as TransactionsUiState.Content
        assertEquals(2, state.items.size)
        assertEquals(TransactionsError.Network, state.refreshError)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun `initial failure maps to the retryable error state`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listPageResult = { throw AppErrorException(AppError.Server(500)) }

        val viewModel = vm(txns)

        assertEquals(
            TransactionsUiState.Error(TransactionsError.Server),
            viewModel.state.value,
        )
    }

    @Test
    fun `a create mutation reloads the current view`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listPageResult = { page(twoRows()) }
        val viewModel = vm(txns)
        assertEquals(1, txns.pageCalls.size)

        txns.emitMutation()

        assertEquals(2, txns.pageCalls.size)
    }

    @Test
    fun `latest filter wins — a superseded response never publishes`() = runTest {
        val txns = FakeTransactionRepository()
        val gateExpense = CompletableDeferred<TransactionPage>()
        val incomeRows = listOf(
            FakeTransactionRepository.transaction(id = "t9", type = TransactionType.INCOME, category = "SALARY"),
        )
        txns.listPageResult = { call ->
            if (call.type == TransactionType.EXPENSE) gateExpense.await() else page(incomeRows)
        }
        val viewModel = vm(txns)

        viewModel.onTypeFilter(TransactionType.EXPENSE) // parks on the gate
        viewModel.onTypeFilter(TransactionType.INCOME) // supersedes it

        val state = viewModel.state.value as TransactionsUiState.Content
        assertEquals(listOf("t9"), state.items.map { it.id })

        // The parked fetch's cancellation already ran; completing the gate
        // is a no-op — state must remain the latest filter's result.
        gateExpense.complete(page(twoRows()))
        assertEquals(listOf("t9"), (viewModel.state.value as TransactionsUiState.Content).items.map { it.id })
    }

    @Test
    fun `SessionExpired on first load stays Loading and onVisible reloads`() = runTest {
        val txns = FakeTransactionRepository()
        var expired = true
        txns.listPageResult = {
            if (expired) throw AppErrorException(AppError.SessionExpired) else page(twoRows())
        }
        val viewModel = vm(txns)

        assertTrue(viewModel.state.value is TransactionsUiState.Loading)

        expired = false
        viewModel.onVisible()

        val state = viewModel.state.value as TransactionsUiState.Content
        assertEquals(2, state.items.size)
    }

    @Test
    fun `SessionExpired on the account-name leg still reloads on resume`() = runTest {
        // Regression: the page fetch succeeds but the trailing
        // accountNames() call hits the expired session — the load was
        // still in flight, so onVisible() must re-run it (not leave a
        // stuck skeleton).
        val txns = FakeTransactionRepository()
        txns.listPageResult = { page(twoRows()) }
        val accounts = FakeAccountRepository()
        var expired = true
        accounts.listResult = { _ ->
            if (expired) {
                throw AppErrorException(AppError.SessionExpired)
            } else {
                listOf(FakeAccountRepository.account(id = "acc-1", name = "Wallet"))
            }
        }
        val viewModel = vm(txns, accounts)

        assertTrue(viewModel.state.value is TransactionsUiState.Loading)

        expired = false
        viewModel.onVisible()

        val state = viewModel.state.value as TransactionsUiState.Content
        assertEquals("Wallet", state.accountNames["acc-1"])
    }

    @Test
    fun `SessionExpired during refresh preserves content and stops refreshing`() = runTest {
        val txns = FakeTransactionRepository()
        var expired = false
        txns.listPageResult = {
            if (expired) throw AppErrorException(AppError.SessionExpired) else page(twoRows())
        }
        val viewModel = vm(txns)

        expired = true
        viewModel.refresh()

        val state = viewModel.state.value as TransactionsUiState.Content
        assertFalse(state.isRefreshing)
        assertEquals(2, state.items.size)
    }

    @Test
    fun `dead-generation mid-load stays Loading and reloads on resume`() = runTest {
        // EXT-MAJOR-1: a response landing after the generation boundary
        // throws SessionEndedException — the same-user resume must still
        // re-run the aborted load, not sit on the skeleton forever.
        val txns = FakeTransactionRepository()
        var dead = true
        txns.listPageResult = {
            if (dead) throw SessionEndedException() else page(twoRows())
        }
        val viewModel = vm(txns)

        assertTrue(viewModel.state.value is TransactionsUiState.Loading)

        dead = false
        viewModel.onVisible()

        val state = viewModel.state.value as TransactionsUiState.Content
        assertEquals(2, state.items.size)
    }

    @Test
    fun `dead-generation mid-refresh clears isRefreshing and reloads on resume`() = runTest {
        val txns = FakeTransactionRepository()
        var dead = false
        txns.listPageResult = {
            if (dead) throw SessionEndedException() else page(twoRows())
        }
        val viewModel = vm(txns)

        dead = true
        viewModel.refresh()

        var state = viewModel.state.value as TransactionsUiState.Content
        assertFalse(state.isRefreshing)
        assertEquals(2, state.items.size)

        dead = false
        viewModel.onVisible()

        state = viewModel.state.value as TransactionsUiState.Content
        assertEquals(2, state.items.size)
        assertEquals(3, txns.pageCalls.size)
    }

    @Test
    fun `dead-generation on options reloads the filter options on resume`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listPageResult = { page(twoRows()) }
        val accounts = FakeAccountRepository()
        var dead = true
        accounts.listResult = { _ ->
            if (dead) {
                throw SessionEndedException()
            } else {
                listOf(FakeAccountRepository.account(id = "acc-1", name = "Wallet"))
            }
        }
        val viewModel = vm(txns, accounts)

        assertTrue(viewModel.options.value.accounts.isEmpty())

        dead = false
        viewModel.onVisible()

        assertEquals(1, viewModel.options.value.accounts.size)
    }

    @Test
    fun `clear filters restores the unfiltered server call`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listPageResult = { page(twoRows()) }
        val viewModel = vm(txns)

        viewModel.onAccountFilter("a1")
        viewModel.onFromFilter(LocalDate.of(2026, 9, 1))
        viewModel.clearFilters()

        val call = txns.pageCalls.last()
        assertNull(call.accountId)
        assertNull(call.type)
        assertNull(call.category)
        assertNull(call.from)
        assertNull(call.to)
        assertNull(call.provenance)
        assertEquals(false, call.uncategorized)
    }

    // ── M15: paging state machine ─────────────────────────────────────

    @Test
    fun `loadNextPage appends the next server page and keeps metadata honest`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listPageResult = { call ->
            when (call.page) {
                0 -> page(listOf(FakeTransactionRepository.transaction(id = "t1")), totalElements = 3, hasNext = true)
                else -> page(
                    listOf(
                        FakeTransactionRepository.transaction(id = "t2"),
                        FakeTransactionRepository.transaction(id = "t3"),
                    ),
                    page = call.page,
                    totalElements = 3,
                    hasNext = false,
                )
            }
        }
        val viewModel = vm(txns)

        var state = viewModel.state.value as TransactionsUiState.Content
        assertEquals(1, state.items.size)
        assertEquals(3L, state.totalElements)
        assertTrue(state.hasMore)

        viewModel.loadNextPage()

        state = viewModel.state.value as TransactionsUiState.Content
        assertEquals(listOf("t1", "t2", "t3"), state.items.map { it.id })
        assertEquals(3L, state.totalElements)
        assertFalse(state.hasMore)
        assertFalse(state.loadingMore)
        assertEquals(1, txns.pageCalls.last().page)
    }

    @Test
    fun `loadNextPage is a no-op at the end of the ledger`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listPageResult = { page(twoRows(), hasNext = false) }
        val viewModel = vm(txns)

        viewModel.loadNextPage()

        assertEquals(1, txns.pageCalls.size) // only the initial fetch
    }

    @Test
    fun `loadNextPage never double-fires while a page is in flight`() = runTest {
        val txns = FakeTransactionRepository()
        val gate = CompletableDeferred<TransactionPage>()
        txns.listPageResult = { call ->
            if (call.page == 0) page(twoRows(), totalElements = 4, hasNext = true) else gate.await()
        }
        val viewModel = vm(txns)

        viewModel.loadNextPage() // parks on the gate
        viewModel.loadNextPage() // must no-op — loadingMore is set
        viewModel.loadNextPage()

        assertEquals(2, txns.pageCalls.size)
        assertTrue((viewModel.state.value as TransactionsUiState.Content).loadingMore)

        gate.complete(
            page(
                listOf(FakeTransactionRepository.transaction(id = "t3")),
                page = 1,
                totalElements = 4,
                hasNext = false,
            ),
        )
        val state = viewModel.state.value as TransactionsUiState.Content
        assertEquals(3, state.items.size)
        assertFalse(state.loadingMore)
    }

    @Test
    fun `a failed page append surfaces an inline error and keeps the rows`() = runTest {
        val txns = FakeTransactionRepository()
        var fail = false
        txns.listPageResult = { call ->
            if (fail) {
                throw AppErrorException(AppError.NetworkUnavailable)
            } else {
                page(twoRows(), page = call.page, totalElements = 4, hasNext = true)
            }
        }
        val viewModel = vm(txns)

        fail = true
        viewModel.loadNextPage()

        val state = viewModel.state.value as TransactionsUiState.Content
        assertEquals(2, state.items.size) // page 0 untouched
        assertEquals(TransactionsError.Network, state.moreError)
        assertFalse(state.loadingMore)
        assertTrue(state.hasMore) // retry still possible
    }

    @Test
    fun `a late page response after a filter change publishes nothing`() = runTest {
        // The page-1 fetch belongs to query N; the user changes a filter
        // mid-flight — the late response must not append into query N+1.
        val txns = FakeTransactionRepository()
        val gate = CompletableDeferred<TransactionPage>()
        var page1Armed = false
        txns.listPageResult = { call ->
            if (page1Armed && call.page == 1) {
                gate.await()
            } else {
                page(twoRows(), page = call.page, totalElements = 4, hasNext = true)
            }
        }
        val viewModel = vm(txns)

        page1Armed = true
        viewModel.loadNextPage() // parks on the gate under query N

        page1Armed = false
        viewModel.onTypeFilter(TransactionType.EXPENSE) // query N+1

        gate.complete(
            page(
                listOf(FakeTransactionRepository.transaction(id = "stale-1")),
                page = 1,
                totalElements = 4,
                hasNext = false,
            ),
        )

        val state = viewModel.state.value as TransactionsUiState.Content
        // The stale append never landed: still the EXPENSE query's 2 rows.
        assertEquals(listOf("t1", "t2"), state.items.map { it.id })
        assertFalse(state.loadingMore)
    }

    @Test
    fun `a filter change resets paging to page 0`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listPageResult = { call -> page(twoRows(), page = call.page, totalElements = 4, hasNext = true) }
        val viewModel = vm(txns)
        viewModel.loadNextPage()
        assertEquals(1, txns.pageCalls.last().page)

        viewModel.onTypeFilter(TransactionType.INCOME)

        assertEquals(0, txns.pageCalls.last().page)
    }

    @Test
    fun `date filters reset paging and travel as LocalDate`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listPageResult = { call -> page(twoRows(), page = call.page, totalElements = 4, hasNext = true) }
        val viewModel = vm(txns)
        viewModel.loadNextPage()

        viewModel.onFromFilter(LocalDate.of(2026, 8, 1))
        viewModel.onToFilter(LocalDate.of(2026, 8, 31))

        val call = txns.pageCalls.last()
        assertEquals(0, call.page)
        assertEquals(LocalDate.of(2026, 8, 1), call.from)
        assertEquals(LocalDate.of(2026, 8, 31), call.to)
    }

    @Test
    fun `SessionExpired mid-append keeps content without a fake error`() = runTest {
        val txns = FakeTransactionRepository()
        var expired = false
        txns.listPageResult = { call ->
            if (expired) {
                throw AppErrorException(AppError.SessionExpired)
            } else {
                page(twoRows(), page = call.page, totalElements = 4, hasNext = true)
            }
        }
        val viewModel = vm(txns)

        expired = true
        viewModel.loadNextPage()

        val state = viewModel.state.value as TransactionsUiState.Content
        assertEquals(2, state.items.size)
        assertFalse(state.loadingMore)
        // The overlay owns expiry — no misleading inline error.
        assertNull(state.moreError)

        // Same-user resume re-runs the query.
        expired = false
        viewModel.onVisible()
        assertEquals(3, txns.pageCalls.size)
        assertEquals(0, txns.pageCalls.last().page) // resume = fresh page 0
    }

    @Test
    fun `a stale append failure cannot poison the new query's state`() = runTest {
        // EXT audit: the append's success path drops superseded results,
        // but its ERROR path must do the same — otherwise a failed F1
        // page clears loadingMore on F2's Content (double-firing the
        // same page → skipped page → ledger gap) and leaks its
        // moreError across the filter boundary.
        val txns = FakeTransactionRepository()
        val staleGate = CompletableDeferred<TransactionPage>()
        val liveGate = CompletableDeferred<TransactionPage>()
        var pageOneCalls = 0
        txns.listPageResult = { call ->
            when {
                call.page != 1 -> page(
                    twoRows(),
                    page = call.page,
                    totalElements = 4,
                    hasNext = true,
                )
                pageOneCalls++ == 0 -> staleGate.await()
                else -> liveGate.await()
            }
        }
        val viewModel = vm(txns)

        viewModel.loadNextPage() // first page-1 call — parked (query N)
        viewModel.onTypeFilter(TransactionType.EXPENSE) // query N+1 loads fresh
        viewModel.loadNextPage() // second page-1 call — parked (query N+1)
        assertTrue((viewModel.state.value as TransactionsUiState.Content).loadingMore)

        staleGate.completeExceptionally(AppErrorException(AppError.NetworkUnavailable))

        val mid = viewModel.state.value as TransactionsUiState.Content
        assertTrue(mid.loadingMore) // the live append is still in flight
        assertNull(mid.moreError) // the stale error never reached the new query
        assertEquals(TransactionType.EXPENSE, mid.filters.type)

        liveGate.complete(
            page(
                listOf(FakeTransactionRepository.transaction(id = "t3")),
                page = 1,
                totalElements = 3,
                hasNext = false,
            ),
        )
        val state = viewModel.state.value as TransactionsUiState.Content
        assertEquals(listOf("t1", "t2", "t3"), state.items.map { it.id })
        assertFalse(state.loadingMore)
        assertEquals(2, txns.pageCalls.count { it.page == 1 })
    }

    @Test
    fun `a stale append session-end clears nothing on the new query`() = runTest {
        // SessionEndedException (dead-generation cache write) on a
        // superseded append must not touch the live query's Content.
        val txns = FakeTransactionRepository()
        val staleGate = CompletableDeferred<TransactionPage>()
        var pageOneCalls = 0
        txns.listPageResult = { call ->
            when {
                call.page != 1 -> page(
                    twoRows(),
                    page = call.page,
                    totalElements = 4,
                    hasNext = true,
                )
                pageOneCalls++ == 0 -> staleGate.await()
                else -> throw SessionEndedException()
            }
        }
        val viewModel = vm(txns)

        viewModel.loadNextPage() // parked append under query N
        viewModel.onTypeFilter(TransactionType.INCOME) // query N+1
        staleGate.completeExceptionally(SessionEndedException())

        val state = viewModel.state.value as TransactionsUiState.Content
        assertEquals(TransactionType.INCOME, state.filters.type)
        assertNull(state.moreError)
        assertFalse(state.loadingMore)
        assertEquals(listOf("t1", "t2"), state.items.map { it.id })
    }

    // ── M18: server-side note search ─────────────────────────────────

    @Test
    fun `search input is instant but the server query waits for the debounce`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listPageResult = { page(twoRows()) }
        val viewModel = vm(txns)
        assertEquals(1, txns.pageCalls.size)

        viewModel.onSearchQueryChange("coffee")

        // The field reflects the keystroke at once; no request yet —
        // the applied query only changes after the quiet period.
        assertEquals("coffee", viewModel.searchInput.value)
        assertEquals(1, txns.pageCalls.size)

        advanceUntilIdle()

        assertEquals(2, txns.pageCalls.size)
        assertEquals("coffee", txns.pageCalls.last().q)
        assertEquals(0, txns.pageCalls.last().page) // a query change restarts paging
        assertEquals("coffee", viewModel.filters.value.query)
    }

    @Test
    fun `rapid typing produces one request for the final term`() = runTest {
        // Six characters must not mean six requests — each keystroke
        // cancels the pending apply, so only the settled term lands.
        val txns = FakeTransactionRepository()
        txns.listPageResult = { page(twoRows()) }
        val viewModel = vm(txns)

        viewModel.onSearchQueryChange("c")
        viewModel.onSearchQueryChange("co")
        viewModel.onSearchQueryChange("coffee")

        advanceUntilIdle()

        assertEquals(2, txns.pageCalls.size)
        assertEquals("coffee", txns.pageCalls.last().q)
    }

    @Test
    fun `clear search applies immediately — no debounce wait`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listPageResult = { page(twoRows()) }
        val viewModel = vm(txns)
        viewModel.onSearchQueryChange("coffee")
        advanceUntilIdle()
        assertEquals("coffee", txns.pageCalls.last().q)

        viewModel.clearSearch()

        // Applied before any scheduler advance — explicit clear is not
        // a keystroke, so the unfiltered load fires right now.
        assertEquals(3, txns.pageCalls.size)
        assertNull(txns.pageCalls.last().q)
        assertEquals("", viewModel.searchInput.value)
        assertEquals("", viewModel.filters.value.query)
    }

    @Test
    fun `search composes with the other server filters on one call`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listPageResult = { page(twoRows()) }
        val viewModel = vm(txns)
        viewModel.onSearchQueryChange("coffee")
        advanceUntilIdle()

        viewModel.onAccountFilter("a1")

        val call = txns.pageCalls.last()
        assertEquals("a1", call.accountId)
        assertEquals("coffee", call.q)
    }

    @Test
    fun `a pending debounce never overwrites a later clear`() = runTest {
        // Type → immediately hit the clear affordance before the
        // debounce fires. The stale apply must die with its job or the
        // cleared field would snap back to the typed term.
        val txns = FakeTransactionRepository()
        txns.listPageResult = { page(twoRows()) }
        val viewModel = vm(txns)
        viewModel.onSearchQueryChange("coffee")

        viewModel.clearSearch()
        advanceUntilIdle()

        val call = txns.pageCalls.last()
        assertNull(call.q)
        assertEquals("", viewModel.searchInput.value)
        assertEquals("", viewModel.filters.value.query)
    }

    @Test
    fun `whitespace-only input never becomes a server query`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listPageResult = { page(twoRows()) }
        val viewModel = vm(txns)

        viewModel.onSearchQueryChange("   ")
        advanceUntilIdle()

        // Trimmed to "" — identical to the current query, so the
        // StateFlow conflation correctly emits nothing: still 1 call.
        assertEquals(1, txns.pageCalls.size)
        assertEquals("", viewModel.filters.value.query)
    }

    @Test
    fun `clearFilters also resets the search field`() = runTest {
        // The all-filters affordance and the field never disagree — a
        // "Clear filters" tap on a searched empty result clears both.
        val txns = FakeTransactionRepository()
        txns.listPageResult = { page(twoRows()) }
        val viewModel = vm(txns)
        viewModel.onSearchQueryChange("coffee")
        advanceUntilIdle()
        viewModel.onAccountFilter("a1")

        viewModel.clearFilters()

        assertEquals("", viewModel.searchInput.value)
        val call = txns.pageCalls.last()
        assertNull(call.q)
        assertNull(call.accountId)
    }

    @Test
    fun `loadNextPage carries the active query`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listPageResult = { call ->
            page(twoRows(), page = call.page, totalElements = 4, hasNext = true)
        }
        val viewModel = vm(txns)
        viewModel.onSearchQueryChange("coffee")
        advanceUntilIdle()

        viewModel.loadNextPage()

        val call = txns.pageCalls.last()
        assertEquals(1, call.page)
        assertEquals("coffee", call.q)
    }

    @Test
    fun `a superseded search response never publishes`() = runTest {
        // The user types "old", it applies, the request parks; they
        // type "new" before it lands. The late "old" page must never
        // replace the "new" result — query rides the same
        // flatMapLatest + loadSerial discipline as every M15 filter.
        val txns = FakeTransactionRepository()
        val staleGate = CompletableDeferred<TransactionPage>()
        val liveRows = listOf(FakeTransactionRepository.transaction(id = "t9"))
        txns.listPageResult = { call ->
            if (call.q == "old") staleGate.await() else page(liveRows)
        }
        val viewModel = vm(txns)

        viewModel.onSearchQueryChange("old")
        advanceUntilIdle() // Q("old") in flight, parked on the gate
        viewModel.onSearchQueryChange("new")
        advanceUntilIdle() // Q("new") supersedes it

        val state = viewModel.state.value as TransactionsUiState.Content
        assertEquals(listOf("t9"), state.items.map { it.id })
        assertEquals("new", state.filters.query)

        staleGate.complete(page(twoRows())) // the stale page lands late
        assertEquals(
            listOf("t9"),
            (viewModel.state.value as TransactionsUiState.Content).items.map { it.id },
        )
    }

    @Test
    fun `a superseded search failure cannot error the live query`() = runTest {
        // Same race, failure shape: Q("old")'s late failure must not
        // tear down Q("new")'s Content into an Error state.
        val txns = FakeTransactionRepository()
        val staleGate = CompletableDeferred<TransactionPage>()
        txns.listPageResult = { call ->
            if (call.q == "old") staleGate.await() else page(twoRows())
        }
        val viewModel = vm(txns)

        viewModel.onSearchQueryChange("old")
        advanceUntilIdle()
        viewModel.onSearchQueryChange("new")
        advanceUntilIdle()

        staleGate.completeExceptionally(AppErrorException(AppError.NetworkUnavailable))

        val state = viewModel.state.value as TransactionsUiState.Content
        assertEquals("new", state.filters.query)
        assertEquals(2, state.items.size)
        assertNull(state.refreshError)
    }

    @Test
    fun `a search load lost to a session switch reloads with the query`() = runTest {
        // A→B: the searched page's cache write lands on a dead
        // generation — dropped, not published; the same-user resume
        // re-runs the load and must still send the active q.
        val txns = FakeTransactionRepository()
        var dead = false
        txns.listPageResult = { call ->
            if (dead) throw SessionEndedException() else page(twoRows())
        }
        val viewModel = vm(txns) // unfiltered page 0 lands while alive

        dead = true
        viewModel.onSearchQueryChange("coffee")
        advanceUntilIdle() // the searched load dies on the boundary

        val mid = viewModel.state.value as TransactionsUiState.Content
        assertFalse(mid.isRefreshing) // pending mark released
        assertEquals(2, mid.items.size) // prior content preserved

        dead = false
        viewModel.onVisible()

        assertEquals("coffee", txns.pageCalls.last().q)
        assertEquals("coffee", viewModel.filters.value.query)
    }

    @Test
    fun `a failed refetch releases a superseded append flag`() = runTest {
        // EXT audit: append parked -> query change -> NEW fetch fails.
        // The retained (old-filter) Content must not keep loadingMore
        // stuck true — the append can never publish (stale tick), and a
        // live flag would dead-end scroll-loading until manual refresh.
        val txns = FakeTransactionRepository()
        val appendGate = CompletableDeferred<TransactionPage>()
        txns.listPageResult = { call ->
            when {
                call.page == 1 -> appendGate.await()
                call.q == "new" ->
                    throw AppErrorException(AppError.NetworkUnavailable)
                else -> page(twoRows(), page = call.page, totalElements = 4, hasNext = true)
            }
        }
        val viewModel = vm(txns)
        viewModel.loadNextPage() // parked append: loadingMore=true
        assertTrue((viewModel.state.value as TransactionsUiState.Content).loadingMore)

        viewModel.onSearchQueryChange("new")
        advanceUntilIdle() // the fresh page-0 fetch fails

        val state = viewModel.state.value as TransactionsUiState.Content
        assertEquals(TransactionsError.Network, state.refreshError)
        assertFalse(state.loadingMore) // dead append's flag must not linger
        // and the parked append landing late still publishes nothing
        appendGate.complete(page(
            listOf(FakeTransactionRepository.transaction(id = "stale")),
            page = 1, totalElements = 4, hasNext = false))
        assertEquals(listOf("t1", "t2"),
            (viewModel.state.value as TransactionsUiState.Content).items.map { it.id })
    }

    @Test
    fun `refresh during an active search resends the query at page 0`() = runTest {
        // Pull-to-refresh must not silently drop the applied term — the
        // fresh load is another emission of the SAME query, so q stays
        // on the wire and pagination restarts at page 0.
        val txns = FakeTransactionRepository()
        txns.listPageResult = { call ->
            page(twoRows(), page = call.page, totalElements = 4, hasNext = true)
        }
        val viewModel = vm(txns)
        viewModel.onSearchQueryChange("coffee")
        advanceUntilIdle()
        viewModel.loadNextPage() // page 1 lands
        advanceUntilIdle()
        assertEquals("coffee", txns.pageCalls.last().q)

        viewModel.refresh()
        advanceUntilIdle()

        val call = txns.pageCalls.last()
        assertEquals(0, call.page)
        assertEquals("coffee", call.q)
        val state = viewModel.state.value as TransactionsUiState.Content
        assertFalse(state.isRefreshing)
        assertEquals(1, state.nextPage)
    }
}
