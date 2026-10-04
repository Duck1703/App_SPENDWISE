package com.spendwise.android.feature.auth

import app.cash.turbine.test
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.fakes.FakeAuthRepository
import com.spendwise.android.fakes.MainDispatcherRule
import com.spendwise.android.data.repository.RegisteredUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(repository: FakeAuthRepository) =
        RegisterViewModel(authRepository = repository)

    private fun filledViewModel(repository: FakeAuthRepository) =
        viewModel(repository).apply {
            onUsernameChanged("ana")
            onPasswordChanged("secret-pw")
        }

    @Test
    fun `blank fields produce inline errors and no repository call`() = runTest {
        val repository = FakeAuthRepository()
        val vm = viewModel(repository)

        vm.submit()

        val state = vm.uiState.value
        assertEquals(AuthFieldError.Required, state.usernameError)
        assertEquals(AuthFieldError.Required, state.passwordError)
        assertTrue(repository.registerCalls.isEmpty())
    }

    @Test
    fun `short password is rejected client-side before the call`() = runTest {
        val repository = FakeAuthRepository()
        val vm = viewModel(repository).apply {
            onUsernameChanged("ana")
            onPasswordChanged("short")
        }

        vm.submit()

        val state = vm.uiState.value
        assertEquals(AuthFieldError.TooShort(8), state.passwordError)
        assertTrue(repository.registerCalls.isEmpty())
    }

    @Test
    fun `successful register emits the Registered event for login prefill`() = runTest {
        val repository = FakeAuthRepository().apply {
            registerResult = { username -> RegisteredUser(id = "u-9", username = username) }
        }
        val vm = filledViewModel(repository)

        vm.events.test {
            vm.submit()
            assertEquals(
                RegisterViewModel.Event.Registered("ana"),
                awaitItem(),
            )
        }
        assertEquals(listOf("ana" to "secret-pw"), repository.registerCalls)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun `400 without fields maps to the hedged duplicate-user error`() = runTest {
        val repository = FakeAuthRepository().apply {
            registerResult = { throw AppErrorException(AppError.BadRequest) }
        }
        val vm = filledViewModel(repository)

        vm.submit()
        advanceUntilIdle()

        assertEquals(AuthFormError.UsernamePossiblyTaken, vm.uiState.value.formError)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun `backend field validation maps to field errors`() = runTest {
        val repository = FakeAuthRepository().apply {
            registerResult = {
                throw AppErrorException(
                    AppError.Validation(mapOf("password" to "size must be between 8 and 255")),
                )
            }
        }
        val vm = filledViewModel(repository)

        vm.submit()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(
            AuthFieldError.Server("size must be between 8 and 255"),
            state.passwordError,
        )
        assertFalse(state.isSubmitting)
    }

    @Test
    fun `network failure maps to the offline form error`() = runTest {
        val repository = FakeAuthRepository().apply {
            registerResult = { throw AppErrorException(AppError.NetworkUnavailable) }
        }
        val vm = filledViewModel(repository)

        vm.submit()
        advanceUntilIdle()

        assertEquals(AuthFormError.NetworkUnavailable, vm.uiState.value.formError)
    }

    @Test
    fun `submit while in flight is locked to a single call`() = runTest {
        val gate = CompletableDeferred<RegisteredUser>()
        val repository = FakeAuthRepository().apply {
            registerResult = { gate.await() }
        }
        val vm = filledViewModel(repository)

        vm.submit()
        vm.submit()
        gate.complete(RegisteredUser(id = "u-1", username = "ana"))
        advanceUntilIdle()

        assertEquals(1, repository.registerCalls.size)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun `password visibility toggles`() {
        val vm = viewModel(FakeAuthRepository())
        assertFalse(vm.uiState.value.passwordVisible)
        vm.onTogglePasswordVisibility()
        assertTrue(vm.uiState.value.passwordVisible)
    }
}
