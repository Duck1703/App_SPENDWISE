package com.spendwise.android.data.remote

import com.spendwise.android.core.serialization.BigDecimalSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * `GET/POST /api/accounts` wire models (BACKEND-CONTRACT § accounts).
 *
 * `AccountResponse`: `{id, name, type, openingBalance, currency, balance}` —
 * `type` arrives as the enum name string; `openingBalance`/`balance` are
 * JSON numbers carried exactly as [BigDecimal].
 * `currency` is the opening/balance currency; the two are always equal.
 */
@Serializable
data class AccountDto(
    val id: String,
    val name: String,
    val type: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val openingBalance: BigDecimal,
    val currency: String,
    @Serializable(with = BigDecimalSerializer::class)
    val balance: BigDecimal,
    /** REQ-BE-006 (M19): server-added; default keeps pre-M19 payloads decodable. */
    val archived: Boolean = false,
)

/**
 * `UpdateAccountRequest` for `PATCH /api/accounts/{id}` (REQ-BE-006).
 * Sparse patch: absent fields stay off the wire (`explicitNulls = false`
 * drops nulls) — `null` here means "not part of the patch". An empty
 * patch is a server 400; foreign id 404; lost-update race 409.
 */
@Serializable
data class UpdateAccountRequestDto(
    val name: String? = null,
    val archived: Boolean? = null,
)

/**
 * `CreateAccountRequest`: `{name, type, openingBalance, currency}` —
 * `type` is the enum name (`CASH`/`BANK`); `openingBalance` is a JSON
 * number ≥ 0 with at most 2 fraction digits.
 */
@Serializable
data class CreateAccountRequestDto(
    val name: String,
    val type: String,
    @Serializable(with = BigDecimalSerializer::class)
    val openingBalance: BigDecimal,
    val currency: String,
)
