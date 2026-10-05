package com.spendwise.android.feature.planning

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.spendwise.android.core.designsystem.amountColor
import com.spendwise.android.core.designsystem.categoryLabel
import com.spendwise.android.core.designsystem.transactionTypeLabel
import com.spendwise.android.core.model.RecurringFrequency
import com.spendwise.android.core.model.RecurringTemplate
import com.spendwise.android.core.model.TransactionType
import com.spendwise.android.core.ui.format.MoneyFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Recurring list (S15) — active + inactive templates, rows → detail. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringListScreen(
    state: RecurringListState,
    onOpenTemplate: (String) -> Unit,
    onCreate: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SwHeader(
            title = stringResource(R.string.recurring_title),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = SwSpacing.Lg, vertical = SwSpacing.Md),
        )
        PullToRefreshBox(
            isRefreshing = (state as? RecurringListState.Content)?.isRefreshing == true,
            onRefresh = onRetry,
            state = rememberPullToRefreshState(),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when (state) {
                RecurringListState.Loading -> SwSkeletonRows(
                    count = 4,
                    modifier = Modifier.padding(horizontal = SwSpacing.Lg),
                )
                is RecurringListState.Error -> when (state.kind) {
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
                RecurringListState.Empty -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    SwStateBlock(
                        title = stringResource(R.string.recurring_empty_title),
                        text = stringResource(R.string.recurring_empty_body),
                        actionLabel = stringResource(R.string.recurring_empty_action),
                        onAction = onCreate,
                        icon = R.drawable.ic_lucide_repeat,
                    )
                }
                is RecurringListState.Content -> RecurringContent(
                    state = state,
                    onOpenTemplate = onOpenTemplate,
                    onCreate = onCreate,
                )
            }
        }
    }
}

@Composable
private fun RecurringContent(
    state: RecurringListState.Content,
    onOpenTemplate: (String) -> Unit,
    onCreate: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = SwSpacing.Lg, vertical = SwSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(SwSpacing.Md),
    ) {
        if (state.refreshError != null) {
            item(key = "refresh-error") {
                SwBanner(
                    title = stringResource(R.string.planning_refresh_error_title),
                    text = stringResource(R.string.planning_refresh_error_body),
                    kind = SwBannerKind.Error,
                )
            }
        }
        item(key = "list") {
            SwCard {
                Column {
                    state.templates.forEachIndexed { index, template ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = SwColors.Line,
                                modifier = Modifier.padding(horizontal = SwSpacing.Md),
                            )
                        }
                        RecurringRow(
                            template = template,
                            accountName = state.accountNames[template.accountId],
                            onClick = { onOpenTemplate(template.id) },
                        )
                    }
                }
            }
        }
        item(key = "add") {
            Button(
                onClick = onCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_plus),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(SwSpacing.Sm))
                Text(stringResource(R.string.recurring_add))
            }
        }
        item(key = "bottom-space") { Spacer(Modifier.height(SwSpacing.Md)) }
    }
}

/** Row: category tile · direction/account·frequency · amount · chevron. */
@Composable
private fun RecurringRow(
    template: RecurringTemplate,
    accountName: String?,
    onClick: () -> Unit,
) {
    val frequency = stringResource(
        when (template.frequency) {
            RecurringFrequency.WEEKLY -> R.string.recurring_frequency_weekly
            RecurringFrequency.MONTHLY -> R.string.recurring_frequency_monthly
            RecurringFrequency.UNKNOWN -> R.string.recurring_frequency_unknown
        },
    )
    val nextDue = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .format(template.nextDueDate)
    val subtitle = stringResource(
        R.string.recurring_row_subtitle,
        transactionTypeLabel(template.direction),
        accountName ?: stringResource(R.string.planning_account_unknown),
        frequency,
        nextDue,
    )
    val amountText = templateAmountText(template)
    val cd = stringResource(
        R.string.recurring_row_cd,
        subtitle,
        amountText,
        if (template.active) {
            stringResource(R.string.planning_status_active)
        } else {
            stringResource(R.string.planning_status_inactive)
        },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clearAndSetSemantics { contentDescription = cd }
            .clickable(onClick = onClick)
            .padding(horizontal = SwSpacing.Md, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(code = template.category)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = template.category?.let { categoryLabel(it) }
                    ?: stringResource(R.string.filter_uncategorized),
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
            if (!template.active) {
                Text(
                    text = stringResource(R.string.planning_status_inactive),
                    style = MaterialTheme.typography.labelSmall,
                    color = SwColors.Muted,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = amountText,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
            ),
            color = if (template.active) amountColor(template.direction) else SwColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            painter = painterResource(R.drawable.ic_lucide_chevron_right),
            contentDescription = null,
            tint = SwColors.Muted,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * `+`/`−` prefix + locale-formatted amount + code — same display
 * convention as `signedAmountText` on transaction rows. Sign is
 * presentation only; the magnitude is the server-sent BigDecimal.
 */
internal fun templateAmountText(template: RecurringTemplate): String {
    val sign = when {
        template.direction == TransactionType.UNKNOWN -> ""
        template.direction.isCredit -> "+"
        else -> "−"
    }
    return "$sign${MoneyFormat.format(template.amount)} ${template.amount.currency.code}"
}
