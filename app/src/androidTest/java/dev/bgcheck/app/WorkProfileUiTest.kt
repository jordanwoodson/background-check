package dev.bgcheck.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bgcheck.app.data.*
import dev.bgcheck.app.ui.AppDetailSheet
import dev.bgcheck.app.ui.AssociatedPackages
import dev.bgcheck.app.ui.BackgroundCheckApp
import dev.bgcheck.app.ui.BackgroundCheckTheme
import dev.bgcheck.app.ui.MonitorScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkProfileUiTest {
    @get:Rule val compose = createComposeRule()
    private val personal = AndroidProfile(0, "Personal", serialNumber = 0)
    private val work = AndroidProfile(10, "Work", isWork = true, serialNumber = 11)
    private val personalApp = AppEntry("dev.fixture.shared", "Shared app", 10421, false,
        processState = ProcessState.FOREGROUND, profile = personal)
    private val workApp = personalApp.copy(uid = 1010421, profile = work)

    private fun policy(app: AppEntry) = PolicyState(app.packageName, "allow", "allow", false,
        userExempt = false, userId = app.userId, uid = app.uid, userSerial = app.profile.serialNumber)

    @Test fun duplicatePackagesRemainDistinctAndProfileFiltersRestore() {
        var selected: AppEntry? = null
        compose.setContent {
            BackgroundCheckTheme {
                BackgroundCheckApp(
                    state = UiState(access = AccessState.READY, loading = false,
                        apps = listOf(personalApp, workApp), profiles = listOf(personal, work)),
                    onRefresh = {}, onConnect = {}, onSelect = { selected = it }, onDismissDetail = {},
                    onAction = {}, onUndo = {}, onOpenAppSettings = {}, onOpenShizuku = {},
                    onOpenWirelessSettings = {}, onClearMessage = {}, onRefreshResources = {},
                    onRefreshEnergy = {}, onSetMonitorPaused = {}, onSetMonitorInterval = {},
                    onMonitorVisible = {}, onForceStop = {},
                )
            }
        }
        compose.onNodeWithText("Apps", substring = false).performClick()
        compose.onNodeWithTag("apps-list").performScrollToNode(hasTestTag("app-row-${workApp.key}"))
        compose.onNodeWithTag("app-row-${workApp.key}").performClick()
        compose.runOnIdle { assertEquals(workApp.key, selected?.key) }
        compose.onNodeWithTag("apps-list").performScrollToNode(hasTestTag("apps-profile-10"))
        compose.onNodeWithTag("apps-profile-10").performClick()
        compose.onNodeWithTag("apps-list").performScrollToNode(hasTestTag("app-row-${workApp.key}"))
        compose.onNodeWithTag("app-row-${personalApp.key}").assertDoesNotExist()
        compose.onNodeWithText("Overview", substring = false).performClick()
        compose.onNodeWithText("Apps", substring = false).performClick()
        compose.onNodeWithTag("apps-list").performScrollToNode(hasTestTag("apps-profile-10"))
        compose.onNodeWithTag("apps-profile-10").assertIsSelected()
        capture("work-profile-apps.png")
    }

    @Test fun overviewFiltersHistoryAndUndoUsesWorkRecord() {
        val records = listOf(personalApp, workApp).map { app ->
            ChangeRecord("change-${app.userId}", app.packageName, app.label, "Restricted background", 100L,
                policy(app), policy(app).copy(userExempt = app.userId == 10, dozeExempt = app.userId == 10), profileName = app.profile.name)
        }
        var restored: ChangeRecord? = null
        compose.setContent {
            BackgroundCheckTheme {
                BackgroundCheckApp(
                    state = UiState(access = AccessState.READY, loading = false,
                        profiles = listOf(personal, work), changes = records),
                    onRefresh = {}, onConnect = {}, onSelect = {}, onDismissDetail = {}, onAction = {},
                    onUndo = { restored = it }, onOpenAppSettings = {}, onOpenShizuku = {},
                    onOpenWirelessSettings = {}, onClearMessage = {}, onRefreshResources = {},
                    onRefreshEnergy = {}, onSetMonitorPaused = {}, onSetMonitorInterval = {},
                    onMonitorVisible = {}, onForceStop = {},
                )
            }
        }
        compose.onNodeWithTag("overview-profile-10").performClick()
        compose.onNodeWithTag("overview-list").performScrollToNode(hasTestTag("history-record-change-10"))
        compose.onNodeWithTag("history-record-change-0").assertDoesNotExist()
        compose.onNode(hasText("Undo") and hasAnyAncestor(hasTestTag("history-record-change-10"))).performClick()
        compose.runOnIdle { assertEquals(null, restored) }
        compose.onNodeWithText("Restore Doze setting across profiles?").assertExists()
        compose.onNodeWithText("Restore across profiles").performClick()
        compose.runOnIdle { assertEquals(10, restored?.userId) }
    }

    @Test fun associationsResolveOnlyInsideTheSelectedProfile() {
        val association = PackageAssociation("dev.fixture.related", AssociationKind.SHARED_UID)
        val source = workApp.copy(associations = listOf(association))
        val personalRelated = personalApp.copy(packageName = association.packageName)
        val workRelated = workApp.copy(packageName = association.packageName)
        var selected: AppEntry? = null
        compose.setContent {
            BackgroundCheckTheme {
                AssociatedPackages(UiState(apps = listOf(workRelated, personalRelated)), source) { selected = it }
            }
        }
        compose.onNodeWithTag("association-${association.kind.name}-${association.packageName}").performClick()
        compose.runOnIdle { assertEquals(workRelated.key, selected?.key) }
    }

    @Test fun detailsRejectAnotherProfilesPolicyAndKeepNavigationIdentity() {
        val state = mutableStateOf(UiState(access = AccessState.READY, loading = false,
            apps = listOf(personalApp, workApp), selected = workApp, policy = policy(personalApp)))
        var settingsApp: AppEntry? = null
        compose.setContent {
            BackgroundCheckTheme {
                AppDetailSheet(state.value, requireNotNull(state.value.selected), onDismiss = {},
                    onAction = {}, onOpenAppSettings = { settingsApp = it }, onConnect = {}, onForceStop = {})
            }
        }
        compose.onNodeWithText("Settings unavailable").assertExists()
        capture("work-profile-detail.png")
        compose.onNodeWithText("Restrict background work").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Open Android app settings").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(workApp.key, settingsApp?.key) }
        compose.runOnIdle {
            state.value = state.value.copy(selected = workApp.copy(profile = work.copy(available = false,
                unavailableReason = "Work profile is paused")), policy = policy(workApp))
        }
        compose.onNodeWithText("Force stop app").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Restrict background work").performScrollTo().assertIsNotEnabled()
    }
    @Test fun monitorUsesFullUidsIncludingUnmatchedWorkProcesses() {
        val processes = listOf(
            ProcessResource(41, 1, personalApp.uid, "u0_a421", "Personal worker", "", "S", 2.0, 100, 200),
            ProcessResource(42, 1, workApp.uid, "u10_a421", "Work worker", "", "S", 3.0, 100, 200),
            ProcessResource(43, 1, 1099001, "u10_i1", "Isolated work worker", "", "S", 1.0, 100, 200),
        )
        compose.setContent {
            BackgroundCheckTheme {
                MonitorScreen(UiState(access = AccessState.READY, loading = false,
                    apps = listOf(personalApp, workApp), profiles = listOf(personal, work),
                    resources = ResourceSnapshot(processes = processes, processesKnown = true, cpuPercent = 57.0),
                    energy = EnergySnapshot(uids = listOf(UidEnergy(personalApp.uid, 2.0), UidEnergy(workApp.uid, 3.0)),
                        estimatedTotalMah = 19.0)),
                    onRefreshResources = {}, onRefreshEnergy = {}, onSetPaused = {}, onSetInterval = {},
                    onSelectApp = {}, onAccess = {})
            }
        }
        compose.onNodeWithTag("monitor-profile-10").performClick()
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasTestTag("process-row-42"))
        compose.onNodeWithTag("process-row-42").assertExists()
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasTestTag("process-row-43"))
        compose.onNodeWithTag("process-row-43").assertExists()
        compose.onNodeWithTag("process-row-41").assertDoesNotExist()
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasText("CPU · all cores"))
        compose.onNodeWithText("57.0%").assertExists()
        compose.onNodeWithText("Battery use", substring = false).performClick()
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasTestTag("energy-row-${workApp.uid}"))
        compose.onNodeWithTag("energy-row-${workApp.uid}").assertExists()
        compose.onNodeWithTag("energy-row-${personalApp.uid}").assertDoesNotExist()
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasText("Estimated total"))
        compose.onNodeWithText("19.00 mAh").assertExists()
    }

    @Test fun changingProfilesClearsConfirmationAndRejectsPersonalDiagnostics() {
        val state = mutableStateOf(UiState(access = AccessState.READY, loading = false,
            apps = listOf(personalApp, workApp), selected = personalApp, policy = policy(personalApp),
            diagnostics = AppDiagnostics(personalApp.packageName, userId = 0, servicesKnown = true,
                services = listOf(RunningService("dev.fixture.shared/.PersonalService", "personal", true)))))
        compose.setContent {
            BackgroundCheckTheme {
                AppDetailSheet(state.value, requireNotNull(state.value.selected), onDismiss = {},
                    onAction = {}, onOpenAppSettings = {}, onConnect = {}, onForceStop = {})
            }
        }
        compose.onNodeWithText("Allow during Doze").performScrollTo().performClick()
        compose.onNodeWithText("Allow activity during Doze?").assertExists()
        compose.onNodeWithText("This changes the Doze exemption across Android profiles", substring = true).assertExists()
        compose.runOnIdle { state.value = state.value.copy(selected = workApp, policy = policy(workApp)) }
        compose.onNodeWithText("Allow activity during Doze?").assertDoesNotExist()
        compose.onNodeWithText("Resource diagnostics").performScrollTo().performClick()
        compose.onNodeWithText("dev.fixture.shared/.PersonalService").assertDoesNotExist()
        compose.onNodeWithText(workApp.uid.toString()).assertExists()
    }

    @Test fun workAppNetworkShortcutCarriesTheProfileIdentity() {
        var opened: AppEntry? = null
        compose.setContent {
            BackgroundCheckTheme {
                BackgroundCheckApp(UiState(access = AccessState.READY, loading = false,
                    apps = listOf(personalApp, workApp), selected = workApp, policy = policy(workApp)),
                    onRefresh = {}, onConnect = {}, onSelect = {}, onDismissDetail = {}, onAction = {},
                    onUndo = {}, onOpenAppSettings = {}, onOpenShizuku = {}, onOpenWirelessSettings = {},
                    onClearMessage = {}, onRefreshResources = {}, onRefreshEnergy = {}, onSetMonitorPaused = {},
                    onSetMonitorInterval = {}, onMonitorVisible = {}, onForceStop = {}, onOpenNetwork = { opened = it })
            }
        }
        compose.onNodeWithText("Network and internet access").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(workApp.key, opened?.key) }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        instrumentation.targetContext.filesDir.resolve(name).outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }

}
