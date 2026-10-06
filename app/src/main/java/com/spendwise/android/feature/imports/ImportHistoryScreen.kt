package com.spendwise.android.feature.imports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spendwise.android.R
import com.spendwise.android.core.designsystem.SwBanner
import com.spendwise.android.core.designsystem.SwBannerKind
import com.spendwise.android.core.designsystem.SwCard
import com.spendwise.android.core.designsystem.SwColors
import com.spendwise.android.core.designsystem.SwHeader
import com.spendwise.android.core.designsystem.SwNetworkError
import com.spendwise.android.core.designsystem.SwServerError
import com.spendwise.android.core.designsystem.SwSkeletonRows
import com.spendwise.android.core.designsystem.SwSpacing
import com.spendwise.android.core.designsystem.SwStateBlock
import com.spendwise.android.core.model.ImportBatchSummary
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Import history (M10, REQ-BE-004) — server-truth batch summaries,
 * newest first; a row opens the per-batch report. Read-only: no
 * delete/re-run exists on the backend.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportHistoryScreen(
    state: ImportHistoryState,
    onOpenBatch: (String) -> Unit,
    onImport: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SwHeader(
            title = stringResource(R.string.import_history_title),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = SwSpacing.Lg, vertical = SwSpacing.Md),
        )
        PullToRefreshBox(
            isRefreshing = (state as? ImportHistoryState.Content)?.isRefreshing == true,
            onRefresh = onRetry,
            state = rememberPullToRefreshState(),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when (state) {
                ImportHistoryState.Loading -> SwSkeletonRows(
                    count = 4,
                    modifier = Modifier.padding(horizontal = SwSpacing.Lg),
                )
                is ImportHistoryState.Error -> when (state.kind) {
                    ImportHistoryError.Network -> SwNetworkError(
                        onRetry = onRetry,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    ImportHistoryError.Server -> SwServerError(
                        onRetry = onRetry,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    ImportHistoryError.Generic -> SwStateBlock(
                        title = stringResource(R.string.state_generic_title),
                        text = stringResource(R.string.state_generic_body),
                        actionLabel = stringResource(R.string.action_try_again),
                        onAction = onRetry,
                        icon = R.drawable.ic_lucide_circle_alert,
                        isError = true,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                ImportHistoryState.Empty -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    SwStateBlock(
                        title = stringResource(R.string.import_history_empty_title),
                        text = stringResource(R.string.import_history_empty_body),
                        actionLabel = stringResource(R.string.import_history_empty_action),
                        onAction = onImport,
                        icon = R.drawable.ic_lucide_upload,
                    )
                }
                is ImportHistoryState.Content -> ImportHistoryContent(
                    state = state,
                    onOpenBatch = onOpenBatch,
                )
            }
        }
    }
}

@Composable
private fun ImportHistoryContent(
    state: ImportHistoryState.Content,
    onOpenBatch: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = SwSpacing.Lg, vertical = SwSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(SwSpacing.Md),
    ) {
        if (state.refreshError != null) {
            item(key = "refresh-error") {
                SwBanner(
                    title = stringResource(R.string.import_history_refresh_error_title),
                    text = stringResource(R.string.import_history_refresh_error_body),
                    kind = SwBannerKind.Error,
                )
            }
        }
        // History is unbounded (the backend list has no pagination) —
        // real lazy items with stable keys, not an eager Column inside
        // one item (same bound as the transactions ledger).
        items(
            items = state.items,
            key = { it.batchId },
            contentType = { "import-batch" },
        ) { item ->
            SwCard {
                ImportHistoryRow(
                    item = item,
                    accountName = state.accountNames[item.accountId],
                    onClick = { onOpenBatch(item.batchId) },
                )
            }
        }
        item(key = "bottom-space") { Spacer(Modifier.height(SwSpacing.Md)) }
    }
}

/** Row: file icon · fileName · date + account + outcome counts · chevron. */
@Composable
private fun ImportHistoryRow(
    item: ImportBatchSummary,
    accountName: String?,
    onClick: () -> Unit,
) {
    val imported = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .format(item.importedAt.atZone(ZoneId.systemDefault()))
    val counts = stringResource(
        R.string.import_history_row_counts,
        item.successCount,
        item.duplicateCount,
        item.failedCount,
    )
    val subtitle = stringResource(
        R.string.import_history_row_subtitle,
        imported,
        accountName ?: stringResource(R.string.planning_account_unknown),
        counts,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clearAndSetSemantics {
                contentDescription = "${item.fileName}, $subtitle"
            }
            .clickable(onClick = onClick)
            .padding(horizontal = SwSpacing.Md, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_lucide_upload),
            contentDescription = null,
            tint = SwColors.Brand,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.fileName,
                style = MaterialTheme.typography.titleMedium,
                color = SwColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SwColors.Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            painter = painterResource(R.drawable.ic_lucide_chevron_right),
            contentDescription = null,
            tint = SwColors.Muted,
            modifier = Modifier.size(20.dp),
        )
    }
}
