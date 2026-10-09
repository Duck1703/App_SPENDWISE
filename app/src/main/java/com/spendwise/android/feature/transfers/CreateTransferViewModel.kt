package com.spendwise.android.feature.transfers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.android.core.model.Account
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.AccountRepository
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.data.repository.TransactionRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Create transfer (M12/REQ-BE-003, PRODUCT §22). Moves money between
 * two of the caller's own same-currency accounts via
 * `POST /api/transfers` — one atomic server write producing the paired
 * TRANSFER_OUT/TRANSFER_IN legs. The client never simulates the pair.
 *
 * Financial safety mirrors transaction create (PRODUCT §44/§45): no
 * idempotency contract on the endpoint, so the submit lock prevents
 * double-taps, nothing retries automatically, an ambiguous transport
 * failure surfaces "couldn't confirm" copy, and a mid-submit session
 * expiry releases the lock — the preserved draft is resubmitted only
 * by the user.
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class CreateTransferViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    /** Optional source-account prefill (account-detail "Transfer"). */
    private val prefillSourceId: String? = savedStateHandle["fromAccountId"]

    private val _state = MutableStateFlow(
        CreateTransferUiState(sourceAccountId = prefillSourceId),
    )
    val state: StateFlow<CreateTransferUiState> = _state.asStateFlow()

    sealed interface Event {
        data object Created : Event
    }

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadOptions()
    }

    fun loadOptions() {
        viewModelScope.launch {
            _state.value = _state.value.copy(optionsLoading = true, optionsError = false)
            try {
                // M19 (REQ-BE-006): archived accounts leave pickers.
                val accounts = accountRepository.list().filterNot { it.archived }
                val sourceId = _state.value.sourceAccountId
                    ?.takeIf { id -> accounts.any { it.id == id } }
                    ?: accounts.firstOrNull()?.id
                _state.value = _state.value.copy(
                    accounts = accounts,
                    sourceAccountId = sourceId,
                    // A stale destination equal to the resolved source is
                    // no longer valid (prefill fallback / reload after a
                    // 404) — drop it the same way the picker does.
                    destinationAccountId = _state.value.destinationAccountId
                        ?.takeIf { id -> id != sourceId && accounts.any { it.id == id } },
                    optionsLoading = false,
                )
            } catch (e: SessionEndedException) {
                optionsExpired = true
                _state.value = _state.value.copy(optionsLoading = false)
                return@launch
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    optionsExpired = true
                    _state.value = _state.value.copy(optionsLoading = false)
                    return@launch
                }
                _state.value = _state.value.copy(optionsLoading = false, optionsError = true)
            }
        }
    }

    private var optionsExpired = false

    /** Re-runs an aborted options load after a same-user resume. */
    fun onVisible() {
        if (optionsExpired) {
            optionsExpired = false
            loadOptions()
        }
    }

    fun onSourceChange(accountId: String) {
        _state.value = _state.value.copy(
            sourceAccountId = accountId,
            // A destination equal to the new source is no longer valid —
            // the destination picker already excludes it, so clear any
            // stale equal selection.
            destinationAccountId = _state.value.destinationAccountId
                ?.takeIf { it != accountId },
            sourceError = null,
            destinationError = null,
            formError = null,
        )
    }

    fun onDestinationChange(accountId: String) {
        if (accountId == _state.value.sourceAccountId) return
        _state.value = _state.value.copy(
            destinationAccountId = accountId,
            destinationError = null,
            formError = null,
        )
    }

    fun onAmountChange(value: String) {
        _state.value = _state.value.copy(amount = value, amountError = null, formError = null)
    }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return

        val source = current.sourceAccount
        val destination = current.destinationAccount
        val sourceError = if (source == null) {
            CreateTransferUiState.AccountError.Required
        } else {
            null
        }
        val destinationError = when {
            destination == null -> CreateTransferUiState.AccountError.Required
            destination.id == source?.id -> CreateTransferUiState.AccountError.SameAsSource
            // Clear rejection rather than a generic server error — the
            // backend would 400 the pair anyway (currency pairing is a
            // domain invariant, PRODUCT §22).
            destination.balance.currency.code != source?.balance?.currency?.code ->
                CreateTransferUiState.AccountError.CrossCurrency
            else -> null
        }
        val (amount, amountError) = parseAmount(current.amount)
        if (sourceError != null || destinationError != null || amountError != null) {
            _state.value = current.copy(
                sourceError = sourceError,
                destinationError = destinationError,
                amountError = amountError,
            )
            return
        }
        checkNotNull(source)
        checkNotNull(destination)

        _state.value = current.copy(
            isSubmitting = true,
            sourceError = null,
            destinationError = null,
            amountError = null,
            formError = null,
        )
        viewModelScope.launch {
            try {
                transactionRepository.transfer(
                    sourceAccountId = source.id,
                    destinationAccountId = destination.id,
                    amount = Money(
                        amount = checkNotNull(amount),
                        currency = CurrencyCode(source.balance.currency.code),
                    ),
                )
                _events.send(Event.Created)
            } catch (e: SessionEndedException) {
                // Dead-generation publish — the response landed, so the
                // pair almost certainly exists server-side. Release the
                // lock and surface the honest ambiguous copy.
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    formError = CreateTransferUiState.FormError.Ambiguous,
                )
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    _state.value = _state.value.copy(isSubmitting = false)
                    return@launch
                }
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    amountError = e.error.fieldMessage("amount")
                        ?.let(CreateTransferUiState.AmountError::Server),
                    sourceError = e.error.fieldMessage("sourceAccountId")
                        ?.let(CreateTransferUiState.AccountError::Server),
                    destinationError = e.error.fieldMessage("destinationAccountId")
                        ?.let(CreateTransferUiState.AccountError::Server),
                    formError = when (val error = e.error) {
                        is AppError.NotFound -> {
                            // A picked account vanished — refresh the
                            // pickers so the stale entry drops out.
                            loadOptions()
                            CreateTransferUiState.FormError.AccountUnavailable
                        }
                        is AppError.Validation ->
                            if (error.fields.keys.any {
                                    it in setOf(
                                        "amount",
                                        "sourceAccountId",
                                        "destinationAccountId",
                                    )
                                }
                            ) {
                                null
                            } else {
                                // currency etc. — picker-locked fields
                                // surface as the form-level error.
                                CreateTransferUiState.FormError.Generic
                            }
                        AppError.NetworkUnavailable ->
                            CreateTransferUiState.FormError.Ambiguous
                        is AppError.Server -> CreateTransferUiState.FormError.Server
                        else -> CreateTransferUiState.FormError.Generic
                    },
                )
            }
        }
    }

    /**
     * Amount rules mirror the backend: required, strictly positive,
     * ≤2 fraction digits, ≤17 integer digits. Comma or dot separator,
     * trimmed whitespace, [BigDecimal]-exact — no Float/Double.
     */
    private fun parseAmount(
        raw: String,
    ): Pair<BigDecimal?, CreateTransferUiState.AmountError?> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null to CreateTransferUiState.AmountError.Required
        val value = trimmed.replace(',', '.').toBigDecimalOrNull()
            ?: return null to CreateTransferUiState.AmountError.Invalid
        return when {
            value <= BigDecimal.ZERO -> null to CreateTransferUiState.AmountError.NotPositive
            value.scale() > CreateTransferUiState.MAX_FRACTION_DIGITS ->
                null to CreateTransferUiState.AmountError.TooPrecise
            value.precision() - value.scale() > CreateTransferUiState.MAX_INTEGER_DIGITS ->
                null to CreateTransferUiState.AmountError.TooLarge
            else -> value to null
        }
    }

    private fun AppError.fieldMessage(field: String): String? =
        (this as? AppError.Validation)?.fields?.get(field)
}

/** Create-transfer form state (M12). */
data class CreateTransferUiState(
    val sourceAccountId: String? = null,
    val destinationAccountId: String? = null,
    val accounts: List<Account> = emptyList(),
    val optionsLoading: Boolean = true,
    val optionsError: Boolean = false,
    val amount: String = "",
    val sourceError: AccountError? = null,
    val destinationError: AccountError? = null,
    val amountError: AmountError? = null,
    val formError: FormError? = null,
    val isSubmitting: Boolean = false,
) {
    val sourceAccount: Account? get() = accounts.firstOrNull { it.id == sourceAccountId }
    val destinationAccount: Account? get() = accounts.firstOrNull { it.id == destinationAccountId }

    /** The destination picker never offers the source account. */
    val destinationOptions: List<Account>
        get() = accounts.filter { it.id != sourceAccountId }

    /** A transfer needs at least two accounts. */
    val hasEnoughAccounts: Boolean get() = accounts.size >= 2

    sealed interface AmountError {
        data object Required : AmountError
        data object Invalid : AmountError
        data object NotPositive : AmountError
        data object TooPrecise : AmountError
        data object TooLarge : AmountError
        data class Server(val message: String) : AmountError
    }

    sealed interface AccountError {
        data object Required : AccountError
        /** Same account picked on both ends — the server also rejects it. */
        data object SameAsSource : AccountError
        /** Currencies differ — the pair would be a server-side 400 anyway. */
        data object CrossCurrency : AccountError
        data class Server(val message: String) : AccountError
    }

    enum class FormError {
        /** Ambiguous failure — the write may have landed server-side. */
        Ambiguous,

        /** A picked account 404'd — picker data is stale. */
        AccountUnavailable,
        Server,
        Generic,
    }

    companion object {
        // Backend @Digits(integer = 17, fraction = 2).
        const val MAX_INTEGER_DIGITS = 17
        const val MAX_FRACTION_DIGITS = 2
    }
}
