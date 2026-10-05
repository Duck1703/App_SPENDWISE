package com.spendwise.android.feature.planning

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
 * Budgets list (S18, PRODUCT §30). Same race/session discipline as the
 * other planning lists: [loadSerial] latest-wins, [expiredMidLoad] +
 * [onVisible] for same-user resume, [BudgetRepository.mutations]
 * reloads after a successful create.
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<BudgetsListState>(BudgetsListState.Loading)
    val state: StateFlow<BudgetsListState> = _state.asStateFlow()

    private var loadSerial = 0L
    private var expiredMidLoad = false

    init {
        refresh()
        viewModelScope.launch {
            budgetRepository.mutations.collect { refresh() }
        }
    }

    fun refresh() = load()

    private fun load() {
        val serial = ++loadSerial
        viewModelScope.launch {
            (_state.value as? BudgetsListState.Content)
                ?.let { _state.value = it.copy(isRefreshing = true, refreshError = null) }
            try {
                val budgets = budgetRepository.list()
                if (serial != loadSerial) return@launch
                _state.value = if (budgets.isEmpty()) {
                    BudgetsListState.Empty
                } else {
                    BudgetsListState.Content(budgets = budgets)
                }
            } catch (e: SessionEndedException) {
                expiredMidLoad = true
                (_state.value as? BudgetsListState.Content)
                    ?.let { _state.value = it.copy(isRefreshing = false) }
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    expiredMidLoad = true
                    (_state.value as? BudgetsListState.Content)
                        ?.let { _state.value = it.copy(isRefreshing = false) }
                    return@launch
                }
                if (serial != loadSerial) return@launch
                val kind = e.error.toPlanningError()
                _state.value = (_state.value as? BudgetsListState.Content)
                    ?.copy(isRefreshing = false, refreshError = kind)
                    ?: BudgetsListState.Error(kind)
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

    private fun AppError.toPlanningError(): PlanningError = when (this) {
        AppError.NetworkUnavailable -> PlanningError.Network
        is AppError.Server -> PlanningError.Server
        else -> PlanningError.Generic
    }
}
