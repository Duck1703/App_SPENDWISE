package com.spendwise.android.data.remote

import com.spendwise.android.core.serialization.BigDecimalSerializer
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * `TransactionResponse` (BACKEND-CONTRACT §11):
 * `{id, accountId, type, amount, currency, category, sourceRef}`.
 *
 * `occurredOn`, `transferRef`, and `description` are **not** on the wire —
 * no date is ever rendered, and the request-side `description` is a
 * rule-engine hint the server never returns. `category` is the code
 * string (null = uncategorized); `type` arrives as the enum name.
 */
@Serializable
data class TransactionDto(
    val id: String,
    val accountId: String,
    val type: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val currency: String,
    val category: String? = null,
    val sourceRef: String? = null,
)

/**
 * `CreateTransactionRequest`: `{accountId, type, amount, currency,
 * category?, description?}` — no `occurredOn` exists (the server stamps
 * `LocalDate.now()`). `amount` is strictly positive; direction is carried
 * by `type` (`INCOME`/`EXPENSE` — transfer types are server-rejected).
 * `description` is the optional categorization hint; it is NOT persisted
 * as transaction text. Null optionals stay off the wire.
 */
@Serializable
data class CreateTransactionRequestDto(
    val accountId: String,
    val type: String,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val currency: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val category: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val description: String? = null,
)

/** `CategoryResponse` = `{value}` — `value` is the category code itself. */
@Serializable
data class CategoryDto(val value: String)
