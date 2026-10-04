package dev.bgcheck.app

import androidx.compose.ui.semantics.SemanticsActions

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

    private fun show(
        sharedUid: Boolean = false,
        access: AccessState = AccessState.READY,
        resources: ResourceSnapshot? = ResourceSnapshot(processes = processes, processesKnown = true),
        energySnapshot: EnergySnapshot? = energy,
        resourceLoading: Boolean = false,
        monitoring: Boolean = false,
    ) {
        val apps = if (sharedUid) listOf(alpha, beta.copy(uid = alpha.uid)) else listOf(alpha, beta)
        compose.setContent {
            BackgroundCheckTheme {
                BackgroundCheckApp(
                    state = UiState(access = access, loading = false, apps = apps,
                        resources = resources, energy = energySnapshot, resourceLoading = resourceLoading, monitoring = monitoring),
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
        compose.onNodeWithText(label).assertIsDisplayed().performClick()
    }

    private fun assertOrder(first: String, second: String, inSheet: Boolean = false) {
        if (!inSheet) compose.onNodeWithTag("monitor-list").performScrollToNode(hasText(second))
        compose.onNodeWithText(second).performScrollTo()
        if (!inSheet) {
            val list = compose.onNodeWithTag("monitor-list")
            val lastBounds = compose.onNodeWithText(second).fetchSemanticsNode().boundsInRoot
            val viewport = list.fetchSemanticsNode().boundsInRoot
            // Keep the last label near the viewport bottom so preceding rows fit.
            list.performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
                scroll(0f, lastBounds.bottom - viewport.bottom + 16f)
            }
        }
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
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasText("Device components"))
        compose.onNodeWithText("Device components").performClick()
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
    @Test fun modesPreserveIndependentSearchAndScroll() {
        show()
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("worker")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasText("Beta worker"))
        val livePosition = compose.onNodeWithText("Beta worker").performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top

        mode("Battery use")
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("Alpha")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasText("Alpha app"))
        val batteryPosition = compose.onNodeWithText("Alpha app").performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top

        mode("Live processes")
        val restoredLivePosition = compose.onNodeWithText("Beta worker").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        assertTrue("Live scroll position must survive mode changes: $livePosition → $restoredLivePosition", kotlin.math.abs(livePosition - restoredLivePosition) < 2f)
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).assertTextContains("worker")
        mode("Live processes")
        compose.onNode(hasSetTextAction()).assertTextContains("worker")

        mode("Battery use")
        val restoredBatteryPosition = compose.onNodeWithText("Alpha app").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        assertTrue("Battery scroll position must survive mode changes: $batteryPosition → $restoredBatteryPosition", kotlin.math.abs(batteryPosition - restoredBatteryPosition) < 2f)
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).assertTextContains("Alpha")
    }

    @Test fun checkingAccessAvoidsSetupAndUnavailableReadingStacks() {
        show(access = AccessState.CHECKING, resources = null, energySnapshot = null)
        compose.onNodeWithText("Checking connection…").assertIsDisplayed()
        compose.onNodeWithText("Set up access").assertDoesNotExist()
        compose.onNodeWithText("Whole device").assertDoesNotExist()
        compose.onNodeWithTag("process-sort").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test fun batteryFlowRemainsAvailableWithoutShizuku() {
        show(access = AccessState.NOT_RUNNING, resources = null, energySnapshot = null)
        mode("Battery use")
        compose.onNodeWithText("Battery flow").assertIsDisplayed()
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasText("Set up access"))
        compose.onNodeWithText("Set up access").assertIsDisplayed()
        compose.onNodeWithText("Android battery estimates").assertDoesNotExist()
        compose.onNodeWithTag("energy-sort").assertDoesNotExist()
    }

    @Test fun initialProcessSampleHidesSearchUntilReadingsExist() {
        show(resources = null, energySnapshot = null, resourceLoading = true)
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasText("Reading processes…"))
        compose.onNodeWithText("Reading processes…").assertIsDisplayed()
        compose.onNodeWithText("Whole device").assertDoesNotExist()
        compose.onNodeWithTag("process-sort").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test fun failedResourceSampleDoesNotClaimLiveReadings() {
        show(resources = ResourceSnapshot(processesKnown = false), monitoring = true)
        compose.onNodeWithText("Readings unavailable").assertIsDisplayed()
        compose.onNodeWithText("Live", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Last sample", substring = true).assertIsDisplayed()
    }


}
