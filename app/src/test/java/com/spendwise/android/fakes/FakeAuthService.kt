package com.spendwise.android.fakes

import com.spendwise.android.data.remote.AuthService
import com.spendwise.android.data.remote.LoginRequestDto
import com.spendwise.android.data.remote.LoginResponseDto
import com.spendwise.android.data.remote.LogoutRequestDto
import com.spendwise.android.data.remote.RegisterRequestDto
import com.spendwise.android.data.remote.RegisterResponseDto
import retrofit2.Response

/**
 * Scripted [AuthService] for repository/ViewModel tests — throws the
 * configured error or returns the canned response, records calls.
 */
class FakeAuthService : AuthService {

    var registerResponse = RegisterResponseDto(id = "user-1", username = "ana")
    var registerError: Throwable? = null
    var loginResponse = LoginResponseDto(accessToken = "tok", expiresAt = "2026-10-04T12:15:00Z")
    var loginError: Throwable? = null
    var logoutError: Throwable? = null
    var logoutResponse: Response<Void> = Response.success(null)

    val registerCalls = mutableListOf<RegisterRequestDto>()
    val loginCalls = mutableListOf<LoginRequestDto>()
    val logoutCalls = mutableListOf<LogoutRequestDto>()

    override suspend fun register(body: RegisterRequestDto): RegisterResponseDto {
        registerCalls += body
        registerError?.let { throw it }
        return registerResponse
    }

    override suspend fun login(body: LoginRequestDto): LoginResponseDto {
        loginCalls += body
        loginError?.let { throw it }
        return loginResponse
    }

    override suspend fun logout(body: LogoutRequestDto): Response<Void> {
        logoutCalls += body
        logoutError?.let { throw it }
        return logoutResponse
    }
}
