package com.spendwise.android.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.spendwise.android.core.designsystem.TransactionRow
import com.spendwise.android.core.model.Account
import com.spendwise.android.core.model.AccountType
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.ui.format.MoneyFormat
import java.math.BigDecimal

/**
 * Home (S05, PRODUCT §17, P3A §7): "How much do I have, and how did
 * this month go?" — hero totals → account carousel → recent activity.
 * Breakdown and budget cards are the deliberate P3A omission; the
 * import quick-card is M5. Per AD-019 the FAB is extended near the top
 * and collapses to a compact icon button after meaningful scroll.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onAddTransaction: () -> Unit,
    onOpenAccount: (String) -> Unit,
    onOpenTransaction: (String) -> Unit,
    onSeeAllActivity: () -> Unit,
    onCreateAccount: () -> Unit,
    onRefresh: () -> Unit,
    onRetryTotals: () -> Unit,
    onRetryActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    // Collapse once the user has scrolled past roughly the header.
    val collapsed by remember { derivedStateOf { scrollState.value > 96 } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = SwColors.Ground,
        floatingActionButton = {
            val fabLabel = stringResource(R.string.home_add_transaction)
            ExtendedFloatingActionButton(
                onClick = onAddTransaction,
                expanded = !collapsed,
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_lucide_plus),
                        contentDescription = null,
                    )
                },
                text = { Text(fabLabel) },
                containerColor = SwColors.Brand,
                contentColor = SwColors.OnAccent,
                // One merged label in BOTH forms — when the text is
                // collapsed away the icon-only button still announces
                // the action (was contentDescription=null on the icon).
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = fabLabel
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            SwHeader(
                title = stringResource(R.string.home_title),
                modifier = Modifier.padding(horizontal = SwSpacing.Lg, vertical = SwSpacing.Md),
            )
            PullToRefreshBox(
                isRefreshing = (state as? HomeUiState.Content)?.isRefreshing == true,
                onRefresh = onRefresh,
                state = rememberPullToRefreshState(),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                when (state) {
                    HomeUiState.Loading -> SwSkeletonRows(
                        count = 4,
                        modifier = Modifier.padding(horizontal = SwSpacing.Lg),
                    )
                    is HomeUiState.Error -> when (state.kind) {
                        HomeError.Network -> SwNetworkError(
                            onRetry = onRefresh,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        HomeError.Server -> SwServerError(
                            onRetry = onRefresh,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        HomeError.Generic -> SwStateBlock(
                            title = stringResource(R.string.state_generic_title),
                            text = stringResource(R.string.state_generic_body),
                            actionLabel = stringResource(R.string.action_try_again),
                            onAction = onRefresh,
                            icon = R.drawable.ic_lucide_circle_alert,
                            isError = true,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    HomeUiState.Empty -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        SwStateBlock(
                            title = stringResource(R.string.home_empty_title),
                            text = stringResource(R.string.home_empty_body),
                            actionLabel = stringResource(R.string.home_empty_action),
                            onAction = onCreateAccount,
                            illustration = R.drawable.coin_3d,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                    is HomeUiState.Content -> HomeContent(
                        state = state,
                        scrollState = scrollState,
                        onOpenAccount = onOpenAccount,
                        onOpenTransaction = onOpenTransaction,
                        onSeeAllActivity = onSeeAllActivity,
                        onRetryTotals = onRetryTotals,
                        onRetryActivity = onRetryActivity,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Content,
    scrollState: androidx.compose.foundation.ScrollState,
    onOpenAccount: (String) -> Unit,
    onOpenTransaction: (String) -> Unit,
    onSeeAllActivity: () -> Unit,
    onRetryTotals: () -> Unit,
    onRetryActivity: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = SwSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(SwSpacing.Md),
    ) {
        if (state.refreshError != null) {
            SwBanner(
                title = stringResource(R.string.home_refresh_error_title),
                text = stringResource(R.string.home_refresh_error_body),
                kind = SwBannerKind.Error,
            )
        }

        MonthHero(totals = state.totals, onRetry = onRetryTotals)

        Text(
            text = stringResource(R.string.home_accounts_section),
            style = MaterialTheme.typography.titleMedium,
            color = SwColors.Ink,
        )
        AccountCarousel(accounts = state.accounts, onOpenAccount = onOpenAccount)

        ActivityPanel(
            activity = state.activity,
            accounts = state.accounts,
            onOpenTransaction = onOpenTransaction,
            onSeeAll = onSeeAllActivity,
            onRetry = onRetryActivity,
        )
        Spacer(Modifier.height(96.dp)) // FAB clearance
    }
}

/**
 * "This month" hero (§17 hard rule): figures render ONLY under a
 * uniform account currency — the response carries no currency field,
 * so the label comes from the accounts, never a guess. Mixed /
 * 400 → the honest totals-unavailable state; a failed fetch degrades
 * to an inline retry without sinking the screen.
 */
@Composable
private fun MonthHero(totals: MonthTotals, onRetry: () -> Unit) {
    SwHeroPanel {
        Text(
            text = stringResource(R.string.home_this_month),
            style = MaterialTheme.typography.labelLarge,
            color = SwColors.Mint,
        )
        Spacer(Modifier.height(SwSpacing.Md))
        when (totals) {
            MonthTotals.Loading -> Text(
                text = stringResource(R.string.home_totals_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = SwColors.Mint,
            )
            is MonthTotals.Loaded -> {
                MonthFigure(
                    label = stringResource(R.string.home_income_label),
                    icon = R.drawable.ic_lucide_arrow_up,
                    amount = totals.income,
                    currency = totals.currency.code,
                )
                Spacer(Modifier.height(SwSpacing.Md))
                MonthFigure(
                    label = stringResource(R.string.home_expense_label),
                    icon = R.drawable.ic_lucide_arrow_down,
                    amount = totals.expense,
                    currency = totals.currency.code,
                    invert = true,
                )
            }
            MonthTotals.Unavailable -> {
                Text(
                    text = stringResource(R.string.home_totals_unavailable_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = SwColors.OnAccent,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(SwSpacing.Sm))
                Text(
                    text = stringResource(R.string.home_totals_unavailable_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SwColors.Mint,
                )
            }
            is MonthTotals.Failed -> {
                Text(
                    text = stringResource(R.string.home_totals_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SwColors.Mint,
                )
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.heightIn(min = SwSpacing.TouchTargetMin),
                ) {
                    Text(
                        text = stringResource(R.string.action_try_again),
                        color = SwColors.OnAccent,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * One hero figure row — direction arrow + label + exact figure.
 * [invert] prefixes the spend figure with the same `−` (U+2212) the
 * transaction rows use — sign glyphs stay consistent app-wide.
 */
@Composable
private fun MonthFigure(
    label: String,
    icon: Int,
    amount: BigDecimal,
    currency: String,
    invert: Boolean = false,
) {
    val figureStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    // Merged figure semantics — "Income 4,500 VND" / "Spent −1,200 VND"
    // read as one unit, never an orphan minus-sign node.
    val figureCd = "$label ${if (invert) "−" else ""}${
        MoneyFormat.format(amount, CurrencyCode(currency))
    } $currency"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics { contentDescription = figureCd },
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = SwColors.Mint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(SwSpacing.Sm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = SwColors.Mint,
            modifier = Modifier.weight(1f),
        )
        if (invert) {
            Text(
                text = "−",
                style = figureStyle,
                color = SwColors.OnAccent,
            )
        }
        MoneyText(
            money = Money(amount = amount, currency = CurrencyCode(currency)),
            style = figureStyle,
            color = SwColors.OnAccent,
            currencyColor = SwColors.Mint,
        )
    }
}

/** Account card carousel (P3A §7) — tap a card → account detail. */
@Composable
private fun AccountCarousel(accounts: List<Account>, onOpenAccount: (String) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(SwSpacing.Md),
        contentPadding = PaddingValues(vertical = SwSpacing.Xs),
    ) {
        items(accounts, key = { it.id }) { account ->
            AccountCard(account = account, onClick = { onOpenAccount(account.id) })
        }
    }
}

@Composable
private fun AccountCard(account: Account, onClick: () -> Unit) {
    val typeLabel = homeAccountTypeLabel(account.type)
    val cd = stringResource(
        R.string.account_row_cd,
        account.name,
        typeLabel,
        MoneyFormat.format(account.balance),
        account.balance.currency.code,
    )
    SwCard(
        modifier = Modifier
            .width(180.dp)
            .clearAndSetSemantics { contentDescription = cd }
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(SwSpacing.Md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountTile(type = account.type, size = 36.dp)
                Spacer(Modifier.weight(1f))
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_chevron_right),
                    contentDescription = null,
                    tint = SwColors.Muted,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(SwSpacing.Md))
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
            Spacer(Modifier.height(SwSpacing.Sm))
            MoneyText(
                money = account.balance,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = SwColors.Ink,
                currencyColor = SwColors.Muted,
            )
        }
    }
}

/**
 * Recent activity panel (P3A §7): the first rows of the unfiltered
 * list as returned — no chronology is promised anywhere in the app.
 * "See all" lands on the Transactions tab; a section failure degrades
 * to inline retry without sinking the screen.
 */
@Composable
private fun ActivityPanel(
    activity: ActivityState,
    accounts: List<Account>,
    onOpenTransaction: (String) -> Unit,
    onSeeAll: () -> Unit,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_activity_section),
            style = MaterialTheme.typography.titleMedium,
            color = SwColors.Ink,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onSeeAll,
            modifier = Modifier.heightIn(min = SwSpacing.TouchTargetMin),
        ) {
            Text(stringResource(R.string.home_activity_see_all))
        }
    }
    SwCard {
        Column {
            when (activity) {
                ActivityState.Loading -> Text(
                    text = stringResource(R.string.home_activity_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SwColors.Muted,
                    modifier = Modifier.padding(SwSpacing.Md),
                )
                is ActivityState.Items -> {
                    if (activity.transactions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.home_activity_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SwColors.Muted,
                            modifier = Modifier.padding(SwSpacing.Md),
                        )
                    } else {
                        val names = accounts.associate { it.id to it.name }
                        activity.transactions.forEachIndexed { index, transaction ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = SwColors.Line,
                                    modifier = Modifier.padding(horizontal = SwSpacing.Md),
                                )
                            }
                            TransactionRow(
                                transaction = transaction,
                                accountName = names[transaction.accountId],
                                onClick = { onOpenTransaction(transaction.id) },
                            )
                        }
                    }
                }
                is ActivityState.Failed -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SwSpacing.Md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.home_activity_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SwColors.Muted,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.heightIn(min = SwSpacing.TouchTargetMin),
                    ) {
                        Text(stringResource(R.string.action_try_again))
                    }
                }
            }
        }
    }
}

@Composable
private fun homeAccountTypeLabel(type: AccountType): String = stringResource(
    when (type) {
        AccountType.CASH -> R.string.account_type_cash
        AccountType.BANK -> R.string.account_type_bank
        AccountType.UNKNOWN -> R.string.account_type_unknown
    },
)
