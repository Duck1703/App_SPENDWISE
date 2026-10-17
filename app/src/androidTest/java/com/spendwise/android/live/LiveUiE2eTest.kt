package com.spendwise.android.live

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.MainActivity
import com.spendwise.android.live.LiveUiE2eGate.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * L1 inert Live UI E2E harness — kept intentionally executable-free
 * (Blueprint V5 §15, L1-A03). Real scenarios live under `live/scenarios/`
 * behind `LiveUiE2eGate.assumeAuthorized(caseId)`; this class only proves
 * the gate machinery itself.
 *
 * Properties:
 * - `Live*` name keeps the proven `notClass` exclusion style viable.
 * - Gate-contract tests are hermetic — fabricated args, no network.
 * - The skeleton scenario is skipped in every mode (closed gate AND open
 *   gate): L1 ships no executable live scenario, so nothing here can be
 *   misread as `PASS_E2E`.
 * - No auto-launch rule exists; [launchMainActivity] is only reachable by
 *   an authorized scenario body after the gate.
 */
@RunWith(AndroidJUnit4::class)
class LiveUiE2eTest {

    /**
     * Controlled Activity entry point for authorized scenarios only —
     * deferring ACTIVITY start; it cannot gate Application startup, which
     * [LiveUiE2eRunner] + the build-time URL + L0 install controls own.
     */
    protected fun launchMainActivity(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java)

    // --- Gate contract (hermetic — fabricated args, no network) ----------

    @Test
    fun gate_deniesWithoutOptInArg() {
        assertEquals(
            Decision.SKIPPED_BY_GATE_NO_OPT_IN,
            LiveUiE2eGate.evaluate(null, "run-1", "https://10.0.0.5:8443/"),
        )
        assertEquals(
            Decision.SKIPPED_BY_GATE_NO_OPT_IN,
            LiveUiE2eGate.evaluate("0", "run-1", "https://10.0.0.5:8443/"),
        )
    }

    @Test
    fun gate_deniesWithoutRunAttestationId() {
        assertEquals(
            Decision.SKIPPED_BY_GATE_NO_RUN_ID,
            LiveUiE2eGate.evaluate("1", null, "https://10.0.0.5:8443/"),
        )
        assertEquals(
            Decision.SKIPPED_BY_GATE_NO_RUN_ID,
            LiveUiE2eGate.evaluate("1", "  ", "https://10.0.0.5:8443/"),
        )
    }

    @Test
    fun gate_deniesNonLiveUrlsEvenWhenOptedIn() {
        assertEquals(
            Decision.SKIPPED_BY_GATE_PLACEHOLDER_URL,
            LiveUiE2eGate.evaluate("1", "run-1", "https://example.invalid/"),
        )
        // Structural parse (V5 L1-A02): decision rests on the real host +
        // the production validator, never substring matching.
        assertFalse(LiveUiE2eGate.isLiveBaseUrl("not a url"))
        // A near-miss hostname is a DIFFERENT dialable host — correctly
        // treated as live (so it still demands attestation). A naive
        // contains("example.invalid") check would wrongly refuse it.
        assertTrue(LiveUiE2eGate.isLiveBaseUrl("https://example.invalid.evil.test/"))
        // The real placeholder host with a non-root path fails ApiBaseUrl
        // regardless — not live.
        assertFalse(LiveUiE2eGate.isLiveBaseUrl("https://example.invalid/path"))
    }

    @Test
    fun gate_satisfiedOnlyWhenAllArgLayersPass() {
        assertEquals(
            Decision.ARGS_SATISFIED,
            LiveUiE2eGate.evaluate("1", "run-1", "https://10.0.0.5:8443/"),
        )
        // ...but ARGS_SATISFIED is not authorization — assumeAuthorized
        // still requires a verified L0 attestation per case (V5 L1-A01).
    }

    @Test
    fun urlValidator_isLiveBaseUrl_structuralCheck() {
        // Private-LAN debug HTTPS passes the production validator.
        assertTrue(LiveUiE2eGate.isLiveBaseUrl("https://10.0.0.5:8443/"))
        // Placeholder can never be live regardless of opt-in args.
        assertFalse(LiveUiE2eGate.isLiveBaseUrl("https://example.invalid/"))
    }

    // --- Skeleton scenario (inert — SKIPPED_BY_GATE in every mode) -------

    @Test
    fun liveScenario_skeleton_isSkippedByGate() {
        LiveUiE2eGate.assumeAuthorized("LUI-00-skeleton")
        // Unreachable on unauthorized runs (assumption above skips) and
        // intentionally skipped even when authorized — L1 has no live body.
        assumeTrue(
            "SKIPPED_BY_GATE: L1 ships no executable live scenario — " +
                "real scenarios live under live/scenarios/.",
            false,
        )
    }
}
