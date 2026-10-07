package com.spendwise.android.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * `GET/POST/PATCH/DELETE /api/budgets`, `GET /api/budgets/{id}`,
 * `GET /api/budgets/{id}/calculation` (verified against
 * `BudgetController` — PATCH/DELETE land in M14 per REQ-BE-011).
 *
 * `PATCH /{id}` corrects `limit` and/or `period` only (category and
 * currency are immutable — delete + recreate is the remedy). Empty
 * patch / invalid values → 400; (user, category, period) collision →
 * 409; foreign id → 404. `DELETE /{id}` hard-deletes the plan → 204;
 * missing/foreign → 404. All endpoints are authenticated — a 401
 * drives `Session.Expired`; PATCH/DELETE are non-idempotent methods
 * so the authenticator never replays them after transmission.
 */
interface BudgetsService {

    @GET("api/budgets")
    suspend fun list(): List<BudgetDto>

    @GET("api/budgets/{id}")
    suspend fun getById(@Path("id") id: String): BudgetDto

    @GET("api/budgets/{id}/calculation")
    suspend fun calculation(@Path("id") id: String): BudgetCalculationDto

    @POST("api/budgets")
    suspend fun create(@Body body: CreateBudgetRequestDto): BudgetDto

    @PATCH("api/budgets/{id}")
    suspend fun update(@Path("id") id: String, @Body body: UpdateBudgetRequestDto): BudgetDto

    @DELETE("api/budgets/{id}")
    suspend fun delete(@Path("id") id: String)
}
