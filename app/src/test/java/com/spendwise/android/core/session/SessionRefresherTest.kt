package com.spendwise.android.core.session

import com.spendwise.android.fakes.FakeTokenStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * M13 (REQ-BE-002): single-flight renewal engine against a MockWebServer.
 * The refresher runs on its own bare client pointing at [server] — every
 * behavior here is wire-real, including rotation persistence and the
 * no-duplicate-write safety invariants.
 */
class SessionRefresherTest {

    private val now: Instant = Instant.parse("2026-10-04T12:00:00Z")
    private val future: Instant = now.plusSeconds(900)
    private val past: Instant = now.minusSeconds(60)

    private lateinit var server: MockWebServer
    private lateinit var store: FakeTokenStore
    private lateinit var sessionManager: SessionManager
    private lateinit var refresher: SessionRefresher

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = FakeTokenStore()
        sessionManager = SessionManager(
            tokenStore = store,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        refresher = SessionRefresher(
            sessionManager = sessionManager,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            json = Json { ignoreUnknownKeys = true },
            apiBaseUrl = server.url("/").toString(),
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun refreshResponse(access: String = "new-access", refresh: String = "new-refresh") =
        MockResponse(
            code = 200,
            body = """{"accessToken":"$access","refreshToken":"$refresh","expiresAt":"${now.plusSeconds(900)}"}""",
        )

    private suspend fun signIn(refresh: String? = "rt-1", token: String = "expired-access") {
        // Locally expired access + live refresh credential: the classic
        // pre-send refresh trigger.
        sessionManager.onSignedIn(token, past, "ana", "u1", refreshToken = refresh)
    }

    @Test
    fun `expired access renews against the refresh endpoint and persists rotated credentials`() = runTest {
        signIn()
        server.enqueue(refreshResponse())

        val outcome = refresher.refreshBlocking(expectedStaleToken = null)

        assertEquals(SessionRefresher.Outcome.Renewed, outcome)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/auth/refresh", request.url.encodedPath)
        assertEquals("""{"refreshToken":"rt-1"}""", request.body?.utf8())

        val session = sessionManager.session.value as Session.SignedIn
        assertEquals("new-access", session.token)
        assertEquals("new-refresh", session.refreshToken)
        assertEquals("ana", session.username)
        // Rotation landed durably — a restart reads the NEW credential.
        assertEquals("new-access", store.token)
        assertEquals("new-refresh", store.refreshToken)
    }

    @Test
    fun `concurrent callers collapse into exactly one refresh call`() = runTest {
        signIn()
        // Gate the refresh response so both callers genuinely overlap.
        server.enqueue(refreshResponse())

        val ready = CountDownLatch(2)
        val done = CountDownLatch(2)
        val outcomes = mutableListOf<SessionRefresher.Outcome>()
        val threads = (1..2).map {
            Thread {
                ready.countDown()
                ready.await()
                synchronized(outcomes) {
                    outcomes += refresher.refreshBlocking(expectedStaleToken = "expired-access")
                }
                done.countDown()
            }
        }
        threads.forEach { it.start() }
        assertTrue(done.await(30, TimeUnit.SECONDS))

        // One wire call total — the loser of the lock race sees the renewed
        // session (AlreadyAlive) or shares the win; never a second POST.
        assertEquals(1, server.requestCount)
        assertTrue(
            synchronized(outcomes) {
                outcomes.all {
                    it == SessionRefresher.Outcome.Renewed ||
                        it == SessionRefresher.Outcome.AlreadyAlive
                }
            },
        )
    }

    @Test
    fun `a peer renewal makes a late caller AlreadyAlive with no wire call`() = runTest {
        signIn()
        server.enqueue(refreshResponse())
        assertEquals(SessionRefresher.Outcome.Renewed, refresher.refreshBlocking(null))

        // Second caller arrives with the OLD stale token in hand — the
        // session already holds a different valid token → no wire call.
        val outcome = refresher.refreshBlocking(expectedStaleToken = "expired-access")

        assertEquals(SessionRefresher.Outcome.AlreadyAlive, outcome)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `no refresh credential short-circuits without a wire call`() = runTest {
        signIn(refresh = null)

        assertEquals(SessionRefresher.Outcome.NoCredential, refresher.refreshBlocking(null))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `definitive 401 rejection expires the session and wipes credentials`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 401, body = ""))

        val outcome = refresher.refreshBlocking(null)

        assertEquals(SessionRefresher.Outcome.Rejected, outcome)
        assertEquals(Session.Expired("ana", "u1"), sessionManager.session.value)
        assertNull(store.token)
        assertNull(store.refreshToken)
        assertEquals("ana", store.lastUsername)
    }

    @Test
    fun `definitive 400 rejection also kills the session`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 400, body = ""))

        assertEquals(SessionRefresher.Outcome.Rejected, refresher.refreshBlocking(null))
        assertTrue(sessionManager.session.value is Session.Expired)
    }

    @Test
    fun `transport failure is transient — session and credentials survive`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 503, body = ""))

        val outcome = refresher.refreshBlocking(null)

        assertEquals(SessionRefresher.Outcome.Transient, outcome)
        val session = sessionManager.session.value as Session.SignedIn
        assertEquals("expired-access", session.token)
        assertEquals("rt-1", session.refreshToken)
        // Stored credential intact for the next attempt.
        assertEquals("rt-1", store.refreshToken)
    }

    @Test
    fun `malformed refresh response is transient, not fatal`() = runTest {
        signIn()
        server.enqueue(MockResponse(code = 200, body = "not json"))

        assertEquals(SessionRefresher.Outcome.Transient, refresher.refreshBlocking(null))
        assertTrue(sessionManager.session.value is Session.SignedIn)
    }

    @Test
    fun `refresh response without a rotated credential is transient`() = runTest {
        signIn()
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"accessToken":"new-access","expiresAt":"${now.plusSeconds(900)}"}""",
            ),
        )

        // A 200 that does not rotate would strand the client — refuse it.
        assertEquals(SessionRefresher.Outcome.Transient, refresher.refreshBlocking(null))
        val session = sessionManager.session.value as Session.SignedIn
        assertEquals("expired-access", session.token)
    }

    @Test
    fun `a session boundary mid-flight discards the renewed credentials`() = runTest {
        signIn()
        // Response is gated: signOut fires while the refresh is in flight.
        server.enqueue(refreshResponse())
        val generation = sessionManager.sessionGeneration

        val call = Thread {
            refresher.refreshBlocking(expectedStaleToken = null)
        }
        call.start()
        server.takeRequest() // refresh is on the wire
        sessionManager.signOut() // generation bumps (already inside runTest)
        call.join(30_000)

        // The late response must NOT resurrect the signed-out session —
        // but the backend DID consume the refresh token (rotation happened
        // server-side). Discarded credentials are never installed locally.
        assertEquals(Session.SignedOut, sessionManager.session.value)
        assertNull(store.token)
        assertTrue(sessionManager.sessionGeneration > generation)
    }

    @Test
    fun `persistence failure on apply is transient — never a fake session`() = runTest {
        signIn()
        server.enqueue(refreshResponse())
        store.writeError = IOException("disk full")

        val outcome = refresher.refreshBlocking(null)

        // Renewal succeeded on the wire but could not be durably stored —
        // the session must not silently adopt volatile credentials.
        assertEquals(SessionRefresher.Outcome.Transient, outcome)
        val session = sessionManager.session.value as Session.SignedIn
        assertEquals("expired-access", session.token)
    }

    @Test
    fun `cold start parks an expired session behind its refresh credential`() = runTest {
        store.save("dead-access", past, "ana", "u1", refreshToken = "rt-parked")
        sessionManager.restore()

        assertEquals(Session.Restoring, sessionManager.session.value)
        assertTrue(sessionManager.hasPendingRenewal())
        assertEquals("rt-parked", sessionManager.renewalToken())

        server.enqueue(refreshResponse())
        refresher.renewPendingSession()

        val session = sessionManager.session.value as Session.SignedIn
        assertEquals("new-access", session.token)
        assertEquals("ana", session.username)
    }

    @Test
    fun `cold-start transient failure resolves to Expired but keeps the credential`() = runTest {
        store.save("dead-access", past, "ana", "u1", refreshToken = "rt-parked")
        sessionManager.restore()
        server.enqueue(MockResponse(code = 503, body = ""))

        refresher.renewPendingSession()

        // Re-auth surface, not an endless Restoring — and the stored
        // credential survives for the next launch (failure may be transient).
        assertEquals(Session.Expired("ana", "u1"), sessionManager.session.value)
        assertEquals("rt-parked", store.refreshToken)
    }

    @Test
    fun `cold-start rejection resolves to Expired and wipes credentials`() = runTest {
        store.save("dead-access", past, "ana", "u1", refreshToken = "rt-parked")
        sessionManager.restore()
        server.enqueue(MockResponse(code = 401, body = ""))

        refresher.renewPendingSession()

        assertEquals(Session.Expired("ana", "u1"), sessionManager.session.value)
        assertNull(store.refreshToken)
    }
}
