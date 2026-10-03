package dev.bgcheck.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bgcheck.app.data.*
import dev.bgcheck.app.ui.BackgroundCheckApp
import dev.bgcheck.app.ui.BackgroundCheckTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SortRestorationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun detailSortChoicesSurviveDismissalAndStaySeparateForEachApp() {
        val first = AppEntry("dev.bgcheck.fixture", "First app", 10421, false)
        val second = AppEntry("dev.bgcheck.other", "Second app", 10422, false)
        fun diagnostics(app: AppEntry) = AppDiagnostics(app.packageName, servicesKnown = true,
            services = listOf(RunningService("${app.packageName}/.ExampleService", "worker", false)))
        val value = mutableStateOf(UiState(
            access = AccessState.READY, loading = false, selected = first, apps = listOf(first, second),
            resources = ResourceSnapshot(processesKnown = true, processes = listOf(
                ProcessResource(10, 1, first.uid, "u0_a421", "first-worker", "first --worker", "S", 10.0, 1024, 4096),
                ProcessResource(20, 1, second.uid, "u0_a422", "second-worker", "second --worker", "S", 20.0, 2048, 8192),
            )), diagnostics = diagnostics(first),
        ))
        compose.setContent {
            BackgroundCheckTheme {
                BackgroundCheckApp(
                    state = value.value, onRefresh = {}, onConnect = {}, onSelect = {},
                    onDismissDetail = { value.value = value.value.copy(selected = null) }, onAction = {},
                    onUndo = {}, onOpenAppSettings = {}, onOpenShizuku = {}, onOpenWirelessSettings = {},
                    onClearMessage = {}, onRefreshResources = {}, onRefreshEnergy = {}, onSetMonitorPaused = {},
                    onSetMonitorInterval = {}, onMonitorVisible = {}, onForceStop = {},
                )
            }
        }

        choose("app-process", "CPU")
        compose.onNodeWithTag("app-process-direction").performScrollTo().performClick()
        choose("services", "Process name")
        compose.onNodeWithTag("services-direction").performScrollTo().performClick()
        closeDetails()
        compose.runOnIdle { value.value = value.value.copy(selected = first, diagnostics = diagnostics(first)) }
        assertSort("app-process", "CPU", "Ascending")
        assertSort("services", "Process name", "Descending")

        closeDetails()
        compose.runOnIdle { value.value = value.value.copy(selected = second, diagnostics = diagnostics(second)) }
        assertSort("app-process", "PID", "Ascending")
        assertSort("services", "Component name", "Ascending")
        choose("app-process", "Name")
        closeDetails()

        compose.runOnIdle { value.value = value.value.copy(selected = first, diagnostics = diagnostics(first)) }
        assertSort("app-process", "CPU", "Ascending")
        assertSort("services", "Process name", "Descending")
    }

    private fun choose(id: String, label: String) {
        compose.onNodeWithTag("$id-sort").performScrollTo().assertIsDisplayed().performClick()
        compose.onNode(hasText(label) and hasAnyAncestor(isPopup())).assertIsDisplayed().performClick()
    }

    private fun assertSort(id: String, key: String, direction: String) {
        compose.onNodeWithTag("$id-sort").performScrollTo().assertTextContains("Sort by: $key")
        compose.onNodeWithTag("$id-direction").performScrollTo().assertTextContains(direction)
    }

    private fun closeDetails() {
        compose.onNodeWithContentDescription("Close app details").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Close app details").assertDoesNotExist()
    }
}
