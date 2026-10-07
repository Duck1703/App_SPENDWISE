package com.spendwise.android.feature.planning

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.model.RecurringFrequency
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
 * Recurring edit (M16, REQ-BE-012): loads the template once, prefills
 * every editable field, and PATCHes a **sparse** body — only fields
 * that differ from the loaded template are sent (`PATCH` semantics,
 * not a masked create). Account, direction, currency, and active are
 * immutable identity and are never sent.
 *
 * Clearing works through the server's explicit flags: category →
 * `clearCategory`, an emptied end date → `clearEndDate`. Write safety
 * mirrors every other mutation screen: submit lock, no auto-retry,
 * ambiguous-outcome copy for dropped-mid-write cases, and a stale
 * session-expired load is re-run on the next resume (never a replay).
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class EditRecurringViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recurringRepository: RecurringRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val templateId: String = checkNotNull(savedStateHandle["templateId"]) {
        "EditRecurringViewModel requires a templateId nav argument"
    }

    private val _state = MutableStateFlow<EditRecurringState>(EditRecurringState.Loading)
    val state: StateFlow<EditRecurringState> = _state.asStateFlow()

    sealed interface Event {
        data object Saved : Event
    }

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var expiredMidLoad = false
    private var loadSerial = 0

    init {
        load()
    }

    fun refresh() = load()

    private fun load() {
        val serial = ++loadSerial
        viewModelScope.launch {
            val current = _state.value
            _state.value = if (current is EditRecurringState.Form) {
                current.copy(isSubmitting = false, formError = null)
            } else {
                EditRecurringState.Loading
            }
            try {
                val template = recurringRepository.findById(templateId)
                if (serial != loadSerial) return@launch
                val accountName = runCatching {
                    accountRepository.findById(template.accountId).name
                }.getOrNull()
                val categories = runCatching { categoryRepository.list() }.getOrDefault(emptyList())
                if (serial != loadSerial) return@launch
                _state.value = EditRecurringState.Form(
                    template = template,
                    accountName = accountName,
                    amount = template.amount.amount.toPlainString(),
                    category = template.category,
                    frequency = template.frequency,
                    nextDueDate = template.nextDueDate.toString(),
                    endDate = template.endDate?.toString() ?: "",
                    categories = categories,
                )
            } catch (e: SessionEndedException) {
                expiredMidLoad = true
                if (serial != loadSerial) return@launch
                (current as? EditRecurringState.Form)?.let { _state.value = it }
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    expiredMidLoad = true
                    if (serial != loadSerial) return@launch
                    (current as? EditRecurringState.Form)?.let { _state.value = it }
                    return@launch
                }
                // A stale failure (superseded by a newer load) must not
                // clobber newer content — M15 stale-error lesson.
                if (serial != loadSerial) return@launch
                _state.value = when (e.error) {
                    is AppError.NotFound -> EditRecurringState.NotFound
                    AppError.NetworkUnavailable -> EditRecurringState.Error(PlanningError.Network)
                    is AppError.Server -> EditRecurringState.Error(PlanningError.Server)
                    else -> EditRecurringState.Error(PlanningError.Generic)
                }
            }
        }
    }

    /** Re-runs an aborted load after a same-user resume pops the overlay. */
    fun onVisible() {
        if (expiredMidLoad) {
            expiredMidLoad = false
            load()
        }
    }

    private fun form(): EditRecurringState.Form? = _state.value as? EditRecurringState.Form

    fun onAmountChange(value: String) {
        form()?.let { _state.value = it.copy(amount = value, amountError = null, formError = null) }
    }

    fun onCategoryChange(code: String?) {
        form()?.let { _state.value = it.copy(category = code, formError = null) }
    }

    fun onFrequencyChange(value: RecurringFrequency) {
        if (value != RecurringFrequency.WEEKLY && value != RecurringFrequency.MONTHLY) return
        form()?.let { _state.value = it.copy(frequency = value, formError = null) }
    }

    fun onNextDueDateChange(value: String) {
        form()?.let {
            _state.value = it.copy(
                nextDueDate = value.take(MAX_DATE_LENGTH),
                dateError = null,
                formError = null,
            )
        }
    }

    fun onEndDateChange(value: String) {
        form()?.let {
            _state.value = it.copy(
                endDate = value.take(MAX_DATE_LENGTH),
                dateError = null,
                formError = null,
            )
        }
    }

    /**
     * `PATCH /{id}` — one user-confirmed call carrying only the changed
     * fields. `isSubmitting` blocks repeats; the write is never
     * auto-retried. A 404 mid-save reloads (the template may be gone);
     * validation errors land on their fields.
     */
    fun save() {
        val form = form() ?: return
        if (form.isSubmitting || !form.hasChanges) return

        val (amount, amountError) = parseAmount(form.amount)
        val (dates, dateError) = parseDates(form.nextDueDate, form.endDate)
        if (amountError != null || dateError != null) {
            _state.value = form.copy(amountError = amountError, dateError = dateError)
            return
        }
        val template = form.template
        _state.value = form.copy(
            isSubmitting = true,
            amountError = null,
            dateError = null,
            formError = null,
        )
        viewModelScope.launch {
            try {
                // Sparse patch: every argument is null/false unless the
                // field genuinely differs from the loaded template.
                recurringRepository.update(
                    id = templateId,
                    amount = checkNotNull(amount)
                        .takeIf { it.compareTo(template.amount.amount) != 0 }
                        ?.let { Money(it, template.amount.currency) },
                    category = form.category
                        .takeIf { it != template.category && it != null },
                    clearCategory = form.category == null && template.category != null,
                    frequency = form.frequency.takeIf { it != template.frequency },
                    nextDueDate = checkNotNull(dates).first
                        .takeIf { it != template.nextDueDate },
                    endDate = dates.second
                        .takeIf { it != template.endDate && it != null },
                    clearEndDate = dates.second == null && template.endDate != null,
                )
                _events.send(Event.Saved)
            } catch (e: SessionEndedException) {
                // Dead-generation publish — the PATCH already landed, so
                // the template almost certainly carries the edit.
                form()?.let {
                    _state.value = it.copy(
                        isSubmitting = false,
                        formError = EditRecurringState.Form.FormError.Ambiguous,
                    )
                }
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    form()?.let { _state.value = it.copy(isSubmitting = false) }
                    return@launch
                }
                if (e.error is AppError.NotFound) {
                    // The template may have been deactivated/removed
                    // elsewhere — reload before claiming anything.
                    _state.value = EditRecurringState.Loading
                    load()
                    return@launch
                }
                form()?.let { current ->
                    _state.value = current.copy(
                        isSubmitting = false,
                        amountError = e.error.fieldMessage("amount")
                            ?.let { EditRecurringState.Form.AmountError.Invalid },
                        dateError = e.error.fieldMessage("nextDueDate")
                            ?.let { EditRecurringState.Form.DateError.Invalid }
                            ?: e.error.fieldMessage("endDate")
                                ?.let { EditRecurringState.Form.DateError.Invalid },
                        formError = when (val error = e.error) {
                            AppError.NetworkUnavailable ->
                                EditRecurringState.Form.FormError.Ambiguous
                            is AppError.Server -> EditRecurringState.Form.FormError.Server
                            is AppError.Validation ->
                                if (error.fields.keys.any {
                                        it in setOf("amount", "nextDueDate", "endDate")
                                    }
                                ) {
                                    null
                                } else {
                                    EditRecurringState.Form.FormError.Generic
                                }
                            else -> EditRecurringState.Form.FormError.Generic
                        },
                    )
                }
            }
        }
    }

    /** Same `BigDecimal`-exact limits as create (`>0`, ≤2dp, ≤17int). */
    private fun parseAmount(raw: String): Pair<BigDecimal?, EditRecurringState.Form.AmountError?> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return null to EditRecurringState.Form.AmountError.Required
        }
        val value = trimmed.replace(',', '.').toBigDecimalOrNull()
            ?: return null to EditRecurringState.Form.AmountError.Invalid
        return when {
            value <= BigDecimal.ZERO -> null to EditRecurringState.Form.AmountError.NotPositive
            value.scale() > MAX_FRACTION_DIGITS ->
                null to EditRecurringState.Form.AmountError.TooPrecise
            value.precision() - value.scale() > MAX_INTEGER_DIGITS ->
                null to EditRecurringState.Form.AmountError.TooLarge
            else -> value to null
        }
    }

    /**
     * ISO `YYYY-MM-DD` parse — `nextDueDate` required; `endDate` blank =
     * none; when both parse, endDate must not precede nextDueDate
     * (client-side parity with create; the server accepts any dates).
     */
    private fun parseDates(
        nextRaw: String,
        endRaw: String,
    ): Pair<Pair<LocalDate, LocalDate?>?, EditRecurringState.Form.DateError?> {
        val next = nextRaw.trim()
        if (next.isEmpty()) return null to EditRecurringState.Form.DateError.Required
        val nextDate = runCatching { LocalDate.parse(next) }.getOrNull()
            ?: return null to EditRecurringState.Form.DateError.Invalid
        val endText = endRaw.trim()
        val endDate = if (endText.isEmpty()) {
            null
        } else {
            runCatching { LocalDate.parse(endText) }.getOrNull()
                ?: return null to EditRecurringState.Form.DateError.Invalid
        }
        if (endDate != null && endDate.isBefore(nextDate)) {
            return null to EditRecurringState.Form.DateError.EndBeforeNext
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
