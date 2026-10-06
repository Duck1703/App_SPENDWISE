package com.spendwise.android.core.session

import com.spendwise.android.data.remote.LoginResponseDto
import com.spendwise.android.data.remote.RefreshRequestDto
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * M13 (REQ-BE-002): single-flight session renewal.
 *
 * One lock serializes every renewal — N concurrent 401s (or expired-token
 * sends) collapse into ONE `POST /api/auth/refresh`; callers that arrive
 * after a peer's renewal see [Outcome.AlreadyAlive] and never hit the wire.
 *
 * The refresh call runs on a dedicated bare [OkHttpClient] with **no
 * interceptors, no authenticator, and no logging** — the credential can
 * never recurse into the auth path it is renewing, and it can never reach
 * a log line (HEADERS-level logging would not print bodies anyway, but the
 * refresh path simply has no logger at all). `retryOnConnectionFailure` is
 * off, matching NetworkModule: a transport failure is [Outcome.Transient],
 * never silently retried.
 *
 * Safety invariants:
 * - The response is applied only to the session generation that requested
 *   it — a logout or different-user sign-in mid-flight discards it (§29).
 * - A definitive server rejection (401/400) is the ONLY outcome that kills
 *   the session; transport failure leaves credentials intact.
 * - Never throws for auth/transport reasons; only programmer defects and
 *   interruption propagate.
 */
@Singleton
class SessionRefresher @Inject constructor(
    private val sessionManager: SessionManager,
    private val clock: Clock,
    private val json: Json,
    @param:ApiBaseUrl private val apiBaseUrl: String,
) {

    sealed interface Outcome {
        /** Fresh credentials were applied to the session. */
        data object Renewed : Outcome

        /** A peer already renewed — the session holds a usable token. */
        data object AlreadyAlive : Outcome

        /** No refresh credential exists (legacy or signed-out session). */
        data object NoCredential : Outcome

        /** Server definitively rejected the credential — session expired. */
        data object Rejected : Outcome

        /** Network/server fault — credential not proven dead, try later. */
        data object Transient : Outcome

        /** Session generation moved mid-flight (logout / different user). */
        data object Discarded : Outcome
    }

    private val lock = ReentrantLock()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    /**
     * Blocking by design: called from OkHttp interceptor/authenticator
     * threads (already blocking contexts) and the startup scope.
     *
     * [expectedStaleToken] = the access token the caller's request carried,
     * or `null` for a pre-send renewal. When the session already holds a
     * different, still-valid token, a peer won the race → [AlreadyAlive].
     */
    fun refreshBlocking(expectedStaleToken: String?): Outcome = lock.withLock {
        val current = sessionManager.session.value
        if (current is Session.SignedIn &&
            current.expiresAt.isAfter(clock.instant()) &&
            current.token != expectedStaleToken
        ) {
            return Outcome.AlreadyAlive
        }
        val refreshToken = sessionManager.renewalToken() ?: return Outcome.NoCredential
        val expectedGeneration = sessionManager.sessionGeneration
        renew(refreshToken, expectedGeneration)
    }

    /** Cold-start entry: resolve a parked [SessionManager.hasPendingRenewal]. */
    fun renewPendingSession() {
        if (!sessionManager.hasPendingRenewal()) return
        lock.withLock {
            val pending = sessionManager.renewalToken() ?: return
            when (renew(pending, sessionManager.sessionGeneration)) {
                Outcome.Transient -> sessionManager.onRenewalUnresolved()
                else -> Unit // Renewed → SignedIn; Rejected/Discarded handled inside
            }
        }
    }

    private fun renew(refreshToken: String, expectedGeneration: Long): Outcome {
        val body = json.encodeToString(
            RefreshRequestDto.serializer(),
            RefreshRequestDto(refreshToken),
        ).toRequestBody("application/json".toMediaType())
        val response = try {
            client.newCall(
                Request.Builder()
                    .url(apiBaseUrl + "api/auth/refresh")
                    .post(body)
                    .build(),
            ).execute()
        } catch (e: IOException) {
            return Outcome.Transient
        }
        return response.use { resp ->
            when {
                resp.isSuccessful -> {
                    val dto = runCatching {
                        json.decodeFromString(
                            LoginResponseDto.serializer(),
                            resp.body.string(),
                        )
                    }.getOrNull() ?: return Outcome.Transient
                    val expiresAt = runCatching { Instant.parse(dto.expiresAt) }.getOrNull()
                    val newRefresh = dto.refreshToken
                    if (expiresAt == null || dto.accessToken.isBlank() || newRefresh.isNullOrBlank()) {
                        return Outcome.Transient
                    }
                    val applied = try {
                        runBlocking {
                            sessionManager.applyRenewedSession(
                                token = dto.accessToken,
                                expiresAt = expiresAt,
                                refreshToken = newRefresh,
                                expectedGeneration = expectedGeneration,
                            )
                        }
                    } catch (e: IOException) {
                        // Persistence failure — the renewal is unusable without
                        // its durable copy; the session stays as it was and the
                        // caller may retry (not a credential rejection).
                        return Outcome.Transient
                    }
                    if (applied) Outcome.Renewed else Outcome.Discarded
                }
                resp.code == 401 || resp.code == 400 -> {
                    runBlocking { sessionManager.onRenewalRejected() }
                    Outcome.Rejected
                }
                else -> Outcome.Transient
            }
        }
    }
}
