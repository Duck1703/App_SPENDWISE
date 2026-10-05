package com.spendwise.android.feature.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.model.TransactionType
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.data.repository.AccountRepository
import com.spendwise.android.data.repository.CategoryRepository
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
 * Create transaction (S11, PRODUCT §20). Client mirrors backend limits
 * (@DecimalMin(0, exclusive) / @Digits(17,2) amount, required account)
 * so most mistakes surface inline; server `fields{}` errors still win.
 *
 * Financial safety (§44/§45): `POST /api/transactions` has NO idempotency
 * contract — an ambiguous transport failure may have recorded the entry.
 * The submit lock prevents double-taps, there is no automatic retry at
 * any layer, and the ambiguous copy says "couldn't confirm" rather than
 * claiming nothing happened. A session expiry mid-submit releases the
 * lock — the preserved draft is resubmitted only by the user.
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class CreateTransactionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    /** Optional account prefill (account-detail "Add transaction"). */
    private val prefillAccountId: String? = savedStateHandle["accountId"]

    private val _state = MutableStateFlow(CreateTransactionUiState(accountId = prefillAccountId))
    val state: StateFlow<CreateTransactionUiState> = _state.asStateFlow()

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
                val accounts = accountRepository.list()
                val categories = runCatching { categoryRepository.list() }.getOrDefault(emptyList())
                _state.value = _state.value.copy(
                    accounts = accounts,
                    categories = categories,
                    accountId = _state.value.accountId
                        ?.takeIf { id -> accounts.any { it.id == id } }
                        ?: accounts.firstOrNull()?.id,
                    optionsLoading = false,
                )
            } catch (e: SessionEndedException) {
                // Dead-generation write — the session ended between
                // dispatch and response. Same resume contract as the
                // 401 path: arm the retry flag, release the skeleton.
                optionsExpired = true
                _state.value = _state.value.copy(optionsLoading = false)
                return@launch
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    // Overlay is being pushed over this screen — the
                    // draft and state are preserved; same-user resume
                    // re-runs options loading via onVisible().
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

    fun onTypeChange(value: TransactionType) {
        if (value != TransactionType.INCOME && value != TransactionType.EXPENSE) return
        _state.value = _state.value.copy(type = value)
    }

    fun onAccountChange(accountId: String) {
        _state.value = _state.value.copy(accountId = accountId, accountError = null, formError = null)
    }

    fun onAmountChange(value: String) {
        _state.value = _state.value.copy(amount = value, amountError = null, formError = null)
    }

    fun onCategoryChange(code: String?) {
        _state.value = _state.value.copy(category = code, formError = null)
    }

    fun onHintChange(value: String) {
        _state.value = _state.value.copy(
            hint = value.take(CreateTransactionUiState.MAX_HINT_LENGTH),
            formError = null,
        )
    }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return

        val accountError = if (current.accountId == null) {
            CreateTransactionUiState.AccountError.Required
        } else {
            null
        }
        val (amount, amountError) = parseAmount(current.amount)
        if (accountError != null || amountError != null) {
            _state.value = current.copy(accountError = accountError, amountError = amountError)
            return
        }

        val account = current.selectedAccount ?: run {
            _state.value = current.copy(
                accountError = CreateTransactionUiState.AccountError.Required,
            )
            return
        }

        _state.value = current.copy(
            isSubmitting = true,
            amountError = null,
            accountError = null,
            formError = null,
        )
        viewModelScope.launch {
            try {
                transactionRepository.create(
                    accountId = account.id,
                    type = current.type,
                    amount = Money(amount = checkNotNull(amount), currency = CurrencyCode(account.balance.currency.code)),
                    category = current.category,
                    hint = current.hint,
                )
                _events.send(Event.Created)
            } catch (e: SessionEndedException) {
                // Dead-generation publish — the response already landed
                // (the repository throws only after it), so the entry
                // almost certainly exists server-side. Release the
                // submit lock and surface the honest ambiguous copy;
                // the preserved draft is resubmitted only by the user.
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    formError = CreateTransactionUiState.CreateTxnFormError.Ambiguous,
                )
            } catch (e: AppErrorException) {
                if (e.error is AppError.SessionExpired) {
                    // Expired overlay pushed; release the lock so the
                    // preserved draft can be resubmitted — by the user —
                    // after a same-user resume. Never auto-replay a
                    // non-idempotent financial POST.
                    _state.value = _state.value.copy(isSubmitting = false)
                    return@launch
                }
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    amountError = e.error.fieldMessage("amount")
                        ?.let(CreateTransactionUiState.AmountError::Server),
                    accountError = e.error.fieldMessage("accountId")
                        ?.let(CreateTransactionUiState.AccountError::Server),
                    formError = when (val error = e.error) {
                        is AppError.NotFound -> {
                            // The chosen account vanished — refresh the
                            // picker so the stale entry drops out.
                            loadOptions()
                            CreateTransactionUiState.CreateTxnFormError.AccountUnavailable
                        }
                        is AppError.Validation ->
                            if (error.fields.keys.any { it in setOf("amount", "accountId") }) {
                                null
                            } else {
                                // currency/category/description/type —
                                // picker-locked or optional fields surface
                                // as the form-level error, honestly.
                                CreateTransactionUiState.CreateTxnFormError.Generic
                            }
                        AppError.NetworkUnavailable ->
                            CreateTransactionUiState.CreateTxnFormError.Ambiguous
                        is AppError.Server -> CreateTransactionUiState.CreateTxnFormError.Server
                        else -> CreateTransactionUiState.CreateTxnFormError.Generic
                    },
                )
            }
        }
    }

    /**
     * Amount rules mirror the backend: required, strictly positive,
     * ≤2 fraction digits, ≤17 integer digits. Decimal input accepts a
     * comma or dot separator (normalized to `.`); whitespace trims.
     * Parsing is [BigDecimal]-exact — no Float/Double anywhere.
     */
    private fun parseAmount(
        raw: String,
    ): Pair<BigDecimal?, CreateTransactionUiState.AmountError?> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null to CreateTransactionUiState.AmountError.Required
        val value = trimmed.replace(',', '.').toBigDecimalOrNull()
            ?: return null to CreateTransactionUiState.AmountError.Invalid
        return when {
            value <= BigDecimal.ZERO -> null to CreateTransactionUiState.AmountError.NotPositive
            value.scale() > CreateTransactionUiState.MAX_FRACTION_DIGITS ->
                null to CreateTransactionUiState.AmountError.TooPrecise
            value.precision() - value.scale() > CreateTransactionUiState.MAX_INTEGER_DIGITS ->
                null to CreateTransactionUiState.AmountError.TooLarge
            else -> value to null
        }
    }

    private fun AppError.fieldMessage(field: String): String? =
        (this as? AppError.Validation)?.fields?.get(field)
}
