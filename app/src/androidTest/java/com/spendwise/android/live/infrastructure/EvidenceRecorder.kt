package com.spendwise.android.live.infrastructure

import androidx.test.platform.app.InstrumentationRegistry
import com.spendwise.android.live.LiveUiE2eGate
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Per-case evidence recorder (V5 §11).
 *
 * Writes a structured JSON record plus artifact files (screenshots) into
 * the *target package's* files dir: `files/live-e2e/<runId>/<caseId>/`.
 * Retrieval (operator, dedicated device): `adb exec-out run-as
 * com.spendwise.android cat files/live-e2e/...` — debug builds only.
 * (Target context because the instrumentation process runs under the app
 * UID: the `.test` package's files dir is not writable here.)
 *
 * Redaction is structural: this recorder never stores tokens, passwords,
 * Authorization headers, or raw server payloads — only kinds, labels,
 * counts, statuses and timings. [redact] strips JWT-shaped strings and
 * bearer values defensively.
 *
 * Verdict honesty (B4/M05): `verdict("PASS")` requires ≥1 passing typed
 * check and zero failures. A teardown failure recorded afterwards
 * DOWNGRADES the stored verdict to FAIL at [flush] — the file can never
 * claim PASS over a broken teardown or unreconciled state.
 */
class EvidenceRecorder(private val caseId: String) {

    enum class Kind {
        STEP, ASSERTION, NETWORK_FIXTURE, SERVER_VERIFY, FRESHNESS,
        SCREENSHOT, VERDICT, NOTE, TEARDOWN, FIXTURE_INVENTORY,
    }

    private val entries = JSONArray()
    private val dir: File by lazy {
        File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "live-e2e/${LiveUiE2eGate.runId().ifBlank { "unauthorized" }}/$caseId",
        ).apply { mkdirs() }
    }

    val startedEpochS: Long = System.currentTimeMillis() / 1000

    private var checkCount = 0
    private var failedChecks = 0
    private var teardownFailed = false
    private var declaredVerdict: String? = null

    fun artifactFile(name: String): File = File(dir, name)

    fun record(kind: Kind, detail: String) {
        entries.put(
            JSONObject()
                .put("t", System.currentTimeMillis())
                .put("kind", kind.name)
                .put("detail", redact(detail)),
        )
    }

    fun step(detail: String) = record(Kind.STEP, detail)

    /** Recorded assertion — used after a hard UI wait/match succeeded. It
     *  counts toward verdict-PASS eligibility because the driver call that
     *  preceded it already threw on failure. */
    fun assertion(detail: String) {
        checkCount++
        record(Kind.ASSERTION, "$detail -> OK")
    }
    fun freshness(detail: String) = record(Kind.FRESHNESS, detail)

    /** Typed freshness (M7): elapsed ms measured at the driver boundary —
     *  never a free-text claim. */
    fun freshness(detail: String, elapsedMs: Long) =
        record(Kind.FRESHNESS, "$detail | ${elapsedMs}ms")

    fun note(detail: String) = record(Kind.NOTE, detail)

    /** Fail-hard business check (B4): records the outcome and throws on
     *  false — a note can never masquerade as a verified assertion. */
    fun check(label: String, ok: Boolean, detail: String = "") {
        checkCount++
        record(Kind.ASSERTION, "$label -> ${if (ok) "OK" else "FAIL"}" +
            if (detail.isNotEmpty()) " | $detail" else "")
        if (!ok) {
            failedChecks++
            throw AssertionError("CHECK_FAILED: $label — $detail")
        }
    }

    /** Fail-hard server-truth check (B4): parsed, typed verification of the
     *  backend state; mismatch → test failure, not a logged "MISSING". */
    fun serverCheck(label: String, ok: Boolean, detail: String = "") {
        checkCount++
        record(Kind.SERVER_VERIFY, "$label -> ${if (ok) "OK" else "FAIL"}" +
            if (detail.isNotEmpty()) " | $detail" else "")
        if (!ok) {
            failedChecks++
            throw AssertionError("SERVER_CHECK_FAILED: $label — $detail")
        }
    }

    /** M05 — teardown failure is real evidence, not a swallowed exception:
     *  it marks the case and downgrades the stored verdict at flush. */
    fun teardownFailure(detail: String) {
        teardownFailed = true
        record(Kind.TEARDOWN, "FAILURE: $detail")
    }

    fun teardownOk(detail: String) = record(Kind.TEARDOWN, "OK: $detail")

    /** M05 — deterministic inventory of fixtures this case created, for
     *  owner-side disposal reconciliation against the disposable DB. */
    fun fixtureInventory(inventory: JSONObject, disposalPolicy: String) {
        record(Kind.FIXTURE_INVENTORY,
            "disposal=$disposalPolicy entities=${inventory.toString().take(600)}")
    }

    /** Verdict is earned: PASS is rejected unless at least one typed check
     *  succeeded and none failed. BLOCKED/PARTIAL/SKIPPED stay recordable. */
    fun verdict(v: String) {
        if (v == "PASS") {
            check(failedChecks == 0 && checkCount > 0) {
                "verdict PASS requires >=1 passing check and zero failed checks"
            }
        }
        declaredVerdict = v
        record(Kind.VERDICT, v)
    }

    /** Fixture-API call record — method/path/status only, never bodies/tokens. */
    fun networkFixture(method: String, path: String, status: Int) =
        record(Kind.NETWORK_FIXTURE, "$method $path -> $status")

    fun serverVerify(detail: String) = record(Kind.SERVER_VERIFY, detail)

    /** Final verdict actually written to disk: a declared PASS is
     *  downgraded to FAIL when teardown failed — teardown defects are
     *  never buried under a passing headline. */
    private fun finalVerdict(): String = when {
        declaredVerdict == null -> "NO_VERDICT"
        declaredVerdict == "PASS" && teardownFailed -> "FAIL_TEARDOWN"
        else -> declaredVerdict!!
    }

    fun flush(): File {
        val doc = JSONObject()
            .put("case_id", caseId)
            .put("run_id", LiveUiE2eGate.runId().ifBlank { JSONObject.NULL })
            .put("started_epoch_s", startedEpochS)
            .put("ended_epoch_s", System.currentTimeMillis() / 1000)
            .put("declared_verdict", declaredVerdict ?: JSONObject.NULL)
            .put("final_verdict", finalVerdict())
            .put("checks_passed", checkCount - failedChecks)
            .put("checks_failed", failedChecks)
            .put("teardown_failed", teardownFailed)
            .put("entries", entries)
        val file = artifactFile("evidence.json")
        file.writeText(doc.toString(2))
        return file
    }

    private fun redact(raw: String): String =
        raw.replace(Regex("Bearer\\s+\\S+"), "Bearer <redacted>")
            .replace(Regex("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}"), "<jwt>")
            .replace(Regex("(\"(?:password|token|refreshToken|accessToken)\"\\s*:\\s*\")[^\"]+\""), "$1<redacted>\"")
}
