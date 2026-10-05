package com.spendwise.android.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendwise.android.R
import com.spendwise.android.core.designsystem.CategoryTile
import com.spendwise.android.core.designsystem.SwBanner
import com.spendwise.android.core.designsystem.SwBannerKind
import com.spendwise.android.core.designsystem.SwCard
import com.spendwise.android.core.designsystem.SwColors
import com.spendwise.android.core.designsystem.SwHeader
import com.spendwise.android.core.designsystem.SwNetworkError
import com.spendwise.android.core.designsystem.SwRadii
import com.spendwise.android.core.designsystem.SwServerError
import com.spendwise.android.core.designsystem.SwSkeletonRows
import com.spendwise.android.core.designsystem.SwSpacing
import com.spendwise.android.core.designsystem.SwStateBlock
import com.spendwise.android.core.designsystem.amountColor
import com.spendwise.android.core.designsystem.categoryLabel
import com.spendwise.android.core.designsystem.provenanceLabel
import com.spendwise.android.core.designsystem.signedAmountText
import com.spendwise.android.core.designsystem.transactionTypeLabel
import com.spendwise.android.core.model.Transaction

/**
 * Transaction detail (S10): read-only — every field comes from the
 * already-fetched list row (no `GET /{id}` exists). The ledger is
 * append-only: no edit / delete / correct / reverse / offset affordance
 * exists anywhere (Human P3A override — PRODUCT §21 dropped for V1).
 */
@Composable
fun TransactionDetailScreen(
    state: TransactionDetailUiState,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SwHeader(
            title = stringResource(R.string.txn_detail_title),
            onBack = onNavigateBack,
            modifier = Modifier.padding(horizontal = SwSpacing.Lg, vertical = SwSpacing.Md),
        )
        when (state) {
            TransactionDetailUiState.Loading -> SwSkeletonRows(
                count = 3,
                modifier = Modifier.padding(horizontal = SwSpacing.Lg),
            )
            is TransactionDetailUiState.Error -> when (state.kind) {
                TransactionsError.Network -> SwNetworkError(
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                TransactionsError.Server -> SwServerError(
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                TransactionsError.Generic -> SwStateBlock(
                    title = stringResource(R.string.state_generic_title),
                    text = stringResource(R.string.state_generic_body),
                    actionLabel = stringResource(R.string.action_try_again),
                    onAction = onRetry,
                    icon = R.drawable.ic_lucide_circle_alert,
                    isError = true,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            TransactionDetailUiState.NotFound -> SwStateBlock(
                title = stringResource(R.string.txn_detail_notfound_title),
                text = stringResource(R.string.txn_detail_notfound_body),
                actionLabel = stringResource(R.string.txn_detail_back_to_list),
                onAction = onNavigateBack,
                icon = R.drawable.ic_lucide_info,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            is TransactionDetailUiState.Content -> DetailContent(
                transaction = state.transaction,
                accountName = state.accountName,
            )
        }
    }
}

@Composable
private fun DetailContent(transaction: Transaction, accountName: String?) {
    val categoryText = transaction.category?.let { categoryLabel(it) }
        ?: stringResource(R.string.txn_category_uncategorized)
    val typeLabel = transactionTypeLabel(transaction.type)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SwSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(SwSpacing.Md),
    ) {
        SwCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SwSpacing.Lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CategoryTile(code = transaction.category, size = 60.dp)
                Spacer(Modifier.height(SwSpacing.Sm))
                Text(
                    text = categoryText,
                    style = MaterialTheme.typography.titleMedium,
                    color = SwColors.Ink,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(SwSpacing.Xs))
                Text(
                    text = signedAmountText(transaction),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 34.sp,
                        fontFeatureSettings = "tnum",
                    ),
                    color = amountColor(transaction.type),
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.height(SwSpacing.Sm))
                DirectionBadge(transaction)
            }
        }

        SwCard {
            Column(modifier = Modifier.padding(SwSpacing.Md)) {
                DetailField(
                    label = stringResource(R.string.txn_detail_field_account),
                    value = accountName ?: "—",
                )
                FieldDivider()
                DetailField(
                    label = stringResource(R.string.txn_detail_field_currency),
                    value = transaction.amount.currency.code,
                )
                FieldDivider()
                DetailField(
                    label = stringResource(R.string.txn_detail_field_source),
                    value = provenanceLabel(transaction.provenance) ?: "—",
                )
            }
        }

        SwBanner(
            title = "",
            text = stringResource(R.string.txn_detail_readonly),
            kind = SwBannerKind.Info,
        )
        Spacer(Modifier.height(SwSpacing.Md))
    }
}

/** Income/Expense (or Transfer) pill — sign + icon + word, never color alone. */
@Composable
private fun DirectionBadge(transaction: Transaction) {
    val isCredit = transaction.type.isCredit
    val icon = when {
        transaction.type == com.spendwise.android.core.model.TransactionType.UNKNOWN ->
            R.drawable.ic_lucide_ellipsis
        isCredit -> R.drawable.ic_lucide_arrow_down
        else -> R.drawable.ic_lucide_arrow_up
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(SwRadii.Pill))
            .background(amountColor(transaction.type).copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = amountColor(transaction.type),
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = transactionTypeLabel(transaction.type),
            style = MaterialTheme.typography.labelLarge,
            color = amountColor(transaction.type),
            fontWeight = FontWeight.SemiBold,
        )
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
