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
import com.spendwise.android.core.designsystem.transactionTypeLabel
import com.spendwise.android.core.model.RecurringFrequency
import com.spendwise.android.core.model.TransactionType

/**
 * Recurring create (S16): direction · account · amount · locked
 * currency · category · frequency · dates. Currency is read-only — it
 * always equals the chosen account's currency (PRODUCT §158).
 */
@Composable
fun CreateRecurringScreen(
    state: CreateRecurringState,
    onDirectionChange: (TransactionType) -> Unit,
    onAccountChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onFrequencyChange: (RecurringFrequency) -> Unit,
    onNextDueDateChange: (String) -> Unit,
    onEndDateChange: (String) -> Unit,
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
                .imePadding()
                .padding(SwSpacing.Lg),
        ) {
            SwHeader(
                title = stringResource(R.string.recurring_create_title),
                subtitle = stringResource(R.string.recurring_create_subtitle),
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
                state.accounts.isEmpty() -> SwStateBlock(
                    title = stringResource(R.string.txn_create_no_accounts_title),
                    text = stringResource(R.string.txn_create_no_accounts_body),
                    icon = R.drawable.ic_lucide_wallet,
                )
                else -> Form(state, onDirectionChange, onAccountChange, onAmountChange,
                    onCategoryChange, onFrequencyChange, onNextDueDateChange, onEndDateChange,
                    onSubmit, onBack)
            }
        }
    }
}

@Composable
private fun Form(
    state: CreateRecurringState,
    onDirectionChange: (TransactionType) -> Unit,
    onAccountChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onFrequencyChange: (RecurringFrequency) -> Unit,
    onNextDueDateChange: (String) -> Unit,
    onEndDateChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    SwSegmentedControl(
        label = stringResource(R.string.txn_create_type_label),
        options = listOf(
            TransactionType.EXPENSE to stringResource(R.string.txn_type_expense),
            TransactionType.INCOME to stringResource(R.string.txn_type_income),
        ),
        selected = state.direction,
        onSelect = onDirectionChange,
    )
    Spacer(Modifier.height(SwSpacing.Md))
    SwDropdownField(
        label = stringResource(R.string.recurring_create_account_label),
        selectedLabel = state.selectedAccount?.name.orEmpty(),
        options = state.accounts.map { it.id to it.name },
        onSelect = onAccountChange,
        error = state.accountError?.let { stringResource(R.string.txn_error_account_required) },
    )
    Spacer(Modifier.height(SwSpacing.Md))
    SwTextField(
        label = stringResource(R.string.recurring_create_amount_label),
        value = state.amount,
        onValueChange = onAmountChange,
        hint = state.selectedAccount?.let {
            stringResource(R.string.recurring_create_amount_hint, it.balance.currency.code)
        },
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
                    R.string.recurring_create_submit
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
private fun amountErrorLabel(error: CreateRecurringState.AmountError): String = stringResource(
    when (error) {
        CreateRecurringState.AmountError.Required -> R.string.txn_error_amount_required
        CreateRecurringState.AmountError.Invalid -> R.string.txn_error_amount_invalid
        CreateRecurringState.AmountError.NotPositive -> R.string.txn_error_amount_positive
        CreateRecurringState.AmountError.TooPrecise -> R.string.txn_error_amount_precise
        CreateRecurringState.AmountError.TooLarge -> R.string.txn_error_amount_large
    },
)

@Composable
private fun dateErrorLabel(error: CreateRecurringState.DateError): String = stringResource(
    when (error) {
        CreateRecurringState.DateError.Required -> R.string.recurring_error_next_due_required
        CreateRecurringState.DateError.Invalid -> R.string.recurring_error_date_invalid
        CreateRecurringState.DateError.EndBeforeNext -> R.string.recurring_error_end_before_next
    },
)

@Composable
private fun formErrorTitle(error: CreateRecurringState.FormError): String = stringResource(
    when (error) {
        CreateRecurringState.FormError.Ambiguous -> R.string.planning_error_ambiguous_title
        CreateRecurringState.FormError.Server -> R.string.state_server_title
        CreateRecurringState.FormError.AccountUnavailable ->
            R.string.recurring_error_account_gone_title
        CreateRecurringState.FormError.Generic -> R.string.state_generic_title
    },
)

@Composable
private fun formErrorBody(error: CreateRecurringState.FormError): String = stringResource(
    when (error) {
        CreateRecurringState.FormError.Ambiguous -> R.string.planning_error_ambiguous_body
        CreateRecurringState.FormError.Server -> R.string.state_server_body
        CreateRecurringState.FormError.AccountUnavailable ->
            R.string.recurring_error_account_gone_body
        CreateRecurringState.FormError.Generic -> R.string.state_generic_body
    },
)
