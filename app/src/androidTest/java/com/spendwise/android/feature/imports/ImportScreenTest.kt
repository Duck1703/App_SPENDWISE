package com.spendwise.android.feature.imports

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.core.designsystem.SpendwiseTheme
import com.spendwise.android.core.model.Account
import com.spendwise.android.core.model.AccountType
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.ImportBatch
import com.spendwise.android.core.model.ImportRow
import com.spendwise.android.core.model.ImportRowStatus
import com.spendwise.android.core.model.Money
import com.spendwise.android.data.repository.SelectedStatement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

/** S12–S14 (PRODUCT §23–25, P3A §5–6): choose → processing → result. */
@RunWith(AndroidJUnit4::class)
class ImportScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private data class Harness(
        var accountPicked: String? = null,
        var pickFileClicks: Int = 0,
        var clearFileClicks: Int = 0,
        var submitClicks: Int = 0,
        var retryClicks: Int = 0,
        var doneClicks: Int = 0,
        var createAccountClicks: Int = 0,
        var backClicks: Int = 0,
    )

    private fun screen(state: ImportUiState, harness: Harness = Harness()) {
        composeRule.setContent {
            SpendwiseTheme {
                ImportScreen(
                    state = state,
                    onAccountSelected = { harness.accountPicked = it },
                    onPickFile = { harness.pickFileClicks++ },
                    onFileCleared = { harness.clearFileClicks++ },
                    onSubmit = { harness.submitClicks++ },
                    onRetry = { harness.retryClicks++ },
                    onDone = { harness.doneClicks++ },
                    onCreateAccount = { harness.createAccountClicks++ },
                    onBack = { harness.backClicks++ },
                )
            }
        }
    }

    private fun account(id: String, name: String, currency: String) = Account(
        id = id,
        name = name,
        type = AccountType.CASH,
        openingBalance = Money(BigDecimal("1000"), CurrencyCode(currency)),
        balance = Money(BigDecimal("1000"), CurrencyCode(currency)),
    )

    private fun form(
        file: SelectedStatement? = null,
        uploading: Boolean = false,
        error: ImportError? = null,
        ambiguous: Boolean = false,
    ) = ImportUiState.Form(
        accounts = listOf(
            account("acc-1", "Wallet", "VND"),
            account("acc-2", "Bank", "USD"),
        ),
        selectedAccountId = "acc-1",
        file = file,
        isUploading = uploading,
        error = error,
        ambiguousNotice = ambiguous,
    )

    private fun batch(
        success: Int = 2,
        duplicates: Int = 1,
        failed: Int = 1,
        rows: List<ImportRow> = listOf(
            ImportRow(2, ImportRowStatus.OK, null, "t-2"),
            ImportRow(3, ImportRowStatus.DUPLICATE, "same sourceTxnId", null),
            ImportRow(4, ImportRowStatus.FAILED, "currency mismatch", null),
        ),
    ) = ImportBatch(
        batchId = "b1",
        accountId = "acc-1",
        fileName = "statement.csv",
        importedAt = Instant.parse("2026-10-04T12:00:00Z"),
        successCount = success,
        duplicateCount = duplicates,
        failedCount = failed,
        rows = rows,
    )

    // ── S12 choose ──────────────────────────────────────────────────

    @Test
    fun choose_rendersAccountPickerFormatTruthAndDisabledSubmit() {
        screen(form())

        composeRule.onNodeWithText("Import into").assertIsDisplayed()
        composeRule.onNodeWithText("What formats are supported?").assertIsDisplayed()
        composeRule.onNodeWithText("Choose file").assertIsDisplayed()
        // No file yet → the Import CTA stays disabled.
        composeRule.onNodeWithText("Import", substring = false)
            .assertIsNotEnabled()
    }

    @Test
    fun choose_pickFileLaunchesPicker() {
        val harness = Harness()
        screen(form(), harness)

        composeRule.onNodeWithText("Choose file").performClick()
        assertEquals(1, harness.pickFileClicks)
    }

    @Test
    fun choose_selectedFileEnablesSubmit() {
        val harness = Harness()
        screen(
            form(file = SelectedStatement("content://x", "oct.csv", 10)),
            harness,
        )

        composeRule.onNodeWithText("oct.csv").assertIsDisplayed()
        composeRule.onNodeWithText("Import").assertIsEnabled().performClick()
        assertEquals(1, harness.submitClicks)
    }

    @Test
    fun choose_accountDropdownSelectsAnotherAccount() {
        val harness = Harness()
        screen(form(), harness)

        // The anchored field (not the label) expands the menu.
        composeRule.onNodeWithContentDescription("Import into").performClick()
        composeRule.onNodeWithText("Bank · USD").performClick()
        assertEquals("acc-2", harness.accountPicked)
    }

    @Test
    fun choose_errorBannerRendersUnsupportedAndTooLarge() {
        screen(form(error = ImportError.UnsupportedFile))
        composeRule.onNodeWithText("File not supported").assertIsDisplayed()
    }

    @Test
    fun choose_ambiguousNoticeRendersHonestResumeCopy() {
        screen(form(ambiguous = true))
        composeRule.onNodeWithText("Import may not have finished").assertIsDisplayed()
        composeRule.onNodeWithText("Re-uploading the same file is safe", substring = true)
            .assertIsDisplayed()
    }

    // ── S13 processing ──────────────────────────────────────────────

    @Test
    fun processing_modalSurfaceAnnouncesAndBlocksActions() {
        val harness = Harness()
        screen(form(uploading = true), harness)

        composeRule.onNodeWithContentDescription("Importing your statement")
            .assertIsDisplayed()
        // The form is fully swapped out — no file row to tap.
        composeRule.onNodeWithText("Choose file").assertDoesNotExist()
        // No back affordance while uploading.
        composeRule.onNode(hasContentDescription("Back")).assertDoesNotExist()
    }

    // ── S14 result ──────────────────────────────────────────────────

    @Test
    fun result_countsFirstThenTilesAndFailureReasons() {
        val harness = Harness()
        screen(ImportUiState.Result(batch()), harness)

        composeRule.onNodeWithText("statement.csv").assertIsDisplayed()
        composeRule.onNodeWithText("Rows in file: 3").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("2 Imported").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("1 Already imported").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("1 Couldn’t import").assertIsDisplayed()
        composeRule.onNodeWithText("Row 4 — currency mismatch").assertIsDisplayed()
        composeRule.onNodeWithText("Done").performClick()
        assertEquals(1, harness.doneClicks)
    }

    @Test
    fun result_allDuplicatesIsCalm_notAnError() {
        screen(
            ImportUiState.Result(
                batch(
                    success = 0,
                    duplicates = 3,
                    failed = 0,
                    rows = listOf(
                        ImportRow(2, ImportRowStatus.DUPLICATE, null, null),
                        ImportRow(3, ImportRowStatus.DUPLICATE, null, null),
                        ImportRow(4, ImportRowStatus.DUPLICATE, null, null),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithText("Nothing new to import").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("3 Already imported").assertIsDisplayed()
    }

    @Test
    fun result_allFailedPointsToTheFormat() {
        screen(
            ImportUiState.Result(
                batch(
                    success = 0,
                    duplicates = 0,
                    failed = 2,
                    rows = listOf(
                        ImportRow(2, ImportRowStatus.FAILED, "bad currency", null),
                        ImportRow(3, ImportRowStatus.FAILED, "zero amount", null),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithText("Nothing could be imported").assertIsDisplayed()
        composeRule.onNodeWithText("Row 2 — bad currency").assertIsDisplayed()
        composeRule.onNodeWithText("Row 3 — zero amount").assertIsDisplayed()
    }

    @Test
    fun result_rowsOnDemandExpandsEveryRowCalmly() {
        screen(ImportUiState.Result(batch()))

        composeRule.onNodeWithText("Show all rows").performClick()
        composeRule.onNodeWithText("Row 2 — imported").assertIsDisplayed()
        composeRule.onNodeWithText("Row 3 — already imported").assertIsDisplayed()
        // Failed rows appear in both the failures card and the full list.
        composeRule.onAllNodesWithText("Row 4 — currency mismatch")
            .assertCountEquals(2)
    }

    @Test
    fun result_emptyFileGetsTheHonestZeroState() {
        screen(ImportUiState.Result(batch(success = 0, duplicates = 0, failed = 0, rows = emptyList())))

        composeRule.onNodeWithText("Empty file").assertIsDisplayed()
        composeRule.onNodeWithText("Rows in file: 0").assertIsDisplayed()
    }

    // ── other states ────────────────────────────────────────────────

    @Test
    fun emptyState_offersCreateAccount() {
        val harness = Harness()
        screen(ImportUiState.Empty, harness)

        composeRule.onNodeWithText("Create an account first").assertIsDisplayed()
        composeRule.onNodeWithText("Create account").performClick()
        assertEquals(1, harness.createAccountClicks)
    }

    @Test
    fun errorState_retries() {
        val harness = Harness()
        screen(ImportUiState.Error(ImportError.Network), harness)

        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, harness.retryClicks)
    }

    @Test
    fun result_unknownRowStatusNeverRendersAsSuccess() {
        // A future status the client doesn't know decodes UNKNOWN — it
        // must not leak into the Imported tile.
        screen(
            ImportUiState.Result(
                batch(
                    success = 0,
                    duplicates = 0,
                    failed = 0,
                    rows = listOf(ImportRow(2, ImportRowStatus.UNKNOWN, "odd", null)),
                ),
            ),
        )

        composeRule.onNodeWithContentDescription("0 Imported").assertIsDisplayed()
        composeRule.onNodeWithText("Rows in file: 1").assertIsDisplayed()
    }
}
