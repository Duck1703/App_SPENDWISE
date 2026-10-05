package com.spendwise.android.feature.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.AccountRepository
import com.spendwise.android.data.repository.ExportSaveException
import com.spendwise.android.data.repository.LedgerExportArtifact
import com.spendwise.android.data.repository.LedgerExportRepository
import com.spendwise.android.data.repository.SessionEndedException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Ledger export VM (M9).
 *
 * - Single-tap flow: `export()` downloads to a private temp artifact,
 *   then raises a one-shot [SaveRequest] — the destination launches
 *   `CreateDocument` and reports the user's URI back.
 * - **Double-submit lock**: [ExportUiState.Phase] gates every entry
 *   point; Downloading/Saving are modal.
 * - **Cleanup discipline**: the temp artifact is deleted on cancel,
 *   save success/failure, session expiry, stale generation (repository
 *   side) and [onCleared]. It is NEVER part of [ExportUiState] — only
 *   its suggested name is.
 * - **EXT-MAJOR-1-style resume**: expiry mid-flow arms
 *   `expiredMidLoad`; `onVisible()` after a same-user resume re-runs
 *   the account load — never an automatic re-download.
 */
@HiltViewModel
class ExportLedgerViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val exportRepository: LedgerExportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ExportUiState>(ExportUiState.Loading)
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    /** The pending temp artifact — VM-private, never in UI state. */
    private var pendingArtifact: LedgerExportArtifact? = null
    private var saveSeq = 0
    private var expiredMidLoad = false

    init {
        loadAccounts()
        viewModelScope.launch {
            // Process death mid-download leaves an orphan temp CSV —
            // sweep it on entry so a stale artifact can never surface
            // for a later session/user.
            runCatching { exportRepository.discardOrphans() }
        }
    }

    fun retry() = loadAccounts()

    private fun loadAccounts() {
        viewModelScope.launch {
            _state.value = ExportUiState.Loading
            try {
                val accounts = accountRepository.list()
                _state.value = ExportUiState.Ready(accounts = accounts)
            } catch (e: SessionEndedException) {
                expiredMidLoad = true
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    expiredMidLoad = true
                    return@launch
                }
                _state.value = ExportUiState.Error(e.error.toExportError())
            }
        }
    }

    /** `null` selects the whole-ledger scope — no `accountId` is sent. */
    fun onScopeSelected(accountId: String?) {
        (_state.value as? ExportUiState.Ready)?.let {
            if (it.phase == ExportUiState.Phase.Idle) {
                _state.value = it.copy(
                    selectedAccountId = accountId,
                    error = null,
                    saved = false,
                )
            }
        }
    }

    /**
     * Download the export — guarded by [ExportUiState.Phase.Idle] so a
     * double-tap can never issue a second GET.
     */
    fun export() {
        val form = _state.value as? ExportUiState.Ready ?: return
        if (form.phase != ExportUiState.Phase.Idle) return
        _state.value = form.copy(
            phase = ExportUiState.Phase.Downloading,
            error = null,
            saved = false,
        )
        viewModelScope.launch {
            try {
                val artifact = exportRepository.download(form.selectedAccountId)
                pendingArtifact = artifact
                val current = _state.value as? ExportUiState.Ready
                if (current == null) {
                    // Form gone (session boundary while downloading) —
                    // the artifact must not outlive its session.
                    runCatching { exportRepository.discard(artifact) }
                    pendingArtifact = null
                    return@launch
                }
                _state.value = current.copy(
                    phase = ExportUiState.Phase.AwaitingDestination,
                    pendingFileName = artifact.suggestedFileName,
                    saveRequest = SaveRequest(++saveSeq, artifact.suggestedFileName),
                )
            } catch (e: SessionEndedException) {
                expiredMidLoad = true
                restoreIdle()
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    expiredMidLoad = true
                    restoreIdle()
                    return@launch
                }
                restoreIdle(error = e.error.toExportError())
            }
        }
    }

    /** System picker returned a destination — stream the artifact into it. */
    fun onDestinationChosen(uri: String) {
        val form = _state.value as? ExportUiState.Ready ?: return
        val artifact = pendingArtifact ?: return
        _state.value = form.copy(
            phase = ExportUiState.Phase.Saving,
            saveRequest = null,
            error = null,
        )
        viewModelScope.launch {
            try {
                exportRepository.save(artifact, uri)
                pendingArtifact = null
                (_state.value as? ExportUiState.Ready)?.let {
                    _state.value = it.copy(
                        phase = ExportUiState.Phase.Idle,
                        pendingFileName = null,
                        saved = true,
                    )
                }
            } catch (e: ExportSaveException) {
                pendingArtifact = null
                restoreIdle(error = ExportError.SaveFailed)
            } catch (e: SessionEndedException) {
                pendingArtifact = null
                expiredMidLoad = true
                restoreIdle()
            }
        }
    }

    /** Picker cancelled — not an error; clean up and return to Idle. */
    fun onDestinationCancelled() {
        val artifact = pendingArtifact
        pendingArtifact = null
        (_state.value as? ExportUiState.Ready)?.let {
            _state.value = it.copy(
                phase = ExportUiState.Phase.Idle,
                pendingFileName = null,
                saveRequest = null,
            )
        }
        if (artifact != null) {
            viewModelScope.launch { exportRepository.discard(artifact) }
        }
    }

    /** Re-runs the aborted account load after a same-user resume. */
    fun onVisible() {
        if (expiredMidLoad) {
            expiredMidLoad = false
            retry()
        }
    }

    override fun onCleared() {
        // Never let a temp artifact outlive its owning screen — the
        // delete is a cheap metadata op; blocking briefly on the
        // teardown path is safer than leaking financial bytes.
        pendingArtifact?.let { runCatching { runBlocking { exportRepository.discard(it) } } }
        pendingArtifact = null
    }

    private fun restoreIdle(error: ExportError? = null) {
        (_state.value as? ExportUiState.Ready)?.let {
            _state.value = it.copy(
                phase = ExportUiState.Phase.Idle,
                pendingFileName = null,
                saveRequest = null,
                error = error,
            )
        }
    }

    private fun AppError.toExportError(): ExportError = when (this) {
        AppError.NetworkUnavailable -> ExportError.Network
        AppError.NotFound -> ExportError.AccountUnavailable
        is AppError.Server -> ExportError.Server
        else -> ExportError.Generic
    }
}
