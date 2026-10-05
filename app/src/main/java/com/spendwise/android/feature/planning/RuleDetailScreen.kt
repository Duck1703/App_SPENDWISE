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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.spendwise.android.core.designsystem.categoryLabel
import com.spendwise.android.core.model.RuleConditionType

/**
 * Rule detail (S23): read-only fields + one soft-disable action —
 * "Disable" asks once, then `DELETE /{id}` flips `enabled`. The screen
 * stays put showing the disabled state; previously-categorized
 * transactions are untouched.
 */
@Composable
fun RuleDetailScreen(
    state: RuleDetailState,
    onDisable: () -> Unit,
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
                title = stringResource(R.string.rule_detail_title),
                onBack = onBack,
            )
            Spacer(Modifier.height(SwSpacing.Lg))
            when (state) {
                RuleDetailState.Loading -> SwSkeletonRows(count = 3)
                RuleDetailState.NotFound -> SwStateBlock(
                    title = stringResource(R.string.txn_detail_notfound_title),
                    text = stringResource(R.string.rule_detail_notfound_body),
                    actionLabel = stringResource(R.string.txn_detail_back_to_list),
                    onAction = onBack,
                    icon = R.drawable.ic_lucide_circle_alert,
                    isError = true,
                )
                is RuleDetailState.Error -> when (state.kind) {
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
                is RuleDetailState.Content -> Detail(
                    state = state,
                    onDisable = { confirmVisible = true },
                )
            }
        }
    }

    if (confirmVisible) {
        AlertDialog(
            onDismissRequest = { confirmVisible = false },
            title = { Text(stringResource(R.string.rule_disable_dialog_title)) },
            text = { Text(stringResource(R.string.rule_disable_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmVisible = false
                        onDisable()
                    },
                ) {
                    Text(
                        stringResource(R.string.rule_disable_confirm),
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
    state: RuleDetailState.Content,
    onDisable: () -> Unit,
) {
    val rule = state.rule
    SwCard {
        Column(modifier = Modifier.padding(SwSpacing.Md)) {
            Text(
                text = conditionText(rule),
                style = MaterialTheme.typography.titleLarge,
                color = SwColors.Ink,
            )
            Spacer(Modifier.height(SwSpacing.Sm))
            Text(
                text = if (rule.enabled) {
                    stringResource(R.string.planning_status_active)
                } else {
                    stringResource(R.string.planning_status_inactive)
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (rule.enabled) SwColors.Income else SwColors.Muted,
            )
            Spacer(Modifier.height(SwSpacing.Md))
            HorizontalDivider(color = SwColors.Line)
            FieldRow(
                label = stringResource(R.string.rule_create_type_label),
                value = stringResource(
                    when (rule.conditionType) {
                        RuleConditionType.CONTAINS -> R.string.rule_type_contains
                        RuleConditionType.EQUALS -> R.string.rule_type_equals
                        RuleConditionType.UNKNOWN -> R.string.rule_type_unknown
                    },
                ),
            )
            FieldRow(
                label = stringResource(R.string.rule_detail_matches),
                value = rule.conditionValue,
            )
            FieldRow(
                label = stringResource(R.string.rule_create_category_label),
                value = rule.targetCategory.takeIf { it.isNotEmpty() }?.let { categoryLabel(it) }
                    ?: stringResource(R.string.filter_uncategorized),
            )
            FieldRow(
                label = stringResource(R.string.rule_create_priority_label),
                value = rule.priority.toString(),
            )
        }
    }
    if (rule.enabled) {
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
        Button(
            onClick = onDisable,
            enabled = !state.isDisabling,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(
                stringResource(
                    if (state.isDisabling) {
                        R.string.rule_disabling
                    } else {
                        R.string.rule_disable
                    },
                ),
            )
        }
    } else {
        Spacer(Modifier.height(SwSpacing.Lg))
        SwBanner(
            title = stringResource(R.string.rule_disabled_title),
            text = stringResource(R.string.rule_disabled_body),
            kind = SwBannerKind.Info,
        )
    }
}
