package com.spendwise.android.feature.planning

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
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
import com.spendwise.android.core.designsystem.amountColor
import com.spendwise.android.core.designsystem.categoryLabel
import com.spendwise.android.core.designsystem.transactionTypeLabel
import com.spendwise.android.core.model.RecurringFrequency
import com.spendwise.android.core.model.RecurringTemplate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Recurring detail (S17): read-only fields + one soft-delete action —
 * "Deactivate" asks once, then `DELETE /{id}` flips `active`. The
 * screen stays put showing the deactivated state; generated
 * transactions are untouched (backend contract).
 */
@Composable
fun RecurringDetailScreen(
    state: RecurringDetailState,
    onDeactivate: () -> Unit,
    onEdit: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmVisible by remember { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SwSpacing.Lg),
        ) {
            SwHeader(
                title = stringResource(R.string.recurring_detail_title),
                onBack = onBack,
            )
            Spacer(Modifier.height(SwSpacing.Lg))
            when (state) {
                RecurringDetailState.Loading -> SwSkeletonRows(count = 3)
                RecurringDetailState.NotFound -> SwStateBlock(
                    title = stringResource(R.string.txn_detail_notfound_title),
                    text = stringResource(R.string.recurring_detail_notfound_body),
                    actionLabel = stringResource(R.string.txn_detail_back_to_list),
                    onAction = onBack,
                    icon = R.drawable.ic_lucide_circle_alert,
                    isError = true,
                )
                is RecurringDetailState.Error -> when (state.kind) {
                    PlanningError.Network -> SwNetworkError(onRetry = onRetry)
                    PlanningError.Server -> SwServerError(onRetry = onRetry)
                    PlanningError.Generic -> SwStateBlock(
                        title = stringResource(R.string.state_generic_title),
                        text = stringResource(R.string.state_generic_body),
                        actionLabel = stringResource(R.string.action_try_again),
                        onAction = onRetry,
                        icon = R.drawable.ic_lucide_circle_alert,
                        isError = true,
                    )
                }
                is RecurringDetailState.Content -> Detail(
                    state = state,
                    onDeactivate = { confirmVisible = true },
                    onEdit = onEdit,
                )
            }
        }
    }

    if (confirmVisible) {
        AlertDialog(
            onDismissRequest = { confirmVisible = false },
            title = { Text(stringResource(R.string.recurring_deactivate_dialog_title)) },
            text = { Text(stringResource(R.string.recurring_deactivate_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmVisible = false
                        onDeactivate()
                    },
                ) {
                    Text(
                        stringResource(R.string.recurring_deactivate_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmVisible = false }) {
                    Text(stringResource(R.string.sign_out_dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun Detail(
    state: RecurringDetailState.Content,
    onDeactivate: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val template = state.template
    SwCard {
        Column(modifier = Modifier.padding(SwSpacing.Md)) {
            Text(
                text = templateAmountText(template),
                style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                color = amountColor(template.direction),
            )
            Spacer(Modifier.height(SwSpacing.Sm))
            Text(
                text = if (template.active) {
                    stringResource(R.string.planning_status_active)
                } else {
                    stringResource(R.string.planning_status_inactive)
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (template.active) SwColors.Income else SwColors.Muted,
            )
            Spacer(Modifier.height(SwSpacing.Md))
            HorizontalDivider(color = SwColors.Line)
            FieldRow(
                label = stringResource(R.string.txn_create_type_label),
                value = transactionTypeLabel(template.direction),
            )
            FieldRow(
                label = stringResource(R.string.recurring_create_account_label),
                value = state.accountName
                    ?: stringResource(R.string.planning_account_unknown),
            )
            FieldRow(
                label = stringResource(R.string.txn_create_category_label),
                value = template.category?.let { categoryLabel(it) }
                    ?: stringResource(R.string.filter_uncategorized),
            )
            FieldRow(
                label = stringResource(R.string.recurring_create_frequency_label),
                value = stringResource(
                    when (template.frequency) {
                        RecurringFrequency.WEEKLY -> R.string.recurring_frequency_weekly
                        RecurringFrequency.MONTHLY -> R.string.recurring_frequency_monthly
                        RecurringFrequency.UNKNOWN -> R.string.recurring_frequency_unknown
                    },
                ),
            )
            FieldRow(
                label = stringResource(R.string.recurring_detail_next_due),
                value = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    .format(template.nextDueDate),
            )
            template.endDate?.let {
                FieldRow(
                    label = stringResource(R.string.recurring_detail_end_date),
                    value = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(it),
                )
            }
        }
    }
    if (template.active) {
        Spacer(Modifier.height(SwSpacing.Lg))
        state.actionError?.let {
            SwBanner(
                title = stringResource(R.string.state_generic_title),
                text = stringResource(
                    when (it) {
                        PlanningError.Network -> R.string.state_network_body
                        PlanningError.Server -> R.string.state_server_body
                        PlanningError.Generic -> R.string.state_generic_body
                    },
                ),
                kind = SwBannerKind.Error,
            )
            Spacer(Modifier.height(SwSpacing.Md))
        }
        // Edit (M16): sparse PATCH of future-generation inputs — the
        // destructive action stays visually secondary.
        OutlinedButton(
            onClick = { onEdit(template.id) },
            enabled = !state.isDeactivating,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(stringResource(R.string.recurring_edit_action))
        }
        Spacer(Modifier.height(SwSpacing.Sm))
        Button(
            onClick = onDeactivate,
            enabled = !state.isDeactivating,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(
                stringResource(
                    if (state.isDeactivating) {
                        R.string.recurring_deactivating
                    } else {
                        R.string.recurring_deactivate
                    },
                ),
            )
        }
    } else {
        Spacer(Modifier.height(SwSpacing.Lg))
        SwBanner(
            title = stringResource(R.string.recurring_deactivated_title),
            text = stringResource(R.string.recurring_deactivated_body),
            kind = SwBannerKind.Info,
        )
    }
}

@Composable
internal fun FieldRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SwSpacing.Sm),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = SwColors.Muted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = SwColors.Ink,
        )
    }
}
