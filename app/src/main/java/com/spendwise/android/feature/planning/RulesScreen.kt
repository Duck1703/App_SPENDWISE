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
import com.spendwise.android.core.designsystem.categoryLabel
import com.spendwise.android.core.model.CategorizationRule
import com.spendwise.android.core.model.RuleConditionType

/** Rules list (S21) — server priority order rendered verbatim. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    state: RulesListState,
    onOpenRule: (String) -> Unit,
    onCreate: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SwHeader(
            title = stringResource(R.string.rules_title),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = SwSpacing.Lg, vertical = SwSpacing.Md),
        )
        PullToRefreshBox(
            isRefreshing = (state as? RulesListState.Content)?.isRefreshing == true,
            onRefresh = onRetry,
            state = rememberPullToRefreshState(),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when (state) {
                RulesListState.Loading -> SwSkeletonRows(
                    count = 4,
                    modifier = Modifier.padding(horizontal = SwSpacing.Lg),
                )
                is RulesListState.Error -> when (state.kind) {
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
                RulesListState.Empty -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    SwStateBlock(
                        title = stringResource(R.string.rules_empty_title),
                        text = stringResource(R.string.rules_empty_body),
                        actionLabel = stringResource(R.string.rules_empty_action),
                        onAction = onCreate,
                        icon = R.drawable.ic_lucide_ticket,
                    )
                }
                is RulesListState.Content -> RulesContent(
                    state = state,
                    onOpenRule = onOpenRule,
                    onCreate = onCreate,
                )
            }
        }
    }
}

@Composable
private fun RulesContent(
    state: RulesListState.Content,
    onOpenRule: (String) -> Unit,
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
                    state.rules.forEachIndexed { index, rule ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = SwColors.Line,
                                modifier = Modifier.padding(horizontal = SwSpacing.Md),
                            )
                        }
                        RuleRow(rule = rule, onClick = { onOpenRule(rule.id) })
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
                Text(stringResource(R.string.rules_add))
            }
        }
        item(key = "bottom-space") { Spacer(Modifier.height(SwSpacing.Md)) }
    }
}

/** Row: target tile · "contains 'x'" → category · priority · chevron. */
@Composable
private fun RuleRow(rule: CategorizationRule, onClick: () -> Unit) {
    val condition = conditionText(rule)
    val target = rule.targetCategory.takeIf { it.isNotEmpty() }?.let { categoryLabel(it) }
        ?: stringResource(R.string.filter_uncategorized)
    val cd = stringResource(
        R.string.rule_row_cd,
        condition,
        target,
        rule.priority,
        if (rule.enabled) {
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
        CategoryTile(code = rule.targetCategory.takeIf { it.isNotEmpty() })
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = condition,
                style = MaterialTheme.typography.titleMedium,
                color = if (rule.enabled) SwColors.Ink else SwColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.rule_row_assigns, target, rule.priority),
                style = MaterialTheme.typography.bodySmall,
                color = SwColors.Muted,
            )
            if (!rule.enabled) {
                Text(
                    text = stringResource(R.string.planning_status_inactive),
                    style = MaterialTheme.typography.labelSmall,
                    color = SwColors.Muted,
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            painter = painterResource(R.drawable.ic_lucide_chevron_right),
            contentDescription = null,
            tint = SwColors.Muted,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** `contains "x"` / `equals "x"` — the raw condition value, quoted verbatim. */
@Composable
internal fun conditionText(rule: CategorizationRule): String = when (rule.conditionType) {
    RuleConditionType.CONTAINS -> stringResource(
        R.string.rule_condition_contains,
        rule.conditionValue,
    )
    RuleConditionType.EQUALS -> stringResource(R.string.rule_condition_equals, rule.conditionValue)
    RuleConditionType.UNKNOWN -> stringResource(
        R.string.rule_condition_unknown,
        rule.conditionValue,
    )
}
