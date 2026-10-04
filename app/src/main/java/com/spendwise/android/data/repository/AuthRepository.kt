package com.spendwise.android.data.repository

import com.spendwise.android.core.network.ApiException
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.core.network.toAppError
import com.spendwise.android.core.session.JwtClaims
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.remote.AuthService
import com.spendwise.android.data.remote.LoginRequestDto
import com.spendwise.android.data.remote.RegisterRequestDto
import java.io.IOException
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/** Result of a successful registration (no token — login is a separate call). */
data class RegisteredUser(val id: String, val username: String)

/**
 * Auth boundary (AD-016, §13). Establishes the session on successful login;
 * logout is best-effort remote + always-clear-local (401 is ignored — the
 * token is already invalid server-side).
 */
interface AuthRepository {

    suspend fun register(username: String, password: String): RegisteredUser

    /** On success the session is established; on failure throws [AppErrorException]. */
    suspend fun login(username: String, password: String)

    /** Always clears the local session; remote blacklist is best-effort. */
    suspend fun logout()
}

@Singleton
class RemoteAuthRepository @Inject constructor(
    private val service: AuthService,
    private val sessionManager: SessionManager,
) : AuthRepository {

    override suspend fun register(username: String, password: String): RegisteredUser =
        try {
            val response = service.register(RegisterRequestDto(username, password))
            RegisteredUser(id = response.id, username = response.username)
        } catch (e: ApiException) {
            throw AppErrorException(e.toAuthError(), e)
        } catch (e: IOException) {
            throw AppErrorException(AppError.NetworkUnavailable, e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AppErrorException(AppError.Unknown(e), e)
        }

    override suspend fun login(username: String, password: String) {
        val response = try {
            service.login(LoginRequestDto(username, password))
        } catch (e: ApiException) {
            throw AppErrorException(e.toAuthError(), e)
        } catch (e: IOException) {
            throw AppErrorException(AppError.NetworkUnavailable, e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AppErrorException(AppError.Unknown(e), e)
        }
        val expiresAt = runCatching { Instant.parse(response.expiresAt) }
            .getOrElse { throw AppErrorException(AppError.Unknown(it), it) }
        if (response.accessToken.isBlank()) {
            // A 200 with a blank token is a malformed success — never sign in
            // with a credential that can only produce "Bearer " requests.
            throw AppErrorException(
                AppError.Unknown(IllegalStateException("blank accessToken")),
            )
        }
        try {
            sessionManager.onSignedIn(
                token = response.accessToken,
                expiresAt = expiresAt,
                username = username,
                userId = JwtClaims.subjectOf(response.accessToken),
            )
        } catch (e: IOException) {
            // Session persistence failed (EXT-MIN-4): onSignedIn fails
            // closed before the session state is set, so no SignedIn is
            // ever observed without its durable copy. This is a storage
            // fault, not a transport one — surfaced as Unknown, never
            // NetworkUnavailable.
            throw AppErrorException(AppError.Unknown(e), e)
        } catch (e: CancellationException) {
            throw e
        }
    }

    override suspend fun logout() {
        try {
            service.logout()
        } catch (_: Exception) {
            // Remote blacklist is best-effort (401 token already dead,
            // offline, malformed response — all fine): the local session is
            // always cleared so the dead token leaves the device (§13).
        }
        sessionManager.signOut()
    }

    /**
     * Public-call mapping: a 401 on login/register means invalid credentials,
     * never session expiry (§13). Everything else uses the default mapping.
     */
    private fun ApiException.toAuthError(): AppError =
        if (status == 401) AppError.InvalidCredentials else toAppError()
}
