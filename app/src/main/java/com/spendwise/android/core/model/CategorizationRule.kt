package com.spendwise.android.core.model

/**
 * A user categorization rule (PRODUCT §30 S21–S23, backend `8194daa`):
 * when a transaction's description matches the condition, the server
 * assigns [targetCategory] at creation/import time. Rules are matched
 * in ascending [priority] order; duplicate (user, priority) is a 409.
 * `enabled=false` after `DELETE /{id}` — disabling, not deletion.
 */
data class CategorizationRule(
    val id: String,
    val conditionType: RuleConditionType,
    /** The literal text the description is matched against. */
    val conditionValue: String,
    /** Server category code assigned on a match. */
    val targetCategory: String,
    val priority: Int,
    val enabled: Boolean,
)

/**
 * Closed wire enum today (`CONTAINS`/`EQUALS`); UNKNOWN keeps a future
 * condition type decodable (AD-008).
 */
enum class RuleConditionType {
    CONTAINS,
    EQUALS,
    UNKNOWN;

    companion object {
        fun fromWire(value: String?): RuleConditionType =
            entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}
