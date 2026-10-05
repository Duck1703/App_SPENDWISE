package com.spendwise.android.data

import com.spendwise.android.data.repository.LedgerExportFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `LedgerExportFileName.sanitize` — the Content-Disposition filename is
 * untrusted server metadata: basename-only, controls stripped, `.csv`
 * enforced, bounded, safe fallback.
 */
class LedgerExportFileNameTest {

    @Test
    fun `quoted attachment filename is used as-is`() {
        assertEquals(
            "transactions.csv",
            LedgerExportFileName.sanitize("attachment; filename=\"transactions.csv\""),
        )
    }

    @Test
    fun `bare filename is accepted`() {
        assertEquals(
            "ledger.csv",
            LedgerExportFileName.sanitize("attachment; filename=ledger.csv"),
        )
    }

    @Test
    fun `filename-star rfc5987 form is preferred`() {
        assertEquals(
            "sao-ke.csv",
            LedgerExportFileName.sanitize("attachment; filename*=UTF-8''sao-ke.csv"),
        )
    }

    @Test
    fun `missing header falls back`() {
        assertEquals("spendwise-ledger.csv", LedgerExportFileName.sanitize(null))
    }

    @Test
    fun `malformed header falls back`() {
        assertEquals("spendwise-ledger.csv", LedgerExportFileName.sanitize("attachment"))
    }

    @Test
    fun `path traversal is stripped to the basename`() {
        assertEquals(
            "evil.csv",
            LedgerExportFileName.sanitize("attachment; filename=\"../../etc/evil.csv\""),
        )
        assertEquals(
            "evil.csv",
            LedgerExportFileName.sanitize("attachment; filename=\"..\\..\\evil.csv\""),
        )
    }

    @Test
    fun `dot-only names fall back`() {
        assertEquals("spendwise-ledger.csv", LedgerExportFileName.sanitize("filename=\"..\""))
        assertEquals("spendwise-ledger.csv", LedgerExportFileName.sanitize("filename=\"\""))
    }

    @Test
    fun `control characters are stripped`() {
        assertEquals(
            "ledger.csv",
            LedgerExportFileName.sanitize("filename=\"led\u0000ger.csv\""),
        )
    }

    @Test
    fun `missing extension gains csv`() {
        assertEquals(
            "export.csv",
            LedgerExportFileName.sanitize("filename=\"export\""),
        )
    }

    @Test
    fun `overlong names are bounded`() {
        val long = "a".repeat(200)
        val result = LedgerExportFileName.sanitize("filename=\"$long.csv\"")
        assertTrue(result.length <= 100)
        assertTrue(result.endsWith(".csv"))
    }
}
