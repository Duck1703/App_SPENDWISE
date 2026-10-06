package com.spendwise.android.feature.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.spendwise.android.core.model.ImportBatch
import com.spendwise.android.core.model.ImportRowStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Import batch detail (M10) — `GET /api/imports/{id}` rendered
 * verbatim: filename, date, account, count tiles, then every row's
 * server-given outcome. The client computes nothing.
 */
@Composable
fun ImportHistoryDetailScreen(
    state: ImportHistoryDetailState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SwHeader(
            title = stringResource(R.string.import_history_detail_title),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = SwSpacing.Lg, vertical = SwSpacing.Md),
        )
        when (state) {
            ImportHistoryDetailState.Loading -> SwSkeletonRows(
                count = 4,
                modifier = Modifier.padding(horizontal = SwSpacing.Lg),
            )
            ImportHistoryDetailState.NotFound -> SwStateBlock(
                title = stringResource(R.string.import_history_notfound_title),
                text = stringResource(R.string.import_history_notfound_body),
                actionLabel = stringResource(R.string.import_history_back_to_list),
                onAction = onBack,
                icon = R.drawable.ic_lucide_circle_alert,
                isError = true,
            )
            is ImportHistoryDetailState.Error -> when (state.kind) {
                ImportHistoryError.Network -> SwNetworkError(onRetry = onRetry)
                ImportHistoryError.Server -> SwServerError(onRetry = onRetry)
                ImportHistoryError.Generic -> SwStateBlock(
                    title = stringResource(R.string.state_generic_title),
                    text = stringResource(R.string.state_generic_body),
                    actionLabel = stringResource(R.string.action_try_again),
                    onAction = onRetry,
                    icon = R.drawable.ic_lucide_circle_alert,
                    isError = true,
                )
            }
            is ImportHistoryDetailState.Content -> ImportHistoryDetailContent(state)
        }
    }
}

@Composable
private fun ImportHistoryDetailContent(state: ImportHistoryDetailState.Content) {
    val batch = state.batch
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = SwSpacing.Lg),
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
        item(key = "meta") {
            Column {
                Text(
                    text = batch.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    color = SwColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = batchMetaText(batch, state.accountName),
                    style = MaterialTheme.typography.bodySmall,
                    color = SwColors.Muted,
                )
            }
        }
        item(key = "tiles") {
            Row(horizontalArrangement = Arrangement.spacedBy(SwSpacing.Sm)) {
                ResultTile(
                    count = batch.successCount,
                    label = stringResource(R.string.import_status_imported),
                    tint = SwColors.Income,
                    modifier = Modifier.weight(1f),
                )
                ResultTile(
                    count = batch.duplicateCount,
                    label = stringResource(R.string.import_status_duplicate),
                    tint = SwColors.Muted,
                    modifier = Modifier.weight(1f),
                )
                ResultTile(
                    count = batch.failedCount,
                    label = stringResource(R.string.import_status_failed),
                    tint = SwColors.Expense,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        items(
            items = batch.rows,
            key = { "row-${it.rowNumber}" },
            contentType = { "import-row" },
        ) { row ->
            SwCard {
                ImportRowLine(
                    text = importRowText(row),
                    failed = row.status == ImportRowStatus.FAILED,
                )
            }
        }
        item(key = "bottom-space") { Spacer(Modifier.height(SwSpacing.Lg)) }
    }
}

@Composable
private fun batchMetaText(batch: ImportBatch, accountName: String?): String {
    val imported = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .format(batch.importedAt.atZone(ZoneId.systemDefault()))
    return stringResource(
        R.string.import_history_detail_meta,
        imported,
        accountName ?: stringResource(R.string.planning_account_unknown),
    )
}
