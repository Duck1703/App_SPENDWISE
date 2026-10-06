package com.spendwise.android.data.remote

import com.spendwise.android.core.serialization.BigDecimalSerializer
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * `TransactionResponse` (BACKEND-CONTRACT §11, M11/REQ-BE-001):
 * `{id, accountId, type, amount, currency, category, sourceRef,
 * occurredOn}`.
 *
 * `occurredOn` is the server-authoritative economic date, ISO
 * `yyyy-MM-dd`, always present (`transactions.occurred_on` is NOT NULL).
 * `transferRef` and `description` are **not** on the wire — the
 * request-side `description` is a rule-engine hint the server never
 * returns. `category` is the code string (null = uncategorized); `type`
 * arrives as the enum name.
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
    val occurredOn: String,
)

/**
 * `CreateTransactionRequest`: `{accountId, type, amount, currency,
 * category?, description?, occurredOn}` — M11: Android always sends the
 * user-selected date explicitly as ISO `yyyy-MM-dd`. The field is
 * optional on the server (omission stamps `LocalDate.now()` — the
 * pre-M11 client contract), but this app never relies on the fallback.
 * `amount` is strictly positive; direction is carried by `type`
 * (`INCOME`/`EXPENSE` — transfer types are server-rejected).
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
    val occurredOn: String,
)

/** `CategoryResponse` = `{value}` — `value` is the category code itself. */
@Serializable
data class CategoryDto(val value: String)
