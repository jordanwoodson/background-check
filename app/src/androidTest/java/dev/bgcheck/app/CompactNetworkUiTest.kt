package dev.bgcheck.app

import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bgcheck.app.data.UiState
import dev.bgcheck.app.ui.BackgroundCheckApp
import dev.bgcheck.app.ui.BackgroundCheckTheme
import dev.bgcheck.network.utils.Constants
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompactNetworkUiTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(NetworkTestPreferences()).around(compose)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
    private val user = android.os.Process.myUid() / 100000
    private val fixture = "dev.bgcheck.fixture"

    @Test fun bothListLayoutsExposeDensityChoices() {
        // Exercise the real disclosures with a narrow viewport and both font scales, without
        // changing the device's system font setting or launching operational firewall actions.
        listOf(1f, 2f).forEach { fontScale ->
            compose.activityRule.scenario.onActivity { activity ->
                activity.setContent {
                    val density = LocalDensity.current.density
                    CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                        BackgroundCheckTheme {
                            Box(Modifier.width(360.dp)) {
                                BackgroundCheckApp(state = UiState(loading = false), onRefresh = {}, onConnect = {}, onSelect = {},
                                    onDismissDetail = {}, onAction = {}, onUndo = {}, onOpenAppSettings = {},
                                    onOpenShizuku = {}, onOpenWirelessSettings = {}, onClearMessage = {},
                                    onRefreshResources = {}, onRefreshEnergy = {}, onSetMonitorPaused = {},
                                    onSetMonitorInterval = {}, onMonitorVisible = {}, onForceStop = {})
                            }
                        }
                    }
                }
            }
            compose.openNetwork()
            assertDensityChoices(true)
            compose.onNodeWithTag("nav-apps").performClick()
            assertDensityChoices(false)
        }
    }

    @Test fun listDensityChoicesAreIndependentAndRestoredOnFreshActivityLaunch() {
        compose.openNetwork()
        chooseDensity(true, "Compact")
        assertTrue(prefs.getBoolean("firewall_compact_layout", false))
        compose.onNodeWithTag("nav-apps").performClick()
        assertChoice(false, "Comfortable", true)
        // Finish/relaunch instead of recreation: the density must come from persistent preferences.
        compose.activityRule.scenario.onActivity { it.finish() }
        androidx.test.core.app.ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
            compose.openNetwork()
            assertChoice(true, "Compact", true)
            compose.onNodeWithTag("nav-apps").performClick()
            assertChoice(false, "Comfortable", true)
            chooseDensity(false, "Compact")
        }
        androidx.test.core.app.ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
            compose.onNodeWithTag("nav-apps").performClick()
            assertChoice(false, "Compact", true)
            chooseDensity(false, "Comfortable")
            compose.openNetwork()
            assertChoice(true, "Compact", true)
        }
        assertTrue(prefs.getBoolean("firewall_compact_layout", false))
        assertFalse(prefs.getBoolean("packages_compact_layout", true))
    }

    @Test fun compactRowsRetainDetailsControlsAndSelectionWhenChangingDensity() {
        compose.activityRule.scenario.onActivity { it.deliverNetworkIntent(Intent(context, MainActivity::class.java)
            .putExtra("dev.bgcheck.network.PACKAGE", fixture)) }
        compose.awaitNetworkNode("network-sheet")
        compose.closeNetworkAppSheet()
        verifyRowDensityAndSelection(true)
        scrollToRow(true)
        compose.onNodeWithTag(rowTag(true)).performClick()
        compose.awaitNetworkNode("network-sheet")
        compose.onNode(hasTestTag("internet-advice")).assertExists()
        compose.onNode(hasText("Internet advice") and hasAnyAncestor(hasTestTag("network-sheet")))
            .performScrollTo().performClick()
        compose.onNodeWithText("not the current firewall status", substring = true).performScrollTo().assertIsDisplayed()
        compose.closeNetworkAppSheet()
        compose.onNodeWithTag("nav-apps").performClick()
        compose.onNodeWithTag("apps-search").performTextReplacement(fixture)
        compose.onNodeWithTag("apps-search").performImeAction()
        compose.awaitNetworkNode(rowTag(false))
        verifyRowDensityAndSelection(false)
        scrollToRow(false)
        compose.onNodeWithTag(rowTag(false)).performClick()
        compose.onNodeWithTag("app-detail-network").performScrollTo().assertExists()
        compose.onNodeWithTag("app-detail-package").performScrollTo().assertExists()
    }

    private fun rowTag(network: Boolean) = "${if (network) "network" else "app"}-row-$user:$fixture"
    private fun scrollToRow(network: Boolean) {
        compose.onNodeWithTag(if (network) "network-list" else "apps-list").performScrollToNode(hasTestTag(rowTag(network)))
    }

    private fun verifyRowDensityAndSelection(network: Boolean) {
        val tag = rowTag(network)
        scrollToRow(network)
        val comfortableHeight = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.height
        if (network) compose.onNode(hasText(fixture) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertExists()
        chooseDensity(network, "Compact")
        scrollToRow(network)
        assertTrue("Compact rows use less vertical space", compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.height < comfortableHeight)
        if (network) {
            compose.onNode(hasText(fixture) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertDoesNotExist()
            compose.onNode(hasText("Internet advice:", substring = true) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertDoesNotExist()
            val controls = compose.onAllNodes(isToggleable() and hasAnyAncestor(hasTestTag(tag))).fetchSemanticsNodes()
            assertTrue("Compact rows retain quick network controls", controls.isNotEmpty())
            controls.forEach { node ->
                val minimum = 48 * context.resources.displayMetrics.density
                assertTrue(node.boundsInRoot.height >= minimum && node.boundsInRoot.width >= minimum)
            }
        } else {
            compose.onNode(hasText("User ·", substring = true) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertExists()
        }
        val title = context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(fixture, 0)).toString()
        compose.onNode(hasText(title) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertCompleteText(title)
        compose.onNodeWithTag(tag).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick)
        listOf("Comfortable", "Compact").forEach { density ->
            chooseDensity(network, density)
            scrollToRow(network)
            if (network) {
                compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag(tag))).assertIsOn()
                if (density == "Comfortable") {
                    compose.onNode(hasText(fixture) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertExists()
                    compose.onNode(hasText("Internet advice:", substring = true) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertExists()
                }
            } else compose.onNodeWithTag(tag).assertIsOn()
        }
        androidx.test.espresso.Espresso.pressBack()
    }

    private fun assertDensityChoices(network: Boolean) {
        listOf("Comfortable", "Compact").forEach { label ->
            openFilters(network)
            val tag = densityTag(network, label)
            scrollToDensity(network, label)
            compose.onNodeWithTag(tag).assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            compose.assertCompleteText(label)
        }
    }

    private fun openFilters(network: Boolean) = compose.openTaggedFilters(
        if (network) "network-list" else "apps-list", if (network) "network-filters" else "apps-filters")
    private fun scrollToDensity(network: Boolean, label: String) {
        val tag = densityTag(network, label)
        compose.scrollListToNode(if (network) "network-list" else "apps-list", hasTestTag(tag))
    }
    private fun densityTag(network: Boolean, label: String) = if (network) "network-filter-${label.lowercase()}" else "apps-density-$label"
    private fun chooseDensity(network: Boolean, label: String) {
        openFilters(network)
        scrollToDensity(network, label)
        compose.onNodeWithTag(densityTag(network, label)).performClick()
        compose.onNodeWithTag(densityTag(network, label)).assertIsSelected()
    }
    private fun assertChoice(network: Boolean, label: String, selected: Boolean) {
        openFilters(network)
        scrollToDensity(network, label)
        compose.onNodeWithTag(densityTag(network, label)).assertIsDisplayed()
        if (selected) compose.onNodeWithTag(densityTag(network, label)).assertIsSelected()
        else compose.onNodeWithTag(densityTag(network, label)).assertIsNotSelected()
    }
}
