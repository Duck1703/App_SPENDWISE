package com.spendwise.android.feature.accounts

import com.spendwise.android.core.model.Account
import com.spendwise.android.core.model.AccountType
import com.spendwise.android.core.model.Transaction

/** Accounts list screen state (S06, PRODUCT §18). */
sealed interface AccountsUiState {

    data object Loading : AccountsUiState

    data object Empty : AccountsUiState

    data class Content(
        val accounts: List<Account>,
        /** >1 distinct balance currency → show the never-aggregated banner. */
        val mixedCurrencies: Boolean,
        val isRefreshing: Boolean = false,
        /**
         * A failed pull-refresh over live content: stays visible as an
         * inline banner while the list stays on screen (MIN-1).
         */
        val refreshError: AccountsError? = null,
    ) : AccountsUiState

    data class Error(val kind: AccountsError) : AccountsUiState
}

enum class AccountsError { Network, Server, Generic }

/** Create-account form state (S07). */
data class CreateAccountUiState(
    val name: String = "",
    val type: AccountType = AccountType.CASH,
    /** ISO-4217 selection — open set per PRODUCT §18. */
    val currencyCode: String = DEFAULT_CURRENCY,
    /** Raw input; empty means "start at 0.00" (backend default semantics). */
    val openingBalance: String = "",
    val nameError: NameError? = null,
    val balanceError: BalanceError? = null,
    val formError: CreateFormError? = null,
    val isSubmitting: Boolean = false,
) {
    sealed interface NameError {
        data object Required : NameError
        data object TooLong : NameError

        /** Server `fields{name}` text — the designed display channel. */
        data class Server(val message: String) : NameError
    }

    sealed interface BalanceError {
        data object Invalid : BalanceError
        data object Negative : BalanceError
        data object TooPrecise : BalanceError
        data object TooLarge : BalanceError
        data class Server(val message: String) : BalanceError
    }

    enum class CreateFormError { Network, Server, Generic }

    companion object {
        const val DEFAULT_CURRENCY = "VND"

        /** Mirrors the backend `@Size(max = 255)` — no invented client cap. */
        const val MAX_NAME_LENGTH = 255
    }
}

/** Account detail state (S08): header plus the account-scoped ledger slice. */
sealed interface AccountDetailUiState {

    data object Loading : AccountDetailUiState

    data class Content(
        val account: Account,
        val isRefreshing: Boolean = false,
        /**
         * `GET /api/transactions?accountId=…` for this account — a
         * failure here never sinks the account header; it renders an
         * inline retry instead (non-fatal section, P3A §11).
         */
        val history: AccountHistory = AccountHistory.Loading,
    ) : AccountDetailUiState

    /** The account-scoped transaction list — read-only like the ledger. */
    sealed interface AccountHistory {
        data object Loading : AccountHistory
        data class Items(val transactions: List<Transaction>) : AccountHistory
        data object Error : AccountHistory
    }

    data object NotFound : AccountDetailUiState

    data class Error(val kind: AccountsError) : AccountDetailUiState
}
