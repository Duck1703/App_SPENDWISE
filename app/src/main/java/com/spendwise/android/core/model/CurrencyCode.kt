package com.spendwise.android.core.model

/**
 * Open ISO-4217-style currency code (AD-007).
 *
 * Validation is syntactic only — three uppercase ASCII letters — so valid
 * codes the app does not know about remain representable instead of crashing
 * or being mislabeled.
 */
@JvmInline
value class CurrencyCode(val code: String) {

    init {
        require(PATTERN.matches(code)) { "Invalid currency code: '$code'" }
    }

    override fun toString(): String = code

    companion object {
        private val PATTERN = Regex("^[A-Z]{3}$")
    }
}
