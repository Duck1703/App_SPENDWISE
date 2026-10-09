package com.spendwise.android.feature.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.spendwise.android.R
import com.spendwise.android.core.designsystem.SwBanner
import com.spendwise.android.core.designsystem.SwBannerKind
import com.spendwise.android.core.designsystem.SwCard
import com.spendwise.android.core.designsystem.SwColors
import com.spendwise.android.core.designsystem.SwHeader
import com.spendwise.android.core.designsystem.SwNetworkError
import com.spendwise.android.core.designsystem.SwServerError
import com.spendwise.android.core.designsystem.SwSkeletonRows
import com.spendwise.android.core.designsystem.SwSpacing
import com.spendwise.android.core.designsystem.SwStateBlock
import com.spendwise.android.core.designsystem.SwTextField

/**
 * Account edit (M19, REQ-BE-006): rename + archive/unarchive as one
 * sparse PATCH. The archive switch carries honest copy — the ledger and
 * balances are untouched; the account leaves entry pickers only.
 */
@Composable
fun EditAccountScreen(
    state: EditAccountState,
    onNameChange: (String) -> Unit,
    onArchivedChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SwHeader(
            title = stringResource(R.string.account_edit_title),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = SwSpacing.Lg, vertical = SwSpacing.Md),
        )
        when (state) {
            EditAccountState.Loading -> SwSkeletonRows(
                count = 3,
                modifier = Modifier.padding(horizontal = SwSpacing.Lg),
            )
            is EditAccountState.Error -> when (state.kind) {
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
            EditAccountState.NotFound -> SwStateBlock(
                title = stringResource(R.string.account_detail_notfound_title),
                text = stringResource(R.string.account_detail_notfound_body),
                actionLabel = stringResource(R.string.account_detail_back_to_accounts),
                onAction = onBack,
                icon = R.drawable.ic_lucide_info,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            is EditAccountState.Form -> EditForm(
                state = state,
                onNameChange = onNameChange,
                onArchivedChange = onArchivedChange,
                onSave = onSave,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun EditForm(
    state: EditAccountState.Form,
    onNameChange: (String) -> Unit,
    onArchivedChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
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
                title = formErrorTitle(error),
                text = formErrorBody(error),
                kind = SwBannerKind.Error,
            )
        }

        SwTextField(
            label = stringResource(R.string.account_create_name_label),
            value = state.name,
            onValueChange = onNameChange,
            hint = stringResource(R.string.account_create_name_hint),
            placeholder = stringResource(R.string.account_create_name_placeholder),
            error = when (state.nameError) {
                EditAccountState.Form.NameError.Required ->
                    stringResource(R.string.account_error_name_required)
                EditAccountState.Form.NameError.Invalid ->
                    stringResource(R.string.account_error_name_long)
                null -> null
            },
            enabled = !state.isSubmitting,
        )

        // Archive (REQ-BE-006): operational flag, honest copy — ledger,
        // balances, and history are untouched; entry pickers only.
        SwCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SwSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.account_edit_archive_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = SwColors.Ink,
                    )
                    Spacer(Modifier.height(SwSpacing.Xs))
                    Text(
                        text = stringResource(R.string.account_edit_archive_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = SwColors.Muted,
                    )
                }
                Spacer(Modifier.width(SwSpacing.Md))
                Switch(
                    checked = state.archived,
                    onCheckedChange = onArchivedChange,
                    enabled = !state.isSubmitting,
                )
            }
        }

        Spacer(Modifier.height(SwSpacing.Sm))
        Button(
            onClick = onSave,
            enabled = state.hasChanges && !state.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(
                stringResource(
                    if (state.isSubmitting) {
                        R.string.txn_create_submitting
                    } else {
                        R.string.recurring_edit_save
                    },
                ),
            )
        }
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
}

@Composable
private fun formErrorTitle(error: EditAccountState.Form.FormError): String = stringResource(
    when (error) {
        EditAccountState.Form.FormError.Ambiguous -> R.string.account_edit_error_ambiguous_title
        EditAccountState.Form.FormError.Conflict -> R.string.account_edit_error_conflict_title
        EditAccountState.Form.FormError.Server -> R.string.state_server_title
        EditAccountState.Form.FormError.Generic -> R.string.state_generic_title
    },
)

@Composable
private fun formErrorBody(error: EditAccountState.Form.FormError): String = stringResource(
    when (error) {
        EditAccountState.Form.FormError.Ambiguous -> R.string.account_edit_error_ambiguous_body
        EditAccountState.Form.FormError.Conflict -> R.string.account_edit_error_conflict_body
        EditAccountState.Form.FormError.Server -> R.string.state_server_body
        EditAccountState.Form.FormError.Generic -> R.string.state_generic_body
    },
)
