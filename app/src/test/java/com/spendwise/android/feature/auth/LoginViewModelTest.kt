package com.spendwise.android.feature.auth

import androidx.lifecycle.SavedStateHandle
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.fakes.FakeAuthRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        repository: FakeAuthRepository,
        handle: SavedStateHandle = SavedStateHandle(),
    ) = LoginViewModel(authRepository = repository, savedStateHandle = handle)

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
        assertFalse(state.isSubmitting)
        assertTrue(repository.loginCalls.isEmpty())
    }

    @Test
    fun `successful login calls the repository once and clears the password`() = runTest {
        val repository = FakeAuthRepository()
        val vm = filledViewModel(repository)

        vm.submit()
        advanceUntilIdle()

        assertEquals(listOf("ana" to "secret-pw"), repository.loginCalls)
        val state = vm.uiState.value
        assertFalse(state.isSubmitting)
        assertEquals("", state.password)
        assertNull(state.formError)
    }

    @Test
    fun `username is trimmed before submission`() = runTest {
        val repository = FakeAuthRepository()
        val vm = viewModel(repository).apply {
            onUsernameChanged("  ana  ")
            onPasswordChanged("secret-pw")
        }

        vm.submit()
        advanceUntilIdle()

        assertEquals(listOf("ana" to "secret-pw"), repository.loginCalls)
    }

    @Test
    fun `invalid credentials map to the inline form error`() = runTest {
        val repository = FakeAuthRepository().apply {
            loginResult = { _, _ -> throw AppErrorException(AppError.InvalidCredentials) }
        }
        val vm = filledViewModel(repository)

        vm.submit()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(AuthFormError.InvalidCredentials, state.formError)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun `backend field validation maps to field errors`() = runTest {
        val repository = FakeAuthRepository().apply {
            loginResult = { _, _ ->
                throw AppErrorException(
                    AppError.Validation(mapOf("username" to "must not be blank")),
                )
            }
        }
        val vm = filledViewModel(repository)

        vm.submit()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(AuthFieldError.Server("must not be blank"), state.usernameError)
        assertNull(state.formError)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun `network failure maps to the offline form error`() = runTest {
        val repository = FakeAuthRepository().apply {
            loginResult = { _, _ -> throw AppErrorException(AppError.NetworkUnavailable) }
        }
        val vm = filledViewModel(repository)

        vm.submit()
        advanceUntilIdle()

        assertEquals(AuthFormError.NetworkUnavailable, vm.uiState.value.formError)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun `server failure maps to the generic server form error`() = runTest {
        val repository = FakeAuthRepository().apply {
            loginResult = { _, _ -> throw AppErrorException(AppError.Server(500)) }
        }
        val vm = filledViewModel(repository)

        vm.submit()
        advanceUntilIdle()

        assertEquals(AuthFormError.ServerError, vm.uiState.value.formError)
    }

    @Test
    fun `submit while in flight is locked to a single call`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeAuthRepository().apply {
            loginResult = { _, _ -> gate.await() }
        }
        val vm = filledViewModel(repository)

        vm.submit()
        vm.submit()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.loginCalls.size)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun `typing clears the previous error`() = runTest {
        val repository = FakeAuthRepository().apply {
            loginResult = { _, _ -> throw AppErrorException(AppError.InvalidCredentials) }
        }
        val vm = filledViewModel(repository)
        vm.submit()
        advanceUntilIdle()
        assertEquals(AuthFormError.InvalidCredentials, vm.uiState.value.formError)

        vm.onPasswordChanged("other-pw")

        assertNull(vm.uiState.value.formError)
    }

    @Test
    fun `password visibility toggles`() {
        val vm = viewModel(FakeAuthRepository())
        assertFalse(vm.uiState.value.passwordVisible)

        vm.onTogglePasswordVisibility()
        assertTrue(vm.uiState.value.passwordVisible)

        vm.onTogglePasswordVisibility()
        assertFalse(vm.uiState.value.passwordVisible)
    }

    @Test
    fun `route args seed prefill, banner and resume flag`() {
        val vm = viewModel(
            FakeAuthRepository(),
            SavedStateHandle(
                mapOf(
                    "prefill" to "ana",
                    "accountCreated" to true,
                    "resume" to true,
                ),
            ),
        )

        val state = vm.uiState.value
        assertEquals("ana", state.username)
        assertTrue(state.showAccountCreatedBanner)
        assertTrue(state.isResume)
    }
}
