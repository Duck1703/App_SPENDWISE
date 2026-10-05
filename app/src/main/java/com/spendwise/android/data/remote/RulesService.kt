package com.spendwise.android.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * `GET/POST/DELETE /api/categorization-rules` (verified against
 * `CategorizationRuleController` at backend `8194daa`).
 *
 * List is server-ordered by ascending priority. `DELETE /{id}`
 * **disables** — 200 with the updated rule (`enabled=false`), no
 * reactivate endpoint. Duplicate (user, priority) → 409; unknown
 * category or condition type → 400; foreign id → 404. All endpoints are
 * authenticated — a 401 drives `Session.Expired`.
 */
interface RulesService {

    @GET("api/categorization-rules")
    suspend fun list(): List<CategorizationRuleDto>

    @GET("api/categorization-rules/{id}")
    suspend fun getById(@Path("id") id: String): CategorizationRuleDto

    @POST("api/categorization-rules")
    suspend fun create(@Body body: CreateRuleRequestDto): CategorizationRuleDto

    @DELETE("api/categorization-rules/{id}")
    suspend fun disable(@Path("id") id: String): CategorizationRuleDto
}
