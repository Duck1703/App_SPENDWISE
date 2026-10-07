package com.spendwise.android.feature.planning

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.BudgetRepository
import com.spendwise.android.data.repository.SessionEndedException
import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Budget detail (S20): `GET /{id}/calculation` — spent/remaining/
 * percentage/status are server-derived figures rendered verbatim; the
 * client performs zero arithmetic on them. M14 (REQ-BE-011) adds the
 * edit + delete lifecycle: PATCH corrects `limit`/`period` (category
 * and currency are immutable — delete + recreate is the remedy),
 * DELETE removes the plan. Both mutations are generation-guarded in
 * the repository and are never auto-replayed (non-idempotent methods).
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class BudgetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val budgetRepository: BudgetRepository,
) : ViewModel() {

    /** One-off UI effects — [Deleted] pops the detail destination. */
    sealed interface Event {
        data object Deleted : Event
    }

    private val budgetId: String = checkNotNull(savedStateHandle["budgetId"]) {
        "BudgetDetailViewModel requires a budgetId nav argument"
    }

    private val _state = MutableStateFlow<BudgetDetailState>(BudgetDetailState.Loading)
    val state: StateFlow<BudgetDetailState> = _state.asStateFlow()

    private val _edit = MutableStateFlow(EditBudgetState())
    val edit: StateFlow<EditBudgetState> = _edit.asStateFlow()

    private val _events = kotlinx.coroutines.channels.Channel<Event>(
        kotlinx.coroutines.channels.Channel.BUFFERED,
    )
    val events = _events.receiveAsFlow()

    private var expiredMidLoad = false

    init {
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        viewModelScope.launch {
            val current = _state.value
            _state.value = if (current is BudgetDetailState.Content) {
                current.copy(isRefreshing = true, refreshError = null)
            } else {
                BudgetDetailState.Loading
            }
            try {
                val calculation = budgetRepository.calculation(budgetId)
                _state.value = BudgetDetailState.Content(calculation)
            } catch (e: SessionEndedException) {
                expiredMidLoad = true
                (current as? BudgetDetailState.Content)
                    ?.let { _state.value = it.copy(isRefreshing = false) }
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    expiredMidLoad = true
                    (current as? BudgetDetailState.Content)
                        ?.let { _state.value = it.copy(isRefreshing = false) }
                    return@launch
                }
                _state.value = when (e.error) {
                    is AppError.NotFound -> BudgetDetailState.NotFound
                    AppError.NetworkUnavailable ->
                        BudgetDetailState.Error(PlanningError.Network)
                    is AppError.Server ->
                        BudgetDetailState.Error(PlanningError.Server)
                    else -> BudgetDetailState.Error(PlanningError.Generic)
                }
            }
        }
    }

    // ── M14 edit dialog ────────────────────────────────────────────

    /** Seeds the dialog from the live server figures. */
    fun openEdit() {
        val content = _state.value as? BudgetDetailState.Content ?: return
        _edit.value = EditBudgetState(
            visible = true,
            limit = content.calculation.limit.amount.toPlainString(),
            period = content.calculation.period,
        )
    }

    fun dismissEdit() {
        _edit.value = EditBudgetState()
    }

    fun onEditLimitChange(value: String) {
        _edit.value = _edit.value.copy(limit = value, limitError = null, formError = null)
    }

    fun onEditPeriodChange(value: String) {
        _edit.value = _edit.value.copy(
            period = value.take(MAX_PERIOD_LENGTH),
            periodError = null,
            formError = null,
        )
    }

    fun saveEdit() {
        val dialog = _edit.value
        val content = _state.value as? BudgetDetailState.Content ?: return
        if (!dialog.visible || dialog.isSubmitting) return

        val periodError = when {
            dialog.period.isBlank() -> CreateBudgetState.PeriodError.Required
            runCatching { YearMonth.parse(dialog.period.trim(), PERIOD_FORMAT) }.isFailure ->
                CreateBudgetState.PeriodError.Invalid
            else -> null
        }
        val (limit, limitError) = parseLimit(dialog.limit)
        if (periodError != null || limitError != null) {
            _edit.value = dialog.copy(periodError = periodError, limitError = limitError)
            return
        }

        val newPeriod = dialog.period.trim()
        val newLimit = checkNotNull(limit)
        val unchanged =
            newPeriod == content.calculation.period &&
                newLimit.compareTo(content.calculation.limit.amount) == 0
        if (unchanged) {
            // Nothing to send — close without burning a request.
            _edit.value = EditBudgetState()
            return
        }

        _edit.value = dialog.copy(isSubmitting = true, formError = null)
        viewModelScope.launch {
            try {
                budgetRepository.update(
                    id = budgetId,
                    limit = Money(newLimit, content.calculation.limit.currency),
                    period = newPeriod,
                )
                _edit.value = EditBudgetState()
                refresh()
            } catch (e: SessionEndedException) {
                // Dead-generation publish — the patch may have committed.
                _edit.value = _edit.value.copy(
                    isSubmitting = false,
                    formError = EditBudgetState.FormError.Ambiguous,
                )
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    _edit.value = _edit.value.copy(isSubmitting = false)
                    return@launch
                }
                _edit.value = _edit.value.copy(
                    isSubmitting = false,
                    limitError = (e.error as? AppError.Validation)
                        ?.fields?.get("limit")
                        ?.let { CreateBudgetState.LimitError.Invalid },
                    periodError = (e.error as? AppError.Validation)
                        ?.fields?.get("period")
                        ?.let { CreateBudgetState.PeriodError.Invalid },
                    formError = when (e.error) {
                        is AppError.Conflict -> EditBudgetState.FormError.Conflict
                        is AppError.NotFound -> {
                            // The budget is gone — reload surfaces NotFound.
                            refresh()
                            null
                        }
                        AppError.NetworkUnavailable -> EditBudgetState.FormError.Ambiguous
                        is AppError.Server -> EditBudgetState.FormError.Server
                        else -> EditBudgetState.FormError.Generic
                    },
                )
            }
        }
    }

    // ── M14 delete ─────────────────────────────────────────────────

    fun delete() {
        viewModelScope.launch {
            try {
                budgetRepository.delete(budgetId)
                _events.send(Event.Deleted)
            } catch (e: SessionEndedException) {
                // Ambiguous — the delete may have committed; reload and
                // let the server truth decide what the user sees.
                refresh()
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) return@launch
                when (e.error) {
                    is AppError.NotFound -> refresh() // already gone
                    else -> refresh() // surface the freshest server state
                }
            }
        }
    }

    /** Re-runs an aborted load after a same-user resume pops the overlay. */
    fun onVisible() {
        if (expiredMidLoad) {
            expiredMidLoad = false
            refresh()
        }
    }

    /**
     * Limit rules mirror the backend: required, strictly positive,
     * ≤2 fraction digits, ≤17 integer digits — [BigDecimal]-exact.
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

    private companion object {
        val PERIOD_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT)
        const val MAX_PERIOD_LENGTH = 7
        const val MAX_FRACTION_DIGITS = 2
        const val MAX_INTEGER_DIGITS = 17
    }
}
