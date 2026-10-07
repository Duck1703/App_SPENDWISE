package com.spendwise.android.feature.planning

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spendwise.android.R
import com.spendwise.android.core.designsystem.CategoryTile
import com.spendwise.android.core.designsystem.SwCard
import com.spendwise.android.core.designsystem.SwColors
import com.spendwise.android.core.designsystem.SwHeader
import com.spendwise.android.core.designsystem.SwNetworkError
import com.spendwise.android.core.designsystem.SwServerError
import com.spendwise.android.core.designsystem.SwSkeletonRows
import com.spendwise.android.core.designsystem.SwSpacing
import com.spendwise.android.core.designsystem.SwStateBlock
import com.spendwise.android.core.designsystem.SwTextField
import com.spendwise.android.core.designsystem.categoryLabel
import com.spendwise.android.core.model.BudgetCalculation
import com.spendwise.android.core.model.BudgetStatus
import com.spendwise.android.core.ui.format.MoneyFormat
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import java.math.BigDecimal

/**
 * Budget detail (S20): `GET /{id}/calculation` rendered verbatim —
 * spent/remaining/percentage/status are the server's figures; the
 * progress bar derives from the server percentage only. The BE-011
 * limitation (no edit/delete) is surfaced honestly beneath the figures.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetDetailScreen(
    state: BudgetDetailState,
    edit: EditBudgetState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpenEdit: () -> Unit,
    onDismissEdit: () -> Unit,
    onEditLimitChange: (String) -> Unit,
    onEditPeriodChange: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleteConfirmVisible by remember { mutableStateOf(false) }
    val editCd = stringResource(R.string.budget_edit_action)
    val deleteCd = stringResource(R.string.budget_delete_action)
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = modifier.fillMaxSize()) {
            SwHeader(
                title = stringResource(R.string.budget_detail_title),
                onBack = onBack,
                modifier = Modifier.padding(horizontal = SwSpacing.Lg, vertical = SwSpacing.Md),
            )
            PullToRefreshBox(
                isRefreshing = (state as? BudgetDetailState.Content)?.isRefreshing == true,
                onRefresh = onRetry,
                state = rememberPullToRefreshState(),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when (state) {
                    BudgetDetailState.Loading -> SwSkeletonRows(
                        count = 3,
                        modifier = Modifier.padding(horizontal = SwSpacing.Lg),
                    )
                    BudgetDetailState.NotFound -> SwStateBlock(
                        title = stringResource(R.string.txn_detail_notfound_title),
                        text = stringResource(R.string.budget_detail_notfound_body),
                        actionLabel = stringResource(R.string.txn_detail_back_to_list),
                        onAction = onBack,
                        icon = R.drawable.ic_lucide_circle_alert,
                        isError = true,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    is BudgetDetailState.Error -> when (state.kind) {
                        PlanningError.Network -> SwNetworkError(
                            onRetry = onRetry,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        PlanningError.Server -> SwServerError(
                            onRetry = onRetry,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        PlanningError.Generic -> SwStateBlock(
                            title = stringResource(R.string.state_generic_title),
                            text = stringResource(R.string.state_generic_body),
                            actionLabel = stringResource(R.string.action_try_again),
                            onAction = onRetry,
                            icon = R.drawable.ic_lucide_circle_alert,
                            isError = true,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    is BudgetDetailState.Content -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = SwSpacing.Lg),
                    ) {
                        Calculation(calculation = state.calculation)
                        Spacer(Modifier.height(SwSpacing.Md))
                        // M14 (REQ-BE-011): a wrong budget is no longer
                        // permanent — limit/period are correctable in
                        // place; delete removes the plan (the ledger
                        // is untouched).
                        OutlinedButton(
                            onClick = onOpenEdit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    contentDescription = editCd
                                },
                        ) {
                            Text(stringResource(R.string.budget_edit_action))
                        }
                        TextButton(
                            onClick = { deleteConfirmVisible = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    contentDescription = deleteCd
                                },
                        ) {
                            Text(
                                stringResource(R.string.budget_delete_action),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    if (edit.visible) {
        EditBudgetDialog(
            edit = edit,
            currency = (state as? BudgetDetailState.Content)
                ?.calculation?.limit?.currency?.code.orEmpty(),
            onLimitChange = onEditLimitChange,
            onPeriodChange = onEditPeriodChange,
            onSave = onSaveEdit,
            onDismiss = onDismissEdit,
        )
    }

    if (deleteConfirmVisible) {
        // M14 external audit: the destructive dialog names WHICH budget
        // (category + month + limit) — two budgets can never be confused.
        val identity = (state as? BudgetDetailState.Content)?.calculation?.let {
            stringResource(
                R.string.budget_delete_dialog_identity,
                categoryLabel(it.category),
                it.period,
            )
        }
        AlertDialog(
            onDismissRequest = { deleteConfirmVisible = false },
            title = { Text(stringResource(R.string.budget_delete_dialog_title)) },
            text = {
                Text(
                    identity?.let {
                        stringResource(R.string.budget_delete_dialog_body_named, it)
                    } ?: stringResource(R.string.budget_delete_dialog_body),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirmVisible = false
                        onDelete()
                    },
                ) {
                    Text(
                        stringResource(R.string.budget_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmVisible = false }) {
                    Text(stringResource(R.string.sign_out_dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun EditBudgetDialog(
    edit: EditBudgetState,
    currency: String,
    onLimitChange: (String) -> Unit,
    onPeriodChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!edit.isSubmitting) onDismiss() },
        title = { Text(stringResource(R.string.budget_edit_dialog_title)) },
        text = {
            Column {
                SwTextField(
                    label = stringResource(R.string.budget_create_limit_label),
                    value = edit.limit,
                    onValueChange = onLimitChange,
                    hint = stringResource(R.string.budget_edit_currency_hint, currency),
                    error = edit.limitError?.let {
                        stringResource(
                            when (it) {
                                CreateBudgetState.LimitError.Required ->
                                    R.string.txn_error_amount_required
                                CreateBudgetState.LimitError.Invalid ->
                                    R.string.txn_error_amount_invalid
                                CreateBudgetState.LimitError.NotPositive ->
                                    R.string.txn_error_amount_positive
                                CreateBudgetState.LimitError.TooPrecise ->
                                    R.string.txn_error_amount_precise
                                CreateBudgetState.LimitError.TooLarge ->
                                    R.string.txn_error_amount_large
                            },
                        )
                    },
                    enabled = !edit.isSubmitting,
                )
                Spacer(Modifier.height(SwSpacing.Md))
                SwTextField(
                    label = stringResource(R.string.budget_create_period_label),
                    value = edit.period,
                    onValueChange = onPeriodChange,
                    placeholder = stringResource(R.string.budget_create_period_placeholder),
                    error = edit.periodError?.let {
                        stringResource(
                            when (it) {
                                CreateBudgetState.PeriodError.Required ->
                                    R.string.budget_error_period_required
                                CreateBudgetState.PeriodError.Invalid ->
                                    R.string.budget_error_period_invalid
                            },
                        )
                    },
                    enabled = !edit.isSubmitting,
                )
                edit.formError?.let {
                    Spacer(Modifier.height(SwSpacing.Sm))
                    Text(
                        text = stringResource(
                            when (it) {
                                EditBudgetState.FormError.Conflict ->
                                    R.string.budget_error_conflict_body
                                EditBudgetState.FormError.Ambiguous ->
                                    R.string.budget_edit_error_ambiguous
                                EditBudgetState.FormError.Server ->
                                    R.string.state_server_body
                                EditBudgetState.FormError.Generic ->
                                    R.string.state_generic_body
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = !edit.isSubmitting,
            ) {
                Text(
                    stringResource(
                        if (edit.isSubmitting) {
                            R.string.txn_create_submitting
                        } else {
                            R.string.budget_edit_save
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !edit.isSubmitting,
            ) {
                Text(stringResource(R.string.sign_out_dialog_cancel))
            }
        },
    )
}

@Composable
private fun Calculation(calculation: BudgetCalculation) {
    SwCard {
        Column(modifier = Modifier.padding(SwSpacing.Md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryTile(code = calculation.category)
                Spacer(Modifier.width(SwSpacing.Md))
                Column {
                    Text(
                        text = categoryLabel(calculation.category),
                        style = MaterialTheme.typography.titleLarge,
                        color = SwColors.Ink,
                    )
                    Text(
                        text = budgetPeriodLabel(calculation.period),
                        style = MaterialTheme.typography.bodySmall,
                        color = SwColors.Muted,
                    )
                }
            }
            Spacer(Modifier.height(SwSpacing.Md))
            Text(
                text = stringResource(
                    when (calculation.status) {
                        BudgetStatus.WITHIN_BUDGET -> R.string.budget_status_within
                        BudgetStatus.OVER_BUDGET -> R.string.budget_status_over
                        BudgetStatus.UNKNOWN -> R.string.budget_status_unknown
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
                color = when (calculation.status) {
                    BudgetStatus.WITHIN_BUDGET -> SwColors.Income
                    BudgetStatus.OVER_BUDGET -> SwColors.Expense
                    BudgetStatus.UNKNOWN -> SwColors.Muted
                },
            )
            Spacer(Modifier.height(SwSpacing.Sm))
            // The bar derives from the server's percentage figure only —
            // a display ratio, not client-computed spend.
            val progressFloat = calculation.percentage
                .divide(BigDecimal(100), 4, java.math.RoundingMode.HALF_UP)
                .coerceIn(BigDecimal.ZERO, BigDecimal.ONE)
                .toFloat()
            val progressCd = stringResource(
                R.string.budget_progress_cd,
                calculation.percentage.toPlainString(),
            )
            LinearProgressIndicator(
                progress = { progressFloat },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(progressFloat, 0f..1f)
                    },
            )
            Text(
                text = progressCd,
                style = MaterialTheme.typography.labelSmall,
                color = SwColors.Muted,
                modifier = Modifier.padding(top = SwSpacing.Xs),
            )
            Spacer(Modifier.height(SwSpacing.Md))
            FieldRow(
                label = stringResource(R.string.budget_field_limit),
                value = "${MoneyFormat.format(calculation.limit)} " +
                    calculation.limit.currency.code,
            )
            FieldRow(
                label = stringResource(R.string.budget_field_spent),
                value = "${MoneyFormat.format(calculation.spent)} " +
                    calculation.spent.currency.code,
            )
            FieldRow(
                label = stringResource(R.string.budget_field_remaining),
                value = "${MoneyFormat.format(calculation.remaining)} " +
                    calculation.remaining.currency.code,
            )
        }
    }
}
