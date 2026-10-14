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
class PackageDescriptionsUiTest {
    @get:Rule val compose = createComposeRule()
    private val terminal = AppEntry("com.termux", "Termux", 10123, false)
    private val system = AppEntry("com.android.systemui", "System UI", 1000, true)
    private val removed = AppEntry("com.android.inputmethod.latin", "Removed keyboard", 10124, true, installed = false)

    private fun show(access: AccessState = AccessState.READY) {
        val state = mutableStateOf(UiState(access = access, loading = false,
            apps = listOf(terminal, system), uninstalledApps = listOf(removed)))
        compose.setContent {
            BackgroundCheckTheme {
                BackgroundCheckApp(state.value,
                    onRefresh = {}, onConnect = {}, onSelect = { state.value = state.value.copy(selected = it) },
                    onDismissDetail = { state.value = state.value.copy(selected = null) }, onAction = {}, onUndo = {},
                    onOpenAppSettings = {}, onOpenShizuku = {}, onOpenWirelessSettings = {}, onClearMessage = {},
                    onRefreshResources = {}, onRefreshEnergy = {}, onSetMonitorPaused = {}, onSetMonitorInterval = {},
                    onMonitorVisible = {}, onForceStop = {})
            }
        }
        compose.onNodeWithText("Apps").performClick()
    }

    private fun filter(label: String) {
        compose.openAppFilters()
        compose.onNodeWithTag("apps-list").performScrollToNode(hasText(label) and isSelectable())
        compose.onNode(hasText(label) and isSelectable()).performScrollTo().performClick()
    }

    @Test fun descriptionsAreVisibleAndSearchableWithoutShizuku() {
        show(AccessState.NOT_RUNNING)
        compose.onNodeWithTag("apps-list").performScrollToNode(hasText("Termux"))
        compose.onNodeWithText("Runs a terminal and Linux command-line tools on Android.").assertIsDisplayed()
        filter("System")
        compose.onNodeWithTag("apps-list").performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("notifications")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithTag("apps-list").performScrollToNode(hasText("System UI"))
        compose.onNodeWithText("Displays notifications, Quick Settings, the status bar and system navigation.").assertIsDisplayed()
        compose.onNodeWithText("Termux").assertDoesNotExist()
    }

    @Test fun uninstalledFilterIgnoresActiveProcessFilterAndDetailsHaveNoControls() {
        show()
        filter("Active")
        filter("Uninstalled")
        compose.onNodeWithTag("apps-list").performScrollToNode(hasText("Removed keyboard"))
        compose.onNodeWithText("Provides an on-screen keyboard for entering text.").assertIsDisplayed()
        compose.onNodeWithText("Removed keyboard").performClick()
        compose.onNodeWithText("What this package does").assertIsDisplayed()
        compose.onNodeWithText("Built-in package guide").assertIsDisplayed()
        compose.onNodeWithText("Uninstalled for this user").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Allow background work").assertDoesNotExist()
        compose.onNodeWithText("Force stop app").assertDoesNotExist()
        compose.onNode(hasText("Reinstall") and hasAnyAncestor(hasTestTag("app-detail-package")))
            .performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Open Android app settings").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("See in Network").assertDoesNotExist()
        compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag("app-detail-network"))).assertDoesNotExist()
        compose.onNodeWithText("Resource diagnostics").assertDoesNotExist()
    }

    @Test fun associationsShowRetainedStateAndOpenTheirOwnDetailsWithoutShizuku() {
        val overlay = AppEntry("com.android.cellbroadcast.overlay", "Alerts overlay", 11001, true)
        val alerts = AppEntry("com.google.android.cellbroadcastreceiver", "Wireless emergency alerts", 11002, true, installed = false)
        val state = mutableStateOf(UiState(access = AccessState.NOT_RUNNING, loading = false,
            apps = listOf(overlay), uninstalledApps = listOf(alerts), selected = overlay))
        compose.setContent {
            BackgroundCheckTheme {
                BackgroundCheckApp(state.value,
                    onRefresh = {}, onConnect = {}, onSelect = { state.value = state.value.copy(selected = it) },
                    onDismissDetail = { state.value = state.value.copy(selected = null) }, onAction = {}, onUndo = {},
                    onOpenAppSettings = {}, onOpenShizuku = {}, onOpenWirelessSettings = {}, onClearMessage = {},
                    onRefreshResources = {}, onRefreshEnergy = {}, onSetMonitorPaused = {}, onSetMonitorInterval = {},
                    onMonitorVisible = {}, onForceStop = {})
            }
        }
        compose.onNodeWithText("Description sources (1)").performScrollTo().performClick()
        compose.onNodeWithText("Android runtime resource overlays (mechanism)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("association-OVERLAY_TARGET-com.google.android.cellbroadcastreceiver").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Wireless emergency alerts", substring = false).assertExists()
        compose.onNodeWithText("Displays emergency alerts broadcast by mobile networks.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Force stop app").assertDoesNotExist()
        compose.onNodeWithText("Allow background work").assertDoesNotExist()
    }
}
