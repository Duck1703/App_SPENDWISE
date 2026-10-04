package com.spendwise.android.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * `GET/POST /api/accounts` + `GET /{id}` (BACKEND-CONTRACT § accounts).
 *
 * All endpoints are **authenticated** — no `@PublicEndpoint`: a 401 here
 * means the session died and the interceptor drives `Session.Expired`.
 * No edit/delete/transfer endpoints exist on the backend — none declared.
 */
interface AccountsService {

    @GET("api/accounts")
    suspend fun list(): List<AccountDto>

    @GET("api/accounts/{id}")
    suspend fun getById(@Path("id") id: String): AccountDto

    @POST("api/accounts")
    suspend fun create(@Body body: CreateAccountRequestDto): AccountDto
}
