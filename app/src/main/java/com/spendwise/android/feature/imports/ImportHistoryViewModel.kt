package com.spendwise.android.feature.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.AccountRepository
import com.spendwise.android.data.repository.ImportRepository
import com.spendwise.android.data.repository.SessionEndedException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Import history list (M10, REQ-BE-004). Latest-wins loads via
 * [loadSerial] — a superseded response can never overwrite newer
 * state. A 401 mid-load arms [expiredMidLoad] so a same-user resume
 * re-runs via [onVisible]; a dead-generation result is discarded,
 * never published.
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class ImportHistoryViewModel @Inject constructor(
    private val importRepository: ImportRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportHistoryState>(ImportHistoryState.Loading)
    val state: StateFlow<ImportHistoryState> = _state.asStateFlow()

    private var loadSerial = 0L
    private var expiredMidLoad = false

    init {
        refresh()
        // A committed import elsewhere invalidates this list — the
        // refetch still goes to the server; nothing is injected locally.
        viewModelScope.launch {
            importRepository.mutations.collect { refresh() }
        }
    }

    fun refresh() {
        val serial = ++loadSerial
        viewModelScope.launch {
            (_state.value as? ImportHistoryState.Content)
                ?.let { _state.value = it.copy(isRefreshing = true, refreshError = null) }
            try {
                val items = importRepository.history()
                if (serial != loadSerial) return@launch
                _state.value = if (items.isEmpty()) {
                    ImportHistoryState.Empty
                } else {
                    ImportHistoryState.Content(
                        items = items,
                        accountNames = accountNames(),
                    )
                }
            } catch (e: SessionEndedException) {
                expiredMidLoad = true
                (_state.value as? ImportHistoryState.Content)
                    ?.let { _state.value = it.copy(isRefreshing = false) }
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    expiredMidLoad = true
                    (_state.value as? ImportHistoryState.Content)
                        ?.let { _state.value = it.copy(isRefreshing = false) }
                    return@launch
                }
                if (serial != loadSerial) return@launch
                val kind = e.error.toHistoryError()
                _state.value = (_state.value as? ImportHistoryState.Content)
                    ?.copy(isRefreshing = false, refreshError = kind)
                    ?: ImportHistoryState.Error(kind)
            }
        }
    }

    /** Account labels are display-only context — a failure degrades to ids. */
    private suspend fun accountNames(): Map<String, String> =
        try {
            accountRepository.list().associate { it.id to it.name }
        } catch (e: AppErrorException) {
            if (e.error is AppError.SessionExpired) throw e
            emptyMap()
        } catch (e: SessionEndedException) {
            throw e
        }

    /** Re-runs an aborted load after a same-user resume pops the overlay. */
    fun onVisible() {
        if (expiredMidLoad) {
            expiredMidLoad = false
            refresh()
        }
    }

    internal fun AppError.toHistoryError(): ImportHistoryError = when (this) {
        AppError.NetworkUnavailable -> ImportHistoryError.Network
        is AppError.Server -> ImportHistoryError.Server
        else -> ImportHistoryError.Generic
    }
}
