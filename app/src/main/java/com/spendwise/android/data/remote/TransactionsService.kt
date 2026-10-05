package com.spendwise.android.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * `GET/POST /api/transactions` (BACKEND-CONTRACT §11 — verified against
 * `TransactionController` at backend `8194daa`).
 *
 * List filters are the three real server params only: `accountId` (UUID —
 * unknown/foreign → 404), `type` (enum name — bad value → 400),
 * `category` (code — unknown value → empty list, no error). There is no
 * `GET /{id}`, no PUT/PATCH/DELETE, no pagination, no date or text
 * filter, no search — the ledger is append-only and read-only after
 * creation.
 *
 * All endpoints are authenticated — a 401 drives `Session.Expired`.
 */
interface TransactionsService {

    @GET("api/transactions")
    suspend fun list(
        @Query("accountId") accountId: String? = null,
        @Query("type") type: String? = null,
        @Query("category") category: String? = null,
    ): List<TransactionDto>

    @POST("api/transactions")
    suspend fun create(@Body body: CreateTransactionRequestDto): TransactionDto
}
