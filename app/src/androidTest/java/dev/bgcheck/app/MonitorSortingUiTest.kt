package dev.bgcheck.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bgcheck.app.data.*
import dev.bgcheck.app.ui.BackgroundCheckApp
import dev.bgcheck.app.ui.BackgroundCheckTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonitorSortingUiTest {
    @get:Rule val compose = createComposeRule()

    private val alpha = AppEntry("dev.bgcheck.zulu", "Alpha app", 10200, false)
    private val beta = AppEntry("dev.bgcheck.alpha", "Beta app", 10300, false)
    private val processes = listOf(
        ProcessResource(12, 1, alpha.uid, "u0_a200", "Alpha worker", "alpha --worker", "S", 10.0, 1024, 4096),
        ProcessResource(3, 1, beta.uid, "u0_a300", "Beta worker", "beta --worker", "R", 30.0, 2048, 8192),
        ProcessResource(2, 1, 0, "root", "Native worker", "/system/bin/native", "S", 20.0, 512, 2048),
        ProcessResource(20, 1, alpha.uid, "u0_a200", "Idle process", "alpha --idle", "S", 0.0, 256, 1024),
    )
    private val energy = EnergySnapshot(
        uids = listOf(UidEnergy(alpha.uid, estimatedMah = 9.0, cpuTimeMs = 1000, networkRxBytes = 8000),
            UidEnergy(beta.uid, estimatedMah = 2.0, cpuTimeMs = 9000, networkRxBytes = 2000)),
        components = listOf(PowerComponent("scrn", 90.0), PowerComponent("cpu", 20.0)),
    )

    private fun show(sharedUid: Boolean = false) {
        val apps = if (sharedUid) listOf(alpha, beta.copy(uid = alpha.uid)) else listOf(alpha, beta)
        compose.setContent {
            BackgroundCheckTheme {
                BackgroundCheckApp(
                    state = UiState(access = AccessState.READY, loading = false, apps = apps,
                        resources = ResourceSnapshot(processes = processes, processesKnown = true), energy = energy),
                    onRefresh = {}, onConnect = {}, onSelect = {}, onDismissDetail = {}, onAction = {},
                    onUndo = {}, onOpenAppSettings = {}, onOpenShizuku = {}, onOpenWirelessSettings = {},
                    onClearMessage = {}, onRefreshResources = {}, onRefreshEnergy = {}, onSetMonitorPaused = {},
                    onSetMonitorInterval = {}, onMonitorVisible = {}, onForceStop = {},
                )
            }
        }
        compose.onNodeWithText("Monitor").performClick()
    }

    private fun chooseSort(id: String, option: String, inSheet: Boolean = false) {
        if (!inSheet) compose.onNodeWithTag("monitor-list").performScrollToNode(hasTestTag("$id-sort"))
        compose.onNodeWithTag("$id-sort").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText(option).assertIsDisplayed().performClick()
    }

    private fun reverse(id: String, inSheet: Boolean = false) {
        if (!inSheet) compose.onNodeWithTag("monitor-list").performScrollToNode(hasTestTag("$id-direction"))
        compose.onNodeWithTag("$id-direction").performScrollTo().assertIsDisplayed().performClick()
    }

    private fun mode(label: String) {
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasText(label))
        compose.onNodeWithText(label).performScrollTo().performClick()
    }

    private fun assertOrder(first: String, second: String, inSheet: Boolean = false) {
        if (!inSheet) compose.onNodeWithTag("monitor-list").performScrollToNode(hasText(first))
        // LazyColumn may reveal only the first row of a containing card. Bring the
        // adjacent second row into view before comparing both rendered positions.
        compose.onNodeWithText(second).performScrollTo()
        val firstTop = compose.onNodeWithText(first).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        val secondTop = compose.onNodeWithText(second).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        assertTrue("Expected '$first' before '$second'", firstTop < secondTop)
    }

    @Test fun processSortAndDirectionKeepSearchAndAppFilter() {
        show()
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("worker")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasText("App processes"))
        compose.onNodeWithText("App processes").performScrollTo().performClick().assertIsSelected()
        chooseSort("process", "Name")
        assertOrder("Alpha worker", "Beta worker")
        reverse("process")
        assertOrder("Beta worker", "Alpha worker")
        compose.onNodeWithText("Native worker").assertDoesNotExist()
        compose.onNodeWithText("Idle process").assertDoesNotExist()
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).assertTextContains("worker")
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasText("App processes"))
        compose.onNodeWithText("App processes").assertIsSelected()
    }

    @Test fun batteryRankingChangesMetricAndKeepsLiveSortIndependent() {
        show()
        chooseSort("process", "PID")
        reverse("process")
        mode("Battery use")
        assertOrder("Alpha app", "Beta app")
        chooseSort("energy", "CPU time")
        assertOrder("Beta app", "Alpha app")
        reverse("energy")
        assertOrder("Alpha app", "Beta app")
        mode("Live processes")
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasTestTag("process-sort"))
        compose.onNodeWithTag("process-sort").assertTextContains("PID", substring = true)
        compose.onNodeWithTag("process-direction").assertTextContains("Descending", substring = true)
        mode("Battery use")
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasTestTag("energy-sort"))
        compose.onNodeWithTag("energy-sort").assertTextContains("CPU time", substring = true)
        compose.onNodeWithTag("energy-direction").assertTextContains("Ascending", substring = true)
    }

    @Test fun componentsCanSortByDisplayedNameAndReverse() {
        show()
        mode("Battery use")
        assertOrder("Screen", "CPU")
        chooseSort("component", "Name")
        assertOrder("CPU", "Screen")
        reverse("component")
        assertOrder("Screen", "CPU")
    }

    @Test fun sharedUidRelatedAppsCanSortByPackageInEitherDirection() {
        show(sharedUid = true)
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasText("Alpha worker"))
        compose.onNodeWithText("Alpha worker").performClick()
        assertOrder("Open Alpha app", "Open Beta app", inSheet = true)
        chooseSort("related-apps", "Package", inSheet = true)
        assertOrder("Open Beta app", "Open Alpha app", inSheet = true)
        reverse("related-apps", inSheet = true)
        assertOrder("Open Alpha app", "Open Beta app", inSheet = true)
        compose.onNodeWithContentDescription("Close Process details").performScrollTo().performClick()
        mode("Battery use")
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasText("Alpha app, Beta app"))
        compose.onNodeWithText("Alpha app, Beta app").performClick()
        assertOrder("Open Alpha app", "Open Beta app", inSheet = true)
        reverse("related-apps", inSheet = true)
        assertOrder("Open Beta app", "Open Alpha app", inSheet = true)
    }
}
