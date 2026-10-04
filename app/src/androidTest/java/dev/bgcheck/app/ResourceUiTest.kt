package dev.bgcheck.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bgcheck.app.data.*
import dev.bgcheck.app.ui.BackgroundCheckApp
import dev.bgcheck.app.ui.BackgroundCheckTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResourceUiTest {
    @get:Rule val compose = createComposeRule()

    private val app = AppEntry("dev.bgcheck.fixture", "Fixture app", 10421, false)
    private val other = AppEntry("dev.bgcheck.other", "Other app", 10422, false)
    private val processes = listOf(
        ProcessResource(734, 1, app.uid, "u0_a421", "fixture:worker", "/app/fixture --worker", "S", 18.5, 8192, 65536),
        ProcessResource(422, 1, other.uid, "u0_a422", "other:worker", "/app/other", "R", null, null, null),
        ProcessResource(10, 2, 0, "root", "native-service", "/system/bin/native-service", "S", 1.0, 1024, 4096),
    )

    private fun show(state: UiState, onStop: () -> Unit = {}, onSelect: (AppEntry) -> Unit = {}) {
        compose.setContent {
            BackgroundCheckTheme {
                BackgroundCheckApp(
                    state = state, onRefresh = {}, onConnect = {}, onSelect = onSelect,
                    onDismissDetail = {}, onAction = {}, onUndo = {}, onOpenAppSettings = {},
                    onOpenShizuku = {}, onOpenWirelessSettings = {}, onClearMessage = {},
                    onRefreshResources = {}, onRefreshEnergy = {}, onSetMonitorPaused = {},
                    onSetMonitorInterval = {}, onMonitorVisible = {}, onForceStop = onStop,
                )
            }
        }
    }

    private fun state() = UiState(access = AccessState.READY, loading = false, apps = listOf(app, other),
        resources = ResourceSnapshot(processes = processes, processesKnown = true))

    @Test fun searchByPidFindsProcessAndOpensResponsibleApp() {
        var selected: AppEntry? = null
        show(state(), onSelect = { selected = it })
        compose.onNodeWithText("Monitor").performClick()
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("734")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasText("fixture:worker"))
        compose.onNodeWithText("other:worker").assertDoesNotExist()
        compose.onNodeWithText("fixture:worker").performClick()
        compose.onNodeWithText("/app/fixture --worker").assertExists()
        compose.onNodeWithText("Open Fixture app").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(app, selected) }
    }

    @Test fun systemFilterKeepsNativeProcessesAndExcludesInstalledApps() {
        show(state())
        compose.onNodeWithText("Monitor").performClick()
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasText("System / native"))
        compose.onNodeWithText("System / native").performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasText("native-service"))
        compose.onNodeWithText("native-service").assertIsDisplayed()
        compose.onNodeWithText("fixture:worker").assertDoesNotExist()
    }

    @Test fun pidSortOrdersProcessesNumerically() {
        show(state())
        compose.onNodeWithText("Monitor").performClick()
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasTestTag("process-sort"))
        compose.onNodeWithTag("process-sort").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("PID").assertIsDisplayed().performClick()
        compose.onNodeWithTag("process-sort").assertTextContains("PID", substring = true)
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasText("fixture:worker"))
        compose.onNodeWithText("fixture:worker").performScrollTo()
        val first = compose.onNodeWithText("native-service").fetchSemanticsNode().boundsInRoot.top
        val second = compose.onNodeWithText("other:worker").fetchSemanticsNode().boundsInRoot.top
        val third = compose.onNodeWithText("fixture:worker").fetchSemanticsNode().boundsInRoot.top
        assertTrue("PIDs 10, 422 and 734 must appear in numeric order", first < second && second < third)
    }

    @Test fun forceStopRequiresConfirmationAndCancelDoesNotRunIt() {
        var calls = 0
        show(state().copy(selected = app), onStop = { calls++ })
        compose.onNodeWithText("Force stop app").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithText("Force stop app").performScrollTo().performClick()
        compose.onNodeWithText("Force stop now").performClick()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun sharedUidAppCannotBeForceStopped() {
        val sibling = other.copy(uid = app.uid)
        show(state().copy(apps = listOf(app, sibling), selected = app))
        compose.onNodeWithText("Force stop app").performScrollTo().assertIsNotEnabled()
    }
}
