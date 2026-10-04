package com.spendwise.android.feature.accounts

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spendwise.android.R
import com.spendwise.android.core.designsystem.SwBanner
import com.spendwise.android.core.designsystem.SwBannerKind
import com.spendwise.android.core.designsystem.SwDropdownField
import com.spendwise.android.core.designsystem.SwHeader
import com.spendwise.android.core.designsystem.SwSegmentedControl
import com.spendwise.android.core.designsystem.SwSpacing
import com.spendwise.android.core.designsystem.SwTextField
import com.spendwise.android.core.model.AccountType
import java.util.Currency
import java.util.Locale

@Composable
fun CreateAccountScreen(
    state: CreateAccountUiState,
    onNameChange: (String) -> Unit,
    onTypeChange: (AccountType) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onOpeningBalanceChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SwHeader(
            title = stringResource(R.string.account_create_title),
            subtitle = stringResource(R.string.account_create_subtitle),
            onBack = onNavigateBack,
            modifier = Modifier.padding(horizontal = SwSpacing.Lg, vertical = SwSpacing.Md),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SwSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(SwSpacing.Md),
        ) {
            state.formError?.let { error ->
                SwBanner(
                    title = stringResource(R.string.auth_error_banner_cd),
                    text = error.displayText(),
                    kind = SwBannerKind.Error,
                )
            }

            SwTextField(
                label = stringResource(R.string.account_create_name_label),
                value = state.name,
                onValueChange = onNameChange,
                hint = stringResource(R.string.account_create_name_hint),
                placeholder = stringResource(R.string.account_create_name_placeholder),
                error = state.nameError?.displayText(),
                enabled = !state.isSubmitting,
            )

            SwSegmentedControl(
                label = stringResource(R.string.account_create_type_label),
                options = listOf(
                    AccountType.CASH to stringResource(R.string.account_type_cash),
                    AccountType.BANK to stringResource(R.string.account_type_bank),
                ),
                selected = state.type,
                onSelect = onTypeChange,
            )

            SwDropdownField(
                label = stringResource(R.string.account_create_currency_label),
                selectedLabel = state.currencyCode.currencyDisplayLabel(),
                options = rememberCurrencyOptions(),
                onSelect = onCurrencyChange,
                hint = stringResource(R.string.account_create_currency_hint),
            )

            SwTextField(
                label = stringResource(R.string.account_create_balance_label),
                value = state.openingBalance,
                onValueChange = onOpeningBalanceChange,
                hint = stringResource(R.string.account_create_balance_hint),
                placeholder = stringResource(R.string.account_create_balance_placeholder),
                error = state.balanceError?.displayText(),
                enabled = !state.isSubmitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )

            Spacer(Modifier.height(SwSpacing.Sm))
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
                            R.string.account_create_submitting
                        } else {
                            R.string.account_create_submit
                        },
                    ),
                )
            }
            Spacer(Modifier.height(SwSpacing.Lg))
        }
    }
}

@Composable
private fun CreateAccountUiState.NameError.displayText(): String = when (this) {
    CreateAccountUiState.NameError.Required ->
        stringResource(R.string.account_error_name_required)
    CreateAccountUiState.NameError.TooLong ->
        stringResource(R.string.account_error_name_long)
    is CreateAccountUiState.NameError.Server -> message
}

@Composable
private fun CreateAccountUiState.BalanceError.displayText(): String = when (this) {
    CreateAccountUiState.BalanceError.Invalid ->
        stringResource(R.string.account_error_balance_invalid)
    CreateAccountUiState.BalanceError.Negative ->
        stringResource(R.string.account_error_balance_negative)
    CreateAccountUiState.BalanceError.TooPrecise ->
        stringResource(R.string.account_error_balance_precise)
    CreateAccountUiState.BalanceError.TooLarge ->
        stringResource(R.string.account_error_balance_large)
    is CreateAccountUiState.BalanceError.Server -> message
}

@Composable
private fun CreateAccountUiState.CreateFormError.displayText(): String = stringResource(
    when (this) {
        CreateAccountUiState.CreateFormError.Network -> R.string.auth_error_network
        CreateAccountUiState.CreateFormError.Server -> R.string.auth_error_server
        CreateAccountUiState.CreateFormError.Generic -> R.string.auth_error_generic
    },
)

/** "USD — US Dollar" style label. */
private fun String.currencyDisplayLabel(): String = runCatching {
    val currency = Currency.getInstance(this)
    "$this — ${currency.getDisplayName(Locale.getDefault())}"
}.getOrDefault(this)

/** Open ISO-4217 set (PRODUCT §18): every JDK currency, sorted by code. */
@Composable
private fun rememberCurrencyOptions(): List<Pair<String, String>> =
    androidx.compose.runtime.remember {
        Currency.getAvailableCurrencies()
            .sortedBy { it.currencyCode }
            .map { it.currencyCode to "${it.currencyCode} — ${it.getDisplayName(Locale.getDefault())}" }
    }
