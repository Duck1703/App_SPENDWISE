package com.spendwise.android.feature.transactions

import com.spendwise.android.core.model.Account
import com.spendwise.android.core.model.Transaction
import com.spendwise.android.core.model.TransactionProvenance
import com.spendwise.android.core.model.TransactionType
import java.time.LocalDate

/**
 * The filter set for the paged ledger read (M15/REQ-BE-007): every
 * selection is a real server parameter on `GET /api/transactions/page` —
 * including [CategorySelection.Uncategorized] (`uncategorized=true` →
 * null-category rows server-side) and [provenance] (mapped server-side
 * to the same `sourceRef` prefix rule). [from]/[to] bound the economic
 * `occurredOn` date inclusively (ISO `yyyy-MM-dd`); [query] is the M18
 * server-side note-search term (`q`). Nothing is filtered client-side
 * anymore: under pagination a local pass would hide matches on
 * not-yet-loaded pages.
 */
data class TransactionFilters(
    val accountId: String? = null,
    val type: TransactionType? = null,
    val category: CategorySelection = CategorySelection.All,
    val provenance: TransactionProvenance? = null,
    /** Inclusive `occurredOn >= from` bound (null = unbounded). */
    val from: LocalDate? = null,
    /** Inclusive `occurredOn <= to` bound (null = unbounded). */
    val to: LocalDate? = null,
    /**
     * M18: the applied note-search term — server-side `q` on the paged
     * endpoint (substring over persisted `description`). Kept on the
     * filter object so every M15 stale-query/load-more guard compares
     * it automatically; a query change is a filter change.
     */
    val query: String = "",
) {
    val isEmpty: Boolean
        get() = accountId == null && type == null &&
            category == CategorySelection.All && provenance == null &&
            from == null && to == null && query.isBlank()

    /** The category code that goes on the wire, if the selection is one. */
    val serverCategory: String?
        get() = (category as? CategorySelection.Code)?.code

    /** True when the wire `uncategorized` flag should be sent. */
    val uncategorizedOnly: Boolean
        get() = category == CategorySelection.Uncategorized

    /** True when a real date bound is set (at least one side). */
    val hasDateRange: Boolean
        get() = from != null || to != null

    /** The wire `q` — blank/whitespace-only never leaves the client. */
    val serverQuery: String?
        get() = query.trim().takeIf { it.isNotEmpty() }
}

/**
 * Category chip selection. [Uncategorized] is the wire
 * `uncategorized=true` server filter (null-category rows); [Code] sends
 * the category code; [All] sends neither. M15: all three are
 * server-side parameters on the paged endpoint.
 */
sealed interface CategorySelection {
    data object All : CategorySelection

    /** Server-side: `uncategorized=true` matches null-category rows. */
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
        /** Loaded rows — pages 0..N appended, in server order. */
        val items: List<Transaction>,
        /** `accountId → name` for row labels. */
        val accountNames: Map<String, String>,
        val filters: TransactionFilters,
        /** Server's total for the active filter set (≥ items.size). */
        val totalElements: Long,
        /** Whether the server reports another page. */
        val hasMore: Boolean,
        /** Index of the next server page to request. */
        val nextPage: Int = 1,
        /** A page >0 fetch is in flight (footer spinner). */
        val loadingMore: Boolean = false,
        /** Last page-append failure — surfaced inline, list stays live. */
        val moreError: TransactionsError? = null,
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
    /** Optional note (wire `description`) — persisted by the server (M17). */
    val description: String = "",
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
        // Backend @Digits(integer = 17, fraction = 2) + @Size(255) description.
        const val MAX_INTEGER_DIGITS = 17
        const val MAX_FRACTION_DIGITS = 2
        const val MAX_DESCRIPTION_LENGTH = 255
    }
}
