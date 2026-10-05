package com.spendwise.android.feature.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.RuleRepository
import com.spendwise.android.data.repository.SessionEndedException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Rules list (S21, PRODUCT §30) — the server already orders by
 * ascending priority; the client renders that order verbatim (a rule's
 * position is meaningful: first match wins). Same race/session
 * discipline as the other planning lists.
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class RulesViewModel @Inject constructor(
    private val ruleRepository: RuleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<RulesListState>(RulesListState.Loading)
    val state: StateFlow<RulesListState> = _state.asStateFlow()

    private var loadSerial = 0L
    private var expiredMidLoad = false

    init {
        refresh()
        viewModelScope.launch {
            ruleRepository.mutations.collect { refresh() }
        }
    }

    fun refresh() = load()

    private fun load() {
        val serial = ++loadSerial
        viewModelScope.launch {
            (_state.value as? RulesListState.Content)
                ?.let { _state.value = it.copy(isRefreshing = true, refreshError = null) }
            try {
                val rules = ruleRepository.list()
                if (serial != loadSerial) return@launch
                _state.value = if (rules.isEmpty()) {
                    RulesListState.Empty
                } else {
                    RulesListState.Content(rules = rules)
                }
            } catch (e: SessionEndedException) {
                expiredMidLoad = true
                (_state.value as? RulesListState.Content)
                    ?.let { _state.value = it.copy(isRefreshing = false) }
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    expiredMidLoad = true
                    (_state.value as? RulesListState.Content)
                        ?.let { _state.value = it.copy(isRefreshing = false) }
                    return@launch
                }
                if (serial != loadSerial) return@launch
                val kind = e.error.toPlanningError()
                _state.value = (_state.value as? RulesListState.Content)
                    ?.copy(isRefreshing = false, refreshError = kind)
                    ?: RulesListState.Error(kind)
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
