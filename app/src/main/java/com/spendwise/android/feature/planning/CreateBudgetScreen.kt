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
import com.spendwise.android.core.designsystem.SwSkeletonRows
import com.spendwise.android.core.designsystem.SwSpacing
import com.spendwise.android.core.designsystem.SwStateBlock
import com.spendwise.android.core.designsystem.SwTextField
import com.spendwise.android.core.designsystem.categoryLabel
import java.util.Currency
import java.util.Locale

/**
 * Budget create (S19): category · month (`YYYY-MM`) · limit · currency.
 * Duplicate (category, period) is the honest 409 copy — budgets are
 * create/read-only (BE-011).
 */
@Composable
fun CreateBudgetScreen(
    state: CreateBudgetState,
    onCategoryChange: (String?) -> Unit,
    onPeriodChange: (String) -> Unit,
    onLimitChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
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
                title = stringResource(R.string.budget_create_title),
                subtitle = stringResource(R.string.budget_create_subtitle),
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
                    onCategoryChange = onCategoryChange,
                    onPeriodChange = onPeriodChange,
                    onLimitChange = onLimitChange,
                    onCurrencyChange = onCurrencyChange,
                    onSubmit = onSubmit,
                    onBack = onBack,
                )
            }
        }
    }
}

@Composable
private fun Form(
    state: CreateBudgetState,
    onCategoryChange: (String?) -> Unit,
    onPeriodChange: (String) -> Unit,
    onLimitChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    SwDropdownField(
        label = stringResource(R.string.budget_create_category_label),
        selectedLabel = state.category?.let { categoryLabel(it) }.orEmpty(),
        options = state.categories.map { it to categoryLabel(it) },
        onSelect = { onCategoryChange(it.takeIf { code -> code.isNotEmpty() }) },
        error = state.categoryError?.let {
            stringResource(R.string.budget_error_category_required)
        },
    )
    Spacer(Modifier.height(SwSpacing.Md))
    SwTextField(
        label = stringResource(R.string.budget_create_period_label),
        value = state.period,
        onValueChange = onPeriodChange,
        placeholder = stringResource(R.string.budget_create_period_placeholder),
        hint = stringResource(R.string.budget_create_period_hint),
        error = state.periodError?.let {
            stringResource(
                when (it) {
                    CreateBudgetState.PeriodError.Required -> R.string.budget_error_period_required
                    CreateBudgetState.PeriodError.Invalid -> R.string.budget_error_period_invalid
                },
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
    )
    Spacer(Modifier.height(SwSpacing.Md))
    SwTextField(
        label = stringResource(R.string.budget_create_limit_label),
        value = state.limit,
        onValueChange = onLimitChange,
        placeholder = stringResource(R.string.budget_create_limit_placeholder),
        error = state.limitError?.let {
            stringResource(
                when (it) {
                    CreateBudgetState.LimitError.Required -> R.string.txn_error_amount_required
                    CreateBudgetState.LimitError.Invalid -> R.string.txn_error_amount_invalid
                    CreateBudgetState.LimitError.NotPositive -> R.string.txn_error_amount_positive
                    CreateBudgetState.LimitError.TooPrecise -> R.string.txn_error_amount_precise
                    CreateBudgetState.LimitError.TooLarge -> R.string.txn_error_amount_large
                },
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
    Spacer(Modifier.height(SwSpacing.Md))
    SwDropdownField(
        label = stringResource(R.string.account_create_currency_label),
        selectedLabel = state.currency?.let { currencyDisplayLabel(it) }.orEmpty(),
        options = state.currencies.map { it to currencyDisplayLabel(it) },
        onSelect = onCurrencyChange,
        hint = stringResource(R.string.budget_create_currency_hint),
        error = state.currencyError?.let {
            stringResource(R.string.budget_error_currency_required)
        },
    )
    state.formError?.let { error ->
        Spacer(Modifier.height(SwSpacing.Md))
        SwBanner(
            title = stringResource(
                when (error) {
                    CreateBudgetState.FormError.Conflict -> R.string.budget_error_conflict_title
                    CreateBudgetState.FormError.Ambiguous -> R.string.planning_error_ambiguous_title
                    CreateBudgetState.FormError.Server -> R.string.state_server_title
                    CreateBudgetState.FormError.Generic -> R.string.state_generic_title
                },
            ),
            text = stringResource(
                when (error) {
                    CreateBudgetState.FormError.Conflict -> R.string.budget_error_conflict_body
                    CreateBudgetState.FormError.Ambiguous -> R.string.planning_error_ambiguous_body
                    CreateBudgetState.FormError.Server -> R.string.state_server_body
                    CreateBudgetState.FormError.Generic -> R.string.state_generic_body
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
                    R.string.budget_create_submit
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

/** `VND` → `VND — Vietnamese Dong` (same display convention as account create). */
private fun currencyDisplayLabel(code: String): String = runCatching {
    val currency = Currency.getInstance(code)
    "$code — ${currency.getDisplayName(Locale.getDefault())}"
}.getOrDefault(code)
