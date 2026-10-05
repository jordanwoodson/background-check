package dev.bgcheck.app

import android.content.Intent
import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.bgcheck.network.utils.Constants
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Apply UI preferences before the activity launches, then restore the device's original choices. */
internal class NetworkTestPreferences(private val overrides: Map<String, Any?> = emptyMap()) : TestRule {
    override fun apply(base: Statement, description: Description) = object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
            val baseline = mapOf<String, Any?>(
                Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT to false,
                Constants.Settings.KEY_FIREWALL_FILTER_TYPE to "all",
                Constants.Settings.KEY_FIREWALL_FILTER_STATE to null,
                Constants.Settings.KEY_FIREWALL_FILTER_PROFILE to "all",
                Constants.Settings.KEY_FIREWALL_FILTER_INTERNET_ONLY to false,
                Constants.Settings.KEY_PACKAGES_FILTER_TYPE to "all",
                Constants.Settings.KEY_PACKAGES_FILTER_STATE to null,
                Constants.Settings.KEY_PACKAGES_FILTER_PROFILE to "all",
                "firewall_compact_layout" to false,
                "packages_compact_layout" to false,
            ) + overrides
            val original = baseline.keys.associateWith { prefs.all[it] }
            fun write(values: Map<String, Any?>) {
                prefs.edit().apply {
                    values.forEach { (key, value) ->
                        when (value) {
                            is Boolean -> putBoolean(key, value)
                            is String -> putString(key, value)
                            else -> remove(key)
                        }
                    }
                }.commit()
            }
            val inspection = context.getSharedPreferences("inspection_preferences", Context.MODE_PRIVATE)
            val inspectionKeys = listOf("overview.page", "apps.type", "apps.profile", "apps.protection", "apps.process", "apps.sort", "apps.direction")
            val oldInspection = inspectionKeys.associateWith { inspection.getString(it, null) }
            inspection.edit().apply { inspectionKeys.forEach { remove(it) } }.commit()
            write(baseline)
            try { base.evaluate() } finally {
                write(original)
                inspection.edit().apply { oldInspection.forEach { (key, value) -> putString(key, value) } }.commit()
            }
        }
    }
}

internal fun ComposeContentTestRule.awaitNetworkNode(tag: String) {
    waitUntil(20_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
}

internal fun ComposeContentTestRule.openNetwork() {
    onNodeWithTag("nav-network").performClick()
    awaitNetworkNode("network-screen")
}

internal fun ComposeContentTestRule.openFirewallSettings() {
    openNetwork()
    onNodeWithTag("network-list").performScrollToNode(hasTestTag("network-open-settings"))
    onNodeWithTag("network-open-settings").performClick()
    awaitNetworkNode("network-settings")
}

internal fun ComposeContentTestRule.openTaggedFilters(listTag: String, tag: String) {
    onNodeWithTag(listTag).performScrollToNode(hasTestTag(tag))
    val header = onNode(hasText("Filters") and hasAnyAncestor(hasTestTag(tag)) and
        SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
    if (header.fetchSemanticsNode().config[SemanticsProperties.StateDescription] == "Collapsed") header.performClick()
}

internal fun ComposeContentTestRule.closeNetworkAppSheet() {
    pressBack()
    waitUntil(5_000) { onAllNodesWithTag("network-sheet").fetchSemanticsNodes().isEmpty() }
    onNodeWithTag("network-sheet").assertDoesNotExist()
}

/** Check the rendered text's layout, including clipping and ellipsis, instead of a View's height. */
internal fun ComposeContentTestRule.assertCompleteText(text: String) {
    onNodeWithText(text, useUnmergedTree = true).assertCompleteText(text)
}

internal fun SemanticsNodeInteraction.assertCompleteText(text: String) {
    val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
    check(results.isNotEmpty()) { "No text layout for $text" }
    results.forEach { layout ->
        org.junit.Assert.assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
        // Paragraph width can retain the parent's constraint even when Text wraps its content.
        // Check the drawn lines, allowing one pixel for rounded fractional font metrics.
        for (line in 0 until layout.lineCount) {
            org.junit.Assert.assertFalse(layout.isLineEllipsized(line))
            org.junit.Assert.assertTrue("$text must fit its width", layout.getLineLeft(line) >= -1f &&
                layout.getLineRight(line) <= layout.size.width + 1f)
            org.junit.Assert.assertTrue("$text must fit its height", layout.getLineBottom(line) <= layout.size.height + 1f)
        }
    }
}

/** Deliver through Android so the real onNewIntent/consumption path is exercised. */
internal fun MainActivity.deliverNetworkIntent(intent: Intent) {
    // ActivityScenario matches lifecycle callbacks against the original launcher intent.
    val launcher = this.intent
    var paused = false
    lateinit var observer: LifecycleEventObserver
    observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_PAUSE) paused = true
        if (paused && event == Lifecycle.Event.ON_RESUME) {
            this.intent = launcher
            lifecycle.removeObserver(observer)
        }
    }
    lifecycle.addObserver(observer)
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
}
