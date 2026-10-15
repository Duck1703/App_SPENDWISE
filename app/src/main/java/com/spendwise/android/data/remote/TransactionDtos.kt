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
 * `description` is the persisted note (M17/REQ-BE-010) — null when the
 * transaction was created without one (all pre-M17 rows, recurring and
 * transfer rows). `transferRef` is **not** on the wire. `category` is
 * the code string (null = uncategorized); `type` arrives as the enum
 * name.
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
    val description: String? = null,
)

/**
 * `CreateTransactionRequest`: `{accountId, type, amount, currency,
 * category?, description?, occurredOn}` — M11: Android always sends the
 * user-selected date explicitly as ISO `yyyy-MM-dd`. The field is
 * optional on the server (omission stamps `LocalDate.now()` — the
 * pre-M11 client contract), but this app never relies on the fallback.
 * `amount` is strictly positive; direction is carried by `type`
 * (`INCOME`/`EXPENSE` — transfer types are server-rejected).
 * `description` is the optional note: M17/REQ-BE-010 persists it (≤255
 * chars) and it still feeds the rules engine when no category is
 * chosen. Null optionals stay off the wire.
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
    // F2 idempotency: client-generated per-intent key. The backend stores it as the row's
    // sourceRef, so a retried submission dedupes on UNIQUE(user_id, source_ref) instead of
    // double-posting. Null keeps the legacy server-generated ref.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val clientRef: String? = null,
)

/**
 * `TransactionPageResponse` (M15/REQ-BE-007):
 * `{content:[TransactionResponse], page, size, totalElements,
 * totalPages, hasNext}` — the paginated ledger envelope from
 * `GET /api/transactions/page`. `totalElements` is the server-computed
 * total for the active filter set; `hasNext` drives the list's
 * load-more affordance.
 */
@Serializable
data class TransactionPageDto(
    val content: List<TransactionDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
)

/** `CategoryResponse` = `{value}` — `value` is the category code itself. */
@Serializable
data class CategoryDto(val value: String)
