package com.spendwise.android.core.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * M21 — build-time API base URL validation (Post-M20 audit P0-1).
 *
 * The Gradle property/env chain can hand Retrofit any string; Retrofit only
 * rejects on *shape*. This validator encodes the deployment policy:
 *
 * - HTTPS is always accepted.
 * - HTTP is accepted **only** in debug builds and **only** for a private/
 *   loopback/link-local host literal — the LAN-debug carve-out that pairs
 *   with the generated debug Network Security Config. Public HTTP is never
 *   reachable: release rejects it here, and Android refuses cleartext to
 *   every non-allowlisted destination anyway.
 * - The `example.invalid` placeholder is rejected in every build type —
 *   shipping an unconfigured release must fail loudly, never silently dial
 *   a dead host.
 * - No credentials in the URL (userinfo is rejected outright).
 *
 * Enforced at the wire boundary: release `Retrofit` construction validates
 * eagerly; every call through the app `OkHttpClient` and the bare
 * `SessionRefresher` client re-checks before dialing (surfaced as
 * [java.io.IOException] — a transport failure, never a crash). App launch
 * and fake-backed tests stay unblocked; the authoritative fail-closed gate
 * for shippable artifacts is the release-build check in
 * `app/build.gradle.kts`.
 */
object ApiBaseUrl {

    private val IPV4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    /** Placeholder host shipped in gradle.properties — never a real backend. */
    private const val PLACEHOLDER_HOST = "example.invalid"

    fun validate(rawUrl: String, debugBuild: Boolean): String {
        val url = rawUrl.trim()
        require(url.isNotEmpty()) {
            "SPENDWISE_API_BASE_URL is empty — set -Pspendwise.api.base.url " +
                "or the SPENDWISE_API_BASE_URL env var before building."
        }
        val parsed = url.toHttpUrlOrNull()
            ?: throw IllegalArgumentException(
                "SPENDWISE_API_BASE_URL '$url' is not a valid absolute URL.",
            )
        require(parsed.host != PLACEHOLDER_HOST) {
            "SPENDWISE_API_BASE_URL still points at the $PLACEHOLDER_HOST " +
                "placeholder — configure a real backend endpoint."
        }
        require(parsed.encodedPath == "/") {
            "SPENDWISE_API_BASE_URL must end at the host root ('$url') — " +
                "Retrofit resolves api/ paths against it."
        }
        require(parsed.username.isEmpty() && parsed.password.isEmpty()) {
            "SPENDWISE_API_BASE_URL must not contain credentials."
        }
        when (parsed.scheme) {
            "https" -> Unit
            "http" -> require(debugBuild && isPrivateHost(parsed.host)) {
                "http:// is only allowed for debug builds against a private " +
                    "LAN address — got '$url'. Use https:// for anything else."
            }
            else -> throw IllegalArgumentException(
                "SPENDWISE_API_BASE_URL scheme must be http or https — got '$url'.",
            )
        }
        return parsed.toString()
    }

    /** True for RFC-1918 / loopback / link-local IPv4 literals, `localhost`. */
    fun isPrivateHost(host: String): Boolean {
        if (host.equals("localhost", ignoreCase = true)) return true
        val m = IPV4.matchEntire(host) ?: return false
        val octets = (1..4).map { m.groupValues[it].toInt() }
        if (octets.any { it > 255 }) return false
        return when (octets[0]) {
            10, 127 -> true
            169 -> octets[1] == 254
            172 -> octets[1] in 16..31
            192 -> octets[1] == 168
            else -> false
        }
    }
}
