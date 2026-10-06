package com.spendwise.android.feature.transfers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spendwise.android.R
import com.spendwise.android.core.designsystem.SwBanner
import com.spendwise.android.core.designsystem.SwBannerKind
import com.spendwise.android.core.designsystem.SwColors
import com.spendwise.android.core.designsystem.SwDropdownField
import com.spendwise.android.core.designsystem.SwHeader
import com.spendwise.android.core.designsystem.SwSkeletonRows
import com.spendwise.android.core.designsystem.SwSpacing
import com.spendwise.android.core.designsystem.SwStateBlock
import com.spendwise.android.core.designsystem.SwTextField
import com.spendwise.android.core.ui.format.MoneyFormat

/**
 * Create transfer (M12/REQ-BE-003, PRODUCT §22): from-account,
 * to-account (excludes the source), amount in the source account's
 * locked currency. One atomic `POST /api/transfers` — no paired
 * manual transactions, no client-side ledger simulation.
 */
@Composable
fun CreateTransferScreen(
    state: CreateTransferUiState,
    onSourceChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNavigateBack: () -> Unit,
    onCreateAccount: () -> Unit,
    onRetryOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SwHeader(
            title = stringResource(R.string.transfer_create_title),
            subtitle = stringResource(R.string.transfer_create_subtitle),
            onBack = onNavigateBack,
            modifier = Modifier.padding(horizontal = SwSpacing.Lg, vertical = SwSpacing.Md),
        )
        when {
            state.optionsLoading -> SwSkeletonRows(
                count = 4,
                modifier = Modifier.padding(horizontal = SwSpacing.Lg),
            )
            state.optionsError -> SwStateBlock(
                title = stringResource(R.string.state_generic_title),
                text = stringResource(R.string.state_generic_body),
                actionLabel = stringResource(R.string.action_try_again),
                onAction = onRetryOptions,
                icon = R.drawable.ic_lucide_circle_alert,
                isError = true,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            !state.hasEnoughAccounts -> SwStateBlock(
                title = stringResource(R.string.transfer_create_need_two_title),
                text = stringResource(R.string.transfer_create_need_two_body),
                actionLabel = stringResource(R.string.transfer_create_need_two_action),
                onAction = onCreateAccount,
                icon = R.drawable.ic_lucide_wallet,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            else -> TransferForm(
                state = state,
                onSourceChange = onSourceChange,
                onDestinationChange = onDestinationChange,
                onAmountChange = onAmountChange,
                onSubmit = onSubmit,
                onNavigateBack = onNavigateBack,
            )
        }
    }
}

@Composable
private fun TransferForm(
    state: CreateTransferUiState,
    onSourceChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
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

        val currency = state.sourceAccount?.balance?.currency?.code
        SwDropdownField(
            label = stringResource(R.string.transfer_create_from_label),
            selectedLabel = state.sourceAccount
                ?.let { "${it.name} — ${MoneyFormat.format(it.balance)} ${it.balance.currency.code}" }
                ?: "",
            options = state.accounts.map {
                it.id to "${it.name} — ${MoneyFormat.format(it.balance)} ${it.balance.currency.code}"
            },
            onSelect = onSourceChange,
            hint = stringResource(R.string.transfer_create_from_hint),
            error = state.sourceError?.displayText(currency.orEmpty()),
        )

        SwDropdownField(
            label = stringResource(R.string.transfer_create_to_label),
            selectedLabel = state.destinationAccount
                ?.let { "${it.name} — ${MoneyFormat.format(it.balance)} ${it.balance.currency.code}" }
                ?: "",
            options = state.destinationOptions.map {
                it.id to "${it.name} — ${MoneyFormat.format(it.balance)} ${it.balance.currency.code}"
            },
            onSelect = onDestinationChange,
            hint = stringResource(R.string.transfer_create_to_hint),
            error = state.destinationError?.displayText(currency.orEmpty()),
        )

        SwTextField(
            label = stringResource(R.string.txn_create_amount_label),
            value = state.amount,
            onValueChange = onAmountChange,
            hint = stringResource(R.string.transfer_create_amount_hint),
            placeholder = stringResource(R.string.txn_create_amount_placeholder),
            error = state.amountError?.displayText(),
            enabled = !state.isSubmitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            trailingIcon = currency?.let {
                {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lucide_lock),
                            contentDescription = stringResource(
                                R.string.txn_create_currency_locked_cd,
                                currency,
                            ),
                            tint = SwColors.Muted,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = currency,
                            style = MaterialTheme.typography.labelLarge,
                            color = SwColors.Muted,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            },
        )

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
                        R.string.transfer_create_submitting
                    } else {
                        R.string.transfer_create_submit
                    },
                ),
            )
        }
        TextButton(
            onClick = onNavigateBack,
            enabled = !state.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(SwSpacing.TouchTargetMin),
        ) {
            Text(stringResource(R.string.txn_create_cancel))
        }
        Spacer(Modifier.height(SwSpacing.Lg))
    }
}

@Composable
private fun CreateTransferUiState.AmountError.displayText(): String = when (this) {
    CreateTransferUiState.AmountError.Required ->
        stringResource(R.string.txn_error_amount_required)
    CreateTransferUiState.AmountError.Invalid ->
        stringResource(R.string.txn_error_amount_invalid)
    CreateTransferUiState.AmountError.NotPositive ->
        stringResource(R.string.txn_error_amount_positive)
    CreateTransferUiState.AmountError.TooPrecise ->
        stringResource(R.string.txn_error_amount_precise)
    CreateTransferUiState.AmountError.TooLarge ->
        stringResource(R.string.txn_error_amount_large)
    is CreateTransferUiState.AmountError.Server -> message
}

@Composable
private fun CreateTransferUiState.AccountError.displayText(
    sourceCurrency: String,
): String = when (this) {
    CreateTransferUiState.AccountError.Required ->
        stringResource(R.string.txn_error_account_required)
    CreateTransferUiState.AccountError.SameAsSource ->
        stringResource(R.string.transfer_create_error_same_account)
    CreateTransferUiState.AccountError.CrossCurrency ->
        stringResource(R.string.transfer_create_error_cross_currency, sourceCurrency)
    is CreateTransferUiState.AccountError.Server -> message
}

@Composable
private fun CreateTransferUiState.FormError.displayText(): String = stringResource(
    when (this) {
        // Ambiguous transport failure — the POST may have landed. Honest
        // copy, no auto-retry, user re-checks before resubmitting (§44).
        CreateTransferUiState.FormError.Ambiguous -> R.string.transfer_create_error_ambiguous
        CreateTransferUiState.FormError.AccountUnavailable ->
            R.string.txn_create_error_account_gone
        CreateTransferUiState.FormError.Server -> R.string.auth_error_server
        CreateTransferUiState.FormError.Generic -> R.string.auth_error_generic
    },
)
