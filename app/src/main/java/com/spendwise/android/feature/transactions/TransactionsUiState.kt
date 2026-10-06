package com.spendwise.android.feature.transactions

import com.spendwise.android.core.model.Account
import com.spendwise.android.core.model.Transaction
import com.spendwise.android.core.model.TransactionProvenance
import com.spendwise.android.core.model.TransactionType
import java.time.LocalDate

/**
 * The three server-side filters (BACKEND-CONTRACT §11) plus the two
 * honest client-side views: [CategorySelection.Uncategorized] matches
 * null-category rows locally, and [provenance] filters the already-
 * fetched list by `sourceRef` (PRODUCT §19 — declared client-side, never
 * implied as a server filter).
 */
data class TransactionFilters(
    val accountId: String? = null,
    val type: TransactionType? = null,
    val category: CategorySelection = CategorySelection.All,
    val provenance: TransactionProvenance? = null,
) {
    val isEmpty: Boolean
        get() = accountId == null && type == null &&
            category == CategorySelection.All && provenance == null

    /** The category code that goes on the wire, if the selection is one. */
    val serverCategory: String?
        get() = (category as? CategorySelection.Code)?.code
}

sealed interface CategorySelection {
    data object All : CategorySelection

    /** Client-side: matches transactions with a null category. */
    data object Uncategorized : CategorySelection

    data class Code(val code: String) : CategorySelection
}

/** Options behind the filter chips — loaded once per session. */
data class FilterOptions(
    val accounts: List<Account> = emptyList(),
    val categories: List<String> = emptyList(),
)

/** Transactions list state (S09, PRODUCT §19). */
sealed interface TransactionsUiState {

    data object Loading : TransactionsUiState

    /** Zero transactions and no active filters — the entry-point state. */
    data object Empty : TransactionsUiState

    data class Content(
        /** Display rows after the client-side provenance/uncategorized pass. */
        val items: List<Transaction>,
        /** `accountId → name` for row labels. */
        val accountNames: Map<String, String>,
        val filters: TransactionFilters,
        val isRefreshing: Boolean = false,
        /** MIN-1 pattern: refresh failure over live content stays inline. */
        val refreshError: TransactionsError? = null,
    ) : TransactionsUiState {
        /** True when filters produced no rows — drives "No matches". */
        val noMatches: Boolean get() = items.isEmpty()
    }

    data class Error(val kind: TransactionsError) : TransactionsUiState
}

enum class TransactionsError { Network, Server, Generic }

/** Transaction detail state (S10 — read-only, derived from list data). */
sealed interface TransactionDetailUiState {

    data object Loading : TransactionDetailUiState

    data class Content(
        val transaction: Transaction,
        val accountName: String?,
        val isRefreshing: Boolean = false,
    ) : TransactionDetailUiState

    data object NotFound : TransactionDetailUiState

    data class Error(val kind: TransactionsError) : TransactionDetailUiState
}

/** Create-transaction form state (S11). */
data class CreateTransactionUiState(
    /** Expense default per P3A. */
    val type: TransactionType = TransactionType.EXPENSE,
    val accountId: String? = null,
    val accounts: List<Account> = emptyList(),
    val categories: List<String> = emptyList(),
    val optionsLoading: Boolean = true,
    val optionsError: Boolean = false,
    val amount: String = "",
    /** Selected server category code; null = "No category". */
    val category: String? = null,
    /**
     * User-selected transaction date (M11). Defaults to the device's
     * local calendar date — the user's "today"; the wire value is the
     * exact [LocalDate], never device-midnight-derived.
     */
    val occurredOn: LocalDate = LocalDate.now(),
    /** Optional categorization hint (wire `description` — never persisted). */
    val hint: String = "",
    val amountError: AmountError? = null,
    val accountError: AccountError? = null,
    val formError: CreateTxnFormError? = null,
    val isSubmitting: Boolean = false,
) {
    val selectedAccount: Account? get() = accounts.firstOrNull { it.id == accountId }

    /** True only when account+category options loaded and ≥1 account exists. */
    val ready: Boolean get() = !optionsLoading && !optionsError && accounts.isNotEmpty()

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
        data class Server(val message: String) : AccountError
    }

    enum class CreateTxnFormError {
        /** Ambiguous failure — the write may have landed server-side. */
        Ambiguous,
        /** Selected account 404'd — picker data is stale. */
        AccountUnavailable,
        Server,
        Generic,
    }

    companion object {
        // Backend @Digits(integer = 17, fraction = 2) + @Size(255) hint.
        const val MAX_INTEGER_DIGITS = 17
        const val MAX_FRACTION_DIGITS = 2
        const val MAX_HINT_LENGTH = 255
    }
}
