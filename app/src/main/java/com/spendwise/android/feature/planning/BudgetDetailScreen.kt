package com.spendwise.android.feature.planning

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spendwise.android.R
import com.spendwise.android.core.designsystem.CategoryTile
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
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    }
                }
            }
        }
    }
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
    Spacer(Modifier.height(SwSpacing.Md))
    SwBanner(
        title = stringResource(R.string.budget_readonly_title),
        text = stringResource(R.string.budget_readonly_body),
        kind = SwBannerKind.Info,
    )
}
