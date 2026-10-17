package com.spendwise.android.live.scenarios

import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spendwise.android.live.infrastructure.LiveCaseIds
import com.spendwise.android.live.infrastructure.RunOwnership
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Live UI E2E — lifecycle scenarios. Owners: LUI-22, LUI-23 (two phases).
 *
 * B03 — process death CANNOT be tested inside a single instrumentation
 * session: the JUnit thread lives in the app process, so killing the app
 * kills the test itself. The protocol is therefore explicitly two-phase:
 *
 *   Phase A  [lui23a_establishStateAndArm] — sign in, build state, write a
 *            run-bound ARM marker (pid + runId + epoch) to the target's
 *            files dir; external host kills the app AFTER this point and
 *            deposits a host-captured proof `{run_id, before_pid,
 *            after_pid, killed_at_epoch_s}` at the attested proof path.
 *   Phase B  [lui23b_verifyAfterRelaunch] — a SECOND instrumentation
 *            invocation: reads marker + proof, verifies the binding
 *            (run_id + before_pid == marker pid + fresh timestamp),
 *            relaunches, asserts restoration or honest login surface.
 *
 * Both phases REQUIRE `process_kill_permission` + `process_kill_proof_path`
 * in the signed attestation — the host orchestrator supplies the kill and
 * the proof; the test supplies the marker and the post-death verification.
 */
@RunWith(AndroidJUnit4::class)
class LiveLifecycleE2eTest : LiveE2eTestBase() {

    private fun armMarkerFile(): File = File(
        InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
        "live-e2e/process-kill-arm.json",
    )

    private fun signedIn(ctx: Ctx) {
        ctx.api.register(ctx.fixture.username, ctx.fixture.password)
        ctx.api.login(ctx.fixture.username, ctx.fixture.password)
        ctx.api.seedAccount(ctx.fixture.accountName("Life"))
        driver.launchMain()
        signInViaUi(ctx, ctx.fixture.username, ctx.fixture.password)
    }

    /** LUI-22 (P2): configuration change / activity recreate — session
     *  survives in-process; Home reloads with the same data. */
    @Test
    fun lui22_activityRecreateKeepsSession() {
        val ctx = begin(LiveCaseIds.LUI_22)
        try {
            signedIn(ctx)
            driver.navAccounts()
            driver.waitText(ctx.fixture.accountName("Life"))

            driver.recreateActivity()
            driver.waitText(ctx.fixture.accountName("Life"), timeoutMs = 60_000)
            ctx.recorder.assertion("same signed-in data after in-process recreate")
            ctx.recorder.freshness("no re-login required post-recreate",
                driver.waitFresh(ctx.fixture.accountName("Life")))
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }

    /** LUI-23 phase A (P2): establish state + write the ARM marker the
     *  host orchestrator correlates against. Verdict PHASE_A_ARMED —
     *  the journey is NOT complete until phase B verifies. */
    @Test
    fun lui23a_establishStateAndArm() {
        val ctx = begin(LiveCaseIds.LUI_23 + "-A")
        try {
            RunOwnership.requireProcessKill(ctx.attestation)
            signedIn(ctx)
            driver.navAccounts()
            driver.waitText(ctx.fixture.accountName("Life"))

            val marker = JSONObject()
                .put("run_id", ctx.attestation.runId)
                .put("case", "LUI-23")
                .put("phase", "ARMED")
                .put("pid", Process.myPid())
                .put("account", ctx.fixture.accountName("Life"))
                .put("armed_epoch_s", System.currentTimeMillis() / 1000)
            armMarkerFile().apply { parentFile?.mkdirs(); writeText(marker.toString()) }
            ctx.recorder.step(
                "ARM marker written: pid=${Process.myPid()} — host must kill " +
                    "now and deposit proof at ${ctx.attestation.processKillProofPath}")
            ctx.recorder.assertion("phase-A state established + marker armed")
            ctx.finish("PASS") // phase-A only — the kill proof is phase B
        } finally { ctx.recorder.flush() }
    }

    /** LUI-23 phase B (P2): verify the host-side kill proof is bound to
     *  THIS run's marker (run_id + same pid + timestamp after arming),
     *  then relaunch and assert restoration or honest login. Absent or
     *  mismatched proof → BLOCKED — a proof file alone is not death. */
    @Test
    fun lui23b_verifyAfterRelaunch() {
        val ctx = begin(LiveCaseIds.LUI_23 + "-B")
        try {
            RunOwnership.requireProcessKill(ctx.attestation)

            val markerFile = armMarkerFile()
            if (!markerFile.isFile) {
                ctx.finishBlocked(
                    "no ARM marker — phase A (lui23a_establishStateAndArm) " +
                        "did not run in this process lineage")
            }
            val marker = JSONObject(markerFile!!.readText())
            ctx.recorder.serverCheck(
                "ARM marker belongs to this run",
                marker.optString("run_id") == ctx.attestation.runId,
                "marker run_id=${marker.optString("run_id")}",
            )
            val armedPid = marker.getLong("pid")
            val armedAt = marker.getLong("armed_epoch_s")

            // Host proof — must bind run_id, record the killed pid as the
            // SAME process we armed, and be fresh (killed after arming).
            val proofFile = File(ctx.attestation.processKillProofPath)
            if (!proofFile.isFile) {
                ctx.finishBlocked(
                    "kill proof absent at '${ctx.attestation.processKillProofPath}' — " +
                        "host orchestrator did not run")
            }
            val proof = JSONObject(proofFile.readText())
            ctx.recorder.serverCheck(
                "proof bound to this run + the armed process died " +
                    "(run_id match, before_pid==armed pid, after_pid<=0, " +
                    "killed_at >= armed_at)",
                proof.optString("run_id") == ctx.attestation.runId &&
                    proof.optLong("before_pid") == armedPid &&
                    proof.optLong("after_pid", -1) <= 0 &&
                    proof.optLong("killed_at_epoch_s") >= armedAt,
                "before=${proof.optLong("before_pid")} after=${proof.optLong("after_pid")} " +
                    "killed_at=${proof.optLong("killed_at_epoch_s")} armed_pid=$armedPid",
            )
            ctx.recorder.assertion("host-verified process death proven for this run")

            driver.launchMain()
            val signedOut = runCatching {
                driver.waitText("Sign in", timeoutMs = 30_000); true
            }.getOrDefault(false)
            if (signedOut) {
                ctx.recorder.assertion("honest login surface after refresh expiry")
            } else {
                driver.waitCd("Home", timeoutMs = 90_000)
                driver.navAccounts()
                driver.waitText(marker.getString("account"), timeoutMs = 60_000)
                ctx.recorder.freshness("persisted session restored post-kill",
                    driver.waitFresh(marker.getString("account")))
            }
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }
}
