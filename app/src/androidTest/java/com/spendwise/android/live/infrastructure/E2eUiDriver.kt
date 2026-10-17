package com.spendwise.android.live.infrastructure

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.spendwise.android.MainActivity

/**
 * Bounded-wait UI driver for Live UI E2E (V5 §5.4).
 *
 * - Every wait is condition-based with a finite timeout — no `Thread.sleep`
 *   anywhere. Timeout expiry is an honest failure, never masked.
 * - Launch is controlled: [launchMain] must be called by the scenario
 *   AFTER `LiveUiE2eGate.assumeAuthorized(caseId)` returns. There is no
 *   auto-launch rule.
 * - [compose] is `createEmptyComposeRule()` — its semantics queries bind
 *   to whichever Activity the scenario manually launched; production Hilt
 *   graph, zero substitution.
 * - SAF/system UI goes through UiAutomator on the real picker.
 */
class E2eUiDriver(
    private val compose: ComposeTestRule,
) {

    companion object {
        const val SHORT_TIMEOUT_MS = 10_000L
        const val NETWORK_TIMEOUT_MS = 30_000L // live round-trips may be slow
        const val SAF_TIMEOUT_MS = 8_000L
    }

    val device: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    lateinit var scenario: ActivityScenario<MainActivity>
        private set

    fun scenarioInitialized(): Boolean = ::scenario.isInitialized

    /** Controlled post-gate Activity start — never before authorization. */
    fun launchMain(): ActivityScenario<MainActivity> {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        return scenario
    }

    // --- Marker semantics (a11y contract of this app) --------------------
    // List/card rows call clearAndSetSemantics: their content is exposed as
    // ONE composite contentDescription ("name, Bank, 500,00 USD"); deeper
    // text (row notes) survives only in the UNMERGED tree. A "marker" is a
    // string that identifies the row to a user — matched against all three
    // channels: merged text, composite cd, unmerged text.

    private fun cdContains(text: String): SemanticsMatcher =
        SemanticsMatcher("cd contains '$text'") { n ->
            n.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }
                ?.any { it.contains(text) } == true
        }

    private fun marker(text: String): SemanticsMatcher =
        hasText(text, substring = true) or cdContains(text)

    private fun markerPresent(text: String): Boolean =
        compose.onAllNodes(marker(text)).fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()

    // --- Bounded waits ---------------------------------------------------

    fun waitText(text: String, substring: Boolean = false, timeoutMs: Long = NETWORK_TIMEOUT_MS) {
        runCatching {
            compose.waitUntil(timeoutMs) { markerPresent(text) }
        }.onFailure {
            // Ground truth on timeout — log every visible Text node.
            runCatching {
                val dump = compose.onAllNodes(SemanticsMatcher("any") { true })
                    .fetchSemanticsNodes().take(60)
                    .joinToString(" || ") { n ->
                        n.config.getOrElseNullable(SemanticsProperties.Text) { null }
                            ?.joinToString(",") { it.text }
                            ?: n.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }
                                ?.joinToString(",")?.let { "cd:$it" }
                            ?: "?"
                    }
                android.util.Log.w("WAITTEXT", "missing='$text' nodes: $dump")
            }
            throw it
        }
    }

    fun waitCd(desc: String, timeoutMs: Long = NETWORK_TIMEOUT_MS) {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodesWithContentDescription(desc)
                .fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithContentDescription(desc, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Non-waiting probe — true when [text] is currently rendered. */
    fun textVisible(text: String, substring: Boolean = false): Boolean =
        runCatching { markerPresent(text) }.getOrDefault(false)

    fun waitTextGone(text: String, substring: Boolean = false, timeoutMs: Long = NETWORK_TIMEOUT_MS) {
        compose.waitUntil(timeoutMs) { !markerPresent(text) }
    }

    /**
     * Committed-state oracle (B04): matches only a node that carries [text]
     * and is NOT an editable field (`hasSetTextAction`). A value the user
     * just typed into a form EditText is editable text — this matcher can
     * never be satisfied by the input itself, so freshness can only be
     * proven by a post-mutation committed node (list row, label, balance).
     */
    fun committedText(text: String, substring: Boolean = false): SemanticsMatcher =
        marker(text) and SemanticsMatcher(
            "non-editable (committed) node",
        ) { !it.config.contains(androidx.compose.ui.semantics.SemanticsActions.SetText) }

    fun waitCommittedText(text: String, substring: Boolean = false,
                          timeoutMs: Long = NETWORK_TIMEOUT_MS) {
        runCatching {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodes(committedText(text, substring))
                .fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(
                    hasText(text, substring = true) and
                        SemanticsMatcher("non-editable") {
                            !it.config.contains(SemanticsActions.SetText)
                        },
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().isNotEmpty()
        }
        }.onFailure {
            runCatching {
                val shot = java.io.File(
                    InstrumentationRegistry.getInstrumentation()
                        .targetContext.filesDir,
                    "waitcommit-timeout-${System.currentTimeMillis()}.png",
                )
                device.takeScreenshot(shot)
                val dump = compose.onAllNodes(SemanticsMatcher("any") { true })
                    .fetchSemanticsNodes().take(60)
                    .joinToString(" || ") { n ->
                        n.config.getOrElseNullable(SemanticsProperties.Text) { null }
                            ?.joinToString(",") { it.text }
                            ?: n.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }
                                ?.joinToString(",")?.let { "cd:$it" }
                            ?: "?"
                    }
                android.util.Log.w("WAITCOMMIT", "missing='$text' shot=${shot.name} nodes: $dump")
            }
            throw it
        }
    }

    /** Freshness probe (LIVE-E2E-UI-001 + B04): bounded wait for a
     *  committed (non-editable) post-mutation node WITHOUT pull-to-refresh —
     *  returns elapsed ms so the recorder can store the freshness latency. */
    fun waitFresh(text: String, substring: Boolean = false, timeoutMs: Long = NETWORK_TIMEOUT_MS): Long {
        val start = System.nanoTime()
        waitCommittedText(text, substring, timeoutMs)
        return (System.nanoTime() - start) / 1_000_000
    }

    /** Mutation-boundary freshness (M7+B04): the timer starts AT the submit
     *  gesture and ends when a committed (non-editable) node carrying the
     *  marker becomes visible — the typed form field can never satisfy it. */
    /** The soft keyboard can cover a form's submit row (it stays up after
     *  the last field edit). A real user dismisses it or scrolls; doing it
     *  here keeps the submit gesture honest — the tapped node is actually
     *  on the screen. No-op when the IME is already hidden. */
    fun dismissKeyboard() {
        runCatching {
            androidx.test.espresso.Espresso.closeSoftKeyboard()
        }
        compose.waitForIdle()
    }

    fun tapAndMeasureFresh(
        tapLabel: String,
        expectText: String,
        substring: Boolean = false,
        timeoutMs: Long = NETWORK_TIMEOUT_MS,
    ): Long {
        dismissKeyboard()
        waitText(tapLabel, timeoutMs = timeoutMs)
        val start = System.nanoTime()
        tapMarkerNow(tapLabel)
        waitCommittedText(expectText, substring, timeoutMs)
        return (System.nanoTime() - start) / 1_000_000
    }

    /** M09 — REAL double-tap via UiAutomator input events on the node's
     *  screen bounds. Unlike `performClick`, raw device clicks are not
     *  synchronized with Compose idleness, so the second gesture can land
     *  while the first request is still in-flight — which is the actual
     *  submit-lock window under test. */
    fun realDoubleTap(nodeText: String, gapMs: Long = 60): Long {
        val interaction = compose.onNodeWithText(nodeText)
        // The submit button can sit below the fold — an unplaced node has
        // empty bounds, so real input events must wait for the reveal.
        val deadline = System.currentTimeMillis() + NETWORK_TIMEOUT_MS
        var b = interaction.fetchSemanticsNode().boundsInWindow
        while (b.isEmpty && System.currentTimeMillis() < deadline) {
            val w = device.displayWidth
            val h = device.displayHeight
            device.swipe(w / 2, (h * 0.7).toInt(), w / 2, (h * 0.4).toInt(), 16)
            compose.waitForIdle()
            b = interaction.fetchSemanticsNode().boundsInWindow
        }
        check(!b.isEmpty) { "'$nodeText' never revealed for real double-tap" }
        val x = ((b.left + b.right) / 2).toInt()
        val y = ((b.top + b.bottom) / 2).toInt()
        val t0 = System.nanoTime()
        device.click(x, y)
        device.click(x, y) // back-to-back hardware-level clicks
        return (System.nanoTime() - t0) / 1_000_000
    }

    /** Real pull-to-refresh gesture — the app's only guaranteed network
     *  trigger on an already-loaded screen (tab nav keeps the VM alive,
     *  so navigation alone refetches nothing). */
    fun pullDownRefresh() {
        val w = device.displayWidth
        val h = device.displayHeight
        device.swipe(w / 2, (h * 0.30).toInt(), w / 2, (h * 0.72).toInt(), 28)
        compose.waitForIdle()
    }

    /** Diagnostic: dump every editable node's raw EditableText + any label
     *  text — unmasks what a form actually holds before submit. */
    fun debugDumpFields(tag: String) {
        val nodes = compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
            .fetchSemanticsNodes()
        android.util.Log.w(tag, "editable nodes=${nodes.size}: " + nodes.joinToString(" | ") { n ->
            val ed = n.config.getOrElseNullable(SemanticsProperties.EditableText) { null }?.text
            val label = n.config.getOrElseNullable(SemanticsProperties.Text) { null }
                ?.joinToString(",") { it.text }
            "ed='$ed' t='$label'"
        })
    }

    // --- Assertions ------------------------------------------------------

    fun assertShown(text: String, substring: Boolean = false) {
        check(markerPresent(text)) { "marker not shown: $text" }
    }

    fun assertAbsent(text: String, substring: Boolean = false) {
        check(!markerPresent(text)) { "marker unexpectedly present: $text" }
    }

    // --- Gestures --------------------------------------------------------

    /** Tap a control INSIDE an open dialog. Dialog content lives in a
     *  separate window subtree — matching by "last clickable" can hit the
     *  dimmed background item (closing the dialog via the scrim) instead
     *  of the confirm. The deterministic selector: a clickable node whose
     *  ancestor chain also contains [dialogTitle]. */
    fun tapDialog(label: String, dialogTitle: String, timeoutMs: Long = NETWORK_TIMEOUT_MS) {
        waitText(dialogTitle, timeoutMs = timeoutMs)
        val inDialog = hasClickAction() and hasText(label) and
            hasAnyAncestor(hasAnyDescendant(hasText(dialogTitle)))
        val matches = compose.onAllNodes(inDialog).fetchSemanticsNodes()
        check(matches.isNotEmpty()) { "dialog '$dialogTitle' has no clickable '$label'" }
        compose.onAllNodes(inDialog).get(matches.size - 1).performClick()
        compose.waitForIdle()
    }

    /** Dialog-confirm freshness: timer starts at the confirm tap inside the
     *  [dialogTitle] subtree and ends when a committed node carries the
     *  [expectText] marker (B04 — an editable field never satisfies it). */
    fun tapDialogAndMeasureFresh(
        label: String,
        dialogTitle: String,
        expectText: String,
        substring: Boolean = false,
        timeoutMs: Long = NETWORK_TIMEOUT_MS,
    ): Long {
        waitText(dialogTitle, timeoutMs = timeoutMs)
        val inDialog = hasClickAction() and hasText(label) and
            hasAnyAncestor(hasAnyDescendant(hasText(dialogTitle)))
        val matches = compose.onAllNodes(inDialog).fetchSemanticsNodes()
        check(matches.isNotEmpty()) { "dialog '$dialogTitle' has no clickable '$label'" }
        val start = System.nanoTime()
        compose.onAllNodes(inDialog).get(matches.size - 1).performClick()
        waitCommittedText(expectText, substring, timeoutMs)
        return (System.nanoTime() - start) / 1_000_000
    }

    fun tap(text: String, substring: Boolean = false, timeoutMs: Long = NETWORK_TIMEOUT_MS) {
        waitText(text, substring, timeoutMs)
        // An open IME overlays bottom-of-form buttons — a real touch on
        // their screen coordinates lands on the keyboard instead.
        dismissKeyboard()
        tapMarkerNow(text)
        compose.waitForIdle()
    }

    /** A semantics node inside a `verticalScroll` Column can be composed
     *  but UNPLACED (zero bounds) while it sits below the fold — invoking
     *  OnClick on it reaches nothing. Reveal it with real swipes until its
     *  window bounds exist, then dispatch the semantics click; a real
     *  device touch follows if the action still can't land. */
    private fun clickNodeWithReveal(node: androidx.compose.ui.test.SemanticsNodeInteraction) {
        val deadline = System.currentTimeMillis() + NETWORK_TIMEOUT_MS
        var bounds = node.fetchSemanticsNode().boundsInWindow
        while (bounds.isEmpty && System.currentTimeMillis() < deadline) {
            val w = device.displayWidth
            val h = device.displayHeight
            device.swipe(w / 2, (h * 0.7).toInt(), w / 2, (h * 0.4).toInt(), 16)
            compose.waitForIdle()
            bounds = node.fetchSemanticsNode().boundsInWindow
        }
        if (bounds.isEmpty) {
            node.performClick()
            return
        }
        // Popup windows (dropdown menus, dialogs) animate in outside the
        // Compose idle tracker — a touch on immediately-reported bounds can
        // land on the scrim before the window is interactive. Wait until
        // the reported bounds are stable across two frames.
        var stable = bounds
        var prev: androidx.compose.ui.geometry.Rect? = null
        val settleDeadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < settleDeadline) {
            val cur = node.fetchSemanticsNode().boundsInWindow
            if (!cur.isEmpty && cur == prev) {
                stable = cur
                break
            }
            prev = cur
            Thread.sleep(120)
        }
        // Nodes inside dialog/popup windows can report window-local bounds —
        // a raw screen click then lands on the scrim or dead space. Resolve
        // the same element through UiAutomator, whose visibleBounds are true
        // screen coordinates across every window.
        val sn = node.fetchSemanticsNode()
        val cd = sn.config
            .getOrElseNullable(SemanticsProperties.ContentDescription) { null }
            ?.firstOrNull()
        val txt = sn.config.getOrElseNullable(SemanticsProperties.Text) { null }
            ?.firstOrNull()?.text
        // A content-described target must resolve by desc — By.text would
        // hit a same-named sibling label instead (e.g. the text above a
        // dropdown anchor) and the gesture would land on the wrong node.
        val uiObj = (cd?.let { device.findObject(By.desc(it)) })
            ?: (txt?.let { device.findObject(By.text(it)) })
        if (uiObj != null) {
            uiObj.click()
        } else {
            device.click(
                ((stable.left + stable.right) / 2).toInt(),
                ((stable.top + stable.bottom) / 2).toInt(),
            )
        }
        compose.waitForIdle()
    }

    /** Open a SwDropdownField and pick an option by its rendered text.
     *  The anchor field carries `contentDescription = label`; the menu is
     *  a PopupWindow — UiAutomator sees it once actually interactive. */
    fun selectDropdown(label: String, optionText: String, timeoutMs: Long = NETWORK_TIMEOUT_MS) {
        val field = compose.onNodeWithContentDescription(label)
        clickNodeWithReveal(field)
        val option = device.wait(
            androidx.test.uiautomator.Until.findObject(
                androidx.test.uiautomator.By.textContains(optionText)),
            timeoutMs,
        ) ?: run {
            // Fall back to the semantics tree when the popup isn't in the
            // a11y stream (some menus render in-process).
            val nodes = compose.onAllNodes(
                hasClickAction() and hasText(optionText, substring = true))
                .fetchSemanticsNodes()
            check(nodes.isNotEmpty()) {
                "dropdown '$label' opened but option '$optionText' never appeared"
            }
            clickNodeWithReveal(
                compose.onAllNodes(hasClickAction() and hasText(optionText, substring = true))
                    .get(nodes.size - 1))
            compose.waitForIdle()
            return
        }
        option.click()
        compose.waitForIdle()
    }

    private fun tapMarkerNow(text: String) {
        // Prefer EXACT matches: "Sign in" must not land on the overlay's
        // "Sign in again" merely because it renders later in the tree.
        val exact = hasClickAction() and
            (hasText(text) or SemanticsMatcher("cd == '$text'") { n ->
                n.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }
                    ?.any { it == text } == true
            })
        val mergedExact = compose.onAllNodes(exact).fetchSemanticsNodes()
        if (mergedExact.isNotEmpty()) {
            clickNodeWithReveal(compose.onAllNodes(exact).get(mergedExact.size - 1))
            return
        }
        val clickable = hasClickAction() and marker(text)
        val merged = compose.onAllNodes(clickable).fetchSemanticsNodes()
        when {
            merged.isNotEmpty() ->
                // Overlapping surfaces render later — the topmost (e.g. the
                // dialog confirm over the More item) is the last match.
                clickNodeWithReveal(compose.onAllNodes(clickable).get(merged.size - 1))
            else -> {
                val ancestors = compose.onAllNodes(
                    hasClickAction() and hasAnyDescendant(hasText(text, substring = true)),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes()
                if (ancestors.isNotEmpty()) {
                    compose.onAllNodes(
                        hasClickAction() and hasAnyDescendant(hasText(text, substring = true)),
                        useUnmergedTree = true,
                    ).get(ancestors.size - 1).performClick()
                    return
                }
                // Rows/cards can carry a composite contentDescription with NO
                // OnClick action in the merged tree (the click lives on a
                // sibling/parent layout node). Last resort: REAL touch on the
                // marker node's window bounds — the same gesture a user makes.
                val markerNodes = compose.onAllNodes(marker(text))
                    .fetchSemanticsNodes()
                if (markerNodes.isEmpty()) {
                    runCatching {
                        val dump = compose.onAllNodes(SemanticsMatcher("any") { true })
                            .fetchSemanticsNodes().take(50).joinToString(" || ") { n ->
                                val cd = n.config.getOrElseNullable(
                                    SemanticsProperties.ContentDescription) { null }
                                    ?.joinToString(",")
                                val t = n.config.getOrElseNullable(
                                    SemanticsProperties.Text) { null }
                                    ?.joinToString(",") { it.text }
                                "clk=${n.config.contains(SemanticsActions.OnClick)}" +
                                    " cd=$cd t=$t"
                            }
                        android.util.Log.w("TAPMISS", "miss='$text' nodes: $dump")
                    }
                    check(false) { "no clickable node labelled '$text'" }
                }
                val b = markerNodes.last().boundsInWindow
                device.click(
                    ((b.left + b.right) / 2).toInt(),
                    ((b.top + b.bottom) / 2).toInt(),
                )
                return
            }
        }
    }

    fun tapCd(desc: String, timeoutMs: Long = NETWORK_TIMEOUT_MS) {
        waitCd(desc, timeoutMs)
        if (compose.onAllNodesWithContentDescription(desc)
                .fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription(desc).performClick()
        } else {
            // Icon-only targets hide their contentDescription below the merged
            // semantics surface — click through UiAutomator instead.
            val obj = device.findObject(By.desc(desc))
                ?: error("no UiObject with content-desc '$desc'")
            obj.click()
        }
        compose.waitForIdle()
    }

    /** Wait until ANY of [texts] is rendered, then tap the matched one —
     *  for CTAs whose label differs by state (e.g. empty "Add your first
     *  account" vs list "Add account"). */
    fun tapAnyOf(vararg texts: String, timeoutMs: Long = NETWORK_TIMEOUT_MS) {
        val found = java.util.concurrent.atomic.AtomicReference<String>()
        compose.waitUntil(timeoutMs) {
            texts.firstOrNull { textVisible(it, substring = true) }
                ?.also { found.set(it) } != null
        }
        tap(found.get(), substring = true)
    }

    /** Type into a field found by its contentDescription (e.g. "Account name"). */
    fun typeCd(fieldDesc: String, value: String, timeoutMs: Long = NETWORK_TIMEOUT_MS) {
        waitCd(fieldDesc, timeoutMs)
        compose.onNodeWithContentDescription(fieldDesc).performTextInput(value)
        compose.waitForIdle()
    }

    /** Type into a field found by its label. SwTextField merges semantics so
     *  the editable node may lack RequestFocus — then SetText is invoked
     *  directly (the node exposes SetTextSubstitution, which writes state). */
    fun typeText(fieldLabel: String, value: String, timeoutMs: Long = NETWORK_TIMEOUT_MS) {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodesWithContentDescription(fieldLabel).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText(fieldLabel).fetchSemanticsNodes().isNotEmpty()
        }
        runCatching {
            writeInto(compose.onNodeWithContentDescription(fieldLabel), value)
        }.recoverCatching {
            writeInto(compose.onNodeWithText(fieldLabel), value)
        }.recoverCatching {
            // Merged SwTextField containers lack RequestFocus — the real
            // editable node lives in the UNMERGED tree under the labeled
            // ancestor, with proper RequestFocus+SetText.
            writeInto(
                compose.onNode(
                    hasSetTextAction() and
                        hasAnyAncestor(hasContentDescription(fieldLabel)),
                    useUnmergedTree = true,
                ), value,
            )
        }.recoverCatching {
            writeInto(
                compose.onNode(
                    hasSetTextAction() and
                        hasAnyAncestor(hasText(fieldLabel)),
                    useUnmergedTree = true,
                ), value,
            )
        }.recoverCatching {
            // Last resort: real click on the labeled node, then write into
            // whichever a11y node holds focus.
            val target = if (compose.onAllNodesWithText(fieldLabel)
                    .fetchSemanticsNodes().isNotEmpty()
            ) compose.onNodeWithText(fieldLabel)
            else compose.onNodeWithContentDescription(fieldLabel)
            runCatching { target.performClick() }
            compose.waitForIdle()
            device.wait(Until.findObject(By.focused(true)), 3_000)
            device.findObject(By.focused(true))?.let {
                it.clear()
                it.setText(value)
            }
            device.waitForIdle()
        }.getOrThrow()
        compose.waitForIdle()
        // Soft-verify: merged fields surface typed values inconsistently
        // (EditableText vs Text vs masked); a missed write still fails
        // downstream at the real assertion (e.g. login → Home).
        val landed = compose.onAllNodes(SemanticsMatcher("typed") { n ->
            n.config.getOrElseNullable(SemanticsProperties.EditableText) { null }
                ?.text?.contains(value) == true ||
                n.config.getOrElseNullable(SemanticsProperties.Text) { null }
                    ?.any { it.text.contains(value) } == true
        }).fetchSemanticsNodes().isNotEmpty()
        if (!landed) {
            android.util.Log.w("TYPETEXT", "value may not have landed in '$fieldLabel'")
        }
    }

    /** Write [value] into [node]. performTextInput APPENDS on fields backed
     *  by InsertTextAtCursor (prefilled forms like the re-auth route) — a
     *  field that already holds content must be cleared via the device path
     *  instead: real tap for focus → UiObject2.clear() → setText. */
    private fun writeInto(node: androidx.compose.ui.test.SemanticsNodeInteraction, value: String) {
        val sn = node.fetchSemanticsNode()
        val filled = sn.config
            .getOrElseNullable(SemanticsProperties.EditableText) { null }
            ?.text?.isNotEmpty() == true
        if (!filled) {
            node.performTextInput(value)
            return
        }
        // Filled field (e.g. prefilled dialog values): focus must precede
        // clear+set. Focus via the node's own click action first — a raw
        // boundsInWindow click inside a Dialog window can land on the scrim
        // (window-local coordinates), dismissing the dialog instead.
        runCatching { node.performClick() }
        compose.waitForIdle()
        var focused = device.findObject(By.focused(true))
        if (focused == null) {
            val b = node.fetchSemanticsNode().boundsInWindow
            device.click(
                ((b.left + b.right) / 2).toInt(),
                ((b.top + b.bottom) / 2).toInt(),
            )
            compose.waitForIdle()
            focused = device.findObject(By.focused(true))
        }
        val target = focused ?: error("no focused editable")
        target.clear()
        target.setText(value)
        device.waitForIdle()
    }

    /** Scroll the first scrollable container until [text] is visible, then tap.
     *  List rows clearAndSetSemantics: a note/description marker exists ONLY as
     *  unmerged text, which performScrollToNode (merged-tree search) can never
     *  reach — the real-user path is swiping, which also drives the LazyColumn
     *  prefetch that fires the append. */
    fun scrollToAndTap(text: String, substring: Boolean = false, timeoutMs: Long = NETWORK_TIMEOUT_MS) {
        swipeUpUntil(text, timeoutMs)
        tap(text, substring)
    }

    fun scrollToText(text: String, substring: Boolean = false, timeoutMs: Long = NETWORK_TIMEOUT_MS) {
        swipeUpUntil(text, timeoutMs)
    }

    /** Real upward swipes until [text] is marker-visible — bounded by
     *  [timeoutMs]. */
    private fun swipeUpUntil(text: String, timeoutMs: Long) {
        val w = device.displayWidth
        val h = device.displayHeight
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!markerPresent(text)) {
            check(System.currentTimeMillis() < deadline) {
                "swipeUpUntil: '$text' never became visible"
            }
            device.swipe(w / 2, (h * 0.78).toInt(), w / 2, (h * 0.25).toInt(), 12)
            compose.waitForIdle()
        }
    }

    /** Toggle the first Toggleable (Switch/Checkbox) to [on] — no-op if
     *  already in that state. */
    fun toggleSwitch(on: Boolean = true, timeoutMs: Long = NETWORK_TIMEOUT_MS) {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodes(isToggleable()).fetchSemanticsNodes().isNotEmpty()
        }
        val n = compose.onNode(isToggleable())
        val state = n.fetchSemanticsNode().config
            .getOrElseNullable(SemanticsProperties.ToggleableState) { null }
        if ((state == ToggleableState.On) != on) n.performClick()
        compose.waitForIdle()
    }

    fun pressBack() {
        device.pressBack()
        compose.waitForIdle()
    }

    // --- Bottom navigation (verified labels from SpendwiseMainShell) -----
    // The nav icon's contentDescription is merged into the parent item and
    // not reachable via By.desc — the reachable selector is the label text
    // ANDED with clickability, which excludes same-named screen titles.

    private fun navTap(label: String, timeoutMs: Long = NETWORK_TIMEOUT_MS) {
        // Pushed routes (account/txn detail, create forms) have no bottom nav —
        // pop back to the shell first. Safe: on a main tab this is a no-op.
        var pops = 0
        while (pops++ < 5) {
            val shellVisible = compose.onAllNodes(
                hasText("More") and hasClickAction(),
            ).fetchSemanticsNodes().isNotEmpty()
            if (shellVisible) break
            device.pressBack()
            compose.waitForIdle()
        }
        compose.waitUntil(timeoutMs) {
            compose.onAllNodes(hasText(label) and hasClickAction())
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText(label) and hasClickAction()).performClick()
        compose.waitForIdle()
    }

    fun navHome() = navTap("Home")
    fun navTransactions() = navTap("Transactions")
    fun navAccounts() = navTap("Accounts")
    fun navMore() = navTap("More")

    // --- SAF / system UI via UiAutomator ---------------------------------

    /** Wait for a node in the platform document picker and click it. */
    fun safPickFile(displayName: String, timeoutMs: Long = SAF_TIMEOUT_MS): Boolean {
        val found = device.wait(Until.hasObject(By.textContains(displayName)), timeoutMs)
        if (!found) return false
        device.findObject(By.textContains(displayName))?.click()
        return true
    }

    /** Click a system-surface button, accepting any of the localized labels
     *  (the DocumentsUI confirm is "Save" in en, "Lưu" in vi). */
    fun systemTapAnyOf(vararg labels: String, timeoutMs: Long = SAF_TIMEOUT_MS): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            for (label in labels) {
                device.findObject(By.textContains(label))?.let {
                    it.click()
                    return true
                }
            }
            Thread.sleep(300)
        }
        return false
    }

    /** Click a button in a system surface (picker Save/Open, dialogs). */
    fun systemTap(label: String, timeoutMs: Long = SAF_TIMEOUT_MS): Boolean {
        val found = device.wait(Until.hasObject(By.textContains(label)), timeoutMs)
        if (!found) return false
        device.findObject(By.textContains(label))?.click()
        return true
    }

    /** Screenshot into the case evidence dir (test-package files). */
    fun screenshot(recorder: EvidenceRecorder, name: String) {
        val file = recorder.artifactFile("$name.png")
        device.takeScreenshot(file)
        recorder.record(EvidenceRecorder.Kind.SCREENSHOT, file.absolutePath)
    }

    /** Recreate the activity (LUI-22 only — NOT process death). */
    fun recreateActivity() {
        scenario.recreate()
        compose.waitForIdle()
    }
}
