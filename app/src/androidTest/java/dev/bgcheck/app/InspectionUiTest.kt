package dev.bgcheck.app

import android.content.Context
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bgcheck.app.data.*
import dev.bgcheck.app.ui.*
import dev.bgcheck.network.data.firewall.FirewallManager.FirewallState
import dev.bgcheck.network.domain.firewall.FirewallHealth
import dev.bgcheck.network.domain.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InspectionUiTest {
    @get:Rule val compose = createComposeRule()
    private val app = AppEntry("dev.bgcheck.fixture", "Pinned fixture", 10123, false,
        profile = AndroidProfile(0, "Personal", serialNumber = 0), firstInstallTime = 100)

    @Test fun overviewSeparatesAccessFromFirewallAndOpensPinnedInstallation() {
        val value = mutableStateOf(UiState(access = AccessState.READY, loading = false, apps = listOf(app),
            profiles = listOf(app.profile), pins = listOf(PinnedApp.from(app)!!),
            protection = ProtectionSummary.from(FirewallState.Stopped, FirewallHealth.Healthy, listOf(app.profile), 0, false)))
        var toggled: AppEntry? = null
        show(value, onSelect = { value.value = value.value.copy(selected = it) }, onPin = { toggled = it })
        compose.onNodeWithTag("overview-list").performScrollToNode(hasTestTag("protection-status"))
        compose.onNode(hasText("Off") and hasAnyAncestor(hasTestTag("protection-status"))).assertExists()
        compose.onNodeWithText("Shizuku access").assertExists()
        capture("overview-protection")
        compose.onNodeWithTag("overview-list").performScrollToNode(hasText("Pinned fixture"))
        compose.onNodeWithText("Pinned fixture").performClick()
        compose.onNodeWithText("Unpin").performClick()
        capture("pinned-detail")
        compose.runOnIdle { assertEquals(app.key, toggled?.key) }
    }

    @Test fun combinedHistorySearchFindsNetworkBeforeAfterAndExports() {
        val before = PolicyState(app.packageName, "allow", null, false)
        val policy = ChangeRecord("policy", app.packageName, "Policy fixture", "Background restricted", 100, before, before.copy(backgroundMode = "ignore"))
        val network = NetworkChangeRecord("network", app.packageName, "Network fixture", 0, app.uid, 0, 100,
            "Personal", 200, NetworkChangeKind.PACKAGE_ENABLED, NetworkChangeValue(packageEnabled = true), NetworkChangeValue(packageEnabled = false))
        val value = mutableStateOf(UiState(access = AccessState.READY, loading = false, profiles = listOf(app.profile),
            changes = listOf(policy), networkChanges = listOf(network)))
        var exports = 0
        show(value, onExport = { exports++ })
        compose.onNodeWithTag("overview-list").performScrollToNode(hasText("Search changes"))
        compose.onNodeWithText("Search changes").performTextInput("disabled")
        compose.onNodeWithTag("overview-list").performScrollToNode(hasText("1 recorded"))
        compose.onNodeWithText("1 recorded").assertExists()
        compose.onNodeWithTag("overview-list").performScrollToNode(hasTestTag("history-network-network"))
        compose.onNodeWithText("Before: Enabled", substring = true).assertExists()
        compose.onNodeWithText("After: Disabled", substring = true).assertExists()
        capture("combined-history")
        compose.onNodeWithTag("history-record-policy").assertDoesNotExist()
        compose.onNodeWithTag("overview-list").performScrollToNode(hasText("Export all changes"))
        compose.onNodeWithText("Export all changes").performClick()
        compose.runOnIdle { assertEquals(1, exports) }
    }

    @Test fun preferenceSurvivesDestroyedCompositionAndInvalidEnumFallsBack() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("inspection_ui_test", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("sort", "removed-enum").commit()
        val visible = mutableStateOf(true)
        compose.setContent {
            BackgroundCheckTheme {
                CompositionLocalProvider(LocalInspectionPreferences provides prefs) {
                    if (visible.value) {
                        var sort by rememberEnumPreference("sort", AppSort.NAME)
                        Button(onClick = { sort = AppSort.UID }) { Text("Preference " + sort.name) }
                    }
                }
            }
        }
        compose.onNodeWithText("Preference NAME").performClick()
        compose.onNodeWithText("Preference UID").assertExists()
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithText("Preference UID").assertExists()
        prefs.edit().clear().commit()
    }

    @Test fun savedComparisonRemainsAvailableWithoutShizuku() {
        val sample = BatteryComparisonSample.capture(EnergySnapshot(uids = listOf(UidEnergy(app.uid, estimatedMah = 1.0)),
            startClockTimeMs = 100, capturedAt = 200), listOf(app), listOf(app.profile))
        val value = mutableStateOf(UiState(access = AccessState.NOT_RUNNING, loading = false,
            profiles = listOf(app.profile), batteryComparison = BatteryComparisonSession(sample)))
        show(value)
        compose.onNodeWithText("Monitor").performClick()
        compose.onNodeWithText("Battery use").performClick()
        compose.onNodeWithTag("monitor-list").performScrollToNode(hasText("Battery comparison"))
        compose.onNodeWithText("Baseline").assertExists()
        compose.onNodeWithText("Compare now").assertIsNotEnabled()
        compose.onNodeWithText("Clear comparison").assertIsEnabled()
        capture("saved-comparison-disconnected")
    }

    private fun show(value: androidx.compose.runtime.State<UiState>, onSelect: (AppEntry) -> Unit = {},
        onPin: (AppEntry) -> Unit = {}, onExport: () -> Unit = {}) {
        compose.setContent { BackgroundCheckTheme {
            BackgroundCheckApp(
                state = value.value, onRefresh = {}, onConnect = {}, onSelect = onSelect,
                onDismissDetail = {}, onAction = {}, onUndo = {}, onOpenAppSettings = {},
                onOpenShizuku = {}, onOpenWirelessSettings = {}, onClearMessage = {},
                onRefreshResources = {}, onRefreshEnergy = {}, onSetMonitorPaused = {},
                onSetMonitorInterval = {}, onMonitorVisible = {}, onForceStop = {},
                onTogglePin = onPin, onExportChanges = onExport)
        } }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            val folder = java.io.File(context.getExternalFilesDir(null), "inspection-captures")
            check(folder.isDirectory || folder.mkdirs())
            val output = java.io.File(folder, name + "-font" + context.resources.configuration.fontScale + ".png")
            output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
