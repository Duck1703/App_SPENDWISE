package com.spendwise.android.feature.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.AccountRepository
import com.spendwise.android.data.repository.ImportRepository
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.data.repository.StatementUnreadableException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Import flow VM (S12–S14, PRODUCT §23–25, PD-012).
 *
 * - **Synchronous + modal**: `submit()` flips [ImportUiState.Form.isUploading]
 *   — the destination renders the blocking processing surface; there is
 *   no progress % (the contract gives none) and no cancel/back.
 * - **No auto-retry** of the non-idempotent upload — the server dedups
 *   by `sourceRef`, so a *manual* re-upload of the same file is safe;
 *   that is exactly what the honest copy says.
 * - **Ambiguous outcome**: an interrupted upload (network drop, 5xx
 *   mid-loop commit, session expiry, dead generation) may have
 *   partially committed — failure states restore the form with
 *   [ImportUiState.Form.ambiguousNotice] instead of claiming failure.
 * - **EXT-MAJOR-1 resume contract**: session expiry mid-anything arms
 *   `expiredMidLoad`; `onVisible()` on a same-user resume re-runs the
 *   account load — never an automatic re-upload.
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class ImportViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val importRepository: ImportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Loading)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    init {
        loadAccounts()
    }

    fun retry() = loadAccounts()

    private fun loadAccounts() {
        viewModelScope.launch {
            _state.value = ImportUiState.Loading
            try {
                val accounts = accountRepository.list()
                _state.value = if (accounts.isEmpty()) {
                    ImportUiState.Empty
                } else {
                    ImportUiState.Form(
                        accounts = accounts,
                        selectedAccountId = accounts.first().id,
                        file = null,
                    )
                }
            } catch (e: SessionEndedException) {
                expiredMidLoad = true
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    expiredMidLoad = true
                    return@launch
                }
                _state.value = ImportUiState.Error(e.error.toImportError())
            }
        }
    }

    fun onAccountSelected(accountId: String) {
        (_state.value as? ImportUiState.Form)?.let {
            _state.value = it.copy(selectedAccountId = accountId, error = null)
        }
    }

    /** Picker callback (uri string form) — extension + size gates. */
    fun onFilePicked(uri: String) {
        val form = _state.value as? ImportUiState.Form ?: return
        _state.value = form.copy(error = null)
        // Latest-pick-wins: a slow provider response for an earlier pick
        // must never overwrite a newer selection.
        val seq = ++pickSeq
        viewModelScope.launch {
            val current = _state.value as? ImportUiState.Form ?: return@launch
            try {
                val file = importRepository.describe(uri)
                if (seq != pickSeq) return@launch
                val latest = _state.value as? ImportUiState.Form ?: return@launch
                val name = file.displayName.lowercase()
                when {
                    !name.endsWith(".csv") && !name.endsWith(".pdf") ->
                        _state.value = latest.copy(error = ImportError.UnsupportedFile)
                    file.sizeBytes > MAX_FILE_BYTES ->
                        _state.value = latest.copy(error = ImportError.FileTooLarge)
                    else ->
                        _state.value = latest.copy(file = file, error = null)
                }
            } catch (e: SessionEndedException) {
                expiredMidLoad = true
            } catch (e: AppErrorException) {
                if (seq != pickSeq) return@launch
                if (e.error is AppError.SessionExpired) {
                    expiredMidLoad = true
                    return@launch
                }
                (_state.value as? ImportUiState.Form)?.let {
                    _state.value = it.copy(error = e.error.toImportError())
                }
            } catch (e: Exception) {
                if (seq != pickSeq) return@launch
                (_state.value as? ImportUiState.Form)?.let {
                    _state.value = it.copy(error = ImportError.Generic)
                }
            }
        }
    }

    private var pickSeq = 0

    fun onFileCleared() {
        pickSeq++ // an in-flight pick must not resurrect a cleared file
        (_state.value as? ImportUiState.Form)?.let {
            _state.value = it.copy(file = null, error = null)
        }
    }

    /**
     * Upload — guarded by [ImportUiState.Form.isUploading] so a
     * double-tap can never issue a second POST.
     */
    fun submit() {
        val form = _state.value as? ImportUiState.Form ?: return
        if (form.isUploading) return
        val accountId = form.selectedAccountId ?: return
        val file = form.file ?: return
        _state.value = form.copy(isUploading = true, error = null)
        viewModelScope.launch {
            val current = _state.value as? ImportUiState.Form ?: return@launch
            try {
                val batch = importRepository.upload(accountId, file)
                _state.value = ImportUiState.Result(batch)
            } catch (e: StatementUnreadableException) {
                _state.value = current.copy(
                    isUploading = false,
                    error = ImportError.Generic,
                )
            } catch (e: SessionEndedException) {
                // Dead-generation — outcome unknown; restore the form
                // with the ambiguous banner and arm the resume retry.
                expiredMidLoad = true
                _state.value = current.copy(
                    isUploading = false,
                    ambiguousNotice = true,
                )
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    expiredMidLoad = true
                    _state.value = current.copy(
                        isUploading = false,
                        ambiguousNotice = true,
                    )
                    return@launch
                }
                _state.value = current.copy(
                    isUploading = false,
                    error = e.error.toImportError(),
                    ambiguousNotice = e.error.isAmbiguousOutcome(),
                )
            }
        }
    }

    private var expiredMidLoad = false

    /** Re-runs the aborted account load after a same-user resume. */
    fun onVisible() {
        if (expiredMidLoad) {
            expiredMidLoad = false
            retry()
        }
    }

    private fun AppError.toImportError(): ImportError = when (this) {
        AppError.NetworkUnavailable -> ImportError.Network
        AppError.BadRequest -> ImportError.UnsupportedFile
        AppError.NotFound -> ImportError.AccountUnavailable
        is AppError.Server -> ImportError.Server
        else -> ImportError.Generic
    }

    /**
     * Outcomes where the server may have committed rows before the
     * failure surfaced — the copy must say "check the ledger" rather
     * than claim nothing happened.
     */
    private fun AppError.isAmbiguousOutcome(): Boolean = when (this) {
        AppError.NetworkUnavailable -> true
        is AppError.Server -> true
        is AppError.Unknown -> true
        else -> false
    }

    private companion object {
        /** ~1 MB — the Boot default file cap the backend inherits. */
        const val MAX_FILE_BYTES = 1_048_576L
    }
}
