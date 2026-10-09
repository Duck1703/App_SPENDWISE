package com.spendwise.android.feature.accounts

import androidx.lifecycle.SavedStateHandle
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.fakes.FakeAccountRepository
import com.spendwise.android.fakes.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `EditAccountViewModel` (M19, REQ-BE-006) — prefill, sparse-patch
 * assembly (rename/archive/unarchive), validation, submit lock, save
 * error paths (409 conflict / ambiguous mid-write), and the
 * stale-load/session races.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditAccountViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(
        accounts: FakeAccountRepository = FakeAccountRepository(),
        id: String = "acc-1",
    ) = EditAccountViewModel(
        SavedStateHandle(mapOf("accountId" to id)),
        accounts,
    )

    private fun form(vm: EditAccountViewModel) = vm.state.value as EditAccountState.Form

    // ── Load / prefill ──────────────────────────────────────────────

    @Test
    fun `prefills name and archived from the loaded account`() = runTest {
        val accounts = FakeAccountRepository().apply {
            findByIdResult = { id, _ ->
                FakeAccountRepository.account(id = id, name = "Wallet", archived = true)
            }
        }
        val viewModel = vm(accounts)
        runCurrent()
        val form = form(viewModel)
        assertEquals("Wallet", form.name)
        assertTrue(form.archived)
        assertFalse(form.hasChanges)
        // The form opened on server truth — never the session cache.
        assertEquals(listOf("acc-1" to true), accounts.findByIdCalls)
    }

    @Test
    fun `maps a missing id to NotFound`() = runTest {
        val accounts = FakeAccountRepository().apply {
            findByIdResult = { _, _ -> throw AppErrorException(AppError.NotFound) }
        }
        val viewModel = vm(accounts, id = "foreign")
        runCurrent()
        assertEquals(EditAccountState.NotFound, viewModel.state.value)
    }

    @Test
    fun `network failure maps to Network error and retry recovers`() = runTest {
        val accounts = FakeAccountRepository().apply {
            findByIdResult = { _, _ -> throw AppErrorException(AppError.NetworkUnavailable) }
        }
        val viewModel = vm(accounts)
        runCurrent()
        assertEquals(EditAccountState.Error(AccountsError.Network), viewModel.state.value)

        accounts.findByIdResult = { id, _ -> FakeAccountRepository.account(id = id) }
        viewModel.refresh()
        runCurrent()
        assertTrue(viewModel.state.value is EditAccountState.Form)
    }

    @Test
    fun `an aborted load after session expiry re-runs on resume`() = runTest {
        val accounts = FakeAccountRepository().apply {
            findByIdResult = { _, _ -> throw AppErrorException(AppError.SessionExpired) }
        }
        val viewModel = vm(accounts)
        runCurrent()
        // Stays Loading — the session overlay owns the failure.
        assertEquals(EditAccountState.Loading, viewModel.state.value)

        accounts.findByIdResult = { id, _ -> FakeAccountRepository.account(id = id) }
        viewModel.onVisible()
        runCurrent()
        assertTrue(viewModel.state.value is EditAccountState.Form)
    }

    @Test
    fun `a stale load failure cannot clobber a newer successful load`() = runTest {
        val accounts = FakeAccountRepository()
        val first = CompletableDeferred<com.spendwise.android.core.model.Account>()
        var call = 0
        accounts.findByIdResult = { id, _ ->
            call++
            if (call == 1) first.await() else FakeAccountRepository.account(id = id)
        }
        val viewModel = vm(accounts)
        runCurrent()
        viewModel.refresh()
        runCurrent()
        assertTrue(viewModel.state.value is EditAccountState.Form)
        // The parked first load now fails — stale, must not clobber Form.
        first.completeExceptionally(AppErrorException(AppError.NetworkUnavailable))
        runCurrent()
        assertTrue(viewModel.state.value is EditAccountState.Form)
    }

    @Test
    fun `a stale load success cannot clobber a newer successful load`() = runTest {
        val accounts = FakeAccountRepository()
        val first = CompletableDeferred<com.spendwise.android.core.model.Account>()
        var call = 0
        accounts.findByIdResult = { id, _ ->
            call++
            if (call == 1) {
                first.await()
            } else {
                FakeAccountRepository.account(id = id, name = "Newer")
            }
        }
        val viewModel = vm(accounts)
        runCurrent()
        viewModel.refresh()
        runCurrent()
        assertEquals("Newer", form(viewModel).name)
        first.complete(FakeAccountRepository.account(id = "acc-1", name = "Stale"))
        runCurrent()
        assertEquals("Newer", form(viewModel).name)
    }

    // ── hasChanges / sparse patch ───────────────────────────────────

    @Test
    fun `save with no changes performs no wire call`() = runTest {
        val accounts = FakeAccountRepository()
        val viewModel = vm(accounts)
        runCurrent()
        viewModel.save()
        runCurrent()
        assertTrue(accounts.updateCalls.isEmpty())
    }

    @Test
    fun `a name-only change sends a sparse patch`() = runTest {
        val accounts = FakeAccountRepository()
        val viewModel = vm(accounts)
        runCurrent()
        viewModel.onNameChange("Renamed")
        viewModel.save()
        runCurrent()
        val call = accounts.updateCalls.single()
        assertEquals("acc-1", call.first)
        assertEquals("Renamed", call.second)
        assertNull(call.third)
    }

    @Test
    fun `an archive-only change sends a sparse patch`() = runTest {
        val accounts = FakeAccountRepository()
        val viewModel = vm(accounts)
        runCurrent()
        viewModel.onArchivedChange(true)
        viewModel.save()
        runCurrent()
        val call = accounts.updateCalls.single()
        assertNull(call.second)
        assertEquals(true, call.third)
    }

    @Test
    fun `unarchiving sends archived false`() = runTest {
        val accounts = FakeAccountRepository().apply {
            findByIdResult = { id, _ ->
                FakeAccountRepository.account(id = id, archived = true)
            }
        }
        val viewModel = vm(accounts)
        runCurrent()
        assertTrue(form(viewModel).archived)
        viewModel.onArchivedChange(false)
        viewModel.save()
        runCurrent()
        val call = accounts.updateCalls.single()
        assertEquals(false, call.third)
        assertNull(call.second)
    }

    @Test
    fun `both changed fields reach the patch`() = runTest {
        val accounts = FakeAccountRepository()
        val viewModel = vm(accounts)
        runCurrent()
        viewModel.onNameChange("Renamed")
        viewModel.onArchivedChange(true)
        viewModel.save()
        runCurrent()
        val call = accounts.updateCalls.single()
        assertEquals("Renamed", call.second)
        assertEquals(true, call.third)
    }

    @Test
    fun `toggling back to the original state leaves no changes`() = runTest {
        val accounts = FakeAccountRepository()
        val viewModel = vm(accounts)
        runCurrent()
        viewModel.onArchivedChange(true)
        assertTrue(form(viewModel).hasChanges)
        viewModel.onArchivedChange(false)
        assertFalse(form(viewModel).hasChanges)
        viewModel.save()
        runCurrent()
        assertTrue(accounts.updateCalls.isEmpty())
    }

    @Test
    fun `whitespace-only name edit is not a change`() = runTest {
        val accounts = FakeAccountRepository().apply {
            findByIdResult = { id, _ -> FakeAccountRepository.account(id = id, name = "Wallet") }
        }
        val viewModel = vm(accounts)
        runCurrent()
        viewModel.onNameChange("Wallet  ")
        assertFalse(form(viewModel).hasChanges)
    }

    @Test
    fun `the sent name is trimmed`() = runTest {
        val accounts = FakeAccountRepository().apply {
            findByIdResult = { id, _ -> FakeAccountRepository.account(id = id, name = "Wallet") }
        }
        val viewModel = vm(accounts)
        runCurrent()
        viewModel.onNameChange("  Renamed  ")
        viewModel.save()
        runCurrent()
        assertEquals("Renamed", accounts.updateCalls.single().second)
    }

    // ── Validation ──────────────────────────────────────────────────

    @Test
    fun `a blank name blocks save and never calls the repository`() = runTest {
        val accounts = FakeAccountRepository()
        val viewModel = vm(accounts)
        runCurrent()
        viewModel.onNameChange("   ")
        viewModel.save()
        runCurrent()
        assertEquals(
            EditAccountState.Form.NameError.Required,
            form(viewModel).nameError,
        )
        assertTrue(accounts.updateCalls.isEmpty())
    }

    // ── Submit lock / outcomes ──────────────────────────────────────

    @Test
    fun `double submit is locked by isSubmitting`() = runTest {
        val accounts = FakeAccountRepository()
        val gate = CompletableDeferred<com.spendwise.android.core.model.Account>()
        accounts.updateResult = { _, _, _ -> gate.await() }
        val viewModel = vm(accounts)
        runCurrent()
        viewModel.onNameChange("Renamed")
        viewModel.save()
        runCurrent()
        assertTrue(form(viewModel).isSubmitting)
        viewModel.save()
        runCurrent()
        assertEquals(1, accounts.updateCalls.size)
        gate.complete(FakeAccountRepository.account())
        runCurrent()
    }

    @Test
    fun `a field edited mid-save cannot contaminate the sent snapshot`() = runTest {
        val accounts = FakeAccountRepository()
        val gate = CompletableDeferred<com.spendwise.android.core.model.Account>()
        accounts.updateResult = { _, _, _ -> gate.await() }
        val viewModel = vm(accounts)
        runCurrent()
        viewModel.onNameChange("Renamed")
        viewModel.save()
        runCurrent()
        // In-flight typing must not leak into the wire call's snapshot.
        viewModel.onNameChange("Later edit")
        runCurrent()
        assertEquals("Renamed", accounts.updateCalls.single().second)
        gate.complete(FakeAccountRepository.account())
        runCurrent()
    }

    @Test
    fun `success emits the Saved event`() = runTest {
        val accounts = FakeAccountRepository()
        val viewModel = vm(accounts)
        runCurrent()
        val event = CompletableDeferred<EditAccountViewModel.Event>()
        val collector = launch { event.complete(viewModel.events.first()) }
        viewModel.onNameChange("Renamed")
        viewModel.save()
        runCurrent()
        assertEquals(EditAccountViewModel.Event.Saved, event.await())
        collector.cancel()
    }

    @Test
    fun `a dropped mid-write surfaces Ambiguous`() = runTest {
        val accounts = FakeAccountRepository().apply {
            updateResult = { _, _, _ -> throw AppErrorException(AppError.NetworkUnavailable) }
        }
        val viewModel = vm(accounts)
        runCurrent()
        viewModel.onNameChange("Renamed")
        viewModel.save()
        runCurrent()
        assertEquals(
            EditAccountState.Form.FormError.Ambiguous,
            form(viewModel).formError,
        )
        assertFalse(form(viewModel).isSubmitting)
    }

    @Test
    fun `a dead-generation save surfaces Ambiguous`() = runTest {
        val accounts = FakeAccountRepository().apply {
            updateResult = { _, _, _ -> throw SessionEndedException() }
        }
        val viewModel = vm(accounts)
        runCurrent()
        viewModel.onNameChange("Renamed")
        viewModel.save()
        runCurrent()
        assertEquals(
            EditAccountState.Form.FormError.Ambiguous,
            form(viewModel).formError,
        )
    }

    @Test
    fun `a 409 surfaces the Conflict state and never retries`() = runTest {
        val accounts = FakeAccountRepository().apply {
            updateResult = { _, _, _ -> throw AppErrorException(AppError.Conflict) }
        }
        val viewModel = vm(accounts)
        runCurrent()
        viewModel.onNameChange("Renamed")
        viewModel.save()
        runCurrent()
        assertEquals(
            EditAccountState.Form.FormError.Conflict,
            form(viewModel).formError,
        )
        assertFalse(form(viewModel).isSubmitting)
        assertEquals(1, accounts.updateCalls.size)
    }

    @Test
    fun `a 404 on save reloads toward NotFound`() = runTest {
        val accounts = FakeAccountRepository()
        val viewModel = vm(accounts)
        runCurrent()
        assertTrue(viewModel.state.value is EditAccountState.Form)
        accounts.updateResult = { _, _, _ -> throw AppErrorException(AppError.NotFound) }
        accounts.findByIdResult = { _, _ -> throw AppErrorException(AppError.NotFound) }
        viewModel.onNameChange("Renamed")
        viewModel.save()
        runCurrent()
        assertEquals(EditAccountState.NotFound, viewModel.state.value)
    }

    @Test
    fun `a server name error lands on the name field`() = runTest {
        val accounts = FakeAccountRepository().apply {
            updateResult = { _, _, _ ->
                throw AppErrorException(
                    AppError.Validation(mapOf("name" to "must not be blank")),
                )
            }
        }
        val viewModel = vm(accounts)
        runCurrent()
        viewModel.onNameChange("Renamed")
        viewModel.save()
        runCurrent()
        val form = form(viewModel)
        assertEquals(EditAccountState.Form.NameError.Invalid, form.nameError)
        assertNull(form.formError)
        assertFalse(form.isSubmitting)
    }

    @Test
    fun `a server error on save lands as a form banner`() = runTest {
        val accounts = FakeAccountRepository().apply {
            updateResult = { _, _, _ -> throw AppErrorException(AppError.Server(500)) }
        }
        val viewModel = vm(accounts)
        runCurrent()
        viewModel.onNameChange("Renamed")
        viewModel.save()
        runCurrent()
        assertEquals(
            EditAccountState.Form.FormError.Server,
            form(viewModel).formError,
        )
    }

    @Test
    fun `save during Loading is a no-op`() = runTest {
        val accounts = FakeAccountRepository()
        val gate = CompletableDeferred<com.spendwise.android.core.model.Account>()
        accounts.findByIdResult = { _, _ -> gate.await() }
        val viewModel = vm(accounts)
        runCurrent()
        viewModel.save()
        runCurrent()
        assertTrue(accounts.updateCalls.isEmpty())
        gate.complete(FakeAccountRepository.account())
        runCurrent()
    }
}
