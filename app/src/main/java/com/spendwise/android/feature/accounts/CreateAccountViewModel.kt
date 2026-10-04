package com.spendwise.android.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.android.core.model.AccountType
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.data.repository.AccountRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Create-account form (S07). Client mirrors backend limits (@Size 255 name,
 * @Digits 17/2 balance, @PositiveOrZero) so most mistakes surface inline —
 * server `fields{}` errors still win and map onto the same channels.
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class CreateAccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateAccountUiState())
    val state: StateFlow<CreateAccountUiState> = _state.asStateFlow()

    sealed interface Event {
        data class Created(val accountId: String) : Event
    }

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onNameChange(value: String) {
        _state.value = _state.value.copy(name = value, nameError = null, formError = null)
    }

    fun onTypeChange(value: AccountType) {
        _state.value = _state.value.copy(type = value)
    }

    fun onCurrencyChange(value: String) {
        _state.value = _state.value.copy(currencyCode = value, formError = null)
    }

    fun onOpeningBalanceChange(value: String) {
        _state.value = _state.value.copy(openingBalance = value, balanceError = null, formError = null)
    }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return

        val nameError = validateName(current.name)
        val (balance, balanceError) = parseOpeningBalance(current.openingBalance)
        if (nameError != null || balanceError != null) {
            _state.value = current.copy(nameError = nameError, balanceError = balanceError)
            return
        }

        _state.value = current.copy(
            isSubmitting = true,
            nameError = null,
            balanceError = null,
            formError = null,
        )
        viewModelScope.launch {
            try {
                val account = accountRepository.create(
                    name = current.name.trim(),
                    type = current.type,
                    openingBalance = Money(
                        amount = balance ?: BigDecimal.ZERO,
                        currency = CurrencyCode(current.currencyCode),
                    ),
                )
                _events.send(Event.Created(account.id))
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    // Overlay pushed; release the submit lock so the preserved
                    // form can be resubmitted after a same-user resume.
                    _state.value = _state.value.copy(isSubmitting = false)
                    return@launch
                }
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    nameError = e.error.fieldMessage("name")
                        ?.let(CreateAccountUiState.NameError::Server),
                    balanceError = e.error.fieldMessage("openingBalance")
                        ?.let(CreateAccountUiState.BalanceError::Server),
                    formError = when (val error = e.error) {
                        is AppError.Validation ->
                            if (error.fields.keys.any { it in setOf("name", "openingBalance") }) {
                                null
                            } else {
                                CreateAccountUiState.CreateFormError.Generic
                            }
                        AppError.NetworkUnavailable -> CreateAccountUiState.CreateFormError.Network
                        is AppError.Server -> CreateAccountUiState.CreateFormError.Server
                        else -> CreateAccountUiState.CreateFormError.Generic
                    },
                )
            }
        }
    }

    private fun validateName(name: String): CreateAccountUiState.NameError? = when {
        name.isBlank() -> CreateAccountUiState.NameError.Required
        name.trim().length > CreateAccountUiState.MAX_NAME_LENGTH ->
            CreateAccountUiState.NameError.TooLong
        else -> null
    }

    /** Empty input = "start at 0" (backend default); commas normalize to dots. */
    private fun parseOpeningBalance(
        raw: String,
    ): Pair<BigDecimal?, CreateAccountUiState.BalanceError?> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return BigDecimal.ZERO to null
        val normalized = trimmed.replace(',', '.')
        val value = normalized.toBigDecimalOrNull()
            ?: return null to CreateAccountUiState.BalanceError.Invalid
        return when {
            value < BigDecimal.ZERO -> null to CreateAccountUiState.BalanceError.Negative
            value.scale() > MAX_FRACTION_DIGITS ->
                null to CreateAccountUiState.BalanceError.TooPrecise
            value.precision() - value.scale() > MAX_INTEGER_DIGITS ->
                null to CreateAccountUiState.BalanceError.TooLarge
            else -> value to null
        }
    }

    private fun AppError.fieldMessage(field: String): String? =
        (this as? AppError.Validation)?.fields?.get(field)

    private companion object {
        // Backend @Digits(integer = 17, fraction = 2).
        const val MAX_INTEGER_DIGITS = 17
        const val MAX_FRACTION_DIGITS = 2
    }
}
