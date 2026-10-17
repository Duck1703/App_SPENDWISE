package com.spendwise.android.live

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spendwise.android.live.infrastructure.ApprovedRunPreflight
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyPairGenerator

/**
 * Negative-authorization contract tests for [ApprovedRunPreflight] (B01/B02).
 *
 * Hermetic — fabricated attestation docs + a freshly generated RSA keypair
 * injected via the test seam (the production path reads the pinned owner
 * key from test-APK assets). Proves every deny branch actually denies:
 * unsigned, wrong-key signature, tampered privileged fields, unknown
 * fields, wrong identity/origin/scope/window, non-positive budget — and
 * that the RUNNER path ([LiveUiE2eRunner.preApplicationGuard]) consumes
 * only its Bundle/Context inputs, never the instrumentation registry.
 */
@RunWith(AndroidJUnit4::class)
class PreflightContractTest {

    private val testCtx get() =
        InstrumentationRegistry.getInstrumentation().context
    private val targetCtx get() =
        InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dir: File
    private lateinit var keyPair: java.security.KeyPair
    private val liveUrl = "https://10.0.0.5:8443/"

    @Before
    fun setUp() {
        // Instrumentation shares the TARGET app's UID — only target dirs
        // are writable from test code; the test package supplies assets only.
        dir = targetCtx.cacheDir.resolve("preflight-test").apply { mkdirs() }
        keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()
    }

    /** A fully-valid attestation bound to THIS device + package + origin. */
    private fun validDoc(): JSONObject = JSONObject()
        .put("run_id", "run-test-1")
        .put("package_id", targetCtx.packageName)
        .put("scope_case_ids", JSONArray().put("LUI-01"))
        .put("allowed_endpoints", JSONArray().put("https://10.0.0.5:8443"))
        .put("isolated_backend_environment_id", "env-test")
        .put("disposable_db_instance_id", "db-test")
        .put("dedicated_device_id", ApprovedRunPreflight.deviceIdentity(targetCtx))
        .put("authorized_operator", "operator-test")
        .put("time_window", JSONObject()
            .put("start_epoch_s", 0)
            .put("end_epoch_s", 4_000_000_000L))
        .put("synthetic_credentials_reference", "creds-test")
        .put("mutation_cleanup_permissions", true)
        .put("bulk_seed_max_rows", 300)
        .put("max_fixture_writes", 500)
        .put("saf_file_paths", JSONArray().put("/tmp/staged/a.csv"))
        .put("token_expiry_mechanism_approved", true)
        .put("token_control_endpoint", "http://control.test/spendwise")
        .put("network_fault_permission", true)
        .put("inflight_fault_endpoint", "http://control.test/fault")
        .put("request_delay_ms", 3000)
        .put("performance_threshold_ms", 8000)
        .put("process_kill_permission", true)
        .put("process_kill_proof_path", "/data/local/tmp/spendwise/kill.json")
        .put("fixture_disposal", "owner_db_reset")
        .put("android_prod_fixes_allowed", false)

    private fun signAndWrite(doc: JSONObject, key: java.security.PrivateKey = keyPair.private): File {
        val signed = doc.put(
            "approval_signature",
            ApprovedRunPreflight.signPayload(
                ApprovedRunPreflight.canonicalPayload(doc), key),
        )
        return File(dir, "go-${System.nanoTime()}.json").apply { writeText(signed.toString()) }
    }

    private fun runLevel(file: File, runId: String? = "run-test-1",
                         baseUrl: String = liveUrl,
                         key: ByteArray = keyPair.public.encoded) =
        ApprovedRunPreflight.preApplicationGuard(
            runId, baseUrl, file.absolutePath, testCtx, targetCtx, key)

    private fun deniedReason(r: ApprovedRunPreflight.Result): String =
        (r as? ApprovedRunPreflight.Result.Denied)?.reason.orEmpty()

    // --- Runner-path (B01): decision consumes only Bundle+Context ----------

    @Test
    fun runner_deniesMissingOptIn() {
        val r = LiveUiE2eRunner.preApplicationGuard(
            Bundle(), testCtx, targetCtx, liveUrl)
        assertTrue(deniedReason(r).contains("opt_in").or(deniedReason(r).contains("liveUiE2e")))
    }

    @Test
    fun runner_deniesAbsentAttestation() {
        val args = Bundle().apply {
            putString(LiveUiE2eGate.ARG_OPT_IN, "1")
            putString(LiveUiE2eGate.ARG_RUN_ID, "run-test-1")
            putString(ApprovedRunPreflight.ARG_ATTESTATION,
                File(dir, "missing.json").absolutePath)
        }
        val r = LiveUiE2eRunner.preApplicationGuard(args, testCtx, targetCtx, liveUrl)
        assertTrue(r is ApprovedRunPreflight.Result.Denied)
    }

    @Test
    fun runner_verifiesFullyBoundAttestation() {
        val file = signAndWrite(validDoc())
        val args = Bundle().apply {
            putString(LiveUiE2eGate.ARG_OPT_IN, "1")
            putString(LiveUiE2eGate.ARG_RUN_ID, "run-test-1")
            putString(ApprovedRunPreflight.ARG_ATTESTATION, file.absolutePath)
        }
        val r = LiveUiE2eRunner.preApplicationGuard(
            args, testCtx, targetCtx, liveUrl, keyPair.public.encoded)
        assertTrue("expected Verified, got ${deniedReason(r)}",
            r is ApprovedRunPreflight.Result.Verified)
    }

    // --- Signature integrity (B02) ------------------------------------------

    @Test
    fun preflight_deniesUnsignedDoc() {
        val file = File(dir, "unsigned.json").apply { writeText(validDoc().toString()) }
        assertTrue(deniedReason(runLevel(file)).contains("signature"))
    }

    @Test
    fun preflight_deniesWrongKeySignature() {
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
            .generateKeyPair()
        val file = signAndWrite(validDoc(), key = other.private)
        assertTrue(deniedReason(runLevel(file)).contains("signature"))
    }

    @Test
    fun preflight_deniesTamperedSafPath() {
        // Privileged field mutated AFTER signing → signature must break.
        val file = signAndWrite(validDoc())
        val tampered = JSONObject(file.readText())
            .put("saf_file_paths", JSONArray().put("/etc/evil.csv"))
        val f2 = File(dir, "tampered-saf.json").apply { writeText(tampered.toString()) }
        assertTrue(deniedReason(runLevel(f2)).contains("signature"))
    }

    @Test
    fun preflight_deniesTamperedKillProofPath() {
        val file = signAndWrite(validDoc())
        val tampered = JSONObject(file.readText())
            .put("process_kill_proof_path", "/data/local/tmp/evil.json")
        val f2 = File(dir, "tampered-kill.json").apply { writeText(tampered.toString()) }
        assertTrue(deniedReason(runLevel(f2)).contains("signature"))
    }

    @Test
    fun preflight_deniesTamperedBulkBudget() {
        val file = signAndWrite(validDoc())
        val tampered = JSONObject(file.readText()).put("bulk_seed_max_rows", 999999)
        val f2 = File(dir, "tampered-bulk.json").apply { writeText(tampered.toString()) }
        assertTrue(deniedReason(runLevel(f2)).contains("signature"))
    }

    @Test
    fun preflight_deniesUnknownPrivilegedField() {
        val doc = validDoc().put("grant_admin", true) // smuggled field
        val file = signAndWrite(doc)
        assertTrue(deniedReason(runLevel(file)).contains("unexpected"))
    }

    // --- Binding denies -----------------------------------------------------

    @Test
    fun preflight_deniesRunIdMismatch() {
        assertTrue(deniedReason(runLevel(signAndWrite(validDoc()), "wrong-run"))
            .contains("run-id"))
    }

    @Test
    fun preflight_deniesWrongPackage() {
        assertTrue(deniedReason(runLevel(signAndWrite(
            validDoc().put("package_id", "com.evil.other")))).contains("package"))
    }

    @Test
    fun preflight_deniesWrongDevice() {
        assertTrue(deniedReason(runLevel(signAndWrite(
            validDoc().put("dedicated_device_id", "other-device"))))
            .contains("dedicated_device_id"))
    }

    @Test
    fun preflight_deniesWrongEndpointOrigin() {
        // Same host, different port — a bare-host match would wrongly pass.
        val r = runLevel(signAndWrite(validDoc()), baseUrl = "https://10.0.0.5:9443/")
        assertTrue(deniedReason(r).contains("allowed_endpoints"))
    }

    @Test
    fun preflight_deniesExpiredWindow() {
        val doc = validDoc().put("time_window", JSONObject()
            .put("start_epoch_s", 1).put("end_epoch_s", 2))
        assertTrue(deniedReason(runLevel(signAndWrite(doc))).contains("time"))
    }

    @Test
    fun preflight_deniesBlankEnvironmentIds() {
        assertTrue(deniedReason(runLevel(signAndWrite(
            validDoc().put("disposable_db_instance_id", " ")))).contains("non-blank"))
    }

    @Test
    fun preflight_deniesEmptyScope() {
        assertTrue(deniedReason(runLevel(signAndWrite(
            validDoc().put("scope_case_ids", JSONArray())))).contains("scope_case_ids"))
    }

    @Test
    fun preflight_deniesNonPositiveBudget() {
        assertTrue(deniedReason(runLevel(signAndWrite(
            validDoc().put("max_fixture_writes", 0)))).contains("budget"))
    }

    // --- Per-case + happy paths ---------------------------------------------

    @Test
    fun preflight_perCaseDeniesCaseOutsideScope() {
        val file = signAndWrite(validDoc())
        val r = ApprovedRunPreflight.verify(
            "LUI-99", "run-test-1", liveUrl, targetCtx, testCtx,
            file.absolutePath, keyPair.public.encoded)
        assertTrue(r is ApprovedRunPreflight.Result.Denied)
    }

    @Test
    fun preflight_verifiesFullyBoundSignedAttestation() {
        val file = signAndWrite(validDoc())
        val r = runLevel(file)
        assertTrue("expected Verified, got ${deniedReason(r)}",
            r is ApprovedRunPreflight.Result.Verified)
        val perCase = ApprovedRunPreflight.verify(
            "LUI-01", "run-test-1", liveUrl, targetCtx, testCtx,
            file.absolutePath, keyPair.public.encoded)
        assertTrue(perCase is ApprovedRunPreflight.Result.Verified)
    }

    @Test
    fun gate_skeletonStillSkippedOnPlaceholderBuild() {
        assertEquals(
            LiveUiE2eGate.Decision.SKIPPED_BY_GATE_NO_OPT_IN,
            LiveUiE2eGate.argsDecision(),
        )
    }
}
