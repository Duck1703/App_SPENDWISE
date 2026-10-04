package com.spendwise.android.feature.accounts

import app.cash.turbine.test
import com.spendwise.android.core.model.AccountType
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.fakes.FakeAccountRepository
import com.spendwise.android.fakes.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

class CreateAccountViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(repo: FakeAccountRepository = FakeAccountRepository()) =
        CreateAccountViewModel(repo)

    @Test
    fun `blank name fails required validation without a request`() = runTest {
        val repo = FakeAccountRepository()
        val vm = viewModel(repo)

        vm.onNameChange("   ")
        vm.submit()

        assertEquals(CreateAccountUiState.NameError.Required, vm.state.value.nameError)
        assertTrue(repo.createCalls.isEmpty())
    }

    @Test
    fun `overlong name fails length validation`() = runTest {
        val repo = FakeAccountRepository()
        val vm = viewModel(repo)

        vm.onNameChange("x".repeat(CreateAccountUiState.MAX_NAME_LENGTH + 1))
        vm.submit()

        assertEquals(CreateAccountUiState.NameError.TooLong, vm.state.value.nameError)
        assertTrue(repo.createCalls.isEmpty())
    }

    @Test
    fun `unparsable balance fails without a request`() = runTest {
        val repo = FakeAccountRepository()
        val vm = viewModel(repo)

        vm.onNameChange("Jar")
        vm.onOpeningBalanceChange("abc")
        vm.submit()

        assertEquals(CreateAccountUiState.BalanceError.Invalid, vm.state.value.balanceError)
        assertTrue(repo.createCalls.isEmpty())
    }

    @Test
    fun `negative balance fails without a request`() = runTest {
        val repo = FakeAccountRepository()
        val vm = viewModel(repo)

        vm.onNameChange("Jar")
        vm.onOpeningBalanceChange("-5")
        vm.submit()

        assertEquals(CreateAccountUiState.BalanceError.Negative, vm.state.value.balanceError)
    }

    @Test
    fun `three fraction digits fails precision`() = runTest {
        val repo = FakeAccountRepository()
        val vm = viewModel(repo)

        vm.onNameChange("Jar")
        vm.onOpeningBalanceChange("1.234")
        vm.submit()

        assertEquals(CreateAccountUiState.BalanceError.TooPrecise, vm.state.value.balanceError)
    }

    @Test
    fun `empty opening balance submits zero`() = runTest {
        val repo = FakeAccountRepository()
        repo.createResult = { name, type, money ->
            FakeAccountRepository.account(name = name, type = type, balance = money.amount.toPlainString())
        }
        val vm = viewModel(repo)

        vm.onNameChange("Jar")
        vm.submit()

        val call = repo.createCalls.single()
        assertEquals("Jar", call.first)
        assertEquals(BigDecimal.ZERO, call.third.amount)
        assertEquals("VND", call.third.currency.code)
    }

    @Test
    fun `comma decimals normalize before submit`() = runTest {
        val repo = FakeAccountRepository()
        val vm = viewModel(repo)

        vm.onNameChange("Jar")
        vm.onOpeningBalanceChange("1500,50")
        vm.submit()

        assertNull(vm.state.value.balanceError)
        assertEquals(BigDecimal("1500.50"), repo.createCalls.single().third.amount)
    }

    @Test
    fun `success emits the Created event with the account id`() = runTest {
        val repo = FakeAccountRepository()
        repo.createResult = { _, _, _ -> FakeAccountRepository.account(id = "created-9") }
        val vm = viewModel(repo)

        vm.events.test {
            vm.onNameChange("Jar")
            vm.submit()
            assertEquals(
                CreateAccountViewModel.Event.Created("created-9"),
                awaitItem(),
            )
        }
    }

    @Test
    fun `server name field error lands on the name channel`() = runTest {
        val repo = FakeAccountRepository()
        repo.createResult = { _, _, _ ->
            throw AppErrorException(AppError.Validation(mapOf("name" to "already exists")))
        }
        val vm = viewModel(repo)

        vm.onNameChange("Jar")
        vm.submit()

        assertEquals(
            CreateAccountUiState.NameError.Server("already exists"),
            vm.state.value.nameError,
        )
        assertNull(vm.state.value.formError)
    }

    @Test
    fun `server balance field error lands on the balance channel`() = runTest {
        val repo = FakeAccountRepository()
        repo.createResult = { _, _, _ ->
            throw AppErrorException(
                AppError.Validation(mapOf("openingBalance" to "must be greater than or equal to 0")),
            )
        }
        val vm = viewModel(repo)

        vm.onNameChange("Jar")
        vm.submit()

        assertEquals(
            CreateAccountUiState.BalanceError.Server("must be greater than or equal to 0"),
            vm.state.value.balanceError,
        )
    }

    @Test
    fun `validation on picker-controlled fields surfaces as generic form error`() = runTest {
        val repo = FakeAccountRepository()
        repo.createResult = { _, _, _ ->
            throw AppErrorException(AppError.Validation(mapOf("currency" to "unsupported")))
        }
        val vm = viewModel(repo)

        vm.onNameChange("Jar")
        vm.submit()

        assertNull(vm.state.value.nameError)
        assertNull(vm.state.value.balanceError)
        assertEquals(
            CreateAccountUiState.CreateFormError.Generic,
            vm.state.value.formError,
        )
    }

    @Test
    fun `network failure surfaces the retryable form error`() = runTest {
        val repo = FakeAccountRepository()
        repo.createResult = { _, _, _ -> throw AppErrorException(AppError.NetworkUnavailable) }
        val vm = viewModel(repo)

        vm.onNameChange("Jar")
        vm.submit()

        assertEquals(
            CreateAccountUiState.CreateFormError.Network,
            vm.state.value.formError,
        )
        assertTrue(!vm.state.value.isSubmitting)
    }

    @Test
    fun `double submit is locked while in flight`() = runTest {
        val repo = FakeAccountRepository()
        val gate = kotlinx.coroutines.CompletableDeferred<com.spendwise.android.core.model.Account>()
        repo.createResult = { _, _, _ -> gate.await() }
        val vm = viewModel(repo)
        vm.onNameChange("Jar")

        vm.submit()
        vm.submit() // ignored — isSubmitting

        assertEquals(1, repo.createCalls.size)
        gate.complete(FakeAccountRepository.account())
    }

    @Test
    fun `SessionExpired during submit does not surface a form error`() = runTest {
        val repo = FakeAccountRepository()
        repo.createResult = { _, _, _ -> throw AppErrorException(AppError.SessionExpired) }
        val vm = viewModel(repo)
        vm.onNameChange("Jar")

        vm.submit()

        assertNull(vm.state.value.formError)
    }

    @Test
    fun `SessionExpired during submit releases the submit lock`() = runTest {
        val repo = FakeAccountRepository()
        repo.createResult = { _, _, _ -> throw AppErrorException(AppError.SessionExpired) }
        val vm = viewModel(repo)
        vm.onNameChange("Jar")
        vm.submit()

        // After the overlay resumes the preserved form, resubmission works.
        repo.createResult = { _, _, _ -> FakeAccountRepository.account(id = "retry-1") }
        vm.submit()

        assertEquals(2, repo.createCalls.size)
    }
}
