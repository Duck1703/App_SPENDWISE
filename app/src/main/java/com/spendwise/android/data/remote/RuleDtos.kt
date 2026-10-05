package com.spendwise.android.data.remote

import kotlinx.serialization.Serializable

/**
 * `CategorizationRuleResponse` / `CreateCategorizationRuleRequest`
 * (verified against `CategorizationRuleController`,
 * `CategorizationRuleResponse`, `CreateCategorizationRuleRequest` at
 * backend `8194daa`).
 */
@Serializable
data class CategorizationRuleDto(
    val id: String,
    val conditionType: String,
    val conditionValue: String,
    val targetCategory: String? = null,
    val priority: Int,
    val enabled: Boolean,
)

/**
 * `{conditionType, conditionValue, targetCategory, priority}` —
 * `conditionType` is `CONTAINS`/`EQUALS`, `conditionValue` ≤ 255 chars,
 * `targetCategory` is the server category code (≤ 32), `priority` is a
 * per-user unique int (duplicate → 409). `enabled` is deliberately not
 * sent on create — the server default (S22 "enabled ignored").
 */
@Serializable
data class CreateRuleRequestDto(
    val conditionType: String,
    val conditionValue: String,
    val targetCategory: String,
    val priority: Int,
)
