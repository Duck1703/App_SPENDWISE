package com.spendwise.android.feature.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.AccountRepository
import com.spendwise.android.data.repository.BudgetRepository
import com.spendwise.android.data.repository.CategoryRepository
import com.spendwise.android.data.repository.SessionEndedException
import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Budget create (S19). Client mirrors backend limits (category required,
 * `YYYY-MM` period, limit > 0 ≤ `Digits(17,2)`) so most mistakes surface
 * inline; server `fields{}` errors still win. A duplicate
 * (category, period) is the honest 409 — budgets are create/read-only
 * (BE-011), so the copy says so.
 *
 * Write safety: the submit lock prevents double-taps, there is no
 * automatic retry, and an ambiguous failure (network loss mid-write,
 * dead-generation) surfaces "couldn't confirm" copy rather than
 * claiming nothing happened. A session expiry mid-submit releases the
 * lock — the preserved draft is resubmitted only by the user.
 *
 * Currency options: budgets are category-scoped and the server counts
 * only same-currency expenses toward spend — so the picker offers the
 * user's account currencies when any exist, falling back to the open
 * ISO-4217 set (same list as account create) when they don't.
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class CreateBudgetViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        CreateBudgetState(period = YearMonth.now().format(PERIOD_FORMAT)),
    )
    val state: StateFlow<CreateBudgetState> = _state.asStateFlow()

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
                val categories = categoryRepository.list()
                val accountCurrencies = try {
                    accountRepository.list().map { it.balance.currency.code }.distinct()
                } catch (e: SessionEndedException) {
                    throw e
                } catch (e: AppErrorException) {
                    if (e.error is AppError.SessionExpired) throw e
                    emptyList()
                }
                _state.value = _state.value.copy(
                    categories = categories,
                    currencies = accountCurrencies.ifEmpty { isoCurrencies() },
                    currency = _state.value.currency
                        ?: accountCurrencies.firstOrNull(),
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

    fun onCategoryChange(code: String?) {
        _state.value = _state.value.copy(category = code, categoryError = null, formError = null)
    }

    fun onPeriodChange(value: String) {
        _state.value = _state.value.copy(
            period = value.take(MAX_PERIOD_LENGTH),
            periodError = null,
            formError = null,
        )
    }

    fun onLimitChange(value: String) {
        _state.value = _state.value.copy(limit = value, limitError = null, formError = null)
    }

    fun onCurrencyChange(value: String) {
        _state.value = _state.value.copy(currency = value, currencyError = null, formError = null)
    }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return

        val categoryError = if (current.category == null) {
            CreateBudgetState.CategoryError.Required
        } else {
            null
        }
        val periodError = when {
            current.period.isBlank() -> CreateBudgetState.PeriodError.Required
            runCatching { YearMonth.parse(current.period.trim(), PERIOD_FORMAT) }.isFailure ->
                CreateBudgetState.PeriodError.Invalid
            else -> null
        }
        val (limit, limitError) = parseLimit(current.limit)
        val currencyError = if (current.currency == null) {
            CreateBudgetState.CurrencyError.Required
        } else {
            null
        }
        if (listOfNotNull(categoryError, periodError, limitError, currencyError).isNotEmpty()) {
            _state.value = current.copy(
                categoryError = categoryError,
                periodError = periodError,
                limitError = limitError,
                currencyError = currencyError,
            )
            return
        }

        _state.value = current.copy(
            isSubmitting = true,
            categoryError = null,
            periodError = null,
            limitError = null,
            currencyError = null,
            formError = null,
        )
        viewModelScope.launch {
            try {
                budgetRepository.create(
                    category = checkNotNull(current.category),
                    period = current.period.trim(),
                    limit = Money(
                        amount = checkNotNull(limit),
                        currency = CurrencyCode(checkNotNull(current.currency)),
                    ),
                )
                _events.send(Event.Created)
            } catch (e: SessionEndedException) {
                // Dead-generation publish — the response already landed,
                // so the budget almost certainly exists server-side.
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    formError = CreateBudgetState.FormError.Ambiguous,
                )
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    // Expired overlay pushed; the preserved draft is
                    // resubmitted only by the user — never auto-replay.
                    _state.value = _state.value.copy(isSubmitting = false)
                    return@launch
                }
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    categoryError = e.error.fieldMessage("category")
                        ?.let { CreateBudgetState.CategoryError.Required },
                    periodError = e.error.fieldMessage("period")
                        ?.let { CreateBudgetState.PeriodError.Invalid },
                    limitError = e.error.fieldMessage("limit")
                        ?.let { CreateBudgetState.LimitError.Invalid },
                    currencyError = e.error.fieldMessage("currency")
                        ?.let { CreateBudgetState.CurrencyError.Required },
                    formError = when (e.error) {
                        is AppError.Conflict -> CreateBudgetState.FormError.Conflict
                        AppError.NetworkUnavailable -> CreateBudgetState.FormError.Ambiguous
                        is AppError.Server -> CreateBudgetState.FormError.Server
                        is AppError.Validation ->
                            if (e.error.fields.keys.any {
                                    it in setOf("category", "period", "limit", "currency")
                                }
                            ) {
                                null
                            } else {
                                CreateBudgetState.FormError.Generic
                            }
                        else -> CreateBudgetState.FormError.Generic
                    },
                )
            }
        }
    }

    /**
     * Limit rules mirror the backend: required, strictly positive,
     * ≤2 fraction digits, ≤17 integer digits — [BigDecimal]-exact,
     * no Float/Double.
     */
    private fun parseLimit(raw: String): Pair<BigDecimal?, CreateBudgetState.LimitError?> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null to CreateBudgetState.LimitError.Required
        val value = trimmed.replace(',', '.').toBigDecimalOrNull()
            ?: return null to CreateBudgetState.LimitError.Invalid
        return when {
            value <= BigDecimal.ZERO -> null to CreateBudgetState.LimitError.NotPositive
            value.scale() > MAX_FRACTION_DIGITS ->
                null to CreateBudgetState.LimitError.TooPrecise
            value.precision() - value.scale() > MAX_INTEGER_DIGITS ->
                null to CreateBudgetState.LimitError.TooLarge
            else -> value to null
        }
    }

    private fun AppError.fieldMessage(field: String): String? =
        (this as? AppError.Validation)?.fields?.get(field)

    private companion object {
        val PERIOD_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT)
        const val MAX_PERIOD_LENGTH = 7
        const val MAX_FRACTION_DIGITS = 2
        const val MAX_INTEGER_DIGITS = 17

        /** Open ISO-4217 set (same source as account create). */
        fun isoCurrencies(): List<String> =
            java.util.Currency.getAvailableCurrencies().map { it.currencyCode }.sorted()
    }
}
