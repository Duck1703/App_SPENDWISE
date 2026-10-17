package com.spendwise.android.live

import androidx.test.platform.app.InstrumentationRegistry
import com.spendwise.android.BuildConfig
import com.spendwise.android.core.network.ApiBaseUrl
import com.spendwise.android.live.infrastructure.ApprovedRunPreflight
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.junit.Assume.assumeTrue

/**
 * In-test opt-in gate for every Live UI E2E scenario (Blueprint V5 §5.3).
 *
 * **A run-id or a URL is NOT owner approval (V5 L1-A01).** This object is
 * the fourth, weakest defense layer — defense-in-depth only. The real
 * boundaries are, in order: the outside-process L0 attestation (verified
 * here via [ApprovedRunPreflight] and earlier by [LiveUiE2eRunner] before
 * `Application.onCreate`), the dedicated test install, and the build-time
 * placeholder URL that `ApiBaseUrl.validate` fails closed.
 *
 * Layers required for a live scenario body to execute:
 *   1. `-e spendwise.liveUiE2e 1` opt-in.
 *   2. `-e spendwise.liveUiE2eRunId <id>` — must equal the `run_id` inside
 *      the on-device L0 attestation file; alone it proves nothing.
 *   3. A build whose API base URL parses as a real (non-placeholder) host.
 *   4. A valid L0 attestation covering this case id, this package, this
 *      host, within its time window.
 *
 * Any miss reports `SKIPPED_BY_GATE` via JUnit assumption — never a PASS,
 * never a FAIL.
 */
object LiveUiE2eGate {

    const val ARG_OPT_IN = "spendwise.liveUiE2e"
    const val ARG_RUN_ID = "spendwise.liveUiE2eRunId"
    const val PLACEHOLDER_HOST = "example.invalid"

    enum class Decision {
        /** All arg/URL layers satisfied — attestation still required per case. */
        ARGS_SATISFIED,
        SKIPPED_BY_GATE_NO_OPT_IN,
        SKIPPED_BY_GATE_NO_RUN_ID,
        SKIPPED_BY_GATE_PLACEHOLDER_URL,
        /** Args+URL fine but no valid L0 attestation binds this case. */
        SKIPPED_BY_GATE_NO_ATTESTATION,
    }

    /**
     * Pure arg/URL evaluation — fabricated inputs, no device state, so the
     * truth table is unit-testable. URL is structurally parsed (V5 L1-A02):
     * the decision hinges on the real host, never a substring test.
     */
    fun evaluate(optInArg: String?, runIdArg: String?, baseUrl: String): Decision = when {
        optInArg != "1" -> Decision.SKIPPED_BY_GATE_NO_OPT_IN
        runIdArg.isNullOrBlank() -> Decision.SKIPPED_BY_GATE_NO_RUN_ID
        !isLiveBaseUrl(baseUrl) -> Decision.SKIPPED_BY_GATE_PLACEHOLDER_URL
        else -> Decision.ARGS_SATISFIED
    }

    /**
     * True when the URL parses to a real host accepted by the production
     * validator — i.e. a build that could actually reach a backend. The
     * `example.invalid` placeholder and malformed URLs are not live.
     */
    fun isLiveBaseUrl(baseUrl: String): Boolean {
        val parsed = baseUrl.toHttpUrlOrNull() ?: return false
        if (parsed.host == PLACEHOLDER_HOST) return false
        return runCatching { ApiBaseUrl.validate(baseUrl, BuildConfig.DEBUG) }.isSuccess
    }

    /** Parsed host of the build URL, or null if not a live build. */
    fun liveUrlHost(): String? =
        BuildConfig.SPENDWISE_API_BASE_URL.toHttpUrlOrNull()?.host
            ?.takeIf { isLiveBaseUrl(BuildConfig.SPENDWISE_API_BASE_URL) }

    fun argsDecision(): Decision {
        val args = InstrumentationRegistry.getArguments()
        return evaluate(
            args.getString(ARG_OPT_IN),
            args.getString(ARG_RUN_ID),
            BuildConfig.SPENDWISE_API_BASE_URL,
        )
    }

    /**
     * Full authorization for [caseId]: arg layers + parsed live URL +
     * verified L0 attestation. First statement of every live scenario.
     */
    fun assumeAuthorized(caseId: String): ApprovedRunPreflight.Attestation {
        val args = InstrumentationRegistry.getArguments()
        val decision = argsDecision()
        assumeTrue(
            "SKIPPED_BY_GATE ($decision): case '$caseId' requires -e $ARG_OPT_IN 1, " +
                "-e $ARG_RUN_ID <id>, a non-placeholder API base URL, and a valid " +
                "L0 attestation on the dedicated device.",
            decision == Decision.ARGS_SATISFIED,
        )
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val preflight = ApprovedRunPreflight.verify(
            caseId = caseId,
            runIdArg = args.getString(ARG_RUN_ID),
            baseUrl = BuildConfig.SPENDWISE_API_BASE_URL,
            // target context = app under test (package id + ANDROID_ID scope);
            // test context = this APK's assets (embedded owner public key).
            context = instrumentation.targetContext,
            keyContext = instrumentation.context,
            attestationPath = args.getString(ApprovedRunPreflight.ARG_ATTESTATION)
                ?: ApprovedRunPreflight.DEFAULT_PATH,
        )
        assumeTrue(
            "SKIPPED_BY_GATE (SKIPPED_BY_GATE_NO_ATTESTATION): " +
                (preflight as? ApprovedRunPreflight.Result.Denied)?.reason,
            preflight is ApprovedRunPreflight.Result.Verified,
        )
        return (preflight as ApprovedRunPreflight.Result.Verified).attestation
    }

    /** Owner-issued run id — call only after [assumeAuthorized]. */
    fun runId(): String =
        InstrumentationRegistry.getArguments().getString(ARG_RUN_ID).orEmpty()
}
