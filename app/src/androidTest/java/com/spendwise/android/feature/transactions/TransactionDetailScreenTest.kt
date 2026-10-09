package com.spendwise.android.feature.transactions

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.R
import com.spendwise.android.TestFormats
import com.spendwise.android.core.designsystem.SpendwiseTheme
import com.spendwise.android.core.model.CurrencyCode
import com.spendwise.android.core.model.Money
import com.spendwise.android.core.model.Transaction
import com.spendwise.android.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/**
 * Stateless-screen tests for transaction detail (S10 — append-only;
 * M20 adds the §21 "offsetting entry" action when the row is eligible).
 */
@RunWith(AndroidJUnit4::class)
class TransactionDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun txn(
        type: TransactionType = TransactionType.EXPENSE,
        category: String? = "FOOD",
        sourceRef: String? = "MANUAL:t1",
        description: String? = null,
    ) = Transaction(
        id = "t1",
        accountId = "acc-1",
        type = type,
        amount = Money(BigDecimal("25.50"), CurrencyCode("USD")),
        category = category,
        sourceRef = sourceRef,
        occurredOn = java.time.LocalDate.of(2026, 10, 4),
        description = description,
    )

    private data class Harness(
        var retries: Int = 0,
        var backs: Int = 0,
        var offsets: MutableList<String> = mutableListOf(),
    )

    private fun screen(state: TransactionDetailUiState, harness: Harness = Harness()) {
        composeRule.setContent {
            SpendwiseTheme {
                TransactionDetailScreen(
                    state = state,
                    onRetry = { harness.retries++ },
                    onNavigateBack = { harness.backs++ },
                    onOffset = { harness.offsets += it },
                )
            }
        }
    }

    @Test
    fun content_showsTypeAmountAccountDateCurrencySource() {
        screen(TransactionDetailUiState.Content(txn(), accountName = "Wallet"))

        composeRule.onNodeWithText("Food").assertIsDisplayed()
        composeRule.onNodeWithText(
            "${TestFormats.signedAmount("25.50", "USD", credit = false)} USD",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Account").assertIsDisplayed()
        composeRule.onNodeWithText("Wallet").assertIsDisplayed()
        // M11 — the server occurrence date is a first-class detail field.
        composeRule.onNodeWithText("Date").assertIsDisplayed()
        composeRule.onNodeWithText(
            com.spendwise.android.core.ui.format.DateFormat.format(
                java.time.LocalDate.of(2026, 10, 4),
            ),
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Currency").assertIsDisplayed()
        composeRule.onNodeWithText("USD").assertIsDisplayed()
        composeRule.onNodeWithText("Source").assertIsDisplayed()
        composeRule.onNodeWithText("Manual").assertIsDisplayed()
    }

    @Test
    fun content_note_showsThePersistedDescriptionWhenPresent() {
        screen(TransactionDetailUiState.Content(
            txn(description = "rent for October"),
            accountName = "Wallet",
        ))
        composeRule.onNodeWithText("Note").assertIsDisplayed()
        composeRule.onNodeWithText("rent for October").assertIsDisplayed()
    }

    @Test
    fun content_note_isHiddenWhenDescriptionIsNull() {
        screen(TransactionDetailUiState.Content(txn(description = null), accountName = "Wallet"))
        composeRule.onAllNodesWithText("Note").assertCountEquals(0)
    }

    @Test
    fun content_note_rendersAMaxLengthDescriptionWhole() {
        // M17: server bound is 255 — the block-style note row must lay the
        // full text out, not truncate it to the side-by-side DetailField.
        val long = "rent for October plus utilities ".repeat(8).take(255)
        screen(TransactionDetailUiState.Content(txn(description = long), accountName = "Wallet"))
        composeRule.onNodeWithText(long).assertIsDisplayed()
    }

    @Test
    fun content_isReadOnly_noEditDeleteOrCorrectAffordance() {
        screen(TransactionDetailUiState.Content(txn(), accountName = "Wallet"))

        composeRule.onNodeWithText(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.txn_detail_readonly),
        ).assertIsDisplayed()
        composeRule.onAllNodesWithText("Edit", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Delete", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Correct", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Reverse", substring = true).assertCountEquals(0)
    }

    @Test
    fun income_showsPlusSignAndWord() {
        screen(
            TransactionDetailUiState.Content(
                txn(type = TransactionType.INCOME, category = "SALARY"),
                accountName = "Wallet",
            ),
        )

        composeRule.onNodeWithText(
            "${TestFormats.signedAmount("25.50", "USD", credit = true)} USD",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Income").assertIsDisplayed()
    }

    @Test
    fun notFound_offersBackToList() {
        val harness = Harness()
        screen(TransactionDetailUiState.NotFound, harness)

        composeRule.onNodeWithText("Transaction not found").assertIsDisplayed()
        composeRule.onNodeWithText("Back to transactions").performClick()
        assertEquals(1, harness.backs)
    }

    // ── M20: offsetting entry (PRODUCT §21) ─────────────────────────

    @Test
    fun offsetEligible_showsTheActionWithHonestCopy_andClickCarriesTheId() {
        val harness = Harness()
        screen(
            TransactionDetailUiState.Content(
                txn(type = TransactionType.EXPENSE),
                accountName = "Wallet",
                offsetEligible = true,
            ),
            harness,
        )

        composeRule.onNodeWithText("Create offsetting entry")
            .performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf("t1"), harness.offsets)

        // §21 verbatim honest consequence copy rides along.
        composeRule.onNodeWithText(
            "opposite entry",
            substring = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            "original stays in history",
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun offsetEligible_neverReadsAsUndoDeleteOrEdit() {
        // §21: the action is a compensating entry — the copy must never
        // masquerade as undo/delete/edit of the original row.
        screen(
            TransactionDetailUiState.Content(
                txn(),
                accountName = "Wallet",
                offsetEligible = true,
            ),
        )

        composeRule.onAllNodesWithText("Undo", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Delete", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Edit", substring = true).assertCountEquals(0)
    }

    @Test
    fun offsetIneligible_hidesTheAction_entirely() {
        // TRANSFER_* legs and archived-account rows arrive with
        // offsetEligible = false — no affordance, no dead hint.
        screen(
            TransactionDetailUiState.Content(
                txn(type = TransactionType.TRANSFER_OUT, sourceRef = "TRANSFER:r1:OUT"),
                accountName = "Wallet",
                offsetEligible = false,
            ),
        )

        composeRule.onAllNodesWithText(
            "Create offsetting entry",
            substring = true,
        ).assertCountEquals(0)
        composeRule.onAllNodesWithText(
            "opposite entry",
            substring = true,
        ).assertCountEquals(0)
    }

    @Test
    fun networkError_retries() {
        val harness = Harness()
        screen(TransactionDetailUiState.Error(TransactionsError.Network), harness)

        composeRule.onNodeWithText(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.state_network_title),
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, harness.retries)
    }
}
