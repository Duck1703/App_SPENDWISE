package com.spendwise.android.data.remote

import com.spendwise.android.core.network.PublicEndpoint
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Backend auth contract (verified against `AuthController.java`,
 * `SecurityConfig.java`, `ApiExceptionHandler.java`):
 *
 * - `POST /api/auth/register` → 201 `RegisterResponse{id, username}`;
 *   400 `fields{}` (validation) or 400 no-fields (duplicate username —
 *   indistinguishable on the wire, hedged copy per §15)
 * - `POST /api/auth/login` → 200 `AuthResponse{accessToken, expiresAt}`;
 *   401 `Invalid credentials.`; 400 `fields{}`
 * - `POST /api/auth/logout` → 204 (JWT `jti` → server blacklist);
 *   401 when unauthenticated — ignored by callers
 */
interface AuthService {

    @PublicEndpoint
    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequestDto): RegisterResponseDto

    @PublicEndpoint
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequestDto): LoginResponseDto

    @POST("api/auth/logout")
    suspend fun logout(): Response<Void>
}
