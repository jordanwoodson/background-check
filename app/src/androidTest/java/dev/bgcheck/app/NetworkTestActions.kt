package dev.bgcheck.app

import android.content.Intent
import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
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
    val header = onNode(hasAnyAncestor(hasTestTag(tag)) and
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
        org.junit.Assert.assertFalse("$text must fit its layout", layout.hasVisualOverflow)
        org.junit.Assert.assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
        for (line in 0 until layout.lineCount) org.junit.Assert.assertFalse(layout.isLineEllipsized(line))
    }
}

/** Deliver through Android so the real onNewIntent/consumption path is exercised. */
internal fun MainActivity.deliverNetworkIntent(intent: Intent) {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
}
