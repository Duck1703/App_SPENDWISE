package com.spendwise.android.feature.home

import com.spendwise.android.core.model.Account
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Transaction
import java.math.BigDecimal

/**
 * Home (S05, PRODUCT §17, P3A §7): account carousel + "This month"
 * hero totals + recent activity — each independently honest.
 */
sealed interface HomeUiState {

    data object Loading : HomeUiState

    /** No accounts — the first-run state drives account creation. */
    data object Empty : HomeUiState

    data class Content(
        val accounts: List<Account>,
        val totals: MonthTotals,
        val activity: ActivityState,
        val isRefreshing: Boolean = false,
        /**
         * A failed pull-refresh over live content stays an inline
         * banner — content is never replaced by an error (MIN-1 rule).
         */
        val refreshError: HomeError? = null,
    ) : HomeUiState

    data class Error(val kind: HomeError) : HomeUiState
}

/**
 * "This month" hero state (PRODUCT §17 hard rule):
 * - [Loaded] only ever happens under a **uniform** account currency —
 *   the response carries no currency field, so the label comes from
 *   the accounts, never from a guess.
 * - [Unavailable] is the honest mixed-currency state; also the mapped
 *   result of a dashboard 400 (same-type mixed-currency aggregation).
 * - [Failed] is a section-scoped retry — it never sinks the screen.
 */
sealed interface MonthTotals {
    data object Loading : MonthTotals
    data class Loaded(
        val income: BigDecimal,
        val expense: BigDecimal,
        val currency: CurrencyCode,
    ) : MonthTotals
    data object Unavailable : MonthTotals
    data class Failed(val kind: HomeError) : MonthTotals
}

/** Recent activity — first rows of the unfiltered list as returned. */
sealed interface ActivityState {
    data object Loading : ActivityState
    data class Items(val transactions: List<Transaction>) : ActivityState
    data class Failed(val kind: HomeError) : ActivityState
}

enum class HomeError { Network, Server, Generic }
