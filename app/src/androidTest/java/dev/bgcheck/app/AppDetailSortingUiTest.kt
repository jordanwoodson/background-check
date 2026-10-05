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

    private fun show(value: MutableState<UiState> = mutableStateOf(state()), expandDiagnostics: Boolean = true, onRetry: () -> Unit = {}) {
        compose.setContent {
            BackgroundCheckTheme {
                AppDetailSheet(state = value.value, app = requireNotNull(value.value.selected),
                    onDismiss = {}, onAction = {}, onOpenAppSettings = {}, onConnect = {}, onForceStop = {}, onRetry = onRetry)
            }
        }
        if (expandDiagnostics) openDiagnostics()
    }

    private fun openDiagnostics() = compose.onNodeWithText("Resource diagnostics").performScrollTo().performClick()

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

    @Test fun diagnosticsStartCollapsedAndRetainTheirControlsWhenOpened() {
        show(expandDiagnostics = false)
        compose.onNodeWithText("Background access").assertExists()
        compose.onNodeWithText("Force stop app").assertExists()
        compose.onNodeWithTag("app-process-sort").assertDoesNotExist()
        compose.onNodeWithTag("services-sort").assertDoesNotExist()
        openDiagnostics()
        assertProcesses(10, 20, 30)
        assertServices(alpha, bravo, charlie)
    }

    @Test fun missingVerifiedPolicyOffersRetryWithoutSuggestingDisconnectedAccess() {
        var retries = 0
        show(expandDiagnostics = false, onRetry = { retries++ })
        compose.onNodeWithText("Settings unavailable").assertExists()
        compose.onNodeWithText("Retry reading settings").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        openDiagnostics()
        compose.onNodeWithText("Process snapshot unavailable. Refresh to try again.").assertExists()
        compose.onNodeWithText("Connect Shizuku for a process snapshot. Installed apps can still be browsed.").assertDoesNotExist()
    }

    @Test fun accessGuidanceMatchesTheConnectionStage() {
        val value = mutableStateOf(state().copy(access = AccessState.NOT_RUNNING))
        show(value, expandDiagnostics = false)
        compose.onNodeWithText("Start Shizuku for app controls").assertExists()
        compose.onNodeWithText("Set up access").assertIsEnabled()
        compose.runOnIdle { value.value = value.value.copy(access = AccessState.NEEDS_PERMISSION) }
        compose.onNodeWithText("Permission needed").assertExists()
        compose.onNode(hasText("Review access") and !hasAnyAncestor(hasTestTag("app-detail-package")))
            .assertIsEnabled()
        compose.onNode(hasText("Review access") and hasAnyAncestor(hasTestTag("app-detail-package")))
            .assertIsEnabled()
        compose.onNodeWithText("Set up access").assertDoesNotExist()
        compose.runOnIdle { value.value = value.value.copy(access = AccessState.CONNECTING) }
        compose.onNodeWithText("Connecting to Shizuku").assertExists()
        compose.onNodeWithText("Please wait…").assertIsNotEnabled()
    }

    @Test fun initialDiagnosticLoadingDoesNotClaimReadingsAreUnavailable() {
        show(mutableStateOf(state().copy(diagnostics = null, diagnosticsLoading = true)))
        compose.onNodeWithText("Reading detailed memory and running services…").assertExists()
        compose.onNodeWithText("PSS").assertDoesNotExist()
        compose.onNodeWithText("Running services unavailable.").assertDoesNotExist()
    }

    @Test fun closeAndOperationFeedbackStayVisibleWhileDetailsScroll() {
        val result = "The background setting could not be verified. Android returned an incomplete response while reading the app’s current policy. " +
            "Review the settings and retry before making another change. The previous policy remains available in Your changes if recovery is needed."
        val value = mutableStateOf(state().copy(busy = true, message = "Reading the previous policy before applying this change."))
        show(value, expandDiagnostics = false)

        compose.onNodeWithText("Stop this app").performScrollTo()
        compose.onNodeWithContentDescription("Close app details").assertIsDisplayed()
        compose.onNodeWithText("Applying and checking your change…").assertIsDisplayed()

        compose.runOnIdle { value.value = value.value.copy(busy = false, message = result) }
        val preview = compose.onNode(hasText(result) and !hasAnyAncestor(hasScrollAction()))
        val fullMessage = compose.onNode(hasText(result) and hasAnyAncestor(hasScrollAction()))
        preview.assertIsDisplayed()
        val previewTop = preview.fetchSemanticsNode().boundsInRoot.top
        fullMessage.performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Close app details").assertIsDisplayed()
        preview.assertIsDisplayed()
        assertEquals(previewTop, preview.fetchSemanticsNode().boundsInRoot.top, 0.5f)

        compose.runOnIdle { value.value = value.value.copy(message = null) }
        preview.assertIsDisplayed()
        fullMessage.assertIsDisplayed()
        compose.onNodeWithContentDescription("Dismiss update").assertIsDisplayed().performClick()
        preview.assertDoesNotExist()
        fullMessage.assertDoesNotExist()
        compose.onNodeWithContentDescription("Dismiss update").assertDoesNotExist()

        compose.runOnIdle { value.value = value.value.copy(message = "Settings refreshed and verified.") }
        compose.onNode(hasText("Settings refreshed and verified.") and !hasAnyAncestor(hasScrollAction())).assertIsDisplayed()
    }

    @Test fun retryClearsPreviousFeedbackAndSuccessfulReadDoesNotRestoreIt() {
        val error = "Couldn’t read this app’s policy. Try again."
        val value = mutableStateOf(state().copy(message = error))
        show(value, expandDiagnostics = false, onRetry = {
            value.value = value.value.copy(message = null, detailLoading = true)
        })
        compose.onNode(hasText(error) and !hasAnyAncestor(hasScrollAction())).assertIsDisplayed()
        compose.onNodeWithText("Retry reading settings").performScrollTo().performClick()
        compose.onAllNodesWithText(error).assertCountEquals(0)
        compose.onNodeWithText("Reading current settings…").assertIsDisplayed()

        compose.runOnIdle {
            value.value = value.value.copy(detailLoading = false,
                policy = PolicyState(app.packageName, backgroundMode = "allow", legacyMode = "allow", dozeExempt = false, userExempt = false))
        }
        compose.onAllNodesWithText(error).assertCountEquals(0)
        compose.onNodeWithContentDescription("Dismiss update").assertDoesNotExist()
        compose.onNodeWithText("Retry reading settings").assertDoesNotExist()
    }

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
        compose.onNodeWithText("Refreshing diagnostics; the previous sample remains below.").assertExists()
        assertProcesses(30, 10, 20)
        assertServices(charlie, alpha, bravo)
        val other = app.copy(packageName = "dev.bgcheck.other", label = "Other app")
        compose.runOnIdle {
            value.value = value.value.copy(selected = other, apps = listOf(other), diagnosticsLoading = false,
                diagnostics = value.value.diagnostics!!.copy(packageName = other.packageName))
        }
        openDiagnostics()
        assertProcesses(10, 20, 30)
        assertServices(alpha, bravo, charlie)
        compose.onNodeWithTag("app-process-sort").assertTextContains("Sort by: PID")
        compose.onNodeWithTag("services-sort").assertTextContains("Sort by: Component name")
    }
}
