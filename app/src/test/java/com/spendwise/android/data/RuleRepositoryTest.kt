package com.spendwise.android.data

import com.spendwise.android.core.model.RuleConditionType
import com.spendwise.android.core.network.ApiException
import com.spendwise.android.core.network.AppError
import com.spendwise.android.core.network.AppErrorException
import com.spendwise.android.core.session.SessionManager
import com.spendwise.android.data.repository.RemoteRuleRepository
import com.spendwise.android.data.repository.SessionEndedException
import com.spendwise.android.fakes.FakeRulesService
import com.spendwise.android.fakes.FakeTokenStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * `RemoteRuleRepository` — server order is preserved verbatim (no
 * client sort), condition-type armor, error translation, generation
 * guard, disable mutation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RuleRepositoryTest {

    private val now: Instant = Instant.parse("2026-10-04T12:00:00Z")

    private data class Harness(
        val service: FakeRulesService,
        val sessionManager: SessionManager,
        val repository: RemoteRuleRepository,
    )

    private fun harness(): Harness {
        val service = FakeRulesService()
        val sessionManager = SessionManager(
            tokenStore = FakeTokenStore(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        return Harness(service, sessionManager, RemoteRuleRepository(service, sessionManager))
    }

    private suspend fun signIn(h: Harness) =
        h.sessionManager.onSignedIn("token", now.plusSeconds(900), "ana", "u1")

    @Test
    fun `list preserves server priority order without re-sorting`() = runTest {
        val h = harness()
        signIn(h)
        h.service.listResult = {
            listOf(
                FakeRulesService.rule(id = "low", priority = 7),
                FakeRulesService.rule(id = "high", priority = 1),
            )
        }
        // The server owns ordering (ascending priority); the client must
        // not reorder even if rows arrive out of order.
        assertEquals(listOf("low", "high"), h.repository.list().map { it.id })
    }

    @Test
    fun `create maps params without sending enabled`() = runTest {
        val h = harness()
        signIn(h)
        h.repository.create(RuleConditionType.CONTAINS, "uber", "TRANSPORT", 3)
        val body = h.service.createCalls.single()
        assertEquals("CONTAINS", body.conditionType)
        assertEquals("uber", body.conditionValue)
        assertEquals("TRANSPORT", body.targetCategory)
        assertEquals(3, body.priority)
    }

    @Test
    fun `unknown condition type armors to UNKNOWN`() = runTest {
        val h = harness()
        signIn(h)
        h.service.listResult = { listOf(FakeRulesService.rule(conditionType = "REGEX")) }
        assertEquals(RuleConditionType.UNKNOWN, h.repository.list().single().conditionType)
    }

    @Test
    fun `disabled rules map enabled=false`() = runTest {
        val h = harness()
        signIn(h)
        h.service.listResult = { listOf(FakeRulesService.rule(enabled = false)) }
        assertFalse(h.repository.list().single().enabled)
    }

    @Test
    fun `409 duplicate priority surfaces Conflict`() = runTest {
        val h = harness()
        signIn(h)
        h.service.createResult = { throw ApiException(409, "Conflict") }
        val thrown = runCatching {
            h.repository.create(RuleConditionType.EQUALS, "x", "FOOD", 1)
        }.exceptionOrNull()
        assertTrue((thrown as? AppErrorException)?.error is AppError.Conflict)
    }

    @Test
    fun `disable emits the mutation signal`() = runTest {
        val h = harness()
        signIn(h)
        val emissions = mutableListOf<Unit>()
        val collector = async { h.repository.mutations.collect { emissions += Unit } }
        runCurrent() // collector is live before the emit
        h.repository.disable("rl1")
        runCurrent()
        collector.cancel()
        assertEquals(listOf("rl1"), h.service.disableCalls)
        assertEquals(1, emissions.size)
    }

    @Test
    fun `a dead-generation disable never emits`() = runTest {
        val h = harness()
        signIn(h)
        val gate = CompletableDeferred<com.spendwise.android.data.remote.CategorizationRuleDto>()
        h.service.disableResult = { gate.await() }
        val emissions = mutableListOf<Unit>()
        val collector = async { h.repository.mutations.collect { emissions += Unit } }
        val pending = async { h.repository.disable("rl1") }
        runCurrent()
        h.sessionManager.signOut()
        gate.complete(FakeRulesService.rule())
        runCatching { pending.await() }
        runCurrent()
        collector.cancel()
        assertTrue(emissions.isEmpty())
    }

    @Test
    fun `400 maps to Validation`() = runTest {
        val h = harness()
        signIn(h)
        h.service.createResult = {
            throw ApiException(400, "Bad request", mapOf("conditionValue" to "size must be between 1 and 255"))
        }
        val thrown = runCatching {
            h.repository.create(RuleConditionType.CONTAINS, "", "FOOD", 1)
        }.exceptionOrNull()
        val error = (thrown as? AppErrorException)?.error
        assertEquals(mapOf("conditionValue" to "size must be between 1 and 255"),
            (error as? AppError.Validation)?.fields)
    }

    @Test
    fun `a dead-generation list result is discarded`() = runTest {
        val h = harness()
        signIn(h)
        val gate = CompletableDeferred<List<com.spendwise.android.data.remote.CategorizationRuleDto>>()
        h.service.listResult = { gate.await() }
        val pending = async { h.repository.list() }
        runCurrent()
        h.sessionManager.signOut()
        gate.complete(emptyList())
        assertTrue(runCatching { pending.await() }.exceptionOrNull() is SessionEndedException)
    }
}
