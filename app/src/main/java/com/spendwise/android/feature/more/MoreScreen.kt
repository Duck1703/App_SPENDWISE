package com.spendwise.android.feature.more

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.spendwise.android.R
import com.spendwise.android.core.designsystem.SwColors
import com.spendwise.android.core.designsystem.SwSpacing

/**
 * More hub — M1: placeholder content + the sign-out affordance with a
 * confirmation dialog (§13). Stateless; the route wrapper supplies the
 * ViewModel state.
 */
@Composable
fun MoreScreen(
    state: MoreUiState,
    onSignOut: () -> Unit,
    onOpenImport: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenRecurring: () -> Unit = {},
    onOpenBudgets: () -> Unit = {},
    onOpenRules: () -> Unit = {},
) {
    var confirmVisible by remember { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(SwSpacing.Lg),
        ) {
            Text(
                text = stringResource(R.string.more_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(SwSpacing.Lg))
            MoreRow(
                label = stringResource(R.string.more_import_label),
                description = stringResource(R.string.more_import_desc),
                icon = R.drawable.ic_lucide_upload,
                onClick = onOpenImport,
            )
            MoreRow(
                label = stringResource(R.string.more_recurring_label),
                description = stringResource(R.string.more_recurring_desc),
                icon = R.drawable.ic_lucide_repeat,
                onClick = onOpenRecurring,
            )
            MoreRow(
                label = stringResource(R.string.more_budgets_label),
                description = stringResource(R.string.more_budgets_desc),
                icon = R.drawable.ic_lucide_banknote,
                onClick = onOpenBudgets,
            )
            MoreRow(
                label = stringResource(R.string.more_rules_label),
                description = stringResource(R.string.more_rules_desc),
                icon = R.drawable.ic_lucide_ticket,
                onClick = onOpenRules,
            )
            Spacer(Modifier.height(SwSpacing.Lg))
            HorizontalDivider()
            Spacer(Modifier.height(SwSpacing.Lg))
            OutlinedButton(
                onClick = { confirmVisible = true },
                enabled = !state.isSigningOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = SwSpacing.TouchTargetMin),
            ) {
                Text(
                    text = stringResource(
                        if (state.isSigningOut) R.string.more_signing_out else R.string.more_sign_out,
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (confirmVisible) {
        AlertDialog(
            onDismissRequest = { confirmVisible = false },
            title = { Text(stringResource(R.string.sign_out_dialog_title)) },
            text = { Text(stringResource(R.string.sign_out_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmVisible = false
                        onSignOut()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.sign_out_dialog_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmVisible = false }) {
                    Text(stringResource(R.string.sign_out_dialog_cancel))
                }
            },
        )
    }
}

/** More list row (P3A §6): icon + label + subtitle + chevron, ≥48dp. */
@Composable
private fun MoreRow(
    label: String,
    description: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SwSpacing.TouchTargetMin)
            .clearAndSetSemantics { contentDescription = label }
            .clickable(onClick = onClick)
            .padding(vertical = SwSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = SwColors.Brand,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(SwSpacing.Md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_lucide_chevron_right),
            contentDescription = null,
            tint = SwColors.Muted,
            modifier = Modifier.size(20.dp),
        )
    }
}
