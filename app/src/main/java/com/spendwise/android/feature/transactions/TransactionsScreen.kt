package com.spendwise.android.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.spendwise.android.R
import com.spendwise.android.core.designsystem.SwBanner
import com.spendwise.android.core.designsystem.SwBannerKind
import com.spendwise.android.core.designsystem.SwCard
import com.spendwise.android.core.designsystem.SwColors
import com.spendwise.android.core.designsystem.SwFilterChip
import com.spendwise.android.core.designsystem.SwHeader
import com.spendwise.android.core.designsystem.SwNetworkError
import com.spendwise.android.core.designsystem.SwRadii
import com.spendwise.android.core.designsystem.SwServerError
import com.spendwise.android.core.designsystem.SwSkeletonRows
import com.spendwise.android.core.designsystem.SwSpacing
import com.spendwise.android.core.designsystem.SwStateBlock
import com.spendwise.android.core.designsystem.TransactionRow
import com.spendwise.android.core.designsystem.categoryLabel
import com.spendwise.android.core.designsystem.provenanceLabel
import com.spendwise.android.core.model.TransactionProvenance
import com.spendwise.android.core.model.TransactionType

/**
 * Transactions (S09): browse-first — the add action is a small header
 * icon, never a FAB (P3A §3). One non-wrapping horizontal filter row:
 * Account / Type / Category (server params) + Source (client-side
 * provenance, declared as such in AD-019/PRODUCT §19).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    state: TransactionsUiState,
    options: FilterOptions,
    filters: TransactionFilters,
    onOpenTransaction: (String) -> Unit,
    onCreateTransaction: () -> Unit,
    onCreateAccount: () -> Unit,
    onAccountFilter: (String?) -> Unit,
    onTypeFilter: (TransactionType?) -> Unit,
    onCategoryFilter: (CategorySelection) -> Unit,
    onProvenanceFilter: (TransactionProvenance?) -> Unit,
    onClearFilters: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SwSpacing.Lg, vertical = SwSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SwHeader(
                title = stringResource(R.string.transactions_title),
                subtitle = stringResource(R.string.transactions_subtitle),
                modifier = Modifier.weight(1f),
            )
            // Small mint-tinted header action — browse stays primary.
            if (options.accounts.isNotEmpty()) {
                IconButton(
                    onClick = onCreateTransaction,
                    modifier = Modifier.size(SwSpacing.TouchTargetMin),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(SwRadii.Md))
                            .background(SwColors.Mint),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lucide_plus),
                            contentDescription = stringResource(R.string.transactions_add_cd),
                            tint = SwColors.BrandDeep,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
        FilterRow(
            filters = filters,
            options = options,
            onAccountFilter = onAccountFilter,
            onTypeFilter = onTypeFilter,
            onCategoryFilter = onCategoryFilter,
            onProvenanceFilter = onProvenanceFilter,
        )
        PullToRefreshBox(
            isRefreshing = (state as? TransactionsUiState.Content)?.isRefreshing == true,
            onRefresh = onRefresh,
            state = rememberPullToRefreshState(),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when (state) {
                TransactionsUiState.Loading -> SwSkeletonRows(
                    count = 5,
                    modifier = Modifier.padding(horizontal = SwSpacing.Lg),
                )
                is TransactionsUiState.Error -> when (state.kind) {
                    TransactionsError.Network -> SwNetworkError(
                        onRetry = onRetry,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    TransactionsError.Server -> SwServerError(
                        onRetry = onRetry,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    TransactionsError.Generic -> SwStateBlock(
                        title = stringResource(R.string.state_generic_title),
                        text = stringResource(R.string.state_generic_body),
                        actionLabel = stringResource(R.string.action_try_again),
                        onAction = onRetry,
                        icon = R.drawable.ic_lucide_circle_alert,
                        isError = true,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                TransactionsUiState.Empty -> EmptyScrollable {
                    SwStateBlock(
                        title = stringResource(R.string.transactions_empty_title),
                        text = stringResource(R.string.transactions_empty_body),
                        actionLabel = stringResource(
                            if (options.accounts.isEmpty()) {
                                R.string.transactions_empty_no_account_action
                            } else {
                                R.string.transactions_empty_action
                            },
                        ),
                        onAction = if (options.accounts.isEmpty()) {
                            onCreateAccount
                        } else {
                            onCreateTransaction
                        },
                        illustration = R.drawable.coin_3d,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                is TransactionsUiState.Content -> TransactionsContent(
                    state = state,
                    options = options,
                    onOpenTransaction = onOpenTransaction,
                    onClearFilters = onClearFilters,
                )
            }
        }
    }
}

/** One horizontal non-wrapping row; scrolls when chips overflow (P3A §3). */
@Composable
private fun FilterRow(
    filters: TransactionFilters,
    options: FilterOptions,
    onAccountFilter: (String?) -> Unit,
    onTypeFilter: (TransactionType?) -> Unit,
    onCategoryFilter: (CategorySelection) -> Unit,
    onProvenanceFilter: (TransactionProvenance?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = SwSpacing.Lg),
        horizontalArrangement = Arrangement.spacedBy(SwSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val allLabel = stringResource(R.string.filter_all)
        SwFilterChip(
            label = stringResource(R.string.filter_account),
            selectedLabel = options.accounts.firstOrNull { it.id == filters.accountId }?.name,
            options = listOf(null as String? to allLabel) +
                options.accounts.map { it.id as String? to it.name },
            onSelect = onAccountFilter,
        )
        SwFilterChip(
            label = stringResource(R.string.filter_type),
            selectedLabel = when (filters.type) {
                TransactionType.INCOME -> stringResource(R.string.txn_type_income)
                TransactionType.EXPENSE -> stringResource(R.string.txn_type_expense)
                else -> null
            },
            options = listOf(
                null as TransactionType? to allLabel,
                TransactionType.INCOME as TransactionType? to
                    stringResource(R.string.txn_type_income),
                TransactionType.EXPENSE as TransactionType? to
                    stringResource(R.string.txn_type_expense),
            ),
            onSelect = onTypeFilter,
        )
        SwFilterChip(
            label = stringResource(R.string.filter_category),
            selectedLabel = when (val c = filters.category) {
                CategorySelection.All -> null
                CategorySelection.Uncategorized -> stringResource(R.string.filter_uncategorized)
                is CategorySelection.Code -> categoryLabel(c.code)
            },
            options = listOf(
                CategorySelection.All to allLabel,
                CategorySelection.Uncategorized to
                    stringResource(R.string.filter_uncategorized),
            ) + options.categories.map { CategorySelection.Code(it) to categoryLabel(it) },
            onSelect = onCategoryFilter,
        )
        SwFilterChip(
            label = stringResource(R.string.filter_source),
            selectedLabel = filters.provenance?.let { provenanceLabel(it) },
            options = listOf(null as TransactionProvenance? to allLabel) +
                listOf(
                    TransactionProvenance.MANUAL,
                    TransactionProvenance.IMPORTED,
                    TransactionProvenance.RECURRING,
                    TransactionProvenance.TRANSFER,
                ).map { it as TransactionProvenance? to provenanceLabel(it).orEmpty() },
            onSelect = onProvenanceFilter,
        )
    }
}

@Composable
private fun EmptyScrollable(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        content = content,
    )
}

@Composable
private fun TransactionsContent(
    state: TransactionsUiState.Content,
    options: FilterOptions,
    onOpenTransaction: (String) -> Unit,
    onClearFilters: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = SwSpacing.Lg,
            vertical = SwSpacing.Sm,
        ),
        verticalArrangement = Arrangement.spacedBy(SwSpacing.Md),
    ) {
        if (state.refreshError != null) {
            item(key = "refresh-error") {
                SwBanner(
                    title = stringResource(R.string.transactions_refresh_error_title),
                    text = stringResource(R.string.transactions_refresh_error_body),
                    kind = SwBannerKind.Error,
                )
            }
        }
        if (state.noMatches) {
            item(key = "no-matches") {
                SwStateBlock(
                    title = stringResource(R.string.transactions_filtered_empty_title),
                    text = stringResource(R.string.transactions_filtered_empty_body),
                    actionLabel = stringResource(R.string.transactions_filtered_empty_action),
                    onAction = onClearFilters,
                    icon = R.drawable.ic_lucide_info,
                )
            }
        } else {
            item(key = "count") {
                Text(
                    text = pluralStringResource(
                        R.plurals.transactions_count,
                        state.items.size,
                        state.items.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = SwColors.Muted,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
            item(key = "list") {
                SwCard {
                    Column {
                        state.items.forEachIndexed { index, transaction ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = SwColors.Line,
                                    modifier = Modifier.padding(horizontal = SwSpacing.Md),
                                )
                            }
                            TransactionRow(
                                transaction = transaction,
                                accountName = state.accountNames[transaction.accountId],
                                onClick = { onOpenTransaction(transaction.id) },
                            )
                        }
                    }
                }
            }
            item(key = "list-note") {
                Text(
                    text = stringResource(R.string.transactions_list_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = SwColors.Muted,
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = SwSpacing.Xs),
                )
            }
        }
        item(key = "bottom-space") { Spacer(Modifier.height(SwSpacing.Md)) }
    }
}
