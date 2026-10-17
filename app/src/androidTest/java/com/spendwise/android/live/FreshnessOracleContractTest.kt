package com.spendwise.android.live

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.android.live.infrastructure.E2eUiDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B04 hermetic negative test — proves the freshness oracle CANNOT be
 * satisfied by the form field where the marker was just typed.
 *
 * The pre-fix oracle (`onAllNodesWithText`) matches editable text too, so
 * a value typed into a field could falsely satisfy a "post-save row
 * visible" wait. `E2eUiDriver.committedText` adds `!hasSetTextAction`,
 * which excludes every editable node — the marker only counts once the
 * mutation actually committed to a read-only node (list row/label/balance).
 *
 * Fully hermetic: `createComposeRule` hosts synthetic content — no app,
 * no backend, no network.
 */
@RunWith(AndroidJUnit4::class)
class FreshnessOracleContractTest {

    @get:Rule
    val compose = createComposeRule()

    private val marker = "e2e-marker-7f3a"

    /** Screen with ONLY an editable field holding the marker — the
     *  committed oracle must NOT match (input ≠ committed output). */
    @Test
    fun committedOracle_doesNotMatchEditableInput() {
        compose.setContent {
            val v = remember { mutableStateOf(marker) }
            BasicTextField(value = v.value, onValueChange = { v.value = it })
        }
        compose.waitForIdle()
        // Raw text matching DOES see the field — this is the false-positive
        // surface the fix removes.
        assertEquals(1,
            compose.onAllNodes(
                androidx.compose.ui.test.hasText(marker)).fetchSemanticsNodes().size)
        // Committed oracle sees nothing — the marker lives only in an input.
        assertEquals(0,
            compose.onAllNodes(
                E2eUiDriver(compose).committedText(marker)).fetchSemanticsNodes().size)
    }

    /** Editable field PLUS a committed row carrying the marker — the
     *  oracle must match exactly the read-only node. */
    @Test
    fun committedOracle_matchesOnlyReadOnlyNode() {
        compose.setContent {
            Column {
                val v = remember { mutableStateOf(marker) }
                BasicTextField(value = v.value, onValueChange = { v.value = it })
                Text(marker) // the committed list-row/label the oracle wants
            }
        }
        compose.waitForIdle()
        assertEquals(1,
            compose.onAllNodes(
                E2eUiDriver(compose).committedText(marker)).fetchSemanticsNodes().size)
    }

    /** `waitCommittedText` times out honestly on editable-only content —
     *  a typed-but-never-committed marker can never be reported fresh. */
    @Test
    fun waitCommittedText_timesOutOnInputOnly() {
        compose.setContent {
            val v = remember { mutableStateOf(marker) }
            BasicTextField(value = v.value, onValueChange = { v.value = it })
        }
        compose.waitForIdle()
        val threw = runCatching {
            E2eUiDriver(compose).waitCommittedText(marker, timeoutMs = 1_500)
        }.isFailure
        assertTrue("waitCommittedText must time out on editable-only content", threw)
    }
}
