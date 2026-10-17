package com.spendwise.android.live.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spendwise.android.live.infrastructure.FixtureApi
import com.spendwise.android.live.infrastructure.LiveCaseIds
import com.spendwise.android.live.infrastructure.OwnerControlPlane
import com.spendwise.android.live.infrastructure.RunOwnership
import com.spendwise.android.live.infrastructure.SyntheticFixture
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * Live UI E2E — authentication/session scenarios.
 * Owners: LUI-01, LUI-02, LUI-19, LUI-20, LUI-28.
 */
@RunWith(AndroidJUnit4::class)
class LiveAuthE2eTest : LiveE2eTestBase() {

    /** App-private session DataStore — readable because instrumentation runs
     *  in the target process. */
    private fun sessionStore(): File = File(
        InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
        "datastore/spendwise_session.preferences_pb",
    )

    /** B05 — renewal evidence is a CONTENT hash of the persisted session,
     *  not an mtime: a file touch without rotation is not renewal. */
    private fun storeHash(store: File): String =
        if (!store.isFile) "absent"
        else MessageDigest.getInstance("SHA-256")
            .digest(store.readBytes()).joinToString("") { "%02x".format(it) }

    /** LUI-01 (P0): Register → prefilled Login + banner → explicit Login → Home.
     *  Register must NOT auto-sign-in (AuthFlow.kt contract). */
    @Test
    fun lui01_registerPrefilledLoginThenHome() {
        val ctx = begin(LiveCaseIds.LUI_01)
        try {
            driver.launchMain()
            ensureSignedOutBoundary(ctx)
            driver.waitText("Spendwise")
            ctx.recorder.step("welcome shown")
            driver.tap("Create account")
            driver.waitText("Create your account")
            driver.typeText("Username", ctx.fixture.username)
            driver.typeText("Password", ctx.fixture.password)
            driver.tap("Create account")
            // Contract: prefilled login + banner, NOT auto-signed-in.
            driver.waitText("Account created — sign in to continue.")
            ctx.recorder.assertion("prefilled login + banner; no auto-login")
            driver.typeText("Password", ctx.fixture.password)
            driver.tap("Sign in")
            driver.waitCd("Home")
            ctx.recorder.assertion("Home shell after explicit login")
            ctx.recorder.serverCheck(
                "fixture login returns 200 for synthetic identity",
                runCatching {
                    ctx.api.login(ctx.fixture.username, ctx.fixture.password)
                }.isSuccess,
            )
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }

    /** LUI-02 (P0): sign out → same-user login → prior data present. */
    @Test
    fun lui02_signOutThenSameUserLoginDataPersists() {
        val ctx = begin(LiveCaseIds.LUI_02)
        try {
            ctx.api.register(ctx.fixture.username, ctx.fixture.password)
            ctx.api.login(ctx.fixture.username, ctx.fixture.password)
            val acctName = ctx.fixture.accountName("Persist")
            ctx.api.seedAccount(acctName, opening = "500.00")
            ctx.recorder.step("API-SEEDED user+account")

            driver.launchMain()
            signInViaUi(ctx, ctx.fixture.username, ctx.fixture.password)
            driver.navAccounts()
            driver.waitText(acctName, substring = true)
            ctx.recorder.freshness("seeded account visible post-login",
                driver.waitFresh(acctName))

            signOutViaUi(ctx)
            signInViaUi(ctx, ctx.fixture.username, ctx.fixture.password)
            driver.navAccounts()
            driver.waitText(acctName, substring = true)
            ctx.recorder.assertion("prior account + data present after re-login")
            ctx.recorder.serverCheck(
                "server retains the account (parsed row for this id)",
                ctx.api.accountsJson().let { arr ->
                    (0 until arr.length()).any {
                        arr.getJSONObject(it).optString("name") == acctName
                    }
                },
            )
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }

    /** LUI-19 (P2): access-token expiry + VALID refresh → deterministic
     *  renewal + continuity (B05). The trigger is the owner control plane
     *  (`token_control_endpoint` op `expire-access`) — a signed field —
     *  never a narrative. Renewal proof: the control call returns 2xx AND
     *  the persisted session-store CONTENT hash rotates AND a protected
     *  action still works — three correlated signals, none alone sufficient. */
    @Test
    fun lui19_accessExpiryValidRefreshRenews() {
        val ctx = begin(LiveCaseIds.LUI_19)
        try {
            RunOwnership.requireTokenExpiryMechanism(ctx.attestation)
            val control = OwnerControlPlane(ctx.recorder, ctx.attestation.tokenControlEndpoint)
            ctx.api.register(ctx.fixture.username, ctx.fixture.password)
            ctx.api.login(ctx.fixture.username, ctx.fixture.password)
            ctx.api.seedAccount(ctx.fixture.accountName("Sess"))
            driver.launchMain()
            signInViaUi(ctx, ctx.fixture.username, ctx.fixture.password)

            val store = sessionStore()
            ctx.recorder.serverCheck(
                "session store persisted before expiry",
                store.isFile, store.absolutePath,
            )
            val beforeHash = storeHash(store)

            // Deterministic trigger — owner control plane expires the
            // access token server-side. The call's 2xx is recorded.
            ctx.recorder.serverCheck(
                "control plane accepted expire-access for the synthetic user",
                control.call("expire-access",
                    """{"username":"${ctx.fixture.username}"}"""),
                "endpoint=${ctx.attestation.tokenControlEndpoint}",
            )

            // Protected action forces the expiry → renewal path. Tab nav
            // alone refetches nothing (VM stays alive) — pull-to-refresh
            // is the guaranteed live call.
            driver.navAccounts()
            driver.pullDownRefresh()
            driver.navHome()

            // Poll bounded for CONTENT rotation — a real credential write.
            val deadline = System.currentTimeMillis() + 60_000
            var nowHash = storeHash(store)
            while (nowHash == beforeHash && System.currentTimeMillis() < deadline) {
                composeRule.waitForIdle()
                Thread.sleep(500)
                nowHash = storeHash(store)
            }
            ctx.recorder.serverCheck(
                "session store content rotated after expiry (renewal written)",
                nowHash != beforeHash && nowHash != "absent",
            )
            driver.navAccounts()
            driver.waitText(ctx.fixture.accountName("Sess"))
            ctx.recorder.assertion(
                "session continuity post-renewal; identity + stack preserved")
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }

    /** LUI-20 (P2): rejected/expired refresh → expired overlay → explicit
     *  re-auth on the pushed login route → same-user resume (B05).
     *  Trigger = control-plane `reject-refresh`; evidence = 2xx control
     *  call + the expired overlay + completed re-auth. */
    @Test
    fun lui20_rejectedRefreshExpiredOverlayReauth() {
        val ctx = begin(LiveCaseIds.LUI_20)
        try {
            RunOwnership.requireTokenExpiryMechanism(ctx.attestation)
            val control = OwnerControlPlane(ctx.recorder, ctx.attestation.tokenControlEndpoint)
            ctx.api.register(ctx.fixture.username, ctx.fixture.password)
            ctx.api.login(ctx.fixture.username, ctx.fixture.password)
            ctx.api.seedAccount(ctx.fixture.accountName("Sess"))
            driver.launchMain()
            signInViaUi(ctx, ctx.fixture.username, ctx.fixture.password)
            driver.navAccounts()

            ctx.recorder.serverCheck(
                "control plane accepted reject-refresh for the synthetic user",
                control.call("reject-refresh",
                    """{"username":"${ctx.fixture.username}"}"""),
            )
            // Force a real call: expired access → refresh → rejected →
            // Session.Expired → the overlay route is pushed.
            driver.pullDownRefresh()
            driver.waitText("Session expired", substring = true)
            ctx.recorder.assertion("Session Expired surface shown after rejected refresh")
            driver.tap("Sign in again")
            // M3: the expired overlay pushes the login route — Welcome is
            // not on screen, so re-auth uses the login-resume helper.
            reAuthViaUi(ctx, ctx.fixture.username, ctx.fixture.password)
            driver.navAccounts()
            driver.waitText(ctx.fixture.accountName("Sess"))
            ctx.recorder.assertion("same-user resume; data intact")
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }

    /** LUI-28 (P1): cross-user isolation — B must not see A's data/caches;
     *  A logging back in still sees A's data. Any leak → FAIL_PRODUCT. */
    @Test
    fun lui28_crossUserIsolation() {
        val ctx = begin(LiveCaseIds.LUI_28)
        try {
            val userA = ctx.fixture.username
            val passA = ctx.fixture.password
            val acctA = ctx.fixture.accountName("A-ONLY")
            ctx.api.register(userA, passA)
            ctx.api.login(userA, passA)
            ctx.api.seedAccount(acctA, opening = "100.00")
            ctx.recorder.step("API-SEEDED user A")

            val fixtureB = SyntheticFixture.forCase(LiveCaseIds.LUI_28 + "-B")
            val apiB = FixtureApi(ctx.recorder, ctx.attestation)
            apiB.register(fixtureB.username, fixtureB.password)
            apiB.login(fixtureB.username, fixtureB.password)
            val acctB = fixtureB.accountName("B-ONLY")
            apiB.seedAccount(acctB, opening = "200.00")
            ctx.recorder.step("API-SEEDED user B")

            driver.launchMain()
            signInViaUi(ctx, userA, passA)
            driver.navAccounts()
            driver.waitText(acctA, substring = true)

            signOutViaUi(ctx)
            signInViaUi(ctx, fixtureB.username, fixtureB.password)
            driver.navAccounts()
            driver.waitText(acctB, substring = true)
            ctx.recorder.check("no A residue in Accounts list",
                runCatching { driver.assertAbsent(acctA) }.isSuccess)
            driver.navHome()
            ctx.recorder.check("no A residue on Home",
                runCatching { driver.assertAbsent(acctA) }.isSuccess)
            driver.navTransactions()
            ctx.recorder.check("no A residue on Transactions",
                runCatching { driver.assertAbsent(acctA) }.isSuccess)

            // Server-side: B's identity cannot read A's resources — parsed
            // rows, not substring (M03). A leak here is a product FAIL.
            ctx.recorder.serverCheck(
                "B GET /api/accounts contains zero A rows",
                apiB.accountsJson().let { arr ->
                    (0 until arr.length()).none {
                        arr.getJSONObject(it).optString("name") == acctA
                    }
                },
            )

            signOutViaUi(ctx)
            signInViaUi(ctx, userA, passA)
            driver.navAccounts()
            driver.waitText(acctA)
            ctx.recorder.check("A sees only A data",
                runCatching { driver.assertAbsent(acctB) }.isSuccess)
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }
}
