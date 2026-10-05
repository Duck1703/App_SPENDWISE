package com.spendwise.android.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming

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

    /**
     * `GET /api/transactions/export` (M9 — `CsvExportController` at
     * backend `8194daa`): server-generated CSV of the caller's ledger.
     * `accountId` omitted exports the whole ledger; a foreign/unknown
     * `accountId` → 404. `200 text/csv` with
     * `Content-Disposition: attachment`.
     *
     * `@Streaming` + [ResponseBody]: the response is piped, never
     * materialized — the CSV is an opaque artifact this app transports
     * but never parses (BE-001 `occurredOn` stays inside the file).
     * [Response] wrapper is needed solely for the Content-Disposition
     * filename hint; non-2xx is thrown upstream by `ErrorInterceptor`.
     */
    @Streaming
    @GET("api/transactions/export")
    suspend fun export(@Query("accountId") accountId: String? = null): Response<ResponseBody>
}
