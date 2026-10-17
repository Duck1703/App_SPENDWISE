package com.spendwise.android.feature.accounts

import androidx.lifecycle.SavedStateHandle
import com.spendwise.android.core.model.TransactionPage
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.fakes.FakeAccountRepository
import com.spendwise.android.fakes.FakeTransactionRepository
import com.spendwise.android.fakes.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AccountDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        id: String = "acc-1",
        repo: FakeAccountRepository = FakeAccountRepository(),
        txns: FakeTransactionRepository = FakeTransactionRepository(),
    ) = AccountDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("accountId" to id)),
        accountRepository = repo,
        transactionRepository = txns,
    )

    @Test
    fun `loads the account into Content on init`() = runTest {
        val repo = FakeAccountRepository()
        repo.findByIdResult = { id, _ -> FakeAccountRepository.account(id = id, name = "Jar") }

        val vm = viewModel(id = "acc-7", repo = repo)

        val state = vm.state.value as AccountDetailUiState.Content
        assertEquals("acc-7", state.account.id)
        assertEquals("Jar", state.account.name)
        assertEquals(listOf("acc-7" to false), repo.findByIdCalls)
    }

    @Test
    fun `404 maps to the honest NotFound state`() = runTest {
        val repo = FakeAccountRepository()
        repo.findByIdResult = { _, _ -> throw AppErrorException(AppError.NotFound) }

        val vm = viewModel(repo = repo)

        assertEquals(AccountDetailUiState.NotFound, vm.state.value)
    }

    @Test
    fun `network failure maps to the retryable Network error`() = runTest {
        val repo = FakeAccountRepository()
        repo.findByIdResult = { _, _ -> throw AppErrorException(AppError.NetworkUnavailable) }

        val vm = viewModel(repo = repo)

        assertEquals(
            AccountDetailUiState.Error(AccountsError.Network),
            vm.state.value,
        )
    }

    @Test
    fun `refresh re-requests with refresh=true`() = runTest {
        val repo = FakeAccountRepository()
        val vm = viewModel(repo = repo)

        vm.refresh()

        assertEquals(listOf("acc-1" to false, "acc-1" to true), repo.findByIdCalls)
    }

    @Test
    fun `SessionExpired leaves state alone — overlay handles it`() = runTest {
        val repo = FakeAccountRepository()
        repo.findByIdResult = { _, _ -> throw AppErrorException(AppError.SessionExpired) }

        val vm = viewModel(repo = repo)

        assertTrue(vm.state.value is AccountDetailUiState.Loading)
    }

    @Test
    fun `expired mid-load re-runs the fetch when the screen returns`() = runTest {
        val repo = FakeAccountRepository()
        var call = 0
        repo.findByIdResult = { id, _ ->
            call++
            if (call == 1) throw AppErrorException(AppError.SessionExpired)
            FakeAccountRepository.account(id = id)
        }
        val vm = viewModel(repo = repo)

        vm.onVisible() // resume-pop re-composition hook

        val state = vm.state.value as AccountDetailUiState.Content
        assertEquals("acc-1", state.account.id)
        assertEquals(listOf("acc-1" to false, "acc-1" to true), repo.findByIdCalls)
    }

    // ── M3: account-scoped history section ────────────────────────────────

    @Test
    fun `content carries the account-scoped history — first server-ordered page`() = runTest {
        val txns = FakeTransactionRepository()
        // W2-F02 Option A: history consumes the server-sorted page
        // endpoint (occurredOn DESC, id DESC), scoped to this account.
        txns.listPageResult = { call ->
            assertEquals("acc-1", call.accountId)
            assertEquals(0, call.page)
            com.spendwise.android.core.model.TransactionPage(
                items = listOf(
                    FakeTransactionRepository.transaction(id = "t1", accountId = "acc-1"),
                ),
                page = call.page,
                size = call.size,
                totalElements = 1,
                totalPages = 1,
                hasNext = false,
            )
        }

        val vm = viewModel(txns = txns)

        val state = vm.state.value as AccountDetailUiState.Content
        val history = state.history as AccountDetailUiState.AccountHistory.Items
        assertEquals(listOf("t1"), history.transactions.map { it.id })
        // Scoped to this account via the real server filter param.
        assertEquals("acc-1", txns.pageCalls.single().accountId)
        assertTrue(txns.listCalls.isEmpty()) // never the unordered unbounded read
    }

    @Test
    fun `history failure degrades the section, never the header`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listPageResult = { _ -> throw AppErrorException(AppError.Server(500)) }

        val vm = viewModel(txns = txns)

        val state = vm.state.value as AccountDetailUiState.Content
        assertEquals("acc-1", state.account.id)
        assertEquals(AccountDetailUiState.AccountHistory.Error, state.history)
    }

    @Test
    fun `history retry reloads only the section`() = runTest {
        val txns = FakeTransactionRepository()
        var fail = true
        txns.listPageResult = { call ->
            if (fail) {
                throw AppErrorException(AppError.NetworkUnavailable)
            } else {
                com.spendwise.android.core.model.TransactionPage(
                    items = listOf(FakeTransactionRepository.transaction(id = "t1")),
                    page = call.page,
                    size = call.size,
                    totalElements = 1,
                    totalPages = 1,
                    hasNext = false,
                )
            }
        }
        val repo = FakeAccountRepository()
        val vm = viewModel(repo = repo, txns = txns)

        fail = false
        vm.retryHistory()

        val state = vm.state.value as AccountDetailUiState.Content
        assertTrue(state.history is AccountDetailUiState.AccountHistory.Items)
        // The account itself was not re-requested.
        assertEquals(1, repo.findByIdCalls.size)
    }

    @Test
    fun `history pages append in order with no duplicates — every row reachable`() = runTest {
        val txns = FakeTransactionRepository()
        // Page 0 supplies t5,t4 (server newest-first); page 1 supplies
        // t3 plus a duplicate t4 — dedup by id must hold.
        txns.listPageResult = { call ->
            com.spendwise.android.core.model.TransactionPage(
                items = when (call.page) {
                    0 -> listOf("t5", "t4").map { FakeTransactionRepository.transaction(id = it, accountId = "acc-1") }
                    else -> listOf("t4", "t3").map { FakeTransactionRepository.transaction(id = it, accountId = "acc-1") }
                },
                page = call.page,
                size = call.size,
                totalElements = 3,
                totalPages = 2,
                hasNext = call.page == 0,
            )
        }
        val vm = viewModel(txns = txns)

        var history = (vm.state.value as AccountDetailUiState.Content).history
            as AccountDetailUiState.AccountHistory.Items
        assertTrue(history.hasNext)

        vm.loadNextHistoryPage()

        history = (vm.state.value as AccountDetailUiState.Content).history
            as AccountDetailUiState.AccountHistory.Items
        assertEquals(listOf("t5", "t4", "t3"), history.transactions.map { it.id })
        assertFalse(history.hasNext)
        assertEquals(2, txns.pageCalls.size)
        assertEquals(0, txns.pageCalls[0].page)
        assertEquals(1, txns.pageCalls[1].page)
    }

    @Test
    fun `history load-more failure keeps rows and exposes the retry state`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listPageResult = { call ->
            if (call.page > 0) throw AppErrorException(AppError.NetworkUnavailable)
            com.spendwise.android.core.model.TransactionPage(
                items = listOf(FakeTransactionRepository.transaction(id = "t5", accountId = "acc-1")),
                page = call.page,
                size = call.size,
                totalElements = 2,
                totalPages = 2,
                hasNext = true,
            )
        }
        val vm = viewModel(txns = txns)

        vm.loadNextHistoryPage()

        val history = (vm.state.value as AccountDetailUiState.Content).history
            as AccountDetailUiState.AccountHistory.Items
        assertEquals(listOf("t5"), history.transactions.map { it.id })
        assertTrue(history.moreError)
        assertFalse(history.loadingMore)
    }

    @Test
    fun `history load-more is a no-op at the last page`() = runTest {
        val txns = FakeTransactionRepository()
        txns.listPageResult = { call ->
            com.spendwise.android.core.model.TransactionPage(
                items = listOf(FakeTransactionRepository.transaction(id = "t1", accountId = "acc-1")),
                page = call.page,
                size = call.size,
                totalElements = 1,
                totalPages = 1,
                hasNext = false,
            )
        }
        val vm = viewModel(txns = txns)

        vm.loadNextHistoryPage()

        assertEquals(1, txns.pageCalls.size) // only the initial page
    }

    @Test
    fun `stale page append is ignored after a refresh reloaded page 0`() = runTest {
        // W3-R01: page 1 in flight → mutation reloads page 0 → stale page 1
        // must NOT append to / advance the fresh history.
        val txns = FakeTransactionRepository()
        val gate = CompletableDeferred<TransactionPage>()
        var pageZeroCalls = 0
        txns.listPageResult = { call ->
            when {
                call.page == 0 -> {
                    pageZeroCalls++
                    TransactionPage(
                        items = listOf(
                            FakeTransactionRepository.transaction(
                                id = if (pageZeroCalls == 1) "t1" else "t2",
                                accountId = "acc-1",
                            ),
                        ),
                        page = 0,
                        size = call.size,
                        totalElements = 3,
                        totalPages = 3,
                        hasNext = true,
                    )
                }
                else -> gate.await()
            }
        }
        val vm = viewModel(txns = txns)

        // Old page 1 dispatched, still in flight.
        vm.loadNextHistoryPage()
        assertTrue(
            ((vm.state.value as AccountDetailUiState.Content).history
                as AccountDetailUiState.AccountHistory.Items).loadingMore,
        )

        // Mutation reloads page 0 — the fresh epoch publishes [t2].
        txns.emitMutation()

        // Stale page 1 finally lands — belongs to the dead epoch.
        gate.complete(
            TransactionPage(
                items = listOf(FakeTransactionRepository.transaction(id = "stale", accountId = "acc-1")),
                page = 1,
                size = 20,
                totalElements = 3,
                totalPages = 3,
                hasNext = true,
            ),
        )

        val history = (vm.state.value as AccountDetailUiState.Content).history
            as AccountDetailUiState.AccountHistory.Items
        assertEquals(listOf("t2"), history.transactions.map { it.id })
        assertEquals(1, history.nextPage) // not advanced by the stale page
        assertFalse(history.loadingMore)
        assertFalse(history.moreError)
    }

    @Test
    fun `stale page error is ignored after a refresh reloaded page 0`() = runTest {
        // W3-R01: the same interleaving where the obsolete request fails —
        // its error must not mark the fresh history's footer.
        val txns = FakeTransactionRepository()
        val gate = CompletableDeferred<TransactionPage>()
        var pageZeroCalls = 0
        txns.listPageResult = { call ->
            when {
                call.page == 0 -> {
                    pageZeroCalls++
                    TransactionPage(
                        items = listOf(
                            FakeTransactionRepository.transaction(
                                id = if (pageZeroCalls == 1) "t1" else "t2",
                                accountId = "acc-1",
                            ),
                        ),
                        page = 0,
                        size = call.size,
                        totalElements = 3,
                        totalPages = 3,
                        hasNext = true,
                    )
                }
                else -> gate.await()
            }
        }
        val vm = viewModel(txns = txns)

        vm.loadNextHistoryPage()
        txns.emitMutation()
        gate.completeExceptionally(AppErrorException(AppError.Server(500)))

        val history = (vm.state.value as AccountDetailUiState.Content).history
            as AccountDetailUiState.AccountHistory.Items
        assertEquals(listOf("t2"), history.transactions.map { it.id })
        assertFalse(history.moreError)
        assertFalse(history.loadingMore)
    }

    @Test
    fun `history request never touches another account's rows`() = runTest {
        val txns = FakeTransactionRepository()
        val vm = viewModel(id = "acc-9", txns = txns)
        vm.state.value // settle

        assertTrue(txns.pageCalls.isNotEmpty())
        assertTrue(txns.pageCalls.all { it.accountId == "acc-9" })
    }

    @Test
    fun `dead-generation mid-load arms the resume retry — no stuck skeleton`() = runTest {
        // EXT-MAJOR-1: the dead-generation publish must arm the same
        // resume-retry as the 401 path.
        val repo = FakeAccountRepository()
        var dead = true
        repo.findByIdResult = { id, _ ->
            if (dead) {
                throw SessionEndedException()
            } else {
                FakeAccountRepository.account(id = id)
            }
        }
        val vm = viewModel(repo = repo)

        assertTrue(vm.state.value is AccountDetailUiState.Loading)

        dead = false
        vm.onVisible()

        val state = vm.state.value as AccountDetailUiState.Content
        assertEquals("acc-1", state.account.id)
        assertEquals(listOf("acc-1" to false, "acc-1" to true), repo.findByIdCalls)
    }

    @Test
    fun `dead-generation on history marks the section for resume retry`() = runTest {
        val repo = FakeAccountRepository()
        val txns = FakeTransactionRepository()
        var dead = true
        txns.listPageResult = { call ->
            if (dead) {
                throw SessionEndedException()
            } else {
                com.spendwise.android.core.model.TransactionPage(
                    items = listOf(FakeTransactionRepository.transaction(id = "t1")),
                    page = call.page, size = call.size,
                    totalElements = 1, totalPages = 1, hasNext = false,
                )
            }
        }
        val vm = viewModel(repo = repo, txns = txns)

        // Header published; the section stayed pending on the dead session.
        var state = vm.state.value as AccountDetailUiState.Content
        assertTrue(state.history is AccountDetailUiState.AccountHistory.Loading)

        dead = false
        vm.onVisible()

        state = vm.state.value as AccountDetailUiState.Content
        assertTrue(state.history is AccountDetailUiState.AccountHistory.Items)
        assertEquals(2, txns.pageCalls.size)
        // The header itself was not refetched — the section retried alone.
        assertEquals(1, repo.findByIdCalls.size)
    }

    // ── M12: transfer-affordance peer gate ───────────────────────────

    @Test
    fun `hasTransferPeer is true when a second account exists`() = runTest {
        val repo = FakeAccountRepository()
        repo.listResult = {
            listOf(
                FakeAccountRepository.account(id = "acc-1"),
                FakeAccountRepository.account(id = "acc-2"),
            )
        }

        val vm = viewModel(repo = repo)

        val state = vm.state.value as AccountDetailUiState.Content
        assertEquals("acc-1", state.account.id)
        assertTrue(state.hasTransferPeer)
    }

    @Test
    fun `hasTransferPeer is false when the account stands alone`() = runTest {
        val repo = FakeAccountRepository()
        repo.listResult = { listOf(FakeAccountRepository.account(id = "acc-1")) }

        val vm = viewModel(repo = repo)

        val state = vm.state.value as AccountDetailUiState.Content
        assertEquals(false, state.hasTransferPeer)
    }

    @Test
    fun `hasTransferPeer is false when the only peer is archived`() = runTest {
        // M19 (REQ-BE-006): an archived peer cannot receive a transfer —
        // the affordance must not appear for it.
        val repo = FakeAccountRepository()
        repo.listResult = {
            listOf(
                FakeAccountRepository.account(id = "acc-1"),
                FakeAccountRepository.account(id = "acc-2", archived = true),
            )
        }

        val vm = viewModel(repo = repo)

        val state = vm.state.value as AccountDetailUiState.Content
        assertEquals(false, state.hasTransferPeer)
    }

    @Test
    fun `a peer-list failure hides the affordance without sinking the detail`() = runTest {
        val repo = FakeAccountRepository()
        repo.listResult = { throw AppErrorException(AppError.Server(503)) }

        val vm = viewModel(repo = repo)

        val state = vm.state.value as AccountDetailUiState.Content
        assertEquals("acc-1", state.account.id)
        assertEquals(false, state.hasTransferPeer)
    }

    @Test
    fun `session expiry on the peer check still propagates to the resume contract`() = runTest {
        val repo = FakeAccountRepository()
        repo.listResult = { throw AppErrorException(AppError.SessionExpired) }

        val vm = viewModel(repo = repo)

        // Expired mid-load arms the retry and publishes no half-state.
        assertTrue(vm.state.value is AccountDetailUiState.Loading)

        repo.listResult = { listOf(FakeAccountRepository.account(id = "acc-1"),
            FakeAccountRepository.account(id = "acc-2")) }
        vm.onVisible()

        val state = vm.state.value as AccountDetailUiState.Content
        assertTrue(state.hasTransferPeer)
    }

    @Test
    fun `a create mutation reloads account and history`() = runTest {
        val repo = FakeAccountRepository()
        val txns = FakeTransactionRepository()
        val vm = viewModel(repo = repo, txns = txns)

        txns.emitMutation()

        assertEquals(listOf("acc-1" to false, "acc-1" to true), repo.findByIdCalls)
        assertEquals(2, txns.pageCalls.size)
    }

    @Test
    fun `an account mutation reloads the detail`() = runTest {
        // M19: after Edit → Saved → pop-back, the rename/archive must
        // repaint — the account mutation signal drives the reload.
        val repo = FakeAccountRepository()
        val vm = viewModel(repo = repo)
        repo.findByIdResult = { id, _ ->
            FakeAccountRepository.account(id = id, name = "Renamed", archived = true)
        }

        repo.emitMutation()

        val state = vm.state.value as AccountDetailUiState.Content
        assertEquals("Renamed", state.account.name)
        assertTrue(state.account.archived)
        assertEquals(listOf("acc-1" to false, "acc-1" to true), repo.findByIdCalls)
    }
}
