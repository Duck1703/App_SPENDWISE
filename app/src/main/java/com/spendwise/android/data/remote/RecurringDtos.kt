package com.spendwise.android.data.remote

import com.spendwise.android.core.serialization.BigDecimalSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * `RecurringTransactionResponse` / `CreateRecurringTransactionRequest` /
 * `UpdateRecurringTransactionRequest` (verified against
 * `RecurringTransactionController`, `RecurringTransactionResponse`,
 * `CreateRecurringTransactionRequest` at backend `8194daa`;
 * `UpdateRecurringTransactionRequest` at `eeb2c2f` + audit `133fe18`).
 *
 * Dates are ISO `YYYY-MM-DD` (`LocalDate` on the wire); `direction` and
 * `frequency` arrive as enum names; `category` is the code or null.
 */
@Serializable
data class RecurringTransactionDto(
    val id: String,
    val accountId: String,
    val direction: String,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val currency: String,
    val category: String? = null,
    val frequency: String,
    val nextDueDate: String,
    val endDate: String? = null,
    val active: Boolean,
)

/**
 * `UpdateRecurringTransactionRequest` for `PATCH
 * /api/recurring-transactions/{id}` (REQ-BE-012, backend `eeb2c2f`).
 * Every field is optional and absent fields stay off the wire —
 * kotlinx.serialization's default `explicitNulls = false` drops null
 * properties — so `null` here means "not part of the patch", never
 * "set null".
 * Clearing category/endDate is the server's explicit
 * `clearCategory`/`clearEndDate` flags; sending a value plus its flag
 * is a server 400.
 */
@Serializable
data class UpdateRecurringRequestDto(
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal? = null,
    val category: String? = null,
    val clearCategory: Boolean = false,
    val frequency: String? = null,
    val nextDueDate: String? = null,
    val endDate: String? = null,
    val clearEndDate: Boolean = false,
)

/**
 * `{accountId, direction, amount, currency, category?, frequency,
 * nextDueDate, endDate?}` — `direction` is `INCOME`/`EXPENSE`,
 * `frequency` is `WEEKLY`/`MONTHLY`, dates ISO `YYYY-MM-DD`. Null
 * optionals stay off the wire. `currency` is locked to the chosen
 * account's currency client-side (PRODUCT §158 defect mitigation — the
 * server does not cross-check).
 */
@Serializable
data class CreateRecurringRequestDto(
    val accountId: String,
    val direction: String,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val currency: String,
    val category: String? = null,
    val frequency: String,
    val nextDueDate: String,
    val endDate: String? = null,
)
