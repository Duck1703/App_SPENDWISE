package com.spendwise.android.live.infrastructure

/**
 * Canonical execution-ownership registry (Blueprint V5 §5.2, §10).
 * Every executable scenario owns exactly one case id; BJ checkpoints are
 * steps of a single cumulative journey, and IND-* names are aliases that
 * must never inflate the scenario count.
 */
object LiveCaseIds {
    const val LUI_01 = "LUI-01" // Register → prefilled Login → Home
    const val LUI_02 = "LUI-02" // Sign out → same-user login → data intact
    const val LUI_03 = "LUI-03" // Create account via UI
    const val LUI_04 = "LUI-04" // Create income + expense via UI
    const val LUI_05 = "LUI-05" // Home recency + month dashboard freshness
    const val LUI_06 = "LUI-06" // Account history page 2
    const val LUI_07 = "LUI-07" // Cross-screen consistency post-mutation
    const val LUI_08 = "LUI-08" // Offset (compensating POST, append-only)
    const val LUI_09 = "LUI-09" // Transfer between own accounts
    const val LUI_10 = "LUI-10" // Search/filter transactions
    const val LUI_11 = "LUI-11" // Budget create→edit→delete
    const val LUI_12 = "LUI-12" // Recurring create→edit→deactivate
    const val LUI_13 = "LUI-13" // Rule create→disable
    const val LUI_14 = "LUI-14" // Rename→archive→unarchive account
    const val LUI_15 = "LUI-15" // CSV import via real SAF picker
    const val LUI_16 = "LUI-16" // PDF import via real picker
    const val LUI_17 = "LUI-17" // Duplicate import handling
    const val LUI_18 = "LUI-18" // Export ledger + content validation
    const val LUI_19 = "LUI-19" // Access expiry + valid refresh renewal
    const val LUI_20 = "LUI-20" // Rejected refresh → overlay → re-auth
    const val LUI_21 = "LUI-21" // Double-tap while isSubmitting → ≤1 create
    const val LUI_22 = "LUI-22" // Activity recreation (NOT process death)
    const val LUI_23 = "LUI-23" // Real process kill + relaunch
    const val LUI_24A = "LUI-24a" // Offline-before-send → single retry
    const val LUI_24B = "LUI-24b" // Ambiguous in-flight loss → no replay
    const val LUI_25 = "LUI-25" // Compose semantics smoke
    const val LUI_26 = "LUI-26" // 200+ ledger bounded fetch + jank
    const val LUI_27 = "LUI-27" // Dashboard aggregates + mixed currency
    const val LUI_28 = "LUI-28" // Cross-user isolation

    val ALL_LUI: List<String> = listOf(
        LUI_01, LUI_02, LUI_03, LUI_04, LUI_05, LUI_06, LUI_07,
        LUI_08, LUI_09, LUI_10, LUI_11, LUI_12, LUI_13, LUI_14,
        LUI_15, LUI_16, LUI_17, LUI_18, LUI_27, LUI_28,
        LUI_19, LUI_20, LUI_21, LUI_22, LUI_23, LUI_24A, LUI_24B,
        LUI_25, LUI_26,
    ) // 29 ids — 24a/24b are distinct variants of one row

    val BJ_CHECKPOINTS: List<String> = (1..22).map { "BJ-%02d".format(it) }
}
