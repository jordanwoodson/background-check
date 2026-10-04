package dev.bgcheck.app

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bgcheck.app.data.AccessState
import dev.bgcheck.app.data.AppEntry
import dev.bgcheck.app.data.ProcessState
import dev.bgcheck.app.data.UiState
import dev.bgcheck.app.ui.BackgroundCheckApp
import dev.bgcheck.app.ui.BackgroundCheckTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessUxTest {
    @get:Rule val compose = createComposeRule()

    @Test fun connectedAccessKeepsSetupOutOfTheWayButAvailableInHelp() {
        show(UiState(access = AccessState.READY, loading = false))
        compose.onNodeWithText("Access").performClick()

        compose.onNodeWithText("You’re connected").assertIsDisplayed()
        compose.onNodeWithText("Grant access").assertDoesNotExist()
        compose.onNodeWithText("Check connection").assertDoesNotExist()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Connection help"))
        compose.onNodeWithText("Pair with wireless debugging").assertDoesNotExist()
        compose.onNodeWithText("Start, then allow access").assertDoesNotExist()

        compose.onNodeWithText("Connection help").performClick()
        listOf(
            "Open Shizuku", "Pair with wireless debugging", "Start, then allow access",
        ).forEach { title ->
            val heading = hasText(title) and !hasClickAction()
            compose.onNode(hasScrollAction()).performScrollToNode(heading)
            compose.onNode(heading).performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun permissionNeededShowsOneGrantActionUntilHelpIsOpened() {
        show(UiState(access = AccessState.NEEDS_PERMISSION, loading = false))
        compose.onNodeWithText("Access").performClick()

        compose.onNodeWithText("Allow Background Check").assertIsDisplayed()
        compose.onAllNodesWithText("Grant access").assertCountEquals(1)
        compose.onNodeWithText("Grant access").assertIsEnabled()
        compose.onNodeWithText("Check connection").assertDoesNotExist()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Connection help"))
        compose.onNodeWithText("Pair with wireless debugging").assertDoesNotExist()
        compose.onNodeWithText("Connection help").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Pair with wireless debugging"))
        compose.onNodeWithText("Pair with wireless debugging").performScrollTo().assertIsDisplayed()
    }

    @Test fun connectingSuccessfullyCollapsesPreviouslyOpenedSetupHelp() {
        val state = show(UiState(access = AccessState.NEEDS_PERMISSION, loading = false))
        compose.onNodeWithText("Access").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Connection help"))
        compose.onNodeWithText("Connection help").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Pair with wireless debugging"))
        compose.onNodeWithText("Pair with wireless debugging").performScrollTo().assertIsDisplayed()

        compose.runOnIdle { state.value = state.value.copy(access = AccessState.READY) }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Connection help"))
        compose.onNodeWithText("Connection help").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"),
        )
        compose.onNodeWithText("Pair with wireless debugging").assertDoesNotExist()
        compose.onNodeWithText("Grant access").assertDoesNotExist()
        compose.onNodeWithText("Check connection").assertDoesNotExist()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("You’re connected"))
        compose.onNodeWithText("You’re connected").assertIsDisplayed()
    }

    @Test fun deniedPermissionExplainsHowToRecoverAndCanOpenShizuku() {
        val message = "Permission was denied. Open Shizuku, choose Authorized apps, and allow Background Check."
        var shizukuOpened = 0
        show(
            UiState(access = AccessState.NEEDS_PERMISSION, accessMessage = message, loading = false),
            onOpenShizuku = { shizukuOpened++ },
        )
        compose.onNodeWithText("Access").performClick()
        compose.onNodeWithText(message).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Open Shizuku").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, shizukuOpened) }
    }

    @Test fun overviewWaitsForAccessChecksBeforeOfferingSetup() {
        val state = show(UiState(access = AccessState.CHECKING, loading = false))

        compose.onNodeWithText("Checking…").assertIsDisplayed()
        compose.onNodeWithText("Set up access").assertDoesNotExist()
        compose.onNodeWithText("Connect to Shizuku").assertDoesNotExist()

        compose.runOnIdle { state.value = state.value.copy(access = AccessState.CONNECTING) }
        compose.onNodeWithText("Connecting…").assertIsDisplayed()
        compose.onNodeWithText("Set up access").assertDoesNotExist()
        compose.onNodeWithText("Connect to Shizuku").assertDoesNotExist()

        compose.runOnIdle { state.value = state.value.copy(access = AccessState.READY) }
        compose.onNodeWithText("Connected").assertIsDisplayed()
        compose.onNodeWithText("Set up access").assertDoesNotExist()
    }

    @Test fun processFilterResumesAfterConnectionReturns() {
        val state = show(UiState(
            access = AccessState.READY, loading = false,
            apps = listOf(
                app("Active fixture", 10421, ProcessState.FOREGROUND),
                app("Cached fixture", 10422, ProcessState.CACHED),
            ),
        ))
        compose.onNodeWithText("Apps").performClick()
        compose.openAppFilters()
        compose.onNodeWithText("Active").performScrollTo().performClick()
        scrollAppsTo("Active fixture")
        compose.onNodeWithText("Active fixture").assertIsDisplayed()
        compose.onNodeWithText("Cached fixture").assertDoesNotExist()

        compose.runOnIdle { state.value = state.value.copy(access = AccessState.NOT_RUNNING) }
        scrollAppsTo("Cached fixture")
        compose.onNodeWithText("Cached fixture").assertIsDisplayed()

        compose.runOnIdle { state.value = state.value.copy(access = AccessState.READY) }
        scrollAppsTo("Active")
        compose.onNodeWithText("Active").assertIsSelected()
        scrollAppsTo("Active fixture")
        compose.onNodeWithText("Active fixture").assertIsDisplayed()
        compose.onNodeWithText("Cached fixture").assertDoesNotExist()
    }

    @Test fun searchIgnoresSurroundingWhitespaceAndKeepsItsLabel() {
        show(UiState(
            access = AccessState.READY, loading = false,
            apps = listOf(
                app("Needle app", 10421, ProcessState.FOREGROUND),
                app("Other app", 10422, ProcessState.FOREGROUND),
            ),
        ))
        compose.onNodeWithText("Apps").performClick()
        compose.onNodeWithTag("apps-list").performScrollToNode(hasSetTextAction())
        compose.onNodeWithText("Search apps", useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("  nEeDlE  ")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithText("Search apps", useUnmergedTree = true).assertIsDisplayed()

        scrollAppsTo("Needle app")
        compose.onNodeWithText("Needle app").assertIsDisplayed()
        compose.onNodeWithText("Other app").assertDoesNotExist()
    }

    @Test fun emptySearchCanResetTheQueryAndFiltersToBrowseAppsAgain() {
        show(UiState(
            access = AccessState.READY, loading = false,
            apps = listOf(
                app("Personal cached app", 10421, ProcessState.CACHED),
                app("System cached app", 10422, ProcessState.CACHED, system = true),
            ),
        ))
        compose.onNodeWithText("Apps").performClick()
        compose.onNodeWithTag("apps-list").performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("missing app")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.openAppFilters()
        compose.onNodeWithText("System").performScrollTo().performClick()
        compose.openAppFilters()
        compose.onNodeWithText("Active").performScrollTo().performClick()

        scrollAppsTo("No matching apps")
        compose.onNodeWithText("No matching apps").assertIsDisplayed()
        scrollAppsTo("Reset filters")
        compose.onNodeWithText("Reset filters").performClick()

        scrollAppsTo("Personal cached app")
        compose.onNodeWithText("Personal cached app").assertIsDisplayed()
        scrollAppsTo("Any state")
        compose.onNodeWithText("Any state").assertIsSelected()
        compose.onNodeWithTag("apps-list").performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")),
        )
        compose.onNodeWithContentDescription("Clear search").assertDoesNotExist()
        compose.onNodeWithText("Search apps", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun scrollAppsTo(text: String) {
        compose.onNodeWithTag("apps-list").performScrollToNode(hasText(text))
    }

    private fun app(name: String, uid: Int, process: ProcessState, system: Boolean = false) = AppEntry(
        packageName = "dev.bgcheck.fixture$uid", label = name, uid = uid,
        system = system, processState = process,
    )

    private fun show(initial: UiState, onOpenShizuku: () -> Unit = {}): MutableState<UiState> {
        val state = mutableStateOf(initial)
        compose.setContent {
            BackgroundCheckTheme {
                BackgroundCheckApp(
                    state = state.value,
                    onRefresh = {}, onConnect = {}, onSelect = {}, onDismissDetail = {}, onAction = {},
                    onUndo = {}, onOpenAppSettings = {}, onOpenShizuku = onOpenShizuku, onOpenWirelessSettings = {},
                    onClearMessage = {}, onRefreshResources = {}, onRefreshEnergy = {}, onSetMonitorPaused = {},
                    onSetMonitorInterval = {}, onMonitorVisible = {}, onForceStop = {},
                )
            }
        }
        return state
    }
}
