package com.spendwise.android.feature.imports

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.core.designsystem.SpendwiseTheme
import com.spendwise.android.core.model.ImportBatchSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** M10 import history — loading/empty/content/error surfaces + row tap. */
@RunWith(AndroidJUnit4::class)
class ImportHistoryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private data class Harness(
        var openedBatch: String? = null,
        var importClicks: Int = 0,
        var retryClicks: Int = 0,
        var backClicks: Int = 0,
    )

    private fun screen(state: ImportHistoryState, harness: Harness = Harness()) {
        composeRule.setContent {
            SpendwiseTheme {
                ImportHistoryScreen(
                    state = state,
                    onOpenBatch = { harness.openedBatch = it },
                    onImport = { harness.importClicks++ },
                    onRetry = { harness.retryClicks++ },
                    onBack = { harness.backClicks++ },
                )
            }
        }
    }

    private fun item(
        batchId: String = "b1",
        fileName: String = "tpbank-oct.csv",
        accountId: String = "acc-1",
    ) = ImportBatchSummary(
        batchId = batchId,
        accountId = accountId,
        fileName = fileName,
        importedAt = Instant.parse("2026-10-04T12:00:00Z"),
        successCount = 10,
        duplicateCount = 2,
        failedCount = 1,
    )

    @Test
    fun content_lists_each_batch_and_a_row_tap_opens_its_report() {
        val harness = Harness()
        screen(
            ImportHistoryState.Content(
                items = listOf(item(batchId = "b-new"), item(batchId = "b-old", fileName = "sep.pdf")),
                accountNames = mapOf("acc-1" to "Wallet"),
            ),
            harness,
        )

        // Rows merge semantics into "<fileName>, <subtitle>" like account rows.
        composeRule.onNodeWithContentDescription("tpbank-oct.csv", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("sep.pdf", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("tpbank-oct.csv", substring = true)
            .performClick()

        assertEquals("b-new", harness.openedBatch)
    }

    @Test
    fun empty_state_offers_the_import_escape() {
        val harness = Harness()
        screen(ImportHistoryState.Empty, harness)

        composeRule.onNodeWithText("No imports yet").assertIsDisplayed()
        composeRule.onNodeWithText("Import a statement").performClick()

        assertEquals(1, harness.importClicks)
    }

    @Test
    fun network_error_renders_the_retry_surface() {
        val harness = Harness()
        screen(ImportHistoryState.Error(ImportHistoryError.Network), harness)

        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, harness.retryClicks)
    }

    @Test
    fun header_back_callback_fires() {
        val harness = Harness()
        screen(ImportHistoryState.Empty, harness)

        composeRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(harness.backClicks == 1)
    }
}
