package com.spendwise.android.feature.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.model.RecurringFrequency
import com.spendwise.android.core.model.TransactionType
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.AccountRepository
import com.spendwise.android.data.repository.CategoryRepository
import com.spendwise.android.data.repository.RecurringRepository
import com.spendwise.android.data.repository.SessionEndedException
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Recurring create (S16). Mirrors backend limits (account required,
 * amount > 0 ≤ `Digits(17,2)`, `nextDueDate` ISO `YYYY-MM-DD`,
 * `endDate` optional) so most mistakes surface inline; server
 * `fields{}` errors still win.
 *
 * Currency is **locked to the chosen account's currency** (PRODUCT
 * §158 defect mitigation — the server does not cross-check
 * account-vs-amount currency, and a mismatched template would generate
 * transactions in a currency the account never declared). The field is
 * read-only display in the UI, never a picker.
 *
 * Write safety: submit lock + no auto-retry + ambiguous-outcome copy,
 * same contract as every other financial POST.
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class CreateRecurringViewModel @Inject constructor(
    private val recurringRepository: RecurringRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateRecurringState())
    val state: StateFlow<CreateRecurringState> = _state.asStateFlow()

    sealed interface Event {
        data object Created : Event
    }

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadOptions()
    }

    fun loadOptions() {
        viewModelScope.launch {
            _state.value = _state.value.copy(optionsLoading = true, optionsError = false)
            try {
                val accounts = accountRepository.list()
                val categories = runCatching { categoryRepository.list() }.getOrDefault(emptyList())
                _state.value = _state.value.copy(
                    accounts = accounts,
                    categories = categories,
                    accountId = _state.value.accountId
                        ?.takeIf { id -> accounts.any { it.id == id } }
                        ?: accounts.firstOrNull()?.id,
                    optionsLoading = false,
                )
            } catch (e: SessionEndedException) {
                optionsExpired = true
                _state.value = _state.value.copy(optionsLoading = false)
                return@launch
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    optionsExpired = true
                    _state.value = _state.value.copy(optionsLoading = false)
                    return@launch
                }
                _state.value = _state.value.copy(optionsLoading = false, optionsError = true)
            }
        }
    }

    private var optionsExpired = false

    /** Re-runs an aborted options load after a same-user resume. */
    fun onVisible() {
        if (optionsExpired) {
            optionsExpired = false
            loadOptions()
        }
    }

    fun onDirectionChange(value: TransactionType) {
        if (value != TransactionType.INCOME && value != TransactionType.EXPENSE) return
        _state.value = _state.value.copy(direction = value, formError = null)
    }

    fun onAccountChange(accountId: String) {
        _state.value = _state.value.copy(accountId = accountId, accountError = null, formError = null)
    }

    fun onAmountChange(value: String) {
        _state.value = _state.value.copy(amount = value, amountError = null, formError = null)
    }

    fun onCategoryChange(code: String?) {
        _state.value = _state.value.copy(category = code, formError = null)
    }

    fun onFrequencyChange(value: RecurringFrequency) {
        if (value != RecurringFrequency.WEEKLY && value != RecurringFrequency.MONTHLY) return
        _state.value = _state.value.copy(frequency = value, formError = null)
    }

    fun onNextDueDateChange(value: String) {
        _state.value = _state.value.copy(
            nextDueDate = value.take(MAX_DATE_LENGTH),
            dateError = null,
            formError = null,
        )
    }

    fun onEndDateChange(value: String) {
        _state.value = _state.value.copy(
            endDate = value.take(MAX_DATE_LENGTH),
            dateError = null,
            formError = null,
        )
    }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return

        val account = current.selectedAccount ?: run {
            _state.value = current.copy(accountError = CreateRecurringState.AccountError.Required)
            return
        }
        val (amount, amountError) = parseAmount(current.amount)
        val (dates, dateError) = parseDates(current.nextDueDate, current.endDate)
        if (amountError != null || dateError != null) {
            _state.value = current.copy(amountError = amountError, dateError = dateError)
            return
        }

        _state.value = current.copy(
            isSubmitting = true,
            accountError = null,
            amountError = null,
            dateError = null,
            formError = null,
        )
        viewModelScope.launch {
            try {
                recurringRepository.create(
                    accountId = account.id,
                    direction = current.direction,
                    // Currency lock: the account's own currency, never a
                    // user-entered code (PRODUCT §158 defect mitigation).
                    amount = Money(
                        amount = checkNotNull(amount),
                        currency = account.balance.currency,
                    ),
                    category = current.category,
                    frequency = current.frequency,
                    nextDueDate = checkNotNull(dates).first,
                    endDate = dates.second,
                )
                _events.send(Event.Created)
            } catch (e: SessionEndedException) {
                // Dead-generation publish — the response already landed,
                // so the template almost certainly exists server-side.
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    formError = CreateRecurringState.FormError.Ambiguous,
                )
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    _state.value = _state.value.copy(isSubmitting = false)
                    return@launch
                }
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    accountError = e.error.fieldMessage("accountId")
                        ?.let { CreateRecurringState.AccountError.Required },
                    amountError = e.error.fieldMessage("amount")
                        ?.let { CreateRecurringState.AmountError.Invalid },
                    dateError = e.error.fieldMessage("nextDueDate")
                        ?.let { CreateRecurringState.DateError.Invalid }
                        ?: e.error.fieldMessage("endDate")
                            ?.let { CreateRecurringState.DateError.Invalid },
                    formError = when (val error = e.error) {
                        // A foreign/unknown account is a server-side 400
                        // here — refresh the picker so it drops out.
                        is AppError.BadRequest -> {
                            loadOptions()
                            CreateRecurringState.FormError.AccountUnavailable
                        }
                        AppError.NetworkUnavailable -> CreateRecurringState.FormError.Ambiguous
                        is AppError.Server -> CreateRecurringState.FormError.Server
                        is AppError.Validation ->
                            if (error.fields.keys.any {
                                    it in setOf("accountId", "amount", "nextDueDate", "endDate")
                                }
                            ) {
                                null
                            } else {
                                CreateRecurringState.FormError.Generic
                            }
                        else -> CreateRecurringState.FormError.Generic
                    },
                )
            }
        }
    }

    /** Same `BigDecimal`-exact limits as transaction create (`>0`, ≤2dp, ≤17int). */
    private fun parseAmount(raw: String): Pair<BigDecimal?, CreateRecurringState.AmountError?> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null to CreateRecurringState.AmountError.Required
        val value = trimmed.replace(',', '.').toBigDecimalOrNull()
            ?: return null to CreateRecurringState.AmountError.Invalid
        return when {
            value <= BigDecimal.ZERO -> null to CreateRecurringState.AmountError.NotPositive
            value.scale() > MAX_FRACTION_DIGITS ->
                null to CreateRecurringState.AmountError.TooPrecise
            value.precision() - value.scale() > MAX_INTEGER_DIGITS ->
                null to CreateRecurringState.AmountError.TooLarge
            else -> value to null
        }
    }

    /**
     * ISO `YYYY-MM-DD` parse — `nextDueDate` required; `endDate` blank =
     * none; when both parse, endDate must not precede nextDueDate.
     */
    private fun parseDates(
        nextRaw: String,
        endRaw: String,
    ): Pair<Pair<LocalDate, LocalDate?>?, CreateRecurringState.DateError?> {
        val next = nextRaw.trim()
        if (next.isEmpty()) return null to CreateRecurringState.DateError.Required
        val nextDate = runCatching { LocalDate.parse(next) }.getOrNull()
            ?: return null to CreateRecurringState.DateError.Invalid
        val endText = endRaw.trim()
        val endDate = if (endText.isEmpty()) {
            null
        } else {
            runCatching { LocalDate.parse(endText) }.getOrNull()
                ?: return null to CreateRecurringState.DateError.Invalid
        }
        if (endDate != null && endDate.isBefore(nextDate)) {
            return null to CreateRecurringState.DateError.EndBeforeNext
        }
        return (nextDate to endDate) to null
    }

    private fun AppError.fieldMessage(field: String): String? =
        (this as? AppError.Validation)?.fields?.get(field)

    private companion object {
        const val MAX_DATE_LENGTH = 10
        const val MAX_FRACTION_DIGITS = 2
        const val MAX_INTEGER_DIGITS = 17
    }
}
