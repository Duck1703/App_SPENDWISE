package com.spendwise.android.data.remote

import com.spendwise.android.core.serialization.BigDecimalSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * `POST /api/transfers` (M12/REQ-BE-003). `currency` is optional on the
 * server — omission defaults to the source account's — but this app
 * always sends it explicitly, locked to the source account (the pair is
 * same-currency by domain rule; a cross-currency request is a client
 * defect the server would reject anyway).
 */
@Serializable
data class CreateTransferRequestDto(
    val sourceAccountId: String,
    val destinationAccountId: String,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val currency: String,
)

/**
 * `201 Created` response: the shared linkage key plus both legs as full
 * `TransactionResponse`s (the M11-era shape, `occurredOn` = server
 * today). `transferRef` is returned only here — it never appears on
 * transaction reads.
 */
@Serializable
data class TransferResponseDto(
    val transferRef: String,
    val outTransaction: TransactionDto,
    val inTransaction: TransactionDto,
)
