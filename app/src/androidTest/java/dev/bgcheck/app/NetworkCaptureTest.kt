package dev.bgcheck.app

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bgcheck.app.ui.appsSelectionRefusal
import dev.bgcheck.app.ui.network.selectionRefusal
import dev.bgcheck.network.De1984Application
import dev.bgcheck.network.domain.model.NetworkPackage
import dev.bgcheck.network.presentation.viewmodel.FirewallViewModel
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Run at each desired font scale/orientation. Only navigation, selection and cancellation occur. */
@RunWith(AndroidJUnit4::class)
class NetworkCaptureTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(NetworkTestPreferences()).around(compose)
    private val user = android.os.Process.myUid() / 100000
    private val chrome = "com.android.chrome"
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val deps get() = (compose.activity.application as De1984Application).dependencies

    @Test fun networkTop() {
        networkModel()
        capture("network-top")
    }

    @Test fun networkFiltersExpanded() {
        networkModel()
        compose.openTaggedFilters("network-list", "network-filters")
        capture("network-filters")
    }

    @Test fun networkAppSheetTop() {
        openNetworkSheet()
        capture("network-sheet-top")
        compose.closeNetworkAppSheet()
    }

    @Test fun networkAppSheetScrolled() {
        openNetworkSheet()
        compose.onNodeWithTag("network-control-local-network").performScrollTo()
        capture("network-sheet-scrolled")
        compose.closeNetworkAppSheet()
    }

    @Test fun networkSelectionWithTwoRows() {
        selectTwoNetworkRows()
        capture("network-selection")
        androidx.test.espresso.Espresso.pressBack()
    }

    @Test fun networkBatchSheet() {
        selectTwoNetworkRows()
        compose.onNodeWithTag("network-batch-action").performClick()
        compose.awaitNetworkNode("network-batch-sheet")
        capture("network-batch-sheet")
        androidx.test.espresso.Espresso.pressBack()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("network-batch-sheet").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun firewallSettingsTop() {
        compose.openFirewallSettings()
        capture("firewall-settings-top")
    }
    @Test fun firewallSettingsProtection() = captureSettingsSection("protection")
    @Test fun firewallSettingsBehavior() = captureSettingsSection("behavior")
    @Test fun firewallSettingsSafetyOverrides() = captureSettingsSection("safety-overrides")
    @Test fun firewallSettingsCaptivePortal() = captureSettingsSection("captive-portal")
    @Test fun firewallSettingsBackupAndRestore() = captureSettingsSection("backup-and-restore")
    @Test fun firewallSettingsDiagnostics() = captureSettingsSection("diagnostics")
    @Test fun firewallSettingsAbout() = captureSettingsSection("about")

    @Test fun logsPage() {
        compose.openFirewallSettings()
        compose.onNodeWithTag("network-settings-list").performScrollToNode(hasTestTag("network-settings-logs"))
        compose.onNodeWithTag("network-settings-logs").performClick()
        compose.awaitNetworkNode("network-logs")
        capture("network-logs")
    }

    @Test fun accessFirewallPermissions() {
        compose.onNodeWithTag("nav-access").performClick()
        val heading = "network-settings-heading-firewall-permissions"
        compose.onNodeWithTag("access-list").performScrollToNode(hasTestTag(heading))
        compose.onNodeWithTag(heading).performScrollTo().assertIsDisplayed()
        capture("access-firewall-permissions")
    }

    @Test fun chromeAppDetailsNetwork() {
        openChromeDetails()
        compose.onNodeWithTag("app-detail-network-heading").performScrollTo().assertIsDisplayed()
        capture("chrome-detail-network")
        androidx.test.espresso.Espresso.pressBack()
    }

    @Test fun chromeAppDetailsPackage() {
        openChromeDetails()
        compose.onNodeWithTag("app-detail-package-heading").performScrollTo().assertIsDisplayed()
        capture("chrome-detail-package")
        androidx.test.espresso.Espresso.pressBack()
    }

    @Test fun appsFiltersExpanded() {
        compose.onNodeWithTag("nav-apps").performClick()
        compose.openTaggedFilters("apps-list", "apps-filters")
        capture("apps-filters")
    }

    @Test fun appsSelectionActionBar() {
        compose.onNodeWithTag("nav-apps").performClick()
        lateinit var model: BackgroundViewModel
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[BackgroundViewModel::class.java] }
        compose.waitUntil(20_000) { !model.state.value.loading && model.state.value.apps.any {
            it.installed && !it.system && appsSelectionRefusal(it, null, false) == null
        } }
        val app = model.state.value.apps.first { it.installed && !it.system && appsSelectionRefusal(it, null, false) == null }
        val row = "app-row-${app.key}"
        compose.onNodeWithTag("apps-list").performScrollToNode(hasTestTag(row))
        compose.onNodeWithTag(row).performSemanticsAction(SemanticsActions.OnLongClick)
        compose.onNodeWithTag("apps-list").performScrollToNode(hasTestTag("apps-selection-bar"))
        compose.onNodeWithTag("apps-selection-bar").assertExists()
        capture("apps-selection")
        compose.onNodeWithTag("apps-close-selection").performClick()
    }

    private fun captureSettingsSection(section: String) {
        compose.openFirewallSettings()
        val heading = "network-settings-heading-$section"
        compose.onNodeWithTag("network-settings-list").performScrollToNode(hasTestTag(heading))
        compose.onNodeWithTag(heading).performScrollTo().assertIsDisplayed()
        capture("firewall-settings-$section")
    }

    private fun networkModel(): FirewallViewModel {
        compose.openNetwork()
        lateinit var model: FirewallViewModel
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[FirewallViewModel::class.java] }
        compose.waitUntil(20_000) { !model.uiState.value.isLoadingData && model.uiState.value.packages.isNotEmpty() }
        return model
    }

    private fun openNetworkSheet(packageName: String? = null) {
        val rows = networkModel().uiState.value.packages
        val pkg = rows.firstOrNull { it.packageName == (packageName ?: chrome) && it.userId == user } ?: rows.first()
        val tag = "network-row-${pkg.userId}:${pkg.packageName}"
        compose.onNodeWithTag("network-list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
        compose.awaitNetworkNode("network-sheet")
    }

    private fun openChromeDetails() {
        assumeTrue("Chrome is required for the app-details captures", runCatching {
            compose.activity.packageManager.getApplicationInfo(chrome, 0)
        }.isSuccess)
        openNetworkSheet(chrome)
        compose.awaitNetworkNode("network-app-details")
        compose.onNodeWithTag("network-app-details").performScrollTo().performClick()
        compose.awaitNetworkNode("app-detail-network")
    }

    private fun selectTwoNetworkRows() {
        val model = networkModel()
        fun selectable(pkg: NetworkPackage): Boolean = selectionRefusal(pkg, deps.firewallManager.activeBackendType.value,
            model.uiState.value.blockingContext) == null
        val rows = model.uiState.value.packages.filter(::selectable).take(2)
        check(rows.size == 2) { "Two selectable installed apps are needed for batch captures" }
        rows.forEachIndexed { index, pkg ->
            val tag = "network-row-${pkg.userId}:${pkg.packageName}"
            compose.onNodeWithTag("network-list").performScrollToNode(hasTestTag(tag))
            if (index == 0) compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnLongClick)
            else compose.onNodeWithTag(tag).performClick()
        }
        compose.onNodeWithTag("network-list").performScrollToNode(hasTestTag("network-batch-action"))
        compose.onNodeWithText("2 selected").assertExists()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val context = instrumentation.targetContext
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "No screenshot returned for $name" }
        try {
            val folder = File(checkNotNull(context.getExternalFilesDir(null)), "ux-captures")
            check(folder.isDirectory || folder.mkdirs()) { "Cannot create capture directory: $folder" }
            val font = context.resources.configuration.fontScale.toString().replace('.', 'p')
            val output = File(folder, "$name-${bitmap.width}x${bitmap.height}-font$font.png")
            output.outputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "Cannot encode $output" }
            }
            Log.i("NetworkCapture", "Saved ${output.absolutePath}")
        } finally { bitmap.recycle() }
    }
}
