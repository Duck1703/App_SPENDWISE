package com.spendwise.android.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spendwise.android.R
import com.spendwise.android.core.model.Transaction
import com.spendwise.android.core.model.TransactionProvenance
import com.spendwise.android.core.model.TransactionType
import com.spendwise.android.core.ui.format.DateFormat
import com.spendwise.android.core.ui.format.MoneyFormat
import java.util.Locale

/**
 * Category tile (P3A §8): fixed bg/fg pair per server category code,
 * icon inside, decorative — the adjacent label carries semantics.
 * Unknown future codes fall back to the uncategorized tile (display
 * only — the code itself is still rendered, never guessed).
 */
@Composable
fun CategoryTile(code: String?, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val (bg, fg, icon) = categoryTileStyle(code)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(size / 2),
        )
    }
}

private data class TileStyle(val bg: Color, val fg: Color, @param:DrawableRes val icon: Int)

private fun categoryTileStyle(code: String?): TileStyle = when (code) {
    "FOOD" -> TileStyle(Color(0xFFFDE8E2), Color(0xFFB2402C), R.drawable.ic_lucide_utensils)
    "TRANSPORT" -> TileStyle(Color(0xFFE3EDFA), Color(0xFF2C5C99), R.drawable.ic_lucide_car)
    "HOUSING" -> TileStyle(Color(0xFFFFF0DB), Color(0xFF8A5200), R.drawable.ic_lucide_house)
    "ENTERTAINMENT" -> TileStyle(Color(0xFFEEE8FB), Color(0xFF5A3EA6), R.drawable.ic_lucide_ticket)
    "SALARY" -> TileStyle(Color(0xFFE0F3E7), Color(0xFF17683B), R.drawable.ic_lucide_banknote)
    else -> TileStyle(Color(0xFFEBEFEE), Color(0xFF475659), R.drawable.ic_lucide_ellipsis)
}

/**
 * Human-facing label for a server category code: known codes map to
 * localized names; unknown/new codes render humanized ("COFFEE_SHOP" →
 * "Coffee shop") — honest display of the server value, never a fake.
 */
@Composable
fun categoryLabel(code: String): String = when (code) {
    "FOOD" -> stringResource(R.string.category_food)
    "TRANSPORT" -> stringResource(R.string.category_transport)
    "HOUSING" -> stringResource(R.string.category_housing)
    "ENTERTAINMENT" -> stringResource(R.string.category_entertainment)
    "SALARY" -> stringResource(R.string.category_salary)
    else -> code.lowercase(Locale.ROOT).replace('_', ' ')
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
}

/** Type label: Income/Expense words; transfer legs + unknowns render honestly. */
@Composable
fun transactionTypeLabel(type: TransactionType): String = stringResource(
    when (type) {
        TransactionType.INCOME -> R.string.txn_type_income
        TransactionType.EXPENSE -> R.string.txn_type_expense
        TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT -> R.string.txn_type_transfer
        TransactionType.UNKNOWN -> R.string.txn_type_unknown
    },
)

@Composable
fun provenanceLabel(provenance: TransactionProvenance): String? = when (provenance) {
    TransactionProvenance.MANUAL -> stringResource(R.string.txn_source_manual)
    TransactionProvenance.IMPORTED -> stringResource(R.string.txn_source_imported)
    TransactionProvenance.RECURRING -> stringResource(R.string.txn_source_recurring)
    TransactionProvenance.TRANSFER -> stringResource(R.string.txn_source_transfer)
    TransactionProvenance.UNKNOWN -> null
}

/**
 * Provenance chip (P3A row chip — shown only when the entry is not
 * Manual, matching the prototype's quiet-by-default intent; UNKNOWN
 * renders nothing rather than guessing).
 */
@Composable
fun ProvenanceBadge(provenance: TransactionProvenance, modifier: Modifier = Modifier) {
    val (label, icon) = when (provenance) {
        TransactionProvenance.IMPORTED ->
            stringResource(R.string.txn_source_imported) to R.drawable.ic_lucide_upload
        TransactionProvenance.RECURRING ->
            stringResource(R.string.txn_source_recurring) to R.drawable.ic_lucide_repeat
        TransactionProvenance.TRANSFER ->
            stringResource(R.string.txn_source_transfer) to R.drawable.ic_lucide_arrow_right_left
        else -> return
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(SwRadii.Pill))
            .background(SwColors.SurfaceMuted)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = SwColors.Muted,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = SwColors.Muted,
        )
    }
}

/**
 * Transaction row (P3A §8): category tile → category/account/provenance
 * → signed amount + direction word. Direction is carried by sign + word
 * + icon color AND text — never color alone (a11y floor).
 */
@Composable
fun TransactionRow(transaction: Transaction, accountName: String?, onClick: () -> Unit) {
    val typeLabel = transactionTypeLabel(transaction.type)
    val categoryText = transaction.category?.let { categoryLabel(it) }
        ?: stringResource(R.string.txn_category_uncategorized)
    val provenanceText = provenanceLabel(transaction.provenance)
        ?.let { ", $it" }
        .orEmpty()
    val signedAmount = signedAmountText(transaction)
    // %2$s carries sign+amount; %3$s carries the code — once.
    val sign = when {
        transaction.type == TransactionType.UNKNOWN -> ""
        transaction.type.isCredit -> "+"
        else -> "−"
    }
    val dateText = DateFormat.format(transaction.occurredOn)
    val cd = stringResource(
        R.string.txn_row_cd,
        typeLabel,
        "$sign${MoneyFormat.format(transaction.amount)}",
        transaction.amount.currency.code,
        categoryText,
        accountName ?: stringResource(R.string.txn_type_unknown),
        provenanceText,
        dateText,
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
        CategoryTile(code = transaction.category)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = categoryText,
                style = MaterialTheme.typography.titleMedium,
                color = SwColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = accountName.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = SwColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ProvenanceBadge(
                    provenance = transaction.provenance,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            // Server economic date (M11) — quiet third line; hierarchy
            // stays category → account → date, amount untouched.
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodySmall,
                color = SwColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = signedAmount,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum",
                ),
                color = amountColor(transaction.type),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = typeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = amountColor(transaction.type),
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

/** `+`/`-` sign + locale-formatted amount + code (e.g. `−1.234,50 VND`). */
fun signedAmountText(transaction: Transaction): String {
    val sign = when {
        transaction.type == TransactionType.UNKNOWN -> ""
        transaction.type.isCredit -> "+"
        else -> "−"
    }
    return "$sign${MoneyFormat.format(transaction.amount)} ${transaction.amount.currency.code}"
}

fun amountColor(type: TransactionType): Color = when (type) {
    TransactionType.INCOME -> SwColors.Income
    TransactionType.EXPENSE -> SwColors.Expense
    else -> SwColors.Ink
}

/**
 * One filter pill (P3A §3): label + chevron; selected → brand-deep "on"
 * chip whose face shows the selected value; opens an anchored dropdown.
 * ≥48dp target, role=Button semantics carry "X filter: value".
 */
@Composable
fun <T> SwFilterChip(
    label: String,
    selectedLabel: String?,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val cd = if (selectedLabel != null) {
        stringResource(R.string.filter_cd_selected, label, selectedLabel)
    } else {
        stringResource(R.string.filter_cd_default, label)
    }
    val selected = selectedLabel != null
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .heightIn(min = SwSpacing.TouchTargetMin)
                .clip(RoundedCornerShape(SwRadii.Pill))
                .background(if (selected) SwColors.BrandDeep else SwColors.Surface)
                .border(
                    width = 1.dp,
                    color = if (selected) SwColors.BrandDeep else SwColors.Line,
                    shape = RoundedCornerShape(SwRadii.Pill),
                )
                .clearAndSetSemantics { contentDescription = cd }
                .clickable { expanded = true }
                .padding(horizontal = SwSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedLabel ?: label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) SwColors.OnAccent else SwColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(R.drawable.ic_lucide_chevron_down),
                contentDescription = null,
                tint = if (selected) SwColors.OnAccent else SwColors.Muted,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}
