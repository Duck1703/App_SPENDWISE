package com.spendwise.android.feature.imports

import androidx.lifecycle.SavedStateHandle
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
 * Import batch detail (M10): `GET /api/imports/{id}` — the per-row
 * report, rendered verbatim; the client computes nothing on it.
 * Foreign/unknown ids surface honest NotFound — never forbidden data
 * (R24/R25). Same resume/session contract as the other detail screens.
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class ImportHistoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val importRepository: ImportRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val batchId: String = checkNotNull(savedStateHandle["batchId"]) {
        "ImportHistoryDetailViewModel requires a batchId nav argument"
    }

    private val _state =
        MutableStateFlow<ImportHistoryDetailState>(ImportHistoryDetailState.Loading)
    val state: StateFlow<ImportHistoryDetailState> = _state.asStateFlow()

    private var expiredMidLoad = false

    init {
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        viewModelScope.launch {
            val current = _state.value
            _state.value = if (current is ImportHistoryDetailState.Content) {
                current.copy(isRefreshing = true, refreshError = null)
            } else {
                ImportHistoryDetailState.Loading
            }
            try {
                val batch = importRepository.batch(batchId)
                _state.value = ImportHistoryDetailState.Content(
                    batch = batch,
                    accountName = accountName(batch.accountId),
                )
            } catch (e: SessionEndedException) {
                expiredMidLoad = true
                (current as? ImportHistoryDetailState.Content)
                    ?.let { _state.value = it.copy(isRefreshing = false) }
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    expiredMidLoad = true
                    (current as? ImportHistoryDetailState.Content)
                        ?.let { _state.value = it.copy(isRefreshing = false) }
                    return@launch
                }
                _state.value = when (e.error) {
                    is AppError.NotFound -> ImportHistoryDetailState.NotFound
                    AppError.NetworkUnavailable ->
                        ImportHistoryDetailState.Error(ImportHistoryError.Network)
                    is AppError.Server ->
                        ImportHistoryDetailState.Error(ImportHistoryError.Server)
                    else -> ImportHistoryDetailState.Error(ImportHistoryError.Generic)
                }
            }
        }
    }

    /** Display-only context — a lookup failure degrades to null. */
    private suspend fun accountName(accountId: String): String? =
        try {
            accountRepository.list().firstOrNull { it.id == accountId }?.name
        } catch (e: AppErrorException) {
            if (e.error is AppError.SessionExpired) throw e
            null
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
}
