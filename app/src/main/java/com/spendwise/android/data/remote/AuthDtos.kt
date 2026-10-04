package com.spendwise.android.data.remote

import kotlinx.serialization.Serializable

// Wire DTOs — backend `record` shapes, exact field names. Never exposed to UI.

@Serializable
data class LoginRequestDto(
    val username: String,
    val password: String,
)

/** `AuthResponse{accessToken, expiresAt}` — expiresAt is ISO-8601 Instant text. */
@Serializable
data class LoginResponseDto(
    val accessToken: String,
    val expiresAt: String,
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
