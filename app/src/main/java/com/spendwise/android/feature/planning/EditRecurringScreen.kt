package com.spendwise.android.feature.planning

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spendwise.android.R
import com.spendwise.android.core.designsystem.SwBanner
import com.spendwise.android.core.designsystem.SwBannerKind
import com.spendwise.android.core.designsystem.SwCard
import com.spendwise.android.core.designsystem.SwDropdownField
import com.spendwise.android.core.designsystem.SwHeader
import com.spendwise.android.core.designsystem.SwSegmentedControl
import com.spendwise.android.core.designsystem.SwSkeletonRows
import com.spendwise.android.core.designsystem.SwSpacing
import com.spendwise.android.core.designsystem.SwStateBlock
import com.spendwise.android.core.designsystem.SwTextField
import com.spendwise.android.core.designsystem.categoryLabel
import com.spendwise.android.core.designsystem.transactionTypeLabel
import com.spendwise.android.core.model.RecurringFrequency

/**
 * Recurring edit (M16, REQ-BE-012): the template's editable
 * future-generation inputs prefilled; immutable identity (direction,
 * account, currency) renders as read-only rows with an honest note.
 * Save is a sparse PATCH — disabled until a field actually differs.
 */
@Composable
fun EditRecurringScreen(
    state: EditRecurringState,
    onAmountChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onFrequencyChange: (RecurringFrequency) -> Unit,
    onNextDueDateChange: (String) -> Unit,
    onEndDateChange: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(SwSpacing.Lg),
        ) {
            SwHeader(
                title = stringResource(R.string.recurring_edit_title),
                subtitle = stringResource(R.string.recurring_edit_subtitle),
                onBack = onBack,
            )
            Spacer(Modifier.height(SwSpacing.Lg))
            when (state) {
                EditRecurringState.Loading -> SwSkeletonRows(count = 3)
                EditRecurringState.NotFound -> SwStateBlock(
                    title = stringResource(R.string.txn_detail_notfound_title),
                    text = stringResource(R.string.recurring_detail_notfound_body),
                    actionLabel = stringResource(R.string.txn_detail_back_to_list),
                    onAction = onBack,
                    icon = R.drawable.ic_lucide_circle_alert,
                    isError = true,
                )
                is EditRecurringState.Error -> SwStateBlock(
                    title = stringResource(R.string.state_generic_title),
                    text = stringResource(
                        when (state.kind) {
                            PlanningError.Network -> R.string.state_network_body
                            PlanningError.Server -> R.string.state_server_body
                            PlanningError.Generic -> R.string.state_generic_body
                        },
                    ),
                    actionLabel = stringResource(R.string.action_try_again),
                    onAction = onRetry,
                    icon = R.drawable.ic_lucide_circle_alert,
                    isError = true,
                )
                is EditRecurringState.Form -> Form(
                    state = state,
                    onAmountChange = onAmountChange,
                    onCategoryChange = onCategoryChange,
                    onFrequencyChange = onFrequencyChange,
                    onNextDueDateChange = onNextDueDateChange,
                    onEndDateChange = onEndDateChange,
                    onSave = onSave,
                    onBack = onBack,
                )
            }
        }
    }
}

@Composable
private fun Form(
    state: EditRecurringState.Form,
    onAmountChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onFrequencyChange: (RecurringFrequency) -> Unit,
    onNextDueDateChange: (String) -> Unit,
    onEndDateChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    // Immutable identity — read-only, with an honest reason.
    SwCard {
        Column(modifier = Modifier.padding(SwSpacing.Md)) {
            FieldRow(
                label = stringResource(R.string.txn_create_type_label),
                value = transactionTypeLabel(state.template.direction),
            )
            FieldRow(
                label = stringResource(R.string.recurring_create_account_label),
                value = state.accountName
                    ?: stringResource(R.string.planning_account_unknown),
            )
            FieldRow(
                label = stringResource(R.string.recurring_create_amount_label),
                value = state.template.amount.currency.code,
            )
            Text(
                text = stringResource(R.string.recurring_edit_locked_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(SwSpacing.Md))
    SwTextField(
        label = stringResource(R.string.recurring_create_amount_label),
        value = state.amount,
        onValueChange = onAmountChange,
        hint = stringResource(
            R.string.recurring_create_amount_hint,
            state.template.amount.currency.code,
        ),
        error = state.amountError?.let { amountErrorLabel(it) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
    Spacer(Modifier.height(SwSpacing.Md))
    SwDropdownField(
        label = stringResource(R.string.txn_create_category_label),
        selectedLabel = state.category?.let { categoryLabel(it) }
            ?: stringResource(R.string.txn_create_category_none),
        options = listOf(
            "" to stringResource(R.string.txn_create_category_none),
        ) + state.categories.map { it to categoryLabel(it) },
        onSelect = { onCategoryChange(it.takeIf { code -> code.isNotEmpty() }) },
    )
    Spacer(Modifier.height(SwSpacing.Md))
    SwSegmentedControl(
        label = stringResource(R.string.recurring_create_frequency_label),
        options = listOf(
            RecurringFrequency.WEEKLY to stringResource(R.string.recurring_frequency_weekly),
            RecurringFrequency.MONTHLY to stringResource(R.string.recurring_frequency_monthly),
        ),
        selected = state.frequency,
        onSelect = onFrequencyChange,
    )
    Spacer(Modifier.height(SwSpacing.Md))
    SwTextField(
        label = stringResource(R.string.recurring_create_next_due_label),
        value = state.nextDueDate,
        onValueChange = onNextDueDateChange,
        placeholder = stringResource(R.string.planning_date_placeholder),
        hint = stringResource(R.string.recurring_create_next_due_hint),
        error = state.dateError?.let { dateErrorLabel(it) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
    )
    Spacer(Modifier.height(SwSpacing.Md))
    SwTextField(
        label = stringResource(R.string.recurring_create_end_date_label),
        value = state.endDate,
        onValueChange = onEndDateChange,
        placeholder = stringResource(R.string.planning_date_placeholder),
        hint = stringResource(R.string.recurring_create_end_date_hint),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
    )
    state.formError?.let { error ->
        Spacer(Modifier.height(SwSpacing.Md))
        SwBanner(
            title = formErrorTitle(error),
            text = formErrorBody(error),
            kind = SwBannerKind.Error,
        )
    }
    Spacer(Modifier.height(SwSpacing.Lg))
    Button(
        onClick = onSave,
        enabled = state.hasChanges && !state.isSubmitting,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(
            stringResource(
                if (state.isSubmitting) {
                    R.string.txn_create_submitting
                } else {
                    R.string.recurring_edit_save
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

@Composable
private fun amountErrorLabel(error: EditRecurringState.Form.AmountError): String = stringResource(
    when (error) {
        EditRecurringState.Form.AmountError.Required -> R.string.txn_error_amount_required
        EditRecurringState.Form.AmountError.Invalid -> R.string.txn_error_amount_invalid
        EditRecurringState.Form.AmountError.NotPositive -> R.string.txn_error_amount_positive
        EditRecurringState.Form.AmountError.TooPrecise -> R.string.txn_error_amount_precise
        EditRecurringState.Form.AmountError.TooLarge -> R.string.txn_error_amount_large
    },
)

@Composable
private fun dateErrorLabel(error: EditRecurringState.Form.DateError): String = stringResource(
    when (error) {
        EditRecurringState.Form.DateError.Required -> R.string.recurring_error_next_due_required
        EditRecurringState.Form.DateError.Invalid -> R.string.recurring_error_date_invalid
        EditRecurringState.Form.DateError.EndBeforeNext -> R.string.recurring_error_end_before_next
    },
)

@Composable
private fun formErrorTitle(error: EditRecurringState.Form.FormError): String = stringResource(
    when (error) {
        EditRecurringState.Form.FormError.Ambiguous -> R.string.planning_error_ambiguous_title
        EditRecurringState.Form.FormError.Server -> R.string.state_server_title
        EditRecurringState.Form.FormError.Generic -> R.string.state_generic_title
    },
)

@Composable
private fun formErrorBody(error: EditRecurringState.Form.FormError): String = stringResource(
    when (error) {
        EditRecurringState.Form.FormError.Ambiguous -> R.string.planning_error_ambiguous_body
        EditRecurringState.Form.FormError.Server -> R.string.state_server_body
        EditRecurringState.Form.FormError.Generic -> R.string.state_generic_body
    },
)
