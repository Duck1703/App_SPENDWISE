package com.spendwise.android.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * `GET/POST /api/accounts` + `GET /{id}` + `PATCH /{id}`
 * (BACKEND-CONTRACT § accounts).
 *
 * All endpoints are **authenticated** — no `@PublicEndpoint`: a 401 here
 * means the session died and the interceptor drives `Session.Expired`.
 * No delete/transfer endpoints exist on the backend — none declared.
 */
interface AccountsService {

    @GET("api/accounts")
    suspend fun list(): List<AccountDto>

    @GET("api/accounts/{id}")
    suspend fun getById(@Path("id") id: String): AccountDto

    @POST("api/accounts")
    suspend fun create(@Body body: CreateAccountRequestDto): AccountDto

    /**
     * `PATCH /api/accounts/{id}` — bounded rename + archive/unarchive
     * (REQ-BE-006, M19). Sparse body; ≥1 field else 400. Foreign id →
     * 404; stale `version` → 409. `type`/`currency`/`balance` have no
     * patch surface. Never auto-retried (mutation no-replay).
     */
    @PATCH("api/accounts/{id}")
    suspend fun update(@Path("id") id: String, @Body body: UpdateAccountRequestDto): AccountDto
}
