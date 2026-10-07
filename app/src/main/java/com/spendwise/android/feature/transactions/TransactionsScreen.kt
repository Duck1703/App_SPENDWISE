package com.spendwise.android.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
import com.spendwise.android.core.ui.format.DateFormat
import com.spendwise.android.core.ui.format.DateFormat.pickerUtcMillisToLocalDate
import com.spendwise.android.core.ui.format.DateFormat.toPickerUtcMillis
import java.time.LocalDate

/**
 * Transactions (S09): browse-first — the add action is a small header
 * icon, never a FAB (P3A §3). One non-wrapping horizontal filter row:
 * Account / Type / Category / Source / From / To — all server-side
 * parameters on `GET /api/transactions/page` (M15). The list lazy-pages:
 * scrolling near the end appends the next server page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    state: TransactionsUiState,
    options: FilterOptions,
    filters: TransactionFilters,
    onOpenTransaction: (String) -> Unit,
    onCreateTransaction: () -> Unit,
    onCreateTransfer: () -> Unit,
    onCreateAccount: () -> Unit,
    onAccountFilter: (String?) -> Unit,
    onTypeFilter: (TransactionType?) -> Unit,
    onCategoryFilter: (CategorySelection) -> Unit,
    onProvenanceFilter: (TransactionProvenance?) -> Unit,
    onFromFilter: (LocalDate?) -> Unit,
    onToFilter: (LocalDate?) -> Unit,
    onLoadMore: () -> Unit,
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
            // Small mint-tinted header actions — browse stays primary.
            // Transfer (M12) sits beside "+" only when two accounts can
            // be picked; the transfer screen itself explains the
            // two-account requirement when fewer exist.
            if (options.accounts.isNotEmpty()) {
                if (options.accounts.size >= 2) {
                    IconButton(
                        onClick = onCreateTransfer,
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
                                painter = painterResource(R.drawable.ic_lucide_arrow_right_left),
                                contentDescription = stringResource(R.string.transactions_transfer_cd),
                                tint = SwColors.BrandDeep,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
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
            onFromFilter = onFromFilter,
            onToFilter = onToFilter,
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
                    onLoadMore = onLoadMore,
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
    onFromFilter: (LocalDate?) -> Unit,
    onToFilter: (LocalDate?) -> Unit,
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
        DateFilterChip(
            label = stringResource(R.string.filter_from),
            selected = filters.from,
            onSelect = onFromFilter,
        )
        DateFilterChip(
            label = stringResource(R.string.filter_to),
            selected = filters.to,
            onSelect = onToFilter,
        )
    }
}

/**
 * Date-range filter chip (M15): the same pill shape as [SwFilterChip],
 * but opens a Material3 [DatePickerDialog] instead of a dropdown. When a
 * date is set, a trailing clear affordance removes the bound. The picker
 * speaks UTC epoch-millis — [DateFormat] pins conversion to UTC
 * start-of-day so the selected calendar day is the wire `occurredOn`,
 * never ±1 from the device zone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFilterChip(
    label: String,
    selected: LocalDate?,
    onSelect: (LocalDate?) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val cd = if (selected != null) {
        stringResource(R.string.filter_cd_selected, label, DateFormat.format(selected))
    } else {
        stringResource(R.string.filter_cd_default, label)
    }
    Row(
        modifier = Modifier
            .heightIn(min = SwSpacing.TouchTargetMin)
            .clip(RoundedCornerShape(SwRadii.Pill))
            .background(if (selected != null) SwColors.BrandDeep else SwColors.Surface)
            .border(
                width = 1.dp,
                color = if (selected != null) SwColors.BrandDeep else SwColors.Line,
                shape = RoundedCornerShape(SwRadii.Pill),
            )
            // Not clearAndSetSemantics: the chip has TWO actions — the pill
            // opens the picker, the trailing × clears — so the clear
            // affordance must survive as its own semantics node.
            .semantics { contentDescription = cd }
            .clickable { showPicker = true }
            .padding(horizontal = SwSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.clearAndSetSemantics {}) {
            Text(
                text = selected?.let { DateFormat.format(it) } ?: label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected != null) SwColors.OnAccent else SwColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected == null) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_chevron_down),
                    contentDescription = null,
                    tint = SwColors.Muted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (selected != null) {
            Spacer(Modifier.width(4.dp))
            // The clear affordance needs a real touch target — a bare
            // 18dp icon is untappable on a physical phone. The box owns
            // the ≥48dp-high hit area; the icon stays visually small.
            Box(
                modifier = Modifier
                    .height(SwSpacing.TouchTargetMin)
                    .width(40.dp)
                    .clickable { onSelect(null) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_x),
                    contentDescription = stringResource(
                        R.string.filter_date_clear_cd,
                        label,
                    ),
                    tint = SwColors.OnAccent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selected?.toPickerUtcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis
                            ?.let { onSelect(pickerUtcMillisToLocalDate(it)) }
                        showPicker = false
                    },
                ) {
                    Text(stringResource(R.string.import_result_done))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.txn_create_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
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
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    val progressCd = stringResource(R.string.transactions_loading_more)
    // Load-more trigger: when the last real row is within 3 items of the
    // viewport bottom, ask for the next page. Guarding on hasMore/
    // loadingMore/moreError (not just position) lets the trigger
    // auto-chain until the viewport fills while making an append
    // failure stop cleanly — tapping the footer banner is the retry.
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3 &&
                state.hasMore && !state.loadingMore && state.moreError == null &&
                !state.isRefreshing
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }
    LazyColumn(
        state = listState,
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
                // M15: the server reports the filtered total — under
                // paging, items.size is just what has loaded so far.
                Text(
                    text = if (state.items.size < state.totalElements) {
                        stringResource(
                            R.string.transactions_page_count,
                            state.items.size,
                            state.totalElements,
                        )
                    } else {
                        pluralStringResource(
                            R.plurals.transactions_count,
                            state.totalElements.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                            state.totalElements.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        )
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = SwColors.Muted,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
            items(
                items = state.items,
                key = { it.id },
                contentType = { "transaction" },
            ) { transaction ->
                SwCard {
                    TransactionRow(
                        transaction = transaction,
                        accountName = state.accountNames[transaction.accountId],
                        onClick = { onOpenTransaction(transaction.id) },
                    )
                }
            }
            // Footer: honest paging state — spinner while appending, an
            // inline retry on failure, an end-of-ledger marker when the
            // server reports no more pages.
            when {
                state.moreError != null -> item(key = "more-error") {
                    SwBanner(
                        title = stringResource(R.string.transactions_more_error_title),
                        text = stringResource(R.string.transactions_more_error_body),
                        kind = SwBannerKind.Error,
                        // The append guard suppresses auto-retry (no
                        // infinite loop) — the banner itself is the
                        // explicit retry affordance.
                        modifier = Modifier.clickable(onClick = onLoadMore),
                    )
                }
                state.loadingMore -> item(key = "loading-more") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = SwSpacing.Sm),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .semantics {
                                    contentDescription =
                                        progressCd
                                },
                            color = SwColors.BrandDeep,
                        )
                    }
                }
                !state.hasMore -> item(key = "list-end") {
                    Text(
                        text = stringResource(R.string.transactions_end_of_list),
                        style = MaterialTheme.typography.labelSmall,
                        color = SwColors.Muted,
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = SwSpacing.Xs),
                    )
                }
            }
        }
        item(key = "bottom-space") { Spacer(Modifier.height(SwSpacing.Md)) }
    }
}
