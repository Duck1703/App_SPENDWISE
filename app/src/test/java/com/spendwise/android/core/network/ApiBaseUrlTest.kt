package com.spendwise.android.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M21 — policy pinning for the API base URL. These tests prove the
 * release path can never silently ship an unconfigured or cleartext
 * endpoint, and that debug HTTP is bounded to private-LAN hosts only.
 */
class ApiBaseUrlTest {

    @Test
    fun `https LAN URL accepted in release`() {
        assertEquals(
            "https://192.168.1.236:8443/",
            ApiBaseUrl.validate("https://192.168.1.236:8443/", debugBuild = false),
        )
    }

    @Test
    fun `https hostname URL accepted in release`() {
        ApiBaseUrl.validate("https://spendwise.lan/", debugBuild = false)
    }

    @Test
    fun `http LAN URL accepted in debug only`() {
        ApiBaseUrl.validate("http://192.168.1.236:8081/", debugBuild = true)
    }

    @Test
    fun `http LAN URL rejected in release`() {
        expectReject("http://192.168.1.236:8081/", debugBuild = false)
    }

    @Test
    fun `http public URL rejected even in debug`() {
        expectReject("http://203.0.113.10:8080/", debugBuild = true)
        expectReject("http://example.com/", debugBuild = true)
    }

    @Test
    fun `placeholder example dot invalid rejected in both build types`() {
        expectReject("https://example.invalid/", debugBuild = true)
        expectReject("https://example.invalid/", debugBuild = false)
    }

    @Test
    fun `blank and malformed URLs rejected`() {
        expectReject("", debugBuild = true)
        expectReject("   ", debugBuild = true)
        expectReject("not a url", debugBuild = true)
        expectReject("ftp://192.168.1.236/", debugBuild = true)
    }

    @Test
    fun `URL with embedded credentials rejected`() {
        expectReject("https://user:pass@192.168.1.236:8443/", debugBuild = false)
    }

    @Test
    fun `non-root path rejected`() {
        expectReject("https://192.168.1.236:8443/api/", debugBuild = false)
    }

    @Test
    fun `private host detection covers RFC1918 loopback link-local`() {
        assertTrue(ApiBaseUrl.isPrivateHost("10.0.0.5"))
        assertTrue(ApiBaseUrl.isPrivateHost("172.16.4.2"))
        assertTrue(ApiBaseUrl.isPrivateHost("172.31.255.254"))
        assertTrue(ApiBaseUrl.isPrivateHost("192.168.1.236"))
        assertTrue(ApiBaseUrl.isPrivateHost("127.0.0.1"))
        assertTrue(ApiBaseUrl.isPrivateHost("169.254.10.10"))
        assertTrue(ApiBaseUrl.isPrivateHost("localhost"))
        assertFalse(ApiBaseUrl.isPrivateHost("172.15.0.1")) // just outside /12
        assertFalse(ApiBaseUrl.isPrivateHost("192.169.0.1"))
        assertFalse(ApiBaseUrl.isPrivateHost("8.8.8.8"))
        assertFalse(ApiBaseUrl.isPrivateHost("192.168.1.256")) // malformed octet
        assertFalse(ApiBaseUrl.isPrivateHost("example.com"))
        assertFalse(ApiBaseUrl.isPrivateHost(""))
    }

    private fun expectReject(url: String, debugBuild: Boolean) {
        try {
            ApiBaseUrl.validate(url, debugBuild)
        } catch (e: IllegalArgumentException) {
            return
        }
        throw AssertionError("expected rejection of '$url' (debug=$debugBuild)")
    }
}
