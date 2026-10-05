package com.spendwise.android.feature.planning

import androidx.lifecycle.SavedStateHandle
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
 * Rule detail (S23): `GET /{id}` content + the disable affordance.
 * `DELETE /{id}` is a soft-disable — the server returns the updated
 * rule (`enabled=false`), so a success stays on this screen showing the
 * disabled state. Same write-safety/session contract as
 * [RecurringDetailViewModel.deactivate].
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class RuleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ruleRepository: RuleRepository,
) : ViewModel() {

    private val ruleId: String = checkNotNull(savedStateHandle["ruleId"]) {
        "RuleDetailViewModel requires a ruleId nav argument"
    }

    private val _state = MutableStateFlow<RuleDetailState>(RuleDetailState.Loading)
    val state: StateFlow<RuleDetailState> = _state.asStateFlow()

    private var expiredMidLoad = false

    init {
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        viewModelScope.launch {
            val current = _state.value
            _state.value = if (current is RuleDetailState.Content) {
                current.copy(isDisabling = false, actionError = null)
            } else {
                RuleDetailState.Loading
            }
            try {
                val rule = ruleRepository.findById(ruleId)
                _state.value = RuleDetailState.Content(rule)
            } catch (e: SessionEndedException) {
                expiredMidLoad = true
                (current as? RuleDetailState.Content)
                    ?.let { _state.value = it }
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    expiredMidLoad = true
                    (current as? RuleDetailState.Content)
                        ?.let { _state.value = it }
                    return@launch
                }
                _state.value = when (e.error) {
                    is AppError.NotFound -> RuleDetailState.NotFound
                    AppError.NetworkUnavailable ->
                        RuleDetailState.Error(PlanningError.Network)
                    is AppError.Server ->
                        RuleDetailState.Error(PlanningError.Server)
                    else -> RuleDetailState.Error(PlanningError.Generic)
                }
            }
        }
    }

    /**
     * `DELETE /{id}` — one user-confirmed call. `isDisabling` blocks
     * repeats; a success publishes the returned (disabled) rule so the
     * state flips in place.
     */
    fun disable() {
        val current = _state.value as? RuleDetailState.Content ?: return
        if (current.isDisabling) return
        if (!current.rule.enabled) return
        _state.value = current.copy(isDisabling = true, actionError = null)
        viewModelScope.launch {
            try {
                val updated = ruleRepository.disable(ruleId)
                _state.value = (_state.value as? RuleDetailState.Content)
                    ?.copy(rule = updated, isDisabling = false)
                    ?: RuleDetailState.Content(updated)
            } catch (e: SessionEndedException) {
                expiredMidLoad = true
                (_state.value as? RuleDetailState.Content)
                    ?.let { _state.value = it.copy(isDisabling = false, actionError = PlanningError.Generic) }
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    expiredMidLoad = true
                    (_state.value as? RuleDetailState.Content)
                        ?.let { _state.value = it.copy(isDisabling = false) }
                    return@launch
                }
                val kind = when (e.error) {
                    is AppError.NotFound -> {
                        refresh()
                        return@launch
                    }
                    AppError.NetworkUnavailable -> PlanningError.Network
                    is AppError.Server -> PlanningError.Server
                    else -> PlanningError.Generic
                }
                (_state.value as? RuleDetailState.Content)
                    ?.let { _state.value = it.copy(isDisabling = false, actionError = kind) }
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
