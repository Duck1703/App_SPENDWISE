package com.spendwise.android.data.remote

import com.spendwise.android.core.serialization.BigDecimalSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * `RecurringTransactionResponse` / `CreateRecurringTransactionRequest`
 * (verified against `RecurringTransactionController`,
 * `RecurringTransactionResponse`, `CreateRecurringTransactionRequest`
 * at backend `8194daa`).
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
