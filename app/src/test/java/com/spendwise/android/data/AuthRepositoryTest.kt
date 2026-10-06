package com.spendwise.android.data

import com.spendwise.android.core.network.ApiException
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.core.session.Session
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.remote.LoginResponseDto
import com.spendwise.android.data.repository.RemoteAuthRepository
import com.spendwise.android.fakes.FakeAuthService
import com.spendwise.android.fakes.FakeTokenStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AuthRepositoryTest {

    private val now: Instant = Instant.parse("2026-10-04T12:00:00Z")
    private val futureExpiry = "2026-10-04T12:15:00Z"

    private fun harness(): Triple<FakeAuthService, SessionManager, RemoteAuthRepository> {
        val service = FakeAuthService()
        val store = FakeTokenStore()
        val sessionManager = SessionManager(
            tokenStore = store,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        return Triple(service, sessionManager, RemoteAuthRepository(service, sessionManager))
    }

    @Test
    fun `login success establishes the session and persists the token`() = runTest {
        val (service, sessionManager, repository) = harness()
        service.loginResponse = LoginResponseDto("jwt-1", futureExpiry)

        repository.login("ana", "secret-pw")

        val session = sessionManager.session.value as Session.SignedIn
        assertEquals("jwt-1", session.token)
        assertEquals(Instant.parse(futureExpiry), session.expiresAt)
        assertEquals("ana", session.username)
        assertEquals(listOf("ana" to "secret-pw"), service.loginCalls.map { it.username to it.password })
    }

    @Test
    fun `login persists the issued refresh credential (M13)`() = runTest {
        val (service, sessionManager, repository) = harness()
        service.loginResponse = LoginResponseDto("jwt-1", futureExpiry, refreshToken = "rt-1")

        repository.login("ana", "secret-pw")

        val session = sessionManager.session.value as Session.SignedIn
        assertEquals("rt-1", session.refreshToken)
        assertEquals("rt-1", sessionManager.renewalToken())
    }

    @Test
    fun `login against a pre-M13 backend leaves a legacy refresh-less session`() = runTest {
        val (service, sessionManager, repository) = harness()
        service.loginResponse = LoginResponseDto("jwt-1", futureExpiry) // no refreshToken

        repository.login("ana", "secret-pw")

        val session = sessionManager.session.value as Session.SignedIn
        assertEquals(null, session.refreshToken)
        assertNull(sessionManager.renewalToken())
    }

    @Test
    fun `logout sends the refresh credential for server-side revocation`() = runTest {
        val (service, sessionManager, repository) = harness()
        sessionManager.onSignedIn("jwt-1", now.plusSeconds(900), "ana", "u1", refreshToken = "rt-1")

        repository.logout()

        assertEquals(listOf("rt-1"), service.logoutCalls.map { it.refreshToken })
        assertEquals(Session.SignedOut, sessionManager.session.value)
    }

    @Test
    fun `login 401 maps to InvalidCredentials and never touches the session`() = runTest {
        val (service, sessionManager, repository) = harness()
        service.loginError = ApiException(status = 401, title = "Unauthorized")

        try {
            repository.login("ana", "wrong")
            fail("Expected AppErrorException")
        } catch (e: AppErrorException) {
            assertEquals(AppError.InvalidCredentials, e.error)
        }
        assertTrue(sessionManager.session.value !is Session.SignedIn)
    }

    @Test
    fun `login 400 with fields maps to Validation`() = runTest {
        val (service, _, repository) = harness()
        service.loginError = ApiException(
            status = 400,
            fields = mapOf("password" to "must not be blank"),
        )

        try {
            repository.login("ana", "")
            fail("Expected AppErrorException")
        } catch (e: AppErrorException) {
            assertEquals(AppError.Validation(mapOf("password" to "must not be blank")), e.error)
        }
    }

    @Test
    fun `network failure maps to NetworkUnavailable`() = runTest {
        val (service, _, repository) = harness()
        service.loginError = IOException("socket closed")

        try {
            repository.login("ana", "pw")
            fail("Expected AppErrorException")
        } catch (e: AppErrorException) {
            assertEquals(AppError.NetworkUnavailable, e.error)
        }
    }

    @Test
    fun `malformed expiresAt maps to Unknown and never signs in`() = runTest {
        val (service, sessionManager, repository) = harness()
        service.loginResponse = LoginResponseDto("jwt-1", "not-a-timestamp")

        try {
            repository.login("ana", "pw")
            fail("Expected AppErrorException")
        } catch (e: AppErrorException) {
            assertTrue(e.error is AppError.Unknown)
        }
        assertFalse(sessionManager.session.value is Session.SignedIn)
    }

    @Test
    fun `blank accessToken maps to Unknown and never signs in`() = runTest {
        val (service, sessionManager, repository) = harness()
        service.loginResponse = LoginResponseDto("", futureExpiry)

        try {
            repository.login("ana", "pw")
            fail("Expected AppErrorException")
        } catch (e: AppErrorException) {
            assertTrue(e.error is AppError.Unknown)
        }
        assertFalse(sessionManager.session.value is Session.SignedIn)
    }

    @Test
    fun `register success returns the created user`() = runTest {
        val (_, _, repository) = harness()

        val user = repository.register("ana", "secret-pw")

        assertEquals("ana", user.username)
    }

    @Test
    fun `register 400 without fields maps to BadRequest (indistinguishable duplicate)`() = runTest {
        val (service, _, repository) = harness()
        service.registerError = ApiException(status = 400, title = "Invalid request")

        try {
            repository.register("ana", "secret-pw")
            fail("Expected AppErrorException")
        } catch (e: AppErrorException) {
            assertEquals(AppError.BadRequest, e.error)
        }
    }

    @Test
    fun `register 400 with fields maps to Validation`() = runTest {
        val (service, _, repository) = harness()
        service.registerError = ApiException(
            status = 400,
            fields = mapOf("username" to "must not be blank"),
        )

        try {
            repository.register(" ", "secret-pw")
            fail("Expected AppErrorException")
        } catch (e: AppErrorException) {
            assertEquals(AppError.Validation(mapOf("username" to "must not be blank")), e.error)
        }
    }

    @Test
    fun `logout clears the local session even when the remote call 401s`() = runTest {
        val (service, sessionManager, repository) = harness()
        sessionManager.onSignedIn("jwt-1", now.plusSeconds(900), "ana", "u1")
        service.logoutError = ApiException(status = 401)

        repository.logout()

        assertEquals(Session.SignedOut, sessionManager.session.value)
        assertNull(sessionManager.accessToken())
        assertEquals(1, service.logoutCalls.size)
    }

    @Test
    fun `logout clears the local session even when the network is down`() = runTest {
        val (service, sessionManager, repository) = harness()
        sessionManager.onSignedIn("jwt-1", now.plusSeconds(900), "ana", "u1")
        service.logoutError = IOException("offline")

        repository.logout()

        assertEquals(Session.SignedOut, sessionManager.session.value)
    }

    @Test
    fun `same-user re-authentication marks the session as resumed`() = runTest {
        val (service, sessionManager, repository) = harness()
        sessionManager.onSignedIn("jwt-old", now.plusSeconds(900), "ana", "u1")
        sessionManager.onAuthenticationExpired()
        service.loginResponse = LoginResponseDto("jwt-new", futureExpiry)

        repository.login("ana", "secret-pw")

        assertTrue((sessionManager.session.value as Session.SignedIn).resumedSession)
    }

    @Test
    fun `different-user re-authentication does not resume`() = runTest {
        val (service, sessionManager, repository) = harness()
        sessionManager.onSignedIn("jwt-old", now.plusSeconds(900), "ana", "u1")
        sessionManager.onAuthenticationExpired()
        service.loginResponse = LoginResponseDto("jwt-new", futureExpiry)

        repository.login("bob", "secret-pw")

        assertFalse((sessionManager.session.value as Session.SignedIn).resumedSession)
    }

    @Test
    fun `login with session persistence failure maps to Unknown — never NetworkUnavailable`() =
        runTest {
            val service = FakeAuthService().apply {
                loginResponse = LoginResponseDto("jwt-1", futureExpiry)
            }
            val store = FakeTokenStore().apply { writeError = IOException("disk full") }
            val sessionManager = SessionManager(
                tokenStore = store,
                clock = Clock.fixed(now, ZoneOffset.UTC),
            )
            val repository = RemoteAuthRepository(service, sessionManager)

            try {
                repository.login("ana", "secret-pw")
                fail("Expected AppErrorException")
            } catch (e: AppErrorException) {
                // A disk fault is not "no connection" — honest Unknown.
                assertTrue(e.error is AppError.Unknown)
                assertFalse(e.error == AppError.NetworkUnavailable)
            }

            // Fails closed: no SignedIn state, credential not persisted.
            assertFalse(sessionManager.session.value is Session.SignedIn)
            assertNull(store.token)
        }
}
