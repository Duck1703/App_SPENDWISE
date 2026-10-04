package com.spendwise.android.feature.accounts

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendwise.android.R
import com.spendwise.android.core.designsystem.AccountTile
import com.spendwise.android.core.designsystem.MoneyText
import com.spendwise.android.core.designsystem.SwBanner
import com.spendwise.android.core.designsystem.SwBannerKind
import com.spendwise.android.core.designsystem.SwCard
import com.spendwise.android.core.designsystem.SwColors
import com.spendwise.android.core.designsystem.SwHeader
import com.spendwise.android.core.designsystem.SwHeroPanel
import com.spendwise.android.core.designsystem.SwNetworkError
import com.spendwise.android.core.designsystem.SwServerError
import com.spendwise.android.core.designsystem.SwSkeletonRows
import com.spendwise.android.core.designsystem.SwSpacing
import com.spendwise.android.core.designsystem.SwStateBlock
import com.spendwise.android.core.model.Account
import com.spendwise.android.core.model.AccountType
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.ui.format.MoneyFormat
import java.math.BigDecimal

@Composable
fun AccountsScreen(
    state: AccountsUiState,
    onOpenAccount: (String) -> Unit,
    onCreateAccount: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SwHeader(
            title = stringResource(R.string.accounts_title),
            modifier = Modifier.padding(horizontal = SwSpacing.Lg, vertical = SwSpacing.Md),
        )
        when (state) {
            AccountsUiState.Loading -> SwSkeletonRows(
                count = 4,
                modifier = Modifier.padding(horizontal = SwSpacing.Lg),
            )
            is AccountsUiState.Error -> when (state.kind) {
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
            AccountsUiState.Empty -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                SwStateBlock(
                    title = stringResource(R.string.accounts_empty_title),
                    text = stringResource(R.string.accounts_empty_body),
                    actionLabel = stringResource(R.string.accounts_empty_action),
                    onAction = onCreateAccount,
                    illustration = R.drawable.coin_3d,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            is AccountsUiState.Content -> AccountsContent(
                state = state,
                onOpenAccount = onOpenAccount,
                onCreateAccount = onCreateAccount,
            )
        }
    }
}

@Composable
private fun AccountsContent(
    state: AccountsUiState.Content,
    onOpenAccount: (String) -> Unit,
    onCreateAccount: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = SwSpacing.Lg,
            vertical = SwSpacing.Sm,
        ),
        verticalArrangement = Arrangement.spacedBy(SwSpacing.Md),
    ) {
        item(key = "hero") { AccountsHero(state.accounts) }
        if (state.mixedCurrencies) {
            item(key = "mixed-banner") {
                SwBanner(
                    title = stringResource(R.string.accounts_mixed_banner_title),
                    text = stringResource(R.string.accounts_mixed_banner_body),
                    kind = SwBannerKind.Info,
                )
            }
        }
        item(key = "list") {
            SwCard {
                Column {
                    state.accounts.forEachIndexed { index, account ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = SwColors.Line,
                                modifier = Modifier.padding(horizontal = SwSpacing.Md),
                            )
                        }
                        AccountRow(account = account, onClick = { onOpenAccount(account.id) })
                    }
                }
            }
        }
        item(key = "add") {
            Button(
                onClick = onCreateAccount,
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
                Text(stringResource(R.string.accounts_add))
            }
        }
        item(key = "bottom-space") { Spacer(Modifier.height(SwSpacing.Md)) }
    }
}

/**
 * Hero (P3A §7): single-currency → live total in that currency; mixed →
 * count only. A cross-currency sum is never computed (PRODUCT §18, AD-021).
 */
@Composable
private fun AccountsHero(accounts: List<Account>) {
    SwHeroPanel {
        val currencies = accounts.map { it.balance.currency }.distinct()
        if (currencies.size == 1) {
            Text(
                text = stringResource(R.string.accounts_hero_label),
                style = MaterialTheme.typography.labelLarge,
                color = SwColors.Mint,
            )
            Spacer(Modifier.height(SwSpacing.Sm))
            val total = accounts
                .map { it.balance.amount }
                .fold(BigDecimal.ZERO, BigDecimal::add)
            MoneyText(
                money = Money(amount = total, currency = currencies.single()),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp),
                color = SwColors.OnAccent,
                currencyColor = SwColors.Mint,
            )
        } else {
            Text(
                text = pluralStringResource(
                    R.plurals.accounts_hero_mixed_title,
                    accounts.size,
                    accounts.size,
                    currencies.size,
                ),
                style = MaterialTheme.typography.headlineMedium,
                color = SwColors.OnAccent,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(SwSpacing.Sm))
            Text(
                text = stringResource(R.string.accounts_hero_mixed_body),
                style = MaterialTheme.typography.bodyMedium,
                color = SwColors.Mint,
            )
        }
        Spacer(Modifier.height(SwSpacing.Md))
        Text(
            text = pluralStringResource(R.plurals.accounts_hero_count, accounts.size, accounts.size),
            style = MaterialTheme.typography.labelMedium,
            color = SwColors.Mint.copy(alpha = 0.8f),
        )
    }
}

/** Row (P3A list card): type tile · name+type · balance · chevron. */
@Composable
private fun AccountRow(account: Account, onClick: () -> Unit) {
    val typeLabel = accountTypeLabel(account.type)
    val cd = stringResource(
        R.string.account_row_cd,
        account.name,
        typeLabel,
        MoneyFormat.format(account.balance),
        account.balance.currency.code,
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
        AccountTile(type = account.type)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.titleMedium,
                color = SwColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = typeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = SwColors.Muted,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            MoneyText(
                money = account.balance,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = SwColors.Ink,
                currencyColor = SwColors.Muted,
            )
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

@Composable
private fun accountTypeLabel(type: AccountType): String = stringResource(
    when (type) {
        AccountType.CASH -> R.string.account_type_cash
        AccountType.BANK -> R.string.account_type_bank
        AccountType.UNKNOWN -> R.string.account_type_unknown
    },
)
