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
 * - `POST /api/auth/login` → 200 `AuthResponse{accessToken, refreshToken,
 *   expiresAt}` (refreshToken added in M13, REQ-BE-002); 401 `Invalid
 *   credentials.`; 400 `fields{}`
 * - `POST /api/auth/refresh` → 200 `AuthResponse` (rotated refresh token);
 *   401 when the credential is unknown/expired/revoked/replayed — the
 *   dedicated single-flight path in `SessionRefresher` calls this endpoint
 *   on a bare client; this service method exists for completeness/tests.
 * - `POST /api/auth/logout` → 204 (revokes the JWT `jti` AND the body's
 *   refresh token; endpoint is permitAll so a dead access token still
 *   revokes server-side); any failure is ignored by callers.
 */
interface AuthService {

    @PublicEndpoint
    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequestDto): RegisterResponseDto

    @PublicEndpoint
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequestDto): LoginResponseDto

    /**
     * `@PublicEndpoint` = refresh-exempt (M13): the endpoint is `permitAll`
     * on the backend and the body's refresh token authorizes its own
     * revocation — a locally-dead access token must not trigger a renewal
     * just to sign out, and a 401 here must never expire the session.
     * When the access token is still valid the interceptor still attaches
     * it, so the server also blacklists its `jti`.
     */
    @PublicEndpoint
    @POST("api/auth/logout")
    suspend fun logout(@Body body: LogoutRequestDto): Response<Void>
}
