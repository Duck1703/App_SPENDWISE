package com.spendwise.android.feature.export

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.spendwise.android.R
import com.spendwise.android.core.designsystem.SwBanner
import com.spendwise.android.core.designsystem.SwBannerKind
import com.spendwise.android.core.designsystem.SwCard
import com.spendwise.android.core.designsystem.SwColors
import com.spendwise.android.core.designsystem.SwDropdownField
import com.spendwise.android.core.designsystem.SwHeader
import com.spendwise.android.core.designsystem.SwNetworkError
import com.spendwise.android.core.designsystem.SwServerError
import com.spendwise.android.core.designsystem.SwSkeletonRows
import com.spendwise.android.core.designsystem.SwSpacing
import com.spendwise.android.core.designsystem.SwStateBlock

/**
 * Ledger Export (M9): scope dropdown → Export CSV → system save dialog
 * → honest result. The transit phases (download/save) are modal —
 * leaving mid-copy would make the save outcome ambiguous.
 */
@Composable
fun ExportLedgerScreen(
    state: ExportUiState,
    onScopeSelected: (String?) -> Unit,
    onExport: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = SwColors.Ground) {
        Column(modifier = Modifier.fillMaxSize()) {
            SwHeader(
                title = stringResource(R.string.export_title),
                subtitle = stringResource(R.string.export_subtitle),
                onBack = onBack,
                modifier = Modifier.padding(horizontal = SwSpacing.Lg, vertical = SwSpacing.Md),
            )
            when (state) {
                ExportUiState.Loading -> SwSkeletonRows(modifier = Modifier.padding(SwSpacing.Lg))
                is ExportUiState.Error -> when (state.kind) {
                    ExportError.Network -> SwNetworkError(onRetry)
                    ExportError.Server -> SwServerError(onRetry)
                    else -> SwStateBlock(
                        title = stringResource(R.string.export_error_generic_title),
                        text = stringResource(R.string.export_error_generic_body),
                        actionLabel = stringResource(R.string.action_try_again),
                        onAction = onRetry,
                        isError = true,
                    )
                }
                is ExportUiState.Ready -> ReadyForm(
                    state = state,
                    onScopeSelected = onScopeSelected,
                    onExport = onExport,
                )
            }
        }
    }
}

@Composable
private fun ReadyForm(
    state: ExportUiState.Ready,
    onScopeSelected: (String?) -> Unit,
    onExport: () -> Unit,
) {
    val busy = state.phase != ExportUiState.Phase.Idle
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SwSpacing.Lg),
    ) {
        SwCard {
            Column(Modifier.padding(SwSpacing.Lg)) {
                SwDropdownField(
                    label = stringResource(R.string.export_scope_label),
                    selectedLabel = state.accounts
                        .firstOrNull { it.id == state.selectedAccountId }?.name
                        ?: stringResource(R.string.export_scope_all),
                    options = listOf(
                        ALL_ACCOUNTS_VALUE to stringResource(R.string.export_scope_all),
                    ) + state.accounts.map { it.id to it.name },
                    onSelect = { value ->
                        onScopeSelected(value.takeUnless { it == ALL_ACCOUNTS_VALUE })
                    },
                    hint = stringResource(R.string.export_scope_hint),
                )
            }
        }

        if (state.saved) {
            Spacer(Modifier.height(SwSpacing.Md))
            SwBanner(
                title = stringResource(R.string.export_saved_title),
                text = stringResource(R.string.export_saved_body),
            )
        }
        state.error?.let { error ->
            Spacer(Modifier.height(SwSpacing.Md))
            SwBanner(
                title = stringResource(R.string.export_error_title),
                text = stringResource(errorBodyRes(error)),
                kind = SwBannerKind.Error,
            )
        }

        Spacer(Modifier.height(SwSpacing.Lg))
        Button(
            onClick = onExport,
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SwSpacing.TouchTargetMin),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(SwSpacing.Sm))
                Text(
                    text = stringResource(
                        if (state.phase == ExportUiState.Phase.Saving) {
                            R.string.export_saving
                        } else {
                            R.string.export_downloading
                        },
                    ),
                )
            } else {
                Text(stringResource(R.string.export_action))
            }
        }
    }
}

private const val ALL_ACCOUNTS_VALUE = "all"

private fun errorBodyRes(error: ExportError): Int = when (error) {
    ExportError.Network -> R.string.export_error_network
    ExportError.Server -> R.string.export_error_server
    ExportError.AccountUnavailable -> R.string.export_error_account
    ExportError.SaveFailed -> R.string.export_error_save
    ExportError.Generic -> R.string.export_error_generic_body
}
