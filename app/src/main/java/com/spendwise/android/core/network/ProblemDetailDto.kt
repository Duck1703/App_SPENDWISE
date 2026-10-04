package com.spendwise.android.core.network

import kotlinx.serialization.Serializable

/**
 * RFC-7807-style problem detail, tolerant decode (AD-006, ARCHITECTURE §12).
 * `title`/`detail` are diagnostics only — never parsed for business logic.
 */
@Serializable
data class ProblemDetailDto(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
    val fields: Map<String, String>? = null,
)
