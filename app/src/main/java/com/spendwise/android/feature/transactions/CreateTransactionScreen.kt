package com.spendwise.android.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spendwise.android.R
import com.spendwise.android.core.designsystem.CategoryTile
import com.spendwise.android.core.designsystem.SwBanner
import com.spendwise.android.core.designsystem.SwBannerKind
import com.spendwise.android.core.designsystem.SwCard
import com.spendwise.android.core.designsystem.SwColors
import com.spendwise.android.core.designsystem.SwDropdownField
import com.spendwise.android.core.designsystem.SwHeader
import com.spendwise.android.core.designsystem.SwRadii
import com.spendwise.android.core.designsystem.SwSegmentedControl
import com.spendwise.android.core.designsystem.SwSkeletonRows
import com.spendwise.android.core.designsystem.SwSpacing
import com.spendwise.android.core.designsystem.SwStateBlock
import com.spendwise.android.core.designsystem.SwTextField
import com.spendwise.android.core.designsystem.categoryLabel
import com.spendwise.android.core.model.TransactionType
import com.spendwise.android.core.ui.format.DateFormat
import com.spendwise.android.core.ui.format.DateFormat.toPickerUtcMillis
import com.spendwise.android.core.ui.format.MoneyFormat
import java.time.LocalDate

/**
 * Create transaction (S11, P3A): type switch, account picker with locked
 * currency, amount, optional category tiles, occurrence date (M11 —
 * real user-selectable `occurredOn` via a Material3 date picker), and
 * optional categorization hint (never persisted).
 */
@Composable
fun CreateTransactionScreen(
    state: CreateTransactionUiState,
    onTypeChange: (TransactionType) -> Unit,
    onAccountChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onHintChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNavigateBack: () -> Unit,
    onCreateAccount: () -> Unit,
    onRetryOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SwHeader(
            title = stringResource(R.string.txn_create_title),
            subtitle = stringResource(R.string.txn_create_subtitle),
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
            state.accounts.isEmpty() -> SwStateBlock(
                title = stringResource(R.string.txn_create_no_accounts_title),
                text = stringResource(R.string.txn_create_no_accounts_body),
                actionLabel = stringResource(R.string.txn_create_no_accounts_action),
                onAction = onCreateAccount,
                icon = R.drawable.ic_lucide_wallet,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            else -> CreateForm(
                state = state,
                onTypeChange = onTypeChange,
                onAccountChange = onAccountChange,
                onAmountChange = onAmountChange,
                onCategoryChange = onCategoryChange,
                onDateChange = onDateChange,
                onHintChange = onHintChange,
                onSubmit = onSubmit,
                onNavigateBack = onNavigateBack,
            )
        }
    }
}

@Composable
private fun CreateForm(
    state: CreateTransactionUiState,
    onTypeChange: (TransactionType) -> Unit,
    onAccountChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onHintChange: (String) -> Unit,
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

        SwSegmentedControl(
            label = stringResource(R.string.txn_create_type_label),
            options = listOf(
                TransactionType.EXPENSE to stringResource(R.string.txn_type_expense),
                TransactionType.INCOME to stringResource(R.string.txn_type_income),
            ),
            selected = state.type,
            onSelect = onTypeChange,
        )

        SwDropdownField(
            label = stringResource(
                if (state.type == TransactionType.EXPENSE) {
                    R.string.txn_create_account_label_from
                } else {
                    R.string.txn_create_account_label_to
                },
            ),
            selectedLabel = state.selectedAccount
                ?.let { "${it.name} — ${MoneyFormat.format(it.balance)} ${it.balance.currency.code}" }
                ?: "",
            options = state.accounts.map {
                it.id to "${it.name} — ${MoneyFormat.format(it.balance)} ${it.balance.currency.code}"
            },
            onSelect = onAccountChange,
            hint = stringResource(R.string.txn_create_account_hint),
            error = state.accountError?.displayText(),
        )

        AmountField(
            state = state,
            onAmountChange = onAmountChange,
        )

        if (state.categories.isNotEmpty()) {
            CategoryGrid(
                categories = state.categories,
                selected = state.category,
                enabled = !state.isSubmitting,
                onSelect = onCategoryChange,
            )
        }

        SwTextField(
            label = stringResource(R.string.txn_create_hint_label),
            value = state.hint,
            onValueChange = onHintChange,
            hint = stringResource(R.string.txn_create_hint_support),
            placeholder = stringResource(R.string.txn_create_hint_placeholder),
            enabled = !state.isSubmitting,
        )

        DateField(
            occurredOn = state.occurredOn,
            enabled = !state.isSubmitting,
            onDateChange = onDateChange,
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
                        R.string.txn_create_submitting
                    } else {
                        R.string.txn_create_submit
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

/** Amount input with the locked account currency shown beside it. */
@Composable
private fun AmountField(
    state: CreateTransactionUiState,
    onAmountChange: (String) -> Unit,
) {
    val currency = state.selectedAccount?.balance?.currency?.code
    SwTextField(
        label = stringResource(R.string.txn_create_amount_label),
        value = state.amount,
        onValueChange = onAmountChange,
        hint = stringResource(R.string.txn_create_amount_hint),
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
}

/**
 * Occurrence date (M11): the same read-only field shape as the account
 * dropdown — a full-width tap target opens a Material3 date picker.
 * Selected value is the locale-formatted [LocalDate]; the merged
 * semantics announce "Date, selected X". The picker speaks UTC
 * epoch-millis — [DateFormat] pins conversion to UTC start-of-day so a
 * device in any timezone picks the calendar day shown, never ±1.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    occurredOn: LocalDate,
    enabled: Boolean,
    onDateChange: (LocalDate) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val label = stringResource(R.string.txn_create_date_label)
    val valueText = DateFormat.format(occurredOn)
    val mergedCd = stringResource(R.string.txn_create_date_cd, label, valueText)
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(SwSpacing.Xs))
        Box {
            OutlinedTextField(
                value = valueText,
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    // Visual only — the overlay below carries the single
                    // merged semantics node so TalkBack announces the
                    // control exactly once.
                    .clearAndSetSemantics {},
                readOnly = true,
                enabled = enabled,
                trailingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_lucide_calendar),
                        contentDescription = null,
                        tint = SwColors.Muted,
                    )
                },
                supportingText = {
                    Text(
                        stringResource(R.string.txn_create_date_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                shape = RoundedCornerShape(SwRadii.Md),
            )
            // Full-field tap target + merged label/value semantics — the
            // whole control is one announced action, like list rows.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(SwRadii.Md))
                    .clearAndSetSemantics { contentDescription = mergedCd }
                    .clickable(enabled = enabled, role = Role.Button) { showPicker = true },
            )
        }
    }
    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = occurredOn.toPickerUtcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis
                            ?.let { onDateChange(DateFormat.pickerUtcMillisToLocalDate(it)) }
                        showPicker = false
                    },
                ) {
                    Text(stringResource(R.string.import_result_done))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.txn_create_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/**
 * Optional category tiles (P3A): server codes + "No category". 48dp
 * targets; selection is a visible brand border, never color alone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryGrid(
    categories: List<String>,
    selected: String?,
    enabled: Boolean,
    onSelect: (String?) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.txn_create_category_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(SwSpacing.Xs))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(SwSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(SwSpacing.Sm),
        ) {
            CategoryChoice(
                code = null,
                label = stringResource(R.string.txn_create_category_none),
                selected = selected == null,
                enabled = enabled,
                onClick = { onSelect(null) },
            )
            categories.forEach { code ->
                CategoryChoice(
                    code = code,
                    label = categoryLabel(code),
                    selected = selected == code,
                    enabled = enabled,
                    onClick = { onSelect(code) },
                )
            }
        }
    }
}

@Composable
private fun CategoryChoice(
    code: String?,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(SwRadii.Md))
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(SwSpacing.TouchTargetMin)
                .clip(RoundedCornerShape(SwRadii.Md))
                .then(
                    if (selected) {
                        Modifier.border(
                            2.dp,
                            SwColors.BrandDeep,
                            RoundedCornerShape(SwRadii.Md),
                        )
                    } else {
                        Modifier
                    },
                )
                .background(
                    if (selected) {
                        SwColors.Mint
                    } else {
                        androidx.compose.ui.graphics.Color.Transparent
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            CategoryTile(code = code, size = SwSpacing.TouchTargetMin)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) SwColors.BrandDeep else SwColors.Muted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun CreateTransactionUiState.AmountError.displayText(): String = when (this) {
    CreateTransactionUiState.AmountError.Required ->
        stringResource(R.string.txn_error_amount_required)
    CreateTransactionUiState.AmountError.Invalid ->
        stringResource(R.string.txn_error_amount_invalid)
    CreateTransactionUiState.AmountError.NotPositive ->
        stringResource(R.string.txn_error_amount_positive)
    CreateTransactionUiState.AmountError.TooPrecise ->
        stringResource(R.string.txn_error_amount_precise)
    CreateTransactionUiState.AmountError.TooLarge ->
        stringResource(R.string.txn_error_amount_large)
    is CreateTransactionUiState.AmountError.Server -> message
}

@Composable
private fun CreateTransactionUiState.AccountError.displayText(): String = when (this) {
    CreateTransactionUiState.AccountError.Required ->
        stringResource(R.string.txn_error_account_required)
    is CreateTransactionUiState.AccountError.Server -> message
}

@Composable
private fun CreateTransactionUiState.CreateTxnFormError.displayText(): String = stringResource(
    when (this) {
        // Ambiguous transport failure — the POST may have landed. Honest
        // copy, no auto-retry, user re-checks before resubmitting (§44).
        CreateTransactionUiState.CreateTxnFormError.Ambiguous ->
            R.string.txn_create_error_ambiguous
        CreateTransactionUiState.CreateTxnFormError.AccountUnavailable ->
            R.string.txn_create_error_account_gone
        CreateTransactionUiState.CreateTxnFormError.Server -> R.string.auth_error_server
        CreateTransactionUiState.CreateTxnFormError.Generic -> R.string.auth_error_generic
    },
)
