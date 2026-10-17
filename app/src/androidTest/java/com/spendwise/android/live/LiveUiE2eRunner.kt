package com.spendwise.android.live

import android.content.Context
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import com.spendwise.android.live.infrastructure.ApprovedRunPreflight

/**
 * Test-only instrumentation runner providing the **pre-Application** guard
 * that in-test gates cannot (Blueprint V5 §5.3 layer-3, L1-A04).
 *
 * `SpendwiseApplication.onCreate` restores the session and can emit a
 * `POST /api/auth/refresh` when a persisted refresh credential survives —
 * at process start, before any Activity and before any test body. An
 * `assumeTrue` inside a test can never gate that.
 *
 * Process-start ordering (verified by execution):
 *   `Instrumentation.newApplication` → runner `onCreate` →
 *   `Instrumentation.callApplicationOnCreate` → `Application.onCreate`.
 *
 * Lifecycle fix (final-review B01): `InstrumentationRegistry` is NOT
 * populated until `MonitoringInstrumentation.onCreate` runs — the earlier
 * implementation read `getArguments()`/`getInstrumentation()` inside this
 * method's pre-super block, which would have thrown `IllegalStateException`
 * on a live-URL build. The guard now consumes ONLY what this method is
 * provably handed: the `arguments` Bundle parameter plus the
 * `Instrumentation.getContext()/getTargetContext()` handles that
 * `ActivityThread` attaches before `onCreate` is invoked. The same seam —
 * [preApplicationGuard] — is exercised directly by the hermetic contract
 * tests, so the runner path is unit-verified, not just sketched.
 *
 * Policy — fail closed, evaluated before the Application starts:
 *
 * - **Placeholder-URL build** (`example.invalid` or any URL the production
 *   `ApiBaseUrl` validator rejects): ordinary behavior — the app cannot
 *   dial anywhere, so hermetic + legacy suites run unchanged.
 * - **Live-URL build**: instrumentation is permitted only when the full
 *   run-level chain is satisfied — `-e spendwise.liveUiE2e 1`, a
 *   `-e spendwise.liveUiE2eRunId` that matches the `run_id` inside a
 *   validly owner-signed L0 attestation on the device, correct package,
 *   dedicated device, allowed origin, in-window, non-empty scope.
 *   Anything less throws before `Application.onCreate` — no restore, no
 *   renewal, zero app traffic.
 *
 * Per-case scope is still enforced later by [LiveUiE2eGate.assumeAuthorized];
 * this guard only licenses the *process* to start on a live build.
 *
 * Wired via `testInstrumentationRunner` in `app/build.gradle.kts` — a
 * test-configuration change only; production code and the release
 * artifact are untouched.
 */
class LiveUiE2eRunner : AndroidJUnitRunner() {

    override fun onCreate(arguments: Bundle) {
        if (LiveUiE2eGate.isLiveBaseUrl(
                com.spendwise.android.BuildConfig.SPENDWISE_API_BASE_URL)) {
            // B01: `arguments` is the Bundle ActivityThread handed to
            // onCreate; `context`/`targetContext` are already attached by
            // Instrumentation.init. InstrumentationRegistry is deliberately
            // never consulted — it is empty until super.onCreate runs.
            val decision = preApplicationGuard(
                arguments = arguments,
                testContext = context,
                targetContext = targetContext,
                baseUrl = com.spendwise.android.BuildConfig.SPENDWISE_API_BASE_URL,
            )
            check(decision is ApprovedRunPreflight.Result.Verified) {
                "LIVE_UI_E2E_PRE_APPLICATION_GUARD: refusing to start the " +
                    "Application on a live-URL build — " +
                    (decision as? ApprovedRunPreflight.Result.Denied)?.reason +
                    ". Startup session restore could emit network traffic " +
                    "before any test gate; instrumentation is denied before " +
                    "callApplicationOnCreate."
            }
        }
        super.onCreate(arguments)
    }

    companion object {

        /** The complete runner-side decision — every input explicit so the
         *  hermetic contract tests can exercise the identical path with a
         *  fabricated Bundle (no instrumentation registry needed). */
        fun preApplicationGuard(
            arguments: Bundle,
            testContext: Context,
            targetContext: Context?,
            baseUrl: String,
            publicKeyDer: ByteArray? = ApprovedRunPreflight.loadOwnerKey(testContext),
        ): ApprovedRunPreflight.Result {
            val optIn = arguments.getString(LiveUiE2eGate.ARG_OPT_IN) == "1"
            if (!optIn) {
                return ApprovedRunPreflight.Result.Denied(
                    "missing -e ${LiveUiE2eGate.ARG_OPT_IN} 1")
            }
            val path = arguments.getString(ApprovedRunPreflight.ARG_ATTESTATION)
                ?: ApprovedRunPreflight.DEFAULT_PATH
            return ApprovedRunPreflight.preApplicationGuard(
                runIdArg = arguments.getString(LiveUiE2eGate.ARG_RUN_ID),
                baseUrl = baseUrl,
                attestationPath = path,
                testContext = testContext,
                targetContext = targetContext,
                publicKeyDer = publicKeyDer,
            )
        }
    }
}
