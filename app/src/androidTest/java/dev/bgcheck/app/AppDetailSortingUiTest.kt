package dev.bgcheck.app

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bgcheck.app.data.*
import dev.bgcheck.app.ui.AppDetailSheet
import dev.bgcheck.app.ui.BackgroundCheckTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDetailSortingUiTest {
    @get:Rule val compose = createComposeRule()
    private val app = AppEntry("dev.bgcheck.fixture", "Fixture app", 10421, false)
    private val processes = listOf(
        ProcessResource(10, 1, app.uid, "u0_a421", "alpha-worker", "/app/alpha", "S", 5.0, 8192, 65536),
        ProcessResource(30, 1, app.uid, "u0_a421", "charlie-worker", "/app/charlie", "R", 50.0, 4096, 16384),
        ProcessResource(20, 1, app.uid, "u0_a421", "bravo-worker", "/app/bravo", "S", null, null, null),
    )
    private val alpha = RunningService("${app.packageName}/.AlphaService", "zebra-process", false)
    private val bravo = RunningService("${app.packageName}/.BravoService", null, false)
    private val charlie = RunningService("${app.packageName}/.CharlieService", "alpha-process", true)

    private fun state() = UiState(access = AccessState.READY, loading = false, apps = listOf(app), selected = app,
        resources = ResourceSnapshot(processes = processes, processesKnown = true),
        diagnostics = AppDiagnostics(app.packageName, services = listOf(charlie, alpha, bravo), servicesKnown = true))

    private fun show(value: MutableState<UiState> = mutableStateOf(state())) {
        compose.setContent {
            BackgroundCheckTheme {
                AppDetailSheet(state = value.value, app = requireNotNull(value.value.selected),
                    onDismiss = {}, onAction = {}, onOpenAppSettings = {}, onConnect = {}, onForceStop = {})
            }
        }
    }

    private fun choose(id: String, label: String) {
        compose.onNodeWithTag("$id-sort").performScrollTo().performClick()
        compose.onNode(hasText(label) and hasAnyAncestor(isPopup())).performClick()
    }

    private fun reverse(id: String) = compose.onNodeWithTag("$id-direction").performScrollTo().performClick()

    private fun rowOrder(prefix: String): List<String> = compose.onAllNodes(
        SemanticsMatcher("Rows with tag prefix $prefix") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
        }, useUnmergedTree = true,
    ).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag].removePrefix(prefix) }

    private fun assertProcesses(vararg pids: Int) = assertEquals(pids.map(Int::toString), rowOrder("app-process-row-"))
    private fun assertServices(vararg services: RunningService) = assertEquals(
        services.map { "${it.component}:${it.process.orEmpty()}" }, rowOrder("service-row-"))

    @Test fun processMetricsSortBothWaysWithUnavailableReadingsLast() {
        show()
        assertProcesses(10, 20, 30)
        choose("app-process", "CPU")
        assertProcesses(30, 10, 20)
        reverse("app-process")
        assertProcesses(10, 30, 20)
        choose("app-process", "Resident memory")
        assertProcesses(10, 30, 20)
        reverse("app-process")
        assertProcesses(30, 10, 20)
        choose("app-process", "Virtual memory")
        assertProcesses(10, 30, 20)
        reverse("app-process")
        assertProcesses(30, 10, 20)
        compose.onAllNodesWithText("Virtual memory").assertCountEquals(3)
        choose("app-process", "Name")
        assertProcesses(10, 20, 30)
        reverse("app-process")
        assertProcesses(30, 20, 10)
        choose("app-process", "PID")
        assertProcesses(10, 20, 30)
        reverse("app-process")
        assertProcesses(30, 20, 10)
        choose("app-process", "State")
        assertProcesses(30, 10, 20)
        reverse("app-process")
        assertProcesses(10, 20, 30)
    }

    @Test fun servicesSortByProcessAndForegroundWithIndependentProcessControls() {
        show()
        choose("app-process", "CPU")
        assertProcesses(30, 10, 20)
        assertServices(alpha, bravo, charlie)
        reverse("services")
        assertServices(charlie, bravo, alpha)
        reverse("services")
        assertServices(alpha, bravo, charlie)
        choose("services", "Process name")
        assertServices(charlie, alpha, bravo)
        reverse("services")
        assertServices(alpha, charlie, bravo)
        assertProcesses(30, 10, 20)
        choose("services", "Foreground status")
        assertServices(charlie, alpha, bravo)
        reverse("services")
        assertServices(alpha, bravo, charlie)
        reverse("app-process")
        assertProcesses(10, 30, 20)
        assertServices(alpha, bravo, charlie)
    }

    @Test fun choicesSurviveUpdatesAndResetForAnotherPackage() {
        val value = mutableStateOf(state())
        show(value)
        choose("app-process", "CPU")
        choose("services", "Process name")
        compose.runOnIdle { value.value = value.value.copy(diagnosticsLoading = true) }
        assertProcesses(30, 10, 20)
        assertServices(charlie, alpha, bravo)
        val other = app.copy(packageName = "dev.bgcheck.other", label = "Other app")
        compose.runOnIdle {
            value.value = value.value.copy(selected = other, apps = listOf(other), diagnosticsLoading = false,
                diagnostics = value.value.diagnostics!!.copy(packageName = other.packageName))
        }
        assertProcesses(10, 20, 30)
        assertServices(alpha, bravo, charlie)
        compose.onNodeWithTag("app-process-sort").assertTextContains("Sort by: PID")
        compose.onNodeWithTag("services-sort").assertTextContains("Sort by: Component name")
    }
}
