package com.spendwise.android.live.scenarios

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.spendwise.android.MainActivity
import com.spendwise.android.live.LiveUiE2eGate
import com.spendwise.android.live.infrastructure.ApprovedRunPreflight
import com.spendwise.android.live.infrastructure.E2eUiDriver
import com.spendwise.android.live.infrastructure.EvidenceRecorder
import com.spendwise.android.live.infrastructure.FixtureApi
import com.spendwise.android.live.infrastructure.RunOwnership
import com.spendwise.android.live.infrastructure.SyntheticFixture
import org.junit.After
import org.junit.Rule

/**
 * Shared base for Live UI E2E scenario classes (V5 §5.2).
 *
 * - `createEmptyComposeRule` binds to the activity launched manually by
 *   the test AFTER `LiveUiE2eGate.assumeAuthorized` — no auto-launch.
 * - [begin] is the single authorization funnel: gate → recorder →
 *   fixture. Everything past it is a real UI/backend interaction that
 *   only an attested run may perform.
 * - [signInViaUi]/[signOutViaUi] are reusable verified-selector flows.
 * - Teardown is evidence (M05): a failed sign-out/close is recorded AND
 *   re-thrown so the case cannot report PASS over a broken teardown; the
 *   fixture inventory is emitted for owner-side disposal reconciliation.
 */
abstract class LiveE2eTestBase {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    protected val driver: E2eUiDriver by lazy { E2eUiDriver(composeRule) }

    /** Active case context — set by [begin], used by the @After teardown. */
    @Volatile
    private var activeCtx: Ctx? = null

    protected data class Ctx(
        val attestation: ApprovedRunPreflight.Attestation,
        val recorder: EvidenceRecorder,
        val fixture: SyntheticFixture,
        val api: FixtureApi,
    )

    /** Gate + evidence context. Everything after this is attested-run code.
     *  B2: every live case at minimum registers a synthetic user — a write —
     *  so mutation_cleanup_permissions is required before anything runs. */
    protected fun begin(caseId: String): Ctx {
        val att = LiveUiE2eGate.assumeAuthorized(caseId)
        RunOwnership.requireMutation(att)
        val rec = EvidenceRecorder(caseId)
        val fixture = SyntheticFixture.forCase(caseId)
        val ctx = Ctx(att, rec, fixture, FixtureApi(rec, att))
        activeCtx = ctx
        rec.step("authorized: case=$caseId run=${LiveUiE2eGate.runId()} env=${att.environmentId}")
        return ctx
    }

    /** Earned finish (M8): captures a final-state screenshot, then records
     *  the declared verdict — `recorder.verdict("PASS")` still requires ≥1
     *  passing check, and teardown can still downgrade it at flush. */
    protected fun Ctx.finish(status: String) {
        runCatching { driver.screenshot(recorder, "final-state") }
        recorder.verdict(status)
        recorder.flush()
    }

    /** Honest terminal BLOCKED: records evidence, then converts to a JUnit
     *  assumption-skip — a blocked case must never be counted PASS. */
    protected fun Ctx.finishBlocked(reason: String): Nothing {
        recorder.note("BLOCKED: $reason")
        recorder.verdict("BLOCKED")
        recorder.flush()
        org.junit.Assume.assumeTrue("BLOCKED: $reason", false)
        throw AssertionError("unreachable")
    }

    // --- Per-case session isolation (B3) ----------------------------------

    /** B3: order-independent session boundary. After [driver.launchMain] the
     *  app may restore a PERSISTED session left by a previous case — it will
     *  land on the signed-in shell, not Welcome. This waits for either
     *  surface and, when signed in, performs the real sign-out flow so the
     *  case always starts at a signed-out boundary. Never clears app data. */
    protected fun ensureSignedOutBoundary(ctx: Ctx) {
        // Race both markers — a cold-start session restore + refresh can take
        // well past 20s before the signed-in shell renders; Welcome is
        // instant. First marker to appear decides the branch.
        var signedIn = false
        var expiredOverlay = false
        composeRule.waitUntil(60_000) {
            when {
                driver.textVisible("Home") -> { signedIn = true; true }
                driver.textVisible("Spendwise") -> true
                driver.textVisible("Session expired") -> { expiredOverlay = true; true }
                else -> false
            }
        }
        when {
            signedIn -> {
                ctx.recorder.step("prior session detected — signing out for isolation")
                signOutViaUi(ctx)
            }
            expiredOverlay -> {
                // A previous case died mid-re-auth: the overlay's re-auth
                // route is itself signed-out. Dismiss to the login form.
                ctx.recorder.step("expired-overlay residue — resolving to login form")
                driver.tap("Sign in again")
                driver.waitText("Username")
            }
            else -> ctx.recorder.step("signed-out boundary confirmed (Welcome)")
        }
    }

    /** B3+M05: deterministic teardown — close the scenario and, when a
     *  session is still active, sign out so the NEXT case cannot inherit
     *  it. Failures are recorded AND re-thrown: a case that passed its
     *  body but failed to clean up reports as a JUnit failure, and its
     *  stored verdict is downgraded to FAIL_TEARDOWN at flush. */
    @After
    fun liveTeardown() {
        val ctx = activeCtx
        try {
            if (ctx == null || !driver.scenarioInitialized()) return
            var teardownError: Throwable? = null
            try {
                val stillSignedIn = driver.textVisible("Home")
                if (stillSignedIn) {
                    driver.navMore()
                    driver.tap("Sign out")
                    driver.tapDialog("Sign out", "Sign out of Spendwise?")
                    driver.waitText("Spendwise", timeoutMs = 15_000)
                    ctx.recorder.teardownOk("signed out — boundary restored")
                } else {
                    ctx.recorder.teardownOk("no active session to close")
                }
            } catch (t: Throwable) {
                teardownError = t
                ctx.recorder.teardownFailure("sign-out/close failed: ${t.message}")
            }
            runCatching {
                ctx.recorder.fixtureInventory(
                    ctx.api.inventory(),
                    ctx.attestation.fixtureDisposal.ifBlank { "owner_db_reset" },
                )
                ctx.api.logout()
                ctx.recorder.flush()
            }
            driver.scenario.close()
            teardownError?.let { throw AssertionError("TEARDOWN_FAILED", it) }
        } finally {
            activeCtx = null
        }
    }

    // --- Verified auth flows ---------------------------------------------

    /** Welcome → Sign in → enter credentials → Home shell present.
     *  Calls [ensureSignedOutBoundary] first — isolation is mandatory. */
    protected fun signInViaUi(ctx: Ctx, username: String, password: String) {
        ensureSignedOutBoundary(ctx)
        ctx.recorder.step("signInViaUi user=$username")
        if (driver.textVisible("Username")) {
            // Boundary resolved on the login form itself (e.g. an expired-
            // overlay residue was dismissed to the re-auth route).
            ctx.recorder.step("login form already presented")
        } else {
            driver.waitText("Spendwise")
            driver.tap("Sign in")
            driver.waitText("Username")
        }
        driver.typeText("Username", username)
        driver.typeText("Password", password)
        driver.tap("Sign in")
        driver.waitText("Home") // bottom-nav label + Home title
        ctx.recorder.assertion("authenticated shell visible (Home nav)")
    }

    /** Re-auth from an already-presented login route — the expired overlay
     *  pushes the login destination (M3); Welcome is not on screen. */
    protected fun reAuthViaUi(ctx: Ctx, username: String, password: String) {
        ctx.recorder.step("reAuthViaUi user=$username")
        driver.waitText("Username")
        driver.typeText("Username", username)
        driver.typeText("Password", password)
        driver.debugDumpFields("REAUTH")
        driver.tap("Sign in")
        driver.waitText("Home")
        ctx.recorder.assertion("re-auth completed → Home shell")
    }

    /** More → Sign out → confirm → Welcome/Sign-in surface returns. */
    protected fun signOutViaUi(ctx: Ctx) {
        ctx.recorder.step("signOutViaUi")
        driver.navMore()
        driver.tap("Sign out")
        driver.tapDialog("Sign out", "Sign out of Spendwise?")
        driver.waitText("Spendwise")
        ctx.recorder.assertion("signed-out surface restored")
    }
}
