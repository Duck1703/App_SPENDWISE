package com.spendwise.android.feature.imports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spendwise.android.R
import com.spendwise.android.core.designsystem.SwBanner
import com.spendwise.android.core.designsystem.SwBannerKind
import com.spendwise.android.core.designsystem.SwCard
import com.spendwise.android.core.designsystem.SwColors
import com.spendwise.android.core.designsystem.SwDropdownField
import com.spendwise.android.core.designsystem.SwHeader
import com.spendwise.android.core.designsystem.SwNetworkError
import com.spendwise.android.core.designsystem.SwRadii
import com.spendwise.android.core.designsystem.SwServerError
import com.spendwise.android.core.designsystem.SwSkeletonRows
import com.spendwise.android.core.designsystem.SwSpacing
import com.spendwise.android.core.designsystem.SwStateBlock
import com.spendwise.android.core.model.ImportBatch
import com.spendwise.android.core.model.ImportRowStatus

/**
 * Import (S12–S14, PRODUCT §23–25, P3A §6): choose → processing →
 * result, one stateful flow. The processing surface is modal — no
 * back, no cancel — because leaving mid-upload would leave the
 * outcome unknown. Result is the one-shot report; the ledger rows
 * then appear in Transactions with the Imported chip.
 */
@Composable
fun ImportScreen(
    state: ImportUiState,
    onAccountSelected: (String) -> Unit,
    onPickFile: () -> Unit,
    onFileCleared: () -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit,
    onCreateAccount: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = SwColors.Ground) {
        Column(modifier = Modifier.fillMaxSize()) {
            SwHeader(
                title = stringResource(R.string.import_title),
                modifier = Modifier.padding(horizontal = SwSpacing.Lg, vertical = SwSpacing.Md),
                onBack = if ((state as? ImportUiState.Form)?.isUploading == true) null else onBack,
            )
            when (state) {
                ImportUiState.Loading -> SwSkeletonRows(
                    count = 4,
                    modifier = Modifier.padding(horizontal = SwSpacing.Lg),
                )
                ImportUiState.Empty -> SwStateBlock(
                    title = stringResource(R.string.import_empty_title),
                    text = stringResource(R.string.import_empty_body),
                    actionLabel = stringResource(R.string.import_empty_action),
                    onAction = onCreateAccount,
                    illustration = R.drawable.coin_3d,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(SwSpacing.Lg),
                )
                is ImportUiState.Error -> when (state.kind) {
                    ImportError.Network -> SwNetworkError(
                        onRetry = onRetry,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    ImportError.Server -> SwServerError(
                        onRetry = onRetry,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    else -> SwStateBlock(
                        title = stringResource(R.string.state_generic_title),
                        text = stringResource(R.string.state_generic_body),
                        actionLabel = stringResource(R.string.action_try_again),
                        onAction = onRetry,
                        icon = R.drawable.ic_lucide_circle_alert,
                        isError = true,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                is ImportUiState.Form -> ImportForm(
                    state = state,
                    onAccountSelected = onAccountSelected,
                    onPickFile = onPickFile,
                    onFileCleared = onFileCleared,
                    onSubmit = onSubmit,
                )
                is ImportUiState.Result -> ImportResult(
                    batch = state.batch,
                    onDone = onDone,
                )
            }
        }
    }
}

/** S12 — account → format truth block → file → submit. */
@Composable
private fun ImportForm(
    state: ImportUiState.Form,
    onAccountSelected: (String) -> Unit,
    onPickFile: () -> Unit,
    onFileCleared: () -> Unit,
    onSubmit: () -> Unit,
) {
    // S13 — modal processing surface: indeterminate, no back, no
    // cancel, no invented progress %. Rendered INSTEAD of the form so
    // no underlying control stays reachable; state lives in the VM.
    if (state.isUploading) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clearAndSetSemantics {
                    contentDescription = "Importing your statement"
                },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = SwColors.Brand)
            Spacer(Modifier.height(SwSpacing.Lg))
            Text(
                text = stringResource(R.string.import_processing_title),
                style = MaterialTheme.typography.titleMedium,
                color = SwColors.Ink,
            )
            Spacer(Modifier.height(SwSpacing.Sm))
            Text(
                text = stringResource(R.string.import_processing_body),
                style = MaterialTheme.typography.bodyMedium,
                color = SwColors.Muted,
                modifier = Modifier.padding(horizontal = SwSpacing.Xl),
            )
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SwSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(SwSpacing.Md),
    ) {
        if (state.ambiguousNotice) {
            SwBanner(
                title = stringResource(R.string.import_ambiguous_title),
                text = stringResource(R.string.import_ambiguous_body),
                kind = SwBannerKind.Error,
            )
        }
        state.error?.let { error ->
            SwBanner(
                title = stringResource(errorTitleRes(error)),
                text = stringResource(errorBodyRes(error)),
                kind = SwBannerKind.Error,
            )
        }

        SwDropdownField(
            label = stringResource(R.string.import_account_label),
            selectedLabel = state.accounts
                .firstOrNull { it.id == state.selectedAccountId }?.name
                ?: stringResource(R.string.import_account_placeholder),
            options = state.accounts.map {
                it.id to "${it.name} · ${it.balance.currency.code}"
            },
            onSelect = onAccountSelected,
            hint = stringResource(R.string.import_account_hint),
        )

        FormatTruthBlock()

        SwCard {
            Column(modifier = Modifier.padding(SwSpacing.Md)) {
                Text(
                    text = stringResource(R.string.import_file_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = SwColors.Ink,
                )
                Spacer(Modifier.height(SwSpacing.Sm))
                if (state.file != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lucide_ticket),
                            contentDescription = null,
                            tint = SwColors.Brand,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(SwSpacing.Sm))
                        Text(
                            text = state.file.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SwColors.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = onFileCleared,
                            modifier = Modifier.heightIn(min = SwSpacing.TouchTargetMin),
                        ) {
                            Text(stringResource(R.string.import_change_file))
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onPickFile,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = SwSpacing.TouchTargetMin),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lucide_upload),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(SwSpacing.Sm))
                        Text(stringResource(R.string.import_pick_file))
                    }
                }
            }
        }

        Button(
            onClick = onSubmit,
            enabled = state.selectedAccountId != null && state.file != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(stringResource(R.string.import_submit))
        }
        Spacer(Modifier.height(SwSpacing.Lg))
    }
}

/** The honest supported-formats block (P3A §6 + PRODUCT §24). */
@Composable
private fun FormatTruthBlock() {
    var expanded by remember { mutableStateOf(false) }
    SwCard {
        Column(modifier = Modifier.padding(SwSpacing.Md)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = SwSpacing.TouchTargetMin)
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_info),
                    contentDescription = null,
                    tint = SwColors.Info,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(SwSpacing.Sm))
                Text(
                    text = stringResource(R.string.import_format_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = SwColors.Ink,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(
                        if (expanded) R.drawable.ic_lucide_chevron_down
                        else R.drawable.ic_lucide_chevron_right,
                    ),
                    contentDescription = null,
                    tint = SwColors.Muted,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = stringResource(R.string.import_format_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = SwColors.Muted,
            )
            if (expanded) {
                Spacer(Modifier.height(SwSpacing.Sm))
                HorizontalDivider(color = SwColors.Line)
                Spacer(Modifier.height(SwSpacing.Sm))
                Text(
                    text = stringResource(R.string.import_format_csv_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = SwColors.Ink,
                )
                Text(
                    text = "occurredOn,amount,currency,description,sourceTxnId",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = SwColors.BrandDeep,
                )
                Text(
                    text = stringResource(R.string.import_format_csv_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = SwColors.Muted,
                )
                Spacer(Modifier.height(SwSpacing.Sm))
                Text(
                    text = stringResource(R.string.import_format_pdf_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = SwColors.Ink,
                )
                Text(
                    text = stringResource(R.string.import_format_pdf_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = SwColors.Muted,
                )
            }
        }
    }
}

/** S14 — counts first, rows on demand; duplicates are calm, never errors. */
@Composable
private fun ImportResult(batch: ImportBatch, onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SwSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(SwSpacing.Md),
    ) {
        Text(
            text = batch.fileName,
            style = MaterialTheme.typography.bodyMedium,
            color = SwColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.import_result_rows, batch.totalRows),
            style = MaterialTheme.typography.titleMedium,
            color = SwColors.Ink,
        )

        when {
            batch.totalRows == 0 -> SwBanner(
                title = stringResource(R.string.import_result_empty_title),
                text = stringResource(R.string.import_result_empty_body),
                kind = SwBannerKind.Info,
            )
            batch.duplicateCount == batch.totalRows -> SwBanner(
                title = stringResource(R.string.import_all_duplicates_title),
                text = stringResource(R.string.import_all_duplicates_body),
                kind = SwBannerKind.Info,
            )
            batch.failedCount == batch.totalRows -> SwBanner(
                title = stringResource(R.string.import_all_failed_title),
                text = stringResource(R.string.import_all_failed_body),
                kind = SwBannerKind.Error,
            )
        }

        // Three status tiles (P3A §6): imported / already-imported
        // (calm) / couldn't-import — count + label, merged semantics.
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

        val failures = batch.rows.filter { it.status == ImportRowStatus.FAILED }
        if (failures.isNotEmpty()) {
            SwCard {
                Column {
                    Text(
                        text = stringResource(R.string.import_failures_section),
                        style = MaterialTheme.typography.labelLarge,
                        color = SwColors.Expense,
                        modifier = Modifier.padding(SwSpacing.Md),
                    )
                    failures.forEach { row ->
                        HorizontalDivider(
                            color = SwColors.Line,
                            modifier = Modifier.padding(horizontal = SwSpacing.Md),
                        )
                        Text(
                            text = if (row.reason.isNullOrBlank()) {
                                stringResource(R.string.import_row_failed_no_reason, row.rowNumber)
                            } else {
                                stringResource(R.string.import_row_failed, row.rowNumber, row.reason)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = SwColors.Muted,
                            modifier = Modifier.padding(SwSpacing.Md),
                        )
                    }
                }
            }
        }

        // Rows on demand (P3A §6): the full per-row list stays one
        // tap away — duplicates read "already imported", never errors.
        if (batch.rows.isNotEmpty()) {
            var rowsExpanded by remember { mutableStateOf(false) }
            SwCard {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = SwSpacing.TouchTargetMin)
                            .clickable { rowsExpanded = !rowsExpanded }
                            .padding(horizontal = SwSpacing.Md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(
                                if (rowsExpanded) R.string.import_rows_hide
                                else R.string.import_rows_show,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = SwColors.Ink,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            painter = painterResource(
                                if (rowsExpanded) R.drawable.ic_lucide_chevron_down
                                else R.drawable.ic_lucide_chevron_right,
                            ),
                            contentDescription = null,
                            tint = SwColors.Muted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    if (rowsExpanded) {
                        batch.rows.forEach { row ->
                            HorizontalDivider(
                                color = SwColors.Line,
                                modifier = Modifier.padding(horizontal = SwSpacing.Md),
                            )
                            Text(
                                text = when (row.status) {
                                    ImportRowStatus.OK ->
                                        stringResource(
                                            R.string.import_row_ok,
                                            row.rowNumber,
                                        )
                                    ImportRowStatus.DUPLICATE ->
                                        stringResource(
                                            R.string.import_row_duplicate,
                                            row.rowNumber,
                                        )
                                    ImportRowStatus.FAILED ->
                                        if (row.reason.isNullOrBlank()) {
                                            stringResource(
                                                R.string.import_row_failed_no_reason,
                                                row.rowNumber,
                                            )
                                        } else {
                                            stringResource(
                                                R.string.import_row_failed,
                                                row.rowNumber,
                                                row.reason,
                                            )
                                        }
                                    ImportRowStatus.UNKNOWN ->
                                        stringResource(
                                            R.string.import_row_unknown,
                                            row.rowNumber,
                                        )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when (row.status) {
                                    ImportRowStatus.FAILED -> SwColors.Expense
                                    else -> SwColors.Muted
                                },
                                modifier = Modifier.padding(SwSpacing.Md),
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(stringResource(R.string.import_result_done))
        }
        Spacer(Modifier.height(SwSpacing.Lg))
    }
}

@Composable
private fun ResultTile(
    count: Int,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val cd = "$count $label"
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(SwRadii.Md))
            .background(SwColors.Surface)
            .heightIn(min = SwSpacing.TouchTargetMin)
            .clearAndSetSemantics { contentDescription = cd }
            .padding(horizontal = SwSpacing.Sm, vertical = SwSpacing.Sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = tint,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = SwColors.Ink,
        )
    }
}

private fun errorTitleRes(error: ImportError): Int = when (error) {
    ImportError.Network -> R.string.import_error_network_title
    ImportError.Server -> R.string.import_error_server_title
    ImportError.UnsupportedFile -> R.string.import_error_unsupported_title
    ImportError.FileTooLarge -> R.string.import_error_too_large_title
    ImportError.AccountUnavailable -> R.string.import_error_account_gone_title
    ImportError.Generic -> R.string.state_generic_title
}

private fun errorBodyRes(error: ImportError): Int = when (error) {
    ImportError.Network -> R.string.import_error_network_body
    ImportError.Server -> R.string.import_error_server_body
    ImportError.UnsupportedFile -> R.string.import_error_unsupported_body
    ImportError.FileTooLarge -> R.string.import_error_too_large_body
    ImportError.AccountUnavailable -> R.string.import_error_account_gone_body
    ImportError.Generic -> R.string.state_generic_body
}
