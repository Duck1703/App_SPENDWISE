package com.spendwise.android.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * `POST /api/transfers` (M12/REQ-BE-003 — verified against
 * `TransferController` at backend `bc6e0bb`+): atomically creates the
 * TRANSFER_OUT/TRANSFER_IN pair. Errors follow the platform mapping:
 * 401 → session expired, 404 → unknown/foreign account, 400 →
 * same-account / non-positive / cross-currency / validation `fields{}`.
 * No GET/PUT/PATCH/DELETE exists — the ledger stays append-only.
 */
interface TransfersService {

    @POST("api/transfers")
    suspend fun create(@Body body: CreateTransferRequestDto): TransferResponseDto
}
