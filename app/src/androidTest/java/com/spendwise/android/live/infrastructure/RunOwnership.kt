package com.spendwise.android.live.infrastructure

import org.junit.Assume.assumeTrue

/**
 * Mechanism approvals and per-case ownership guards (V5 §6, §13 —
 * hardened per final-review M01/M04/M09).
 *
 * Every capability is a SIGNED attestation field, enforced at the exact
 * point of use. Missing grant → honest `SKIPPED_BY_GATE`/`BLOCKED` —
 * never a fake PASS, never an improvised mechanism.
 */
object RunOwnership {

    /** Deny-by-default write permission (B2): every mutation-owning case —
     *  fixture seed AND the UI mutations it verifies — requires
     *  `mutation_cleanup_permissions` on a *verified* disposable DB. */
    fun requireMutation(a: ApprovedRunPreflight.Attestation) {
        assumeTrue(
            "REQUIRES_OWNER_APPROVAL: mutation_cleanup_permissions not granted " +
                "for disposable_db '${a.disposableDbId}' — no writes permitted",
            a.mutationCleanupPermitted && a.disposableDbId.isNotBlank(),
        )
    }

    /** Deterministic token expiry/reject needs the owner control plane —
     *  `token_control_endpoint` (B05) — not a narrative hand-wave. */
    fun requireTokenExpiryMechanism(a: ApprovedRunPreflight.Attestation) {
        assumeTrue(
            "REQUIRES_OWNER_APPROVAL: token expiry mechanism not granted " +
                "(token_expiry_mechanism_approved + token_control_endpoint)",
            a.tokenExpiryMechanismApproved && a.tokenControlEndpoint.isNotBlank(),
        )
    }

    fun requireNetworkFault(a: ApprovedRunPreflight.Attestation) {
        assumeTrue(
            "REQUIRES_OWNER_APPROVAL: network fault permission not granted " +
                "in L0 attestation",
            a.networkFaultPermitted,
        )
    }

    /** M04/M09: deterministic in-flight loss or request delay needs the
     *  owner-controlled fault endpoint — device toggles alone cannot
     *  schedule a mid-request drop or a server-visible delay. */
    fun requireInFlightFault(a: ApprovedRunPreflight.Attestation) {
        assumeTrue(
            "REQUIRES_OWNER_APPROVAL: in-flight fault control endpoint not " +
                "attested (inflight_fault_endpoint) — cannot deterministically " +
                "lose a request mid-flight",
            a.inflightFaultEndpoint.isNotBlank(),
        )
    }

    /** M09: the double-submit window exists only while the first request
     *  is provably in-flight — requires the owner delay mechanism. */
    fun requireRequestDelay(a: ApprovedRunPreflight.Attestation) {
        assumeTrue(
            "REQUIRES_OWNER_APPROVAL: request_delay_ms not attested — the " +
                "submit-lock window cannot be held open deterministically",
            a.networkFaultPermitted && a.requestDelayMs > 0,
        )
    }

    fun requireProcessKill(a: ApprovedRunPreflight.Attestation) {
        assumeTrue(
            "REQUIRES_OWNER_APPROVAL: process kill permission/proof path " +
                "not granted in L0 attestation",
            a.processKillPermitted && a.processKillProofPath.isNotBlank(),
        )
    }

    /** M01: the signed bulk budget is a hard cap — needs `n <=
     *  bulk_seed_max_rows`, not just a boolean. */
    fun requireBulkSeed(a: ApprovedRunPreflight.Attestation, rowsNeeded: Int) {
        assumeTrue(
            "BLOCKED_BY_ENVIRONMENT: bulk seed needs $rowsNeeded rows but " +
                "signed bulk_seed_max_rows=${a.bulkSeedMaxRows} is smaller",
            rowsNeeded in 1..a.bulkSeedMaxRows,
        )
    }

    fun requireSafStaging(a: ApprovedRunPreflight.Attestation, fileName: String) {
        assumeTrue(
            "BLOCKED_BY_ENVIRONMENT: no attested SAF staging path covers " +
                "'$fileName' (saf_file_paths)",
            a.safFilePaths.any { it.endsWith(fileName) || it.endsWith("/$fileName") },
        )
    }

    /** M08: performance claims need a signed threshold — without one the
     *  timing is recorded but the verdict stays NOT_MEASURED. */
    fun performanceThresholdAttested(a: ApprovedRunPreflight.Attestation): Boolean =
        a.performanceThresholdMs > 0
}
