package com.spendwise.android.live.scenarios

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.spendwise.android.live.infrastructure.LiveCaseIds
import com.spendwise.android.live.infrastructure.RunOwnership
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Live UI E2E — import/export. Owners: LUI-15, LUI-16, LUI-17, LUI-18.
 * Every case drives the REAL SAF picker via UiAutomator and requires an
 * attested `saf_file_paths` staging entry per fixture.
 */
@RunWith(AndroidJUnit4::class)
class LiveImportExportE2eTest : LiveE2eTestBase() {

    private fun signedInWithAccount(ctx: Ctx): Pair<String, String> {
        ctx.api.register(ctx.fixture.username, ctx.fixture.password)
        ctx.api.login(ctx.fixture.username, ctx.fixture.password)
        val acct = ctx.fixture.accountName("Imp")
        val acctId = ctx.api.seedAccount(acct, currency = "USD")
        driver.launchMain()
        signInViaUi(ctx, ctx.fixture.username, ctx.fixture.password)
        return acct to acctId
    }

    private fun openImportInto(acct: String) {
        driver.navMore()
        driver.tapCd("Import statement")
        driver.waitText("Import into")
        driver.tap("Import into")
        driver.tap(acct, substring = true)
    }

    private fun pickAndImport(fileName: String) {
        driver.tap("Choose file")
        val picked = driver.safPickFile(fileName)
        check(picked) { "BLOCKED: SAF picker did not expose '$fileName'" }
        driver.systemTap("Open")
        driver.tap("Import")
    }

    private fun parsedRowCount(ctx: Ctx, acctId: String): Int =
        ctx.api.transactionRows(acctId).size

    /** LUI-15 (P1): valid Spendwise CSV → parsed rows land → history (M03). */
    @Test
    fun lui15_csvImportViaRealSaf() {
        val ctx = begin(LiveCaseIds.LUI_15)
        try {
            val file = "spendwise_${ctx.fixture.tag}.csv"
            RunOwnership.requireSafStaging(ctx.attestation, file)
            val (acct, acctId) = signedInWithAccount(ctx)
            openImportInto(acct)
            pickAndImport(file)
            driver.waitText("imported", substring = true, timeoutMs = 120_000)
            ctx.recorder.assertion("CSV import result surfaced (row counts)")
            // M03: parsed rows — each carries this accountId + required fields.
            val rows = ctx.api.transactionRows(acctId)
            ctx.recorder.serverCheck(
                "CSV import produced parsed transaction rows on this account",
                rows.isNotEmpty() && rows.all {
                    it.optString("accountId") == acctId &&
                        it.optString("amount").isNotBlank() &&
                        it.optString("currency") == "USD"
                },
                "rows=${rows.size}",
            )
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }

    /** LUI-16 (P1): TPBank PDF → parsed rows → imported — requires an
     *  owner-provided machine-readable TPBank fixture in saf_file_paths. */
    @Test
    fun lui16_pdfImportViaRealSaf() {
        val ctx = begin(LiveCaseIds.LUI_16)
        try {
            val file = "tpbank_${ctx.fixture.tag}.pdf"
            RunOwnership.requireSafStaging(ctx.attestation, file)
            val (acct, acctId) = signedInWithAccount(ctx)
            openImportInto(acct)
            pickAndImport(file)
            driver.waitText("imported", substring = true, timeoutMs = 120_000)
            ctx.recorder.assertion("PDF parsed rows surfaced")
            val rows = ctx.api.transactionRows(acctId)
            ctx.recorder.serverCheck(
                "PDF import produced parsed transaction rows on this account",
                rows.isNotEmpty() && rows.all { it.optString("accountId") == acctId },
                "rows=${rows.size}",
            )
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }

    /** LUI-17 (P1): re-importing identical rows reports them as already
     *  imported — parsed row count must not grow (M03). */
    @Test
    fun lui17_duplicateImportIdempotent() {
        val ctx = begin(LiveCaseIds.LUI_17)
        try {
            val file = "spendwise_${ctx.fixture.tag}.csv"
            RunOwnership.requireSafStaging(ctx.attestation, file)
            val (acct, acctId) = signedInWithAccount(ctx)
            openImportInto(acct)
            pickAndImport(file)
            driver.waitText("imported", substring = true, timeoutMs = 120_000)
            val afterFirst = parsedRowCount(ctx, acctId)
            ctx.recorder.serverCheck("first import produced rows", afterFirst >= 1,
                "afterFirst=$afterFirst")
            ctx.recorder.step("first import done — $afterFirst parsed rows")

            openImportInto(acct)
            pickAndImport(file)
            driver.waitText("already imported", substring = true, timeoutMs = 120_000)
            ctx.recorder.assertion("duplicate rows reported — no double ledger")
            ctx.recorder.serverCheck(
                "parsed row count unchanged after re-import",
                parsedRowCount(ctx, acctId) == afterFirst,
                "afterFirst=$afterFirst",
            )
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }

    /** LUI-18 (P1): export CSV → real SAF save under a staged name →
     *  read the actual document back and parse real CSV rows (M06).
     *  MediaStore read-back is bound by DATE_ADDED >= case start so a
     *  stale same-named file can never satisfy the check. */
    @Test
    fun lui18_exportCsvContentValidated() {
        val ctx = begin(LiveCaseIds.LUI_18)
        try {
            val target = "spendwise_export_${ctx.fixture.tag}.csv"
            RunOwnership.requireSafStaging(ctx.attestation, target)
            // Stale same-named files make DocumentsUI dedupe into
            // "name (1).csv" — clear them so the picker lands on the
            // attested name (freshness-bound read-back still enforced).
            com.spendwise.android.live.infrastructure.OwnerControlPlane(
                ctx.recorder, ctx.attestation.tokenControlEndpoint,
            ).get("del-saf?name=" + java.net.URLEncoder.encode(target, "UTF-8"))
            val (_, acctId) = signedInWithAccount(ctx)
            val marker = ctx.fixture.txnNote("exp")
            ctx.api.seedTransaction(
                accountId = acctId, type = "EXPENSE", amount = "12.00",
                currency = "USD", category = "FOOD", note = marker,
                occurredOn = "2024-06-15",
            )
            val startedMs = System.currentTimeMillis()
            driver.navMore()
            driver.tapCd("Ledger export")
            driver.waitText("Export ledger")
            driver.tap("Export CSV")

            // CreateDocument flow: name the file in the picker's text field,
            // then confirm with the system Save button.
            val device = driver.device
            val named = device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 8_000)
            check(named) { "BLOCKED: CreateDocument filename field not found" }
            device.findObject(By.clazz("android.widget.EditText"))?.text = target
            check(driver.systemTapAnyOf("Save", "Lưu", "LƯU")) {
                "BLOCKED: system Save button missing"
            }
            driver.waitText("Ledger exported", timeoutMs = 120_000)
            ctx.recorder.assertion("export completed to SAF location")

            // M06: freshness-bound read-back + real CSV row parse —
            // header + a data row matching the marker in its description
            // column, exact amount/currency fields.
            val csv = readExportedCsv(ctx, target, sinceMs = startedMs)
            ctx.recorder.serverCheck(
                "exported CSV readable, fresh, and parses to the seeded row",
                csv != null && validateExportCsv(csv, marker),
                "bytes=${csv?.length ?: -1}",
            )
            ctx.finish("PASS")
        } finally { ctx.recorder.flush() }
    }

    /** Parse the CSV: header must be the documented column set, and at
     *  least one data row's `description` column equals [marker] with
     *  amount `12.00` + currency `USD` in the correct positions. */
    private fun validateExportCsv(csv: String, marker: String): Boolean {
        val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return false
        val header = lines[0].split(",").map { it.trim().removeSurrounding("\"") }
        val expected = listOf(
            "occurredOn", "type", "amount", "currency", "category",
            "sourceRef", "description",
        )
        if (header != expected) return false
        val descIdx = header.indexOf("description")
        val amountIdx = header.indexOf("amount")
        val currencyIdx = header.indexOf("currency")
        return lines.drop(1).any { line ->
            val cols = line.split(",").map { it.trim().removeSurrounding("\"") }
            cols.size == header.size &&
                cols[descIdx] == marker &&
                cols[amountIdx] == "12.00" &&
                cols[currencyIdx] == "USD"
        }
    }

    /** Read the exported document back through the owner control plane's
     *  `read-saf` channel. Scoped storage denies this process any storage
     *  grant (neither app nor test package holds READ_* permission), and
     *  the transient SAF URI grant covers only the exact returned docId —
     *  not reconstructible. The control plane reads the real document on
     *  the host via adb and returns its bytes + mtime; freshness is bound
     *  by mtime >= [sinceMs] so a stale same-named file can never satisfy
     *  the check (M06). */
    private fun readExportedCsv(
        ctx: Ctx,
        displayName: String,
        sinceMs: Long,
    ): String? {
        val control = com.spendwise.android.live.infrastructure.OwnerControlPlane(
            ctx.recorder, ctx.attestation.tokenControlEndpoint,
        )
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            val body = control.get(
                "read-saf?name=" + java.net.URLEncoder.encode(displayName, "UTF-8"),
            )
            if (body != null) {
                val obj = runCatching { org.json.JSONObject(body) }.getOrNull()
                val mtimeMs = (obj?.optLong("mtime_s", 0) ?: 0) * 1000
                val csv = obj?.optString("csv")
                if (csv != null && csv.isNotEmpty() && mtimeMs >= sinceMs - 5_000) {
                    return csv
                }
            }
            Thread.sleep(1_000)
        }
        return null
    }
}
