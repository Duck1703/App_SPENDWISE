package com.spendwise.android.live.infrastructure

import android.content.Context
import android.os.Build
import android.provider.Settings
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.File
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * L0 Owner-GO attestation verifier (Blueprint V5 §2, §5.3 layer-1/4 —
 * hardened per independent-review findings B01/B02).
 *
 * **Trust boundary (B02):** the owner signs the attestation OFF-DEVICE with
 * an RSA private key that never enters the repository or the run command.
 * Verification uses the pinned public key embedded in the TEST APK assets
 * (`androidTest/assets/l0-owner.pub`, DER/X.509). There is NO runtime key
 * path argument — the invoker cannot redirect verification at their own
 * key, and cannot produce a matching signature without the private key.
 * APK build provenance is the outermost trust anchor: whoever builds or
 * approves the test APK controls which public key is embedded.
 *
 * **B01:** every input is an explicit parameter — nothing reads
 * `InstrumentationRegistry`. [LiveUiE2eRunner] calls [preApplicationGuard]
 * with the `arguments` Bundle it is handed and `targetContext`, both of
 * which exist before `super.onCreate`; the static registry is never
 * touched on the startup path.
 *
 * **Signing completeness:** [canonicalPayload] covers EVERY privileged
 * field — mechanism flags, file paths, budgets, endpoints, identity,
 * window. Unknown top-level fields are rejected outright, so a forged or
 * tampered document can never smuggle an unsigned privilege.
 */
object ApprovedRunPreflight {

    const val ARG_ATTESTATION = "spendwise.liveUiE2eAttestation"
    const val DEFAULT_PATH = "/data/local/tmp/spendwise/l0-go.json"
    const val OWNER_KEY_ASSET = "l0-owner.pub"

    /** Every legal top-level field. Anything outside this set fails closed. */
    private val KNOWN_FIELDS = setOf(
        "run_id", "package_id", "scope_case_ids", "allowed_endpoints",
        "isolated_backend_environment_id", "disposable_db_instance_id",
        "dedicated_device_id", "authorized_operator", "time_window",
        "synthetic_credentials_reference",
        "mutation_cleanup_permissions", "bulk_seed_max_rows",
        "max_fixture_writes", "saf_file_paths",
        "token_expiry_mechanism_approved", "token_control_endpoint",
        "network_fault_permission", "inflight_fault_endpoint",
        "request_delay_ms", "performance_threshold_ms",
        "process_kill_permission", "process_kill_proof_path",
        "fixture_disposal", "android_prod_fixes_allowed",
        "approval_signature",
    )

    sealed interface Result {
        data class Verified(val attestation: Attestation) : Result
        data class Denied(val reason: String) : Result
    }

    data class Attestation(
        val runId: String,
        val packageId: String,
        val scopeCaseIds: List<String>,
        val allowedEndpoints: List<String>,
        val environmentId: String,
        val disposableDbId: String,
        val dedicatedDeviceId: String,
        val operatorId: String,
        val windowStartEpochS: Long,
        val windowEndEpochS: Long,
        val syntheticCredentialsRef: String,
        val mutationCleanupPermitted: Boolean,
        val bulkSeedMaxRows: Int,
        val maxFixtureWrites: Int,
        val safFilePaths: List<String>,
        val tokenExpiryMechanismApproved: Boolean,
        val tokenControlEndpoint: String,
        val networkFaultPermitted: Boolean,
        val inflightFaultEndpoint: String,
        val requestDelayMs: Long,
        val performanceThresholdMs: Long,
        val processKillPermitted: Boolean,
        val processKillProofPath: String,
        val fixtureDisposal: String,
        val androidProdFixesAllowed: Boolean,
        val approvalSignature: String,
    ) {
        fun inWindow(nowEpochS: Long): Boolean =
            nowEpochS in windowStartEpochS..windowEndEpochS

        fun coversCase(caseId: String): Boolean =
            scopeCaseIds.isNotEmpty() &&
                (scopeCaseIds.contains("*") || scopeCaseIds.contains(caseId))

        /** Exact-origin match: scheme + host + effective port — never a
         *  bare-host or substring check. */
        fun allowsOrigin(baseUrl: String): Boolean {
            val origin = originOf(baseUrl) ?: return false
            return allowedEndpoints.any { originOf(it) == origin }
        }
    }

    private fun originOf(url: String): String? {
        val h = url.toHttpUrlOrNull() ?: return null
        return "${h.scheme}://${h.host}:${h.port}"
    }

    // --- Runner entry point (pre-Application, B01) -------------------------

    /**
     * The exact check the runner performs inside `onCreate(arguments)`
     * BEFORE `super.onCreate` — i.e. before `callApplicationOnCreate` and
     * `Application.onCreate`. Uses only the `arguments` Bundle and the
     * already-attached instrumentation [context]/target package — nothing
     * from `InstrumentationRegistry` (unregistered this early).
     *
     * Denial is thrown by the caller; this returns the decision.
     */
    fun preApplicationGuard(
        runIdArg: String?,
        baseUrl: String,
        attestationPath: String,
        testContext: Context,
        targetContext: Context?,
        publicKeyDer: ByteArray? = loadOwnerKey(testContext),
    ): Result {
        if (targetContext == null) {
            return Result.Denied("target context unavailable pre-Application")
        }
        return verifyDoc(attestationPath, publicKeyDer) { a, doc ->
            bindingChecks(a, doc, runIdArg, baseUrl, targetContext.packageName,
                deviceIdentity(targetContext), publicKeyDer)
        }
    }

    /** Per-case gate used inside [LiveUiE2eGate.assumeAuthorized] — by then
     *  the registry is live, but we still take explicit inputs. `context`
     *  must be the TARGET context: package id and ANDROID_ID are scoped to
     *  the app under test, not the test package. The verification key is
     *  loaded from the TEST package assets via [keyContext]. */
    fun verify(
        caseId: String,
        runIdArg: String?,
        baseUrl: String,
        context: Context,
        keyContext: Context = context,
        attestationPath: String = DEFAULT_PATH,
        publicKeyDer: ByteArray? = loadOwnerKey(keyContext),
    ): Result = verifyDoc(attestationPath, publicKeyDer) { a, doc ->
        bindingChecks(a, doc, runIdArg, baseUrl, context.packageName,
            deviceIdentity(context), publicKeyDer).let { r ->
            if (r is Result.Verified && !a.coversCase(caseId)) {
                Result.Denied("case '$caseId' not in attestation scope_case_ids")
            } else r
        }
    }

    /** The owner verification key lives in the TEST APK's assets — baked at
     *  build time, unreachable from instrumentation arguments. */
    fun loadOwnerKey(context: Context): ByteArray? = runCatching {
        context.assets.open(OWNER_KEY_ASSET).use { it.readBytes() }
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun verifyDoc(
        path: String,
        publicKeyDer: ByteArray?,
        check: (Attestation, JSONObject) -> Result,
    ): Result {
        val file = File(path)
        if (!file.isFile) {
            return Result.Denied(
                "L0 attestation absent at '$path' — no verified owner GO. " +
                    "A run-id argument alone is not authorization.",
            )
        }
        val doc = runCatching { JSONObject(file.readText()) }
            .getOrElse { return Result.Denied("attestation unreadable/invalid JSON: ${it.message}") }
        val unknown = doc.keys().asSequence().firstOrNull { it !in KNOWN_FIELDS }
        if (unknown != null) {
            return Result.Denied("attestation contains unexpected unsigned field '$unknown'")
        }
        val a = parse(doc) ?: return Result.Denied("attestation missing required fields")
        return check(a, doc)
    }

    private fun bindingChecks(
        a: Attestation, doc: JSONObject, runIdArg: String?, baseUrl: String,
        packageName: String, deviceId: String, publicKeyDer: ByteArray?,
    ): Result = when {
        !verifySignature(doc, a.approvalSignature, publicKeyDer) ->
            Result.Denied("approval_signature invalid — attestation is not owner-signed")
        runIdArg.isNullOrBlank() || a.runId != runIdArg ->
            Result.Denied("run-id '$runIdArg' does not match attestation run_id")
        a.packageId != packageName ->
            Result.Denied("attestation package '${a.packageId}' != under-test package '$packageName'")
        a.dedicatedDeviceId != deviceId ->
            Result.Denied(
                "dedicated_device_id '${a.dedicatedDeviceId}' != this device " +
                    "'$deviceId' — attestation is for a different device",
            )
        !a.allowsOrigin(baseUrl) ->
            Result.Denied(
                "build endpoint '${originOf(baseUrl)}' not in attestation " +
                    "allowed_endpoints (exact scheme+host+port required)",
            )
        a.scopeCaseIds.isEmpty() ->
            Result.Denied("scope_case_ids must be non-empty")
        a.environmentId.isBlank() || a.disposableDbId.isBlank() ||
            a.dedicatedDeviceId.isBlank() || a.operatorId.isBlank() ||
            a.syntheticCredentialsRef.isBlank() ->
            Result.Denied("env/db/device/operator/credentials ids must all be non-blank")
        a.maxFixtureWrites <= 0 ->
            Result.Denied("max_fixture_writes must be a positive signed budget")
        !a.inWindow(System.currentTimeMillis() / 1000) ->
            Result.Denied("current time outside attestation time_window")
        else -> Result.Verified(a)
    }

    // --- Owner approval signature (RSA over the canonical payload) ---------

    /** Canonical payload the owner signs — EVERY privileged field, fixed
     *  order, pipe-joined. Arrays serialize in document order. */
    internal fun canonicalPayload(doc: JSONObject): String {
        fun s(k: String) = doc.optString(k, "")
        fun i(k: String) = doc.optLong(k, 0).toString()
        fun b(k: String) = doc.optBoolean(k, false).toString()
        fun a(k: String) = doc.optJSONArray(k)
            ?.let { (0 until it.length()).joinToString(",") { i -> it.getString(i) } }
            .orEmpty()
        return listOf(
            s("run_id"), s("package_id"), a("scope_case_ids"),
            a("allowed_endpoints"), s("isolated_backend_environment_id"),
            s("disposable_db_instance_id"), s("dedicated_device_id"),
            s("authorized_operator"),
            doc.optJSONObject("time_window")?.optLong("start_epoch_s", 0).toString(),
            doc.optJSONObject("time_window")?.optLong("end_epoch_s", 0).toString(),
            s("synthetic_credentials_reference"),
            b("mutation_cleanup_permissions"), i("bulk_seed_max_rows"),
            i("max_fixture_writes"), a("saf_file_paths"),
            b("token_expiry_mechanism_approved"), s("token_control_endpoint"),
            b("network_fault_permission"), s("inflight_fault_endpoint"),
            i("request_delay_ms"), i("performance_threshold_ms"),
            b("process_kill_permission"), s("process_kill_proof_path"),
            s("fixture_disposal"), b("android_prod_fixes_allowed"),
        ).joinToString("|")
    }

    /** Signs the canonical payload with the owner's RSA private key
     *  (off-device use — owner tooling / contract tests only). */
    internal fun signPayload(payload: String, privateKey: java.security.PrivateKey): String {
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(privateKey)
        sig.update(payload.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(sig.sign())
    }

    private fun verifySignature(
        doc: JSONObject, signature: String, publicKeyDer: ByteArray?,
    ): Boolean {
        if (signature.isBlank() || publicKeyDer == null) return false
        return runCatching {
            val pub = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(publicKeyDer))
            val sig = Signature.getInstance("SHA256withRSA")
            sig.initVerify(pub)
            sig.update(canonicalPayload(doc).toByteArray(Charsets.UTF_8))
            sig.verify(Base64.getDecoder().decode(signature))
        }.getOrDefault(false)
    }

    // --- Identity -----------------------------------------------------------

    /** Stable per-install device identity — ANDROID_ID preferred, serial
     *  fallback. Provisioned into the attestation by the operator. */
    internal fun deviceIdentity(ctx: Context): String {
        val androidId = runCatching {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()
        return androidId?.takeUnless { it.isBlank() || it == "9774d56d682e549c" }
            ?: Build.SERIAL.takeUnless { it.isNullOrBlank() || it == "unknown" }
            ?: androidId.orEmpty()
    }

    private fun org.json.JSONArray.toStringList(): List<String> =
        (0 until length()).map { getString(it) }

    private fun parse(doc: JSONObject): Attestation? = runCatching {
        val window = doc.getJSONObject("time_window")
        Attestation(
            runId = doc.getString("run_id"),
            packageId = doc.getString("package_id"),
            scopeCaseIds = doc.getJSONArray("scope_case_ids").toStringList(),
            allowedEndpoints = doc.getJSONArray("allowed_endpoints").toStringList(),
            environmentId = doc.getString("isolated_backend_environment_id"),
            disposableDbId = doc.getString("disposable_db_instance_id"),
            dedicatedDeviceId = doc.getString("dedicated_device_id"),
            operatorId = doc.getString("authorized_operator"),
            windowStartEpochS = window.getLong("start_epoch_s"),
            windowEndEpochS = window.getLong("end_epoch_s"),
            syntheticCredentialsRef = doc.getString("synthetic_credentials_reference"),
            mutationCleanupPermitted = doc.optBoolean("mutation_cleanup_permissions", false),
            bulkSeedMaxRows = doc.optInt("bulk_seed_max_rows", 0),
            maxFixtureWrites = doc.optInt("max_fixture_writes", 0),
            safFilePaths = doc.optJSONArray("saf_file_paths")?.toStringList().orEmpty(),
            tokenExpiryMechanismApproved = doc.optBoolean("token_expiry_mechanism_approved", false),
            tokenControlEndpoint = doc.optString("token_control_endpoint", ""),
            networkFaultPermitted = doc.optBoolean("network_fault_permission", false),
            inflightFaultEndpoint = doc.optString("inflight_fault_endpoint", ""),
            requestDelayMs = doc.optLong("request_delay_ms", 0),
            performanceThresholdMs = doc.optLong("performance_threshold_ms", 0),
            processKillPermitted = doc.optBoolean("process_kill_permission", false),
            processKillProofPath = doc.optString("process_kill_proof_path", ""),
            fixtureDisposal = doc.optString("fixture_disposal", ""),
            androidProdFixesAllowed = doc.optBoolean("android_prod_fixes_allowed", false),
            approvalSignature = doc.optString("approval_signature", ""),
        )
    }.getOrNull()
}
