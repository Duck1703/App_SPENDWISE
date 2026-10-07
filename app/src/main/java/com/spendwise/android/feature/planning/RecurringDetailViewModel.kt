package com.spendwise.android.feature.planning

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.AccountRepository
import com.spendwise.android.data.repository.RecurringRepository
import com.spendwise.android.data.repository.SessionEndedException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Recurring detail (S17): `GET /{id}` content + the deactivate
 * affordance. `DELETE /{id}` is a soft-delete — the server returns the
 * updated template (`active=false`), so a success stays on this screen
 * showing the deactivated state rather than popping. Deactivation is a
 * write: the action locks synchronously, never auto-retries, and a
 * 401/dead-generation outcome surfaces honest copy (the template may
 * already be deactivated server-side).
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class RecurringDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recurringRepository: RecurringRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val templateId: String = checkNotNull(savedStateHandle["templateId"]) {
        "RecurringDetailViewModel requires a templateId nav argument"
    }

    private val _state = MutableStateFlow<RecurringDetailState>(RecurringDetailState.Loading)
    val state: StateFlow<RecurringDetailState> = _state.asStateFlow()

    private var expiredMidLoad = false

    init {
        load(initial = true)
        // A save on the edit screen emits a mutation — reload so this
        // detail reflects server truth when it resumes (M16).
        viewModelScope.launch {
            recurringRepository.mutations.collect { refresh() }
        }
    }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        viewModelScope.launch {
            val current = _state.value
            _state.value = if (current is RecurringDetailState.Content) {
                current.copy(isDeactivating = false, actionError = null)
            } else {
                RecurringDetailState.Loading
            }
            try {
                val template = recurringRepository.findById(templateId)
                val accountName = runCatching {
                    accountRepository.findById(template.accountId).name
                }.getOrNull()
                _state.value = RecurringDetailState.Content(template, accountName)
            } catch (e: SessionEndedException) {
                expiredMidLoad = true
                (current as? RecurringDetailState.Content)
                    ?.let { _state.value = it }
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    expiredMidLoad = true
                    (current as? RecurringDetailState.Content)
                        ?.let { _state.value = it }
                    return@launch
                }
                _state.value = when (e.error) {
                    is AppError.NotFound -> RecurringDetailState.NotFound
                    AppError.NetworkUnavailable ->
                        RecurringDetailState.Error(PlanningError.Network)
                    is AppError.Server ->
                        RecurringDetailState.Error(PlanningError.Server)
                    else -> RecurringDetailState.Error(PlanningError.Generic)
                }
            }
        }
    }

    /**
     * `DELETE /{id}` — one user-confirmed call. `isDeactivating` blocks
     * repeats; a success publishes the returned (inactive) template so
     * the row's state flips in place.
     */
    fun deactivate() {
        val current = _state.value as? RecurringDetailState.Content ?: return
        if (current.isDeactivating) return
        if (!current.template.active) return
        _state.value = current.copy(isDeactivating = true, actionError = null)
        viewModelScope.launch {
            try {
                val updated = recurringRepository.deactivate(templateId)
                _state.value = (_state.value as? RecurringDetailState.Content)
                    ?.copy(template = updated, isDeactivating = false)
                    ?: RecurringDetailState.Content(updated, current.accountName)
            } catch (e: SessionEndedException) {
                expiredMidLoad = true
                (_state.value as? RecurringDetailState.Content)
                    ?.let { _state.value = it.copy(isDeactivating = false, actionError = PlanningError.Generic) }
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    expiredMidLoad = true
                    (_state.value as? RecurringDetailState.Content)
                        ?.let { _state.value = it.copy(isDeactivating = false) }
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
                (_state.value as? RecurringDetailState.Content)
                    ?.let { _state.value = it.copy(isDeactivating = false, actionError = kind) }
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
