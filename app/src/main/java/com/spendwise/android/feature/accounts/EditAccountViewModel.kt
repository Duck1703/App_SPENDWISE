package com.spendwise.android.feature.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.AccountRepository
import com.spendwise.android.data.repository.SessionEndedException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Account edit (M19, REQ-BE-006): loads the account once, prefills name
 * and archive state, and PATCHes a **sparse** body — only fields that
 * differ from the loaded account are sent. `type`, `currency`,
 * `openingBalance`, and `balance` are immutable identity and are never
 * sent.
 *
 * Write safety mirrors every other mutation screen: submit lock, no
 * auto-retry, ambiguous-outcome copy for dropped-mid-write cases, a
 * truthful 409 conflict state (never silently retrying the loser), and
 * a stale session-expired load re-runs on the next resume.
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class EditAccountViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val accountId: String = checkNotNull(savedStateHandle["accountId"]) {
        "EditAccountViewModel requires an accountId nav argument"
    }

    private val _state = MutableStateFlow<EditAccountState>(EditAccountState.Loading)
    val state: StateFlow<EditAccountState> = _state.asStateFlow()

    sealed interface Event {
        data object Saved : Event
    }

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var expiredMidLoad = false
    private var loadSerial = 0

    init {
        load()
    }

    fun refresh() = load()

    private fun load() {
        val serial = ++loadSerial
        viewModelScope.launch {
            val current = _state.value
            _state.value = if (current is EditAccountState.Form) {
                current.copy(isSubmitting = false, formError = null)
            } else {
                EditAccountState.Loading
            }
            try {
                // refresh=true: the form must open on the server's truth —
                // a cached pre-PATCH row would silently resurrect a stale
                // name/archive state into the user's next edit.
                val account = accountRepository.findById(accountId, refresh = true)
                if (serial != loadSerial) return@launch
                _state.value = EditAccountState.Form(
                    account = account,
                    name = account.name,
                    archived = account.archived,
                    nameError = null,
                    isSubmitting = false,
                    formError = null,
                )
            } catch (e: SessionEndedException) {
                expiredMidLoad = true
                if (serial != loadSerial) return@launch
                (current as? EditAccountState.Form)?.let { _state.value = it }
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    expiredMidLoad = true
                    if (serial != loadSerial) return@launch
                    (current as? EditAccountState.Form)?.let { _state.value = it }
                    return@launch
                }
                // A stale failure must not clobber newer content — M15 lesson.
                if (serial != loadSerial) return@launch
                _state.value = when (e.error) {
                    is AppError.NotFound -> EditAccountState.NotFound
                    AppError.NetworkUnavailable -> EditAccountState.Error(AccountsError.Network)
                    is AppError.Server -> EditAccountState.Error(AccountsError.Server)
                    else -> EditAccountState.Error(AccountsError.Generic)
                }
            }
        }
    }

    /** Re-runs an aborted load after a same-user resume pops the overlay. */
    fun onVisible() {
        if (expiredMidLoad) {
            expiredMidLoad = false
            load()
        }
    }

    private fun form(): EditAccountState.Form? = _state.value as? EditAccountState.Form

    fun onNameChange(value: String) {
        form()?.let {
            _state.value = it.copy(
                name = value.take(MAX_NAME_LENGTH),
                nameError = null,
                formError = null,
            )
        }
    }

    /** Flip the archived intent — the flag is applied by the same PATCH. */
    fun onArchivedChange(archived: Boolean) {
        form()?.let {
            _state.value = it.copy(archived = archived, formError = null)
        }
    }

    /**
     * `PATCH /{id}` — one user-confirmed call carrying only the changed
     * fields. `isSubmitting` blocks repeats; the write is never
     * auto-retried. A 409 means a concurrent write won — the form reloads
     * server truth instead of claiming the save; a 404 mid-save reloads
     * (the account may be gone); validation errors land on `name`.
     */
    fun save() {
        val form = form() ?: return
        if (form.isSubmitting || !form.hasChanges) return

        val trimmed = form.name.trim()
        if (trimmed.isEmpty()) {
            _state.value = form.copy(nameError = EditAccountState.Form.NameError.Required)
            return
        }
        val account = form.account
        _state.value = form.copy(
            name = trimmed,
            isSubmitting = true,
            nameError = null,
            formError = null,
        )
        viewModelScope.launch {
            try {
                accountRepository.update(
                    id = accountId,
                    name = trimmed.takeIf { it != account.name },
                    archived = form.archived.takeIf { it != account.archived },
                )
                _events.send(Event.Saved)
            } catch (e: SessionEndedException) {
                // Dead-generation publish — the PATCH already landed, so
                // the account almost certainly carries the edit.
                form()?.let {
                    _state.value = it.copy(
                        isSubmitting = false,
                        formError = EditAccountState.Form.FormError.Ambiguous,
                    )
                }
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    form()?.let { _state.value = it.copy(isSubmitting = false) }
                    return@launch
                }
                if (e.error is AppError.NotFound) {
                    _state.value = EditAccountState.Loading
                    load()
                    return@launch
                }
                form()?.let { current ->
                    _state.value = current.copy(
                        isSubmitting = false,
                        nameError = e.error.fieldMessage("name")
                            ?.let { EditAccountState.Form.NameError.Invalid },
                        formError = when (val error = e.error) {
                            AppError.NetworkUnavailable ->
                                EditAccountState.Form.FormError.Ambiguous
                            AppError.Conflict ->
                                EditAccountState.Form.FormError.Conflict
                            is AppError.Server -> EditAccountState.Form.FormError.Server
                            is AppError.Validation ->
                                if (error.fields.keys.any { it == "name" }) {
                                    null
                                } else {
                                    EditAccountState.Form.FormError.Generic
                                }
                            else -> EditAccountState.Form.FormError.Generic
                        },
                    )
                }
            }
        }
    }

    private fun AppError.fieldMessage(field: String): String? =
        (this as? AppError.Validation)?.fields?.get(field)

    private companion object {
        const val MAX_NAME_LENGTH = 255
    }
}
