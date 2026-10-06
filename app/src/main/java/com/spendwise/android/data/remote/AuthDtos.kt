package com.spendwise.android.data.remote

import kotlinx.serialization.Serializable

// Wire DTOs — backend `record` shapes, exact field names. Never exposed to UI.

@Serializable
data class LoginRequestDto(
    val username: String,
    val password: String,
)

/**
 * `AuthResponse{accessToken, refreshToken, expiresAt}` — expiresAt is ISO-8601
 * Instant text. `refreshToken` arrived in M13 (REQ-BE-002): nullable with a
 * default so a pre-M13 backend response still decodes as a legacy session.
 */
@Serializable
data class LoginResponseDto(
    val accessToken: String,
    val expiresAt: String,
    val refreshToken: String? = null,
) {
    // Credential-bearing wire DTO — redact like the session classes so a
    // stray log/debug print can never emit token material.
    override fun toString(): String =
        "LoginResponseDto(accessToken=***, expiresAt=$expiresAt, " +
            "refreshToken=${if (refreshToken != null) "***" else "null"})"
}

/** `RefreshRequest{refreshToken}` — REQ-BE-002 rotation request. */
@Serializable
data class RefreshRequestDto(
    val refreshToken: String,
)

/** `LogoutRequest{refreshToken}` — optional; lets a dead-access-token client
 *  still revoke its refresh credential (the endpoint is permitAll). */
@Serializable
data class LogoutRequestDto(
    val refreshToken: String? = null,
)

@Serializable
data class RegisterRequestDto(
    val username: String,
    val password: String,
)

/** `RegisterResponse{id (UUID), username}`. */
@Serializable
data class RegisterResponseDto(
    val id: String,
    val username: String,
)
