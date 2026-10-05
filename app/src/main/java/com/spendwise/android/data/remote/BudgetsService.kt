package com.spendwise.android.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * `GET/POST /api/budgets`, `GET /api/budgets/{id}`,
 * `GET /api/budgets/{id}/calculation` (verified against
 * `BudgetController` at backend `8194daa`).
 *
 * There is no PUT/PATCH/DELETE — budgets are create/read-only (BE-011).
 * Duplicate (user, category, period) → 409; unknown category or
 * malformed period → 400; foreign id → 404. All endpoints are
 * authenticated — a 401 drives `Session.Expired`.
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
}
