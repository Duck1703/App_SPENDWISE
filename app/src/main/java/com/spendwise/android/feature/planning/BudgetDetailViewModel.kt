package com.spendwise.android.feature.planning

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.BudgetRepository
import com.spendwise.android.data.repository.SessionEndedException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Budget detail (S20): `GET /{id}/calculation` — spent/remaining/
 * percentage/status are server-derived figures rendered verbatim; the
 * client performs zero arithmetic on them. Read-only surface (no
 * edit/delete exists — BE-011); same resume/session contract as the
 * other detail screens.
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class BudgetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val budgetRepository: BudgetRepository,
) : ViewModel() {

    private val budgetId: String = checkNotNull(savedStateHandle["budgetId"]) {
        "BudgetDetailViewModel requires a budgetId nav argument"
    }

    private val _state = MutableStateFlow<BudgetDetailState>(BudgetDetailState.Loading)
    val state: StateFlow<BudgetDetailState> = _state.asStateFlow()

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

    /** Re-runs an aborted load after a same-user resume pops the overlay. */
    fun onVisible() {
        if (expiredMidLoad) {
            expiredMidLoad = false
            refresh()
        }
    }
}
