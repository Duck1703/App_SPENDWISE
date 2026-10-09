package com.spendwise.android.feature.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.android.core.model.Account
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.model.Transaction
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
import java.time.LocalDate
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
 *
 * M20 (PRODUCT §21) — offsetting entry: when the route carries
 * `offsetTransactionId`, options loading also resolves that source row
 * and prefills the INVERSE type plus the same account/amount/category
 * (currency follows the locked account). The source must still be an
 * INCOME/EXPENSE row on a non-archived account — the detail screen
 * already hides the action otherwise, and this guard stays honest if
 * the row or account changed between screens. The post is an ordinary
 * create: the original is untouched (append-only) and every draft /
 * submit-lock / ambiguous-failure guarantee above applies unchanged.
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

    /** M20: source row id when this form is an offsetting entry. */
    private val offsetTransactionId: String? = savedStateHandle["offsetTransactionId"]

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
                // M19 (REQ-BE-006): archived accounts leave entry
                // pickers; ledger/history surfaces still see them.
                val accounts = accountRepository.list().filterNot { it.archived }
                val categories = runCatching { categoryRepository.list() }.getOrDefault(emptyList())
                // The §21 prefill applies ONCE — a later loadOptions
                // re-run (account-404 picker refresh, retry) refreshes
                // options but must never clobber the user's edits.
                val alreadyPrefilled = _state.value.offsetSourceId != null
                val offsetSource = if (offsetTransactionId != null && !alreadyPrefilled) {
                    transactionRepository.findById(offsetTransactionId)
                } else {
                    null
                }
                if (offsetSource != null && !offsetSource.isOffsettableOn(accounts)) {
                    // The source turned TRANSFER_* / UNKNOWN, or its
                    // account is gone/archived — refuse the prefill
                    // honestly rather than minting a wrong entry.
                    _state.value = _state.value.copy(
                        accounts = accounts,
                        categories = categories,
                        optionsLoading = false,
                        optionsError = true,
                    )
                    return@launch
                }
                val base = _state.value.copy(
                    accounts = accounts,
                    categories = categories,
                    optionsLoading = false,
                )
                _state.value = if (offsetSource == null) {
                    base.copy(
                        // A stale accountId (picker refresh, archived
                        // offset source) drops out honestly — never a
                        // silent substitute.
                        accountId = _state.value.accountId
                            ?.takeIf { id -> accounts.any { it.id == id } }
                            ?: accounts.firstOrNull()?.id,
                    )
                } else {
                    // §21 prefill: inverse type, same account/amount/
                    // category — currency follows the locked account.
                    // A null source category prefills "No category".
                    base.copy(
                        accountId = offsetSource.accountId,
                        type = offsetSource.inverseType,
                        amount = offsetSource.amount.amount.toPlainString(),
                        category = offsetSource.category,
                        offsetSourceId = offsetSource.id,
                    )
                }
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

    fun onDateChange(value: LocalDate) {
        _state.value = _state.value.copy(occurredOn = value, formError = null)
    }

    fun onDescriptionChange(value: String) {
        _state.value = _state.value.copy(
            description = value.cappedDescriptionLength(),
            formError = null,
        )
    }

    // take() counts UTF-16 code units; a cap landing between the halves of a
    // surrogate pair would leave a dangling high surrogate on the wire, which
    // the backend cannot store - drop the pair's orphaned half instead.
    private fun String.cappedDescriptionLength(): String {
        val capped = take(CreateTransactionUiState.MAX_DESCRIPTION_LENGTH)
        return if (capped.lastOrNull()?.isHighSurrogate() == true) capped.dropLast(1) else capped
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
                    occurredOn = current.occurredOn,
                    category = current.category,
                    description = current.description,
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
                                // currency/category/description/type/
                                // occurredOn — picker-locked or optional
                                // fields surface as the form-level error,
                                // honestly.
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

    /** §21: the offset is the arithmetic inverse — INCOME ↔ EXPENSE. */
    private val Transaction.inverseType: TransactionType
        get() = if (type == TransactionType.INCOME) {
            TransactionType.EXPENSE
        } else {
            TransactionType.INCOME
        }

    /**
     * §21 eligibility re-checked at prefill time: only INCOME/EXPENSE
     * rows offset (TRANSFER_* would un-pair an atomic transfer) and the
     * account must still be a live picker entry (non-archived).
     */
    private fun Transaction.isOffsettableOn(accounts: List<Account>): Boolean =
        (type == TransactionType.INCOME || type == TransactionType.EXPENSE) &&
            accounts.any { it.id == accountId }
}
