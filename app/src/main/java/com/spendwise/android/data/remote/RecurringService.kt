package com.spendwise.android.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * `GET/POST/DELETE /api/recurring-transactions` (verified against
 * `RecurringTransactionController` at backend `8194daa`).
 *
 * `DELETE /{id}` **deactivates** — it returns 200 with the updated
 * template (`active=false`), never deletes generated transactions, and
 * there is no reactivate endpoint. Foreign/unknown id → 404;
 * foreign `accountId` on create → 400 (DomainException).
 * All endpoints are authenticated — a 401 drives `Session.Expired`.
 */
interface RecurringService {

    @GET("api/recurring-transactions")
    suspend fun list(): List<RecurringTransactionDto>

    @GET("api/recurring-transactions/{id}")
    suspend fun getById(@Path("id") id: String): RecurringTransactionDto

    @POST("api/recurring-transactions")
    suspend fun create(@Body body: CreateRecurringRequestDto): RecurringTransactionDto

    /**
     * `PATCH /api/recurring-transactions/{id}` — bounded edit of
     * future-generation inputs (REQ-BE-012, backend `eeb2c2f`). The
     * body carries only changed fields plus `clearCategory`/
     * `clearEndDate` flags; account/direction/currency/active are
     * immutable. Foreign/unknown id → 404; an empty patch or a
     * value+clear-flag pair → 400. Not replayed after transmit
     * (authenticator retries GET/HEAD only).
     */
    @PATCH("api/recurring-transactions/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: UpdateRecurringRequestDto,
    ): RecurringTransactionDto

    @DELETE("api/recurring-transactions/{id}")
    suspend fun deactivate(@Path("id") id: String): RecurringTransactionDto
}
