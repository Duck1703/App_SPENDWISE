package com.spendwise.android.feature.accounts

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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendwise.android.R
import com.spendwise.android.core.designsystem.AccountTile
import com.spendwise.android.core.designsystem.MoneyText
import com.spendwise.android.core.designsystem.SwCard
import com.spendwise.android.core.designsystem.SwColors
import com.spendwise.android.core.designsystem.SwHeader
import com.spendwise.android.core.designsystem.SwHeroPanel
import com.spendwise.android.core.designsystem.SwNetworkError
import com.spendwise.android.core.designsystem.SwServerError
import com.spendwise.android.core.designsystem.SwSkeletonRows
import com.spendwise.android.core.designsystem.SwSpacing
import com.spendwise.android.core.designsystem.SwStateBlock
import com.spendwise.android.core.designsystem.TransactionRow
import com.spendwise.android.core.model.Account
import com.spendwise.android.core.model.AccountType
import com.spendwise.android.core.ui.format.MoneyFormat

@Composable
fun AccountDetailScreen(
    state: AccountDetailUiState,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onAddTransaction: (String) -> Unit,
    onTransfer: (String) -> Unit,
    onRetryHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SwHeader(
            title = stringResource(R.string.account_detail_title),
            onBack = onNavigateBack,
            modifier = Modifier.padding(horizontal = SwSpacing.Lg, vertical = SwSpacing.Md),
        )
        when (state) {
            AccountDetailUiState.Loading -> SwSkeletonRows(
                count = 3,
                modifier = Modifier.padding(horizontal = SwSpacing.Lg),
            )
            is AccountDetailUiState.Error -> when (state.kind) {
                AccountsError.Network -> SwNetworkError(
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                AccountsError.Server -> SwServerError(
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                AccountsError.Generic -> SwStateBlock(
                    title = stringResource(R.string.state_generic_title),
                    text = stringResource(R.string.state_generic_body),
                    actionLabel = stringResource(R.string.action_try_again),
                    onAction = onRetry,
                    icon = R.drawable.ic_lucide_circle_alert,
                    isError = true,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            AccountDetailUiState.NotFound -> SwStateBlock(
                title = stringResource(R.string.account_detail_notfound_title),
                text = stringResource(R.string.account_detail_notfound_body),
                actionLabel = stringResource(R.string.account_detail_back_to_accounts),
                onAction = onNavigateBack,
                icon = R.drawable.ic_lucide_info,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            is AccountDetailUiState.Content -> AccountDetailContent(
                state = state,
                onOpenTransaction = onOpenTransaction,
                onAddTransaction = onAddTransaction,
                onTransfer = onTransfer,
                onRetryHistory = onRetryHistory,
            )
        }
    }
}

@Composable
private fun AccountDetailContent(
    state: AccountDetailUiState.Content,
    onOpenTransaction: (String) -> Unit,
    onAddTransaction: (String) -> Unit,
    onTransfer: (String) -> Unit,
    onRetryHistory: () -> Unit,
) {
    val account = state.account
    // M7 perf: LazyColumn — an account's transaction history is
    // unbounded (backend has no pagination); rows compose lazily.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = SwSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(SwSpacing.Md),
    ) {
        item(key = "hero") {
        SwHeroPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountTile(type = account.type, size = 52.dp)
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(SwSpacing.Md))
            Text(
                text = account.name,
                style = MaterialTheme.typography.titleLarge,
                color = SwColors.Mint,
            )
            Spacer(Modifier.height(SwSpacing.Xs))
            Text(
                text = stringResource(R.string.account_detail_balance_label),
                style = MaterialTheme.typography.labelMedium,
                color = SwColors.Mint.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(2.dp))
            MoneyText(
                money = account.balance,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp),
                color = SwColors.OnAccent,
                currencyColor = SwColors.Mint,
            )
        }
        }

        item(key = "fields") {
        SwCard {
            Column(modifier = Modifier.padding(SwSpacing.Md)) {
                DetailField(
                    label = stringResource(R.string.account_detail_field_type),
                    value = stringResource(
                        when (account.type) {
                            AccountType.CASH -> R.string.account_type_cash
                            AccountType.BANK -> R.string.account_type_bank
                            AccountType.UNKNOWN -> R.string.account_type_unknown
                        },
                    ),
                )
                FieldDivider()
                DetailField(
                    label = stringResource(R.string.account_detail_field_currency),
                    value = currencyDisplayName(account.balance.currency.code),
                )
                FieldDivider()
                DetailField(
                    label = stringResource(R.string.account_detail_field_opening),
                    value = MoneyFormat.format(account.openingBalance) +
                        " " + account.openingBalance.currency.code,
                )
            }
        }
        }

        // Account-scoped history (P3A §11): section owns its own failure
        // — the header above is never sunk by it.
        item(key = "history-header") {
            HistoryHeader(
                onAddTransaction = { onAddTransaction(account.id) },
                onTransfer = if (state.hasTransferPeer) {
                    { onTransfer(account.id) }
                } else {
                    null
                },
            )
        }
        when (val history = state.history) {
            AccountDetailUiState.AccountHistory.Loading -> item(key = "history-loading") {
                SwSkeletonRows(count = 2)
            }
            AccountDetailUiState.AccountHistory.Error -> item(key = "history-error") {
                HistoryError(onRetryHistory = onRetryHistory)
            }
            is AccountDetailUiState.AccountHistory.Items ->
                if (history.transactions.isEmpty()) {
                    item(key = "history-empty") {
                        Text(
                            text = stringResource(R.string.account_detail_history_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SwColors.Muted,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }
                } else {
                    items(
                        items = history.transactions,
                        key = { it.id },
                        contentType = { "transaction" },
                    ) { transaction ->
                        SwCard {
                            TransactionRow(
                                transaction = transaction,
                                accountName = account.name,
                                onClick = { onOpenTransaction(transaction.id) },
                            )
                        }
                    }
                }
        }
        item(key = "bottom-space") { Spacer(Modifier.height(SwSpacing.Md)) }
    }
}

@Composable
private fun HistoryHeader(
    onAddTransaction: () -> Unit,
    onTransfer: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.account_detail_history_title),
            style = MaterialTheme.typography.titleMedium,
            color = SwColors.Ink,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 2.dp),
        )
        Spacer(Modifier.weight(1f))
        if (onTransfer != null) {
            TextButton(
                onClick = onTransfer,
                modifier = Modifier.heightIn(min = SwSpacing.TouchTargetMin),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_arrow_right_left),
                    contentDescription = null,
                    tint = SwColors.Brand,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.account_detail_transfer),
                    color = SwColors.Brand,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        TextButton(
            onClick = onAddTransaction,
            modifier = Modifier.heightIn(min = SwSpacing.TouchTargetMin),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lucide_plus),
                contentDescription = null,
                tint = SwColors.Brand,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.account_detail_add_transaction),
                color = SwColors.Brand,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun HistoryError(onRetryHistory: () -> Unit) {
    SwCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SwSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lucide_circle_alert),
                contentDescription = null,
                tint = SwColors.Error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(SwSpacing.Sm))
            Text(
                text = stringResource(R.string.account_detail_history_error),
                style = MaterialTheme.typography.bodyMedium,
                color = SwColors.Muted,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onRetryHistory,
                modifier = Modifier.heightIn(min = SwSpacing.TouchTargetMin),
            ) {
                Text(stringResource(R.string.action_try_again))
            }
        }
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = SwColors.Muted,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = SwColors.Ink,
        )
    }
}

@Composable
private fun FieldDivider() {
    HorizontalDivider(color = SwColors.Line)
}

/** "USD — US Dollar" when the JDK knows the code, else the raw code. */
private fun currencyDisplayName(code: String): String = runCatching {
    val currency = java.util.Currency.getInstance(code)
    "$code — ${currency.getDisplayName(java.util.Locale.getDefault())}"
}.getOrDefault(code)
