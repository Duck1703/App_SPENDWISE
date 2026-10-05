package com.spendwise.android.data

import com.spendwise.android.core.network.ApiException
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.remote.CategoryDto
import com.spendwise.android.data.repository.RemoteCategoryRepository
import com.spendwise.android.fakes.FakeCategoriesService
import com.spendwise.android.fakes.FakeTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CategoryRepositoryTest {

    private val now: Instant = Instant.parse("2026-10-04T12:00:00Z")

    private data class Harness(
        val service: FakeCategoriesService,
        val sessionManager: SessionManager,
        val repository: RemoteCategoryRepository,
    )

    private fun harness(): Harness {
        val service = FakeCategoriesService()
        val sessionManager = SessionManager(
            tokenStore = FakeTokenStore(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        val scope = CoroutineScope(Dispatchers.Unconfined)
        return Harness(service, sessionManager, RemoteCategoryRepository(service, sessionManager, scope))
    }

    @Test
    fun `list maps the value codes`() = runTest {
        val (service, _, repository) = harness()
        service.listResponse = listOf(CategoryDto("FOOD"), CategoryDto("HOUSING"))

        assertEquals(listOf("FOOD", "HOUSING"), repository.list())
    }

    @Test
    fun `list caches per session — repeats skip the network`() = runTest {
        val (service, _, repository) = harness()

        repository.list()
        repository.list()

        assertEquals(1, service.listCalls)
    }

    @Test
    fun `refresh bypasses the cache`() = runTest {
        val (service, _, repository) = harness()
        repository.list()
        repository.list(refresh = true)

        assertEquals(2, service.listCalls)
    }

    @Test
    fun `session invalidation drops the cache so list refetches`() = runTest {
        val (service, sessionManager, repository) = harness()
        sessionManager.onSignedIn("t1", now.plusSeconds(900), "ana", "u1")
        repository.list()

        sessionManager.onAuthenticationExpired()

        repository.list()
        assertEquals(2, service.listCalls)
    }

    @Test
    fun `a new session generation never reads the prior cache`() = runTest {
        val (service, sessionManager, repository) = harness()
        sessionManager.onSignedIn("t1", now.plusSeconds(900), "ana", "u1")
        service.listResponse = listOf(CategoryDto("FOOD"))
        repository.list()

        sessionManager.signOut()
        sessionManager.onSignedIn("t2", now.plusSeconds(900), "bob", "u2")
        service.listResponse = listOf(CategoryDto("SALARY"))

        assertEquals(listOf("SALARY"), repository.list())
        assertEquals(2, service.listCalls)
    }

    @Test
    fun `io failure maps to NetworkUnavailable`() = runTest {
        val (service, _, repository) = harness()
        service.listError = IOException("offline")

        try {
            repository.list()
            fail("Expected AppErrorException")
        } catch (e: AppErrorException) {
            assertEquals(AppError.NetworkUnavailable, e.error)
        }
    }

    @Test
    fun `empty server set is honored — categories are never invented`() = runTest {
        val (service, _, repository) = harness()
        service.listResponse = emptyList()

        assertEquals(emptyList<String>(), repository.list())
    }

    @Test
    fun `unexpected api error maps through toAppError`() = runTest {
        val (service, _, repository) = harness()
        service.listError = ApiException(status = 500)

        try {
            repository.list()
            fail("Expected AppErrorException")
        } catch (e: AppErrorException) {
            assertEquals(AppError.Server(500), e.error)
        }
    }
}
