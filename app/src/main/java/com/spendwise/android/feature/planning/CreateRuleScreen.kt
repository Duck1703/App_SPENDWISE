package com.spendwise.android.feature.planning

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.spendwise.android.R
import com.spendwise.android.core.designsystem.SwBanner
import com.spendwise.android.core.designsystem.SwBannerKind
import com.spendwise.android.core.designsystem.SwDropdownField
import com.spendwise.android.core.designsystem.SwHeader
import com.spendwise.android.core.designsystem.SwSegmentedControl
import com.spendwise.android.core.designsystem.SwSkeletonRows
import com.spendwise.android.core.designsystem.SwSpacing
import com.spendwise.android.core.designsystem.SwStateBlock
import com.spendwise.android.core.designsystem.SwTextField
import com.spendwise.android.core.designsystem.categoryLabel
import com.spendwise.android.core.model.RuleConditionType

/**
 * Rule create (S22): match type · text · target category · priority.
 * Priority duplicates surface on the field (409); `enabled` is never
 * sent (S22 note).
 */
@Composable
fun CreateRuleScreen(
    state: CreateRuleState,
    onConditionTypeChange: (RuleConditionType) -> Unit,
    onConditionValueChange: (String) -> Unit,
    onTargetCategoryChange: (String?) -> Unit,
    onPriorityChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetryOptions: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SwSpacing.Lg),
        ) {
            SwHeader(
                title = stringResource(R.string.rule_create_title),
                subtitle = stringResource(R.string.rule_create_subtitle),
                onBack = onBack,
            )
            Spacer(Modifier.height(SwSpacing.Lg))
            when {
                state.optionsLoading -> SwSkeletonRows(count = 3)
                state.optionsError -> SwStateBlock(
                    title = stringResource(R.string.state_generic_title),
                    text = stringResource(R.string.state_generic_body),
                    actionLabel = stringResource(R.string.action_try_again),
                    onAction = onRetryOptions,
                    icon = R.drawable.ic_lucide_circle_alert,
                    isError = true,
                )
                else -> Form(
                    state = state,
                    onConditionTypeChange = onConditionTypeChange,
                    onConditionValueChange = onConditionValueChange,
                    onTargetCategoryChange = onTargetCategoryChange,
                    onPriorityChange = onPriorityChange,
                    onSubmit = onSubmit,
                    onBack = onBack,
                )
            }
        }
    }
}

@Composable
private fun Form(
    state: CreateRuleState,
    onConditionTypeChange: (RuleConditionType) -> Unit,
    onConditionValueChange: (String) -> Unit,
    onTargetCategoryChange: (String?) -> Unit,
    onPriorityChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    SwSegmentedControl(
        label = stringResource(R.string.rule_create_type_label),
        options = listOf(
            RuleConditionType.CONTAINS to stringResource(R.string.rule_type_contains),
            RuleConditionType.EQUALS to stringResource(R.string.rule_type_equals),
        ),
        selected = state.conditionType,
        onSelect = onConditionTypeChange,
    )
    Spacer(Modifier.height(SwSpacing.Md))
    SwTextField(
        label = stringResource(R.string.rule_create_value_label),
        value = state.conditionValue,
        onValueChange = onConditionValueChange,
        placeholder = stringResource(R.string.rule_create_value_placeholder),
        hint = stringResource(R.string.rule_create_value_hint),
        error = state.valueError?.let {
            stringResource(
                when (it) {
                    CreateRuleState.ValueError.Required -> R.string.rule_error_value_required
                    CreateRuleState.ValueError.TooLong -> R.string.rule_error_value_long
                },
            )
        },
    )
    Spacer(Modifier.height(SwSpacing.Md))
    SwDropdownField(
        label = stringResource(R.string.rule_create_category_label),
        selectedLabel = state.targetCategory?.let { categoryLabel(it) }.orEmpty(),
        options = state.categories.map { it to categoryLabel(it) },
        onSelect = { onTargetCategoryChange(it.takeIf { code -> code.isNotEmpty() }) },
        error = state.categoryError?.let {
            stringResource(R.string.rule_error_category_required)
        },
    )
    Spacer(Modifier.height(SwSpacing.Md))
    SwTextField(
        label = stringResource(R.string.rule_create_priority_label),
        value = state.priority,
        onValueChange = onPriorityChange,
        placeholder = stringResource(R.string.rule_create_priority_placeholder),
        hint = stringResource(R.string.rule_create_priority_hint),
        error = state.priorityError?.let {
            stringResource(
                when (it) {
                    CreateRuleState.PriorityError.Required -> R.string.rule_error_priority_required
                    CreateRuleState.PriorityError.Invalid -> R.string.rule_error_priority_invalid
                    CreateRuleState.PriorityError.Conflict -> R.string.rule_error_priority_conflict
                },
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    state.formError?.let { error ->
        Spacer(Modifier.height(SwSpacing.Md))
        SwBanner(
            title = stringResource(
                when (error) {
                    CreateRuleState.FormError.Ambiguous -> R.string.planning_error_ambiguous_title
                    CreateRuleState.FormError.Server -> R.string.state_server_title
                    CreateRuleState.FormError.Generic -> R.string.state_generic_title
                },
            ),
            text = stringResource(
                when (error) {
                    CreateRuleState.FormError.Ambiguous -> R.string.planning_error_ambiguous_body
                    CreateRuleState.FormError.Server -> R.string.state_server_body
                    CreateRuleState.FormError.Generic -> R.string.state_generic_body
                },
            ),
            kind = SwBannerKind.Error,
        )
    }
    Spacer(Modifier.height(SwSpacing.Lg))
    Button(
        onClick = onSubmit,
        enabled = !state.isSubmitting,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(
            stringResource(
                if (state.isSubmitting) {
                    R.string.txn_create_submitting
                } else {
                    R.string.rule_create_submit
                },
            ),
        )
    }
    Spacer(Modifier.height(SwSpacing.Sm))
    OutlinedButton(
        onClick = onBack,
        enabled = !state.isSubmitting,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(stringResource(R.string.txn_create_cancel))
    }
    Spacer(Modifier.height(SwSpacing.Md))
}
