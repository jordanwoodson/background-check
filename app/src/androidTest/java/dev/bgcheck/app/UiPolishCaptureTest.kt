package dev.bgcheck.app

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bgcheck.app.data.*
import dev.bgcheck.app.ui.BackgroundCheckApp
import dev.bgcheck.app.ui.BackgroundCheckTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Visual review captures using injected sample data, without Shizuku or device changes.
 * Run this class after configuring the emulator's desired font scale and orientation.
 * PNGs go to the target app's external files directory under ux-captures; filenames
 * include screenshot dimensions and font scale so those runs can be compared.
 * The instrumentation argument captureBaseline=true supports the previous screen
 * structure and prefixes its files, allowing the same test APK to capture both builds.
 */
@RunWith(AndroidJUnit4::class)
class UiPolishCaptureTest {
    @get:Rule val compose = createComposeRule()

    private val sampledAt = System.currentTimeMillis()
    private val baseline = InstrumentationRegistry.getArguments().getString("captureBaseline") == "true"
    private var appliedActions = 0
    private var forceStops = 0
    private val signal = AppEntry(
        "org.thoughtcrime.securesms", "Signal", 10421, false,
        processState = ProcessState.SERVICE, standbyBucket = 10, dozeExempt = false,
    )
    private val firefox = AppEntry(
        "org.mozilla.firefox", "Firefox", 10422, false,
        processState = ProcessState.FOREGROUND, standbyBucket = 10, dozeExempt = false,
    )
    private val spotify = AppEntry(
        "com.spotify.music", "Spotify", 10423, false,
        processState = ProcessState.SERVICE, standbyBucket = 20, dozeExempt = false,
    )
    private val photos = AppEntry(
        "com.google.android.apps.photos", "Photos", 10424, false,
        processState = ProcessState.CACHED, standbyBucket = 20, dozeExempt = false,
    )
    private val calendar = AppEntry(
        "com.google.android.calendar", "Calendar", 10425, false,
        processState = ProcessState.BACKGROUND, standbyBucket = 30, dozeExempt = false,
    )
    private val calculator = AppEntry(
        "com.google.android.calculator", "Calculator", 10426, false,
        processState = ProcessState.NOT_RUNNING, standbyBucket = 40, dozeExempt = false,
    )
    private val system = AppEntry(
        "com.android.systemui", "System UI", 1000, true,
        processState = ProcessState.FOREGROUND, standbyBucket = 5, dozeExempt = true,
        protectedReason = "Required for system navigation and notifications.",
    )

    @Test fun captureOverview() {
        show()
        compose.onNodeWithText("System overview").assertExists()
        capture("01-overview")
        scrollListTo("overview-list", "Kept for quick reopening")
        capture("02-overview-processes")
        scrollListTo("overview-list", hasTestTag("history-record-capture-spotify"))
        capture("03-overview-history")
    }

    @Test fun captureLongBatteryState() {
        val initial = readyState()
        show(initial.copy(battery = initial.battery.copy(powerState = BatteryPowerState.PLUGGED_IDLE, currentMa = 0.0, powerMw = 0.0)))
        scrollListTo("overview-list", "73%")
        compose.onNodeWithText("73%").assertIsDisplayed()
        scrollListTo("overview-list", BatteryPowerState.PLUGGED_IDLE.label)
        compose.onNodeWithText(BatteryPowerState.PLUGGED_IDLE.label).assertIsDisplayed()
        capture("26-overview-plugged-idle")
    }

    @Test fun captureApps() {
        show()
        compose.onNodeWithText("Apps").performClick()
        capture("04-apps")
        scrollListTo("apps-list", "Calendar")
        capture("05-apps-list")
    }

    @Test fun captureLiveMonitor() {
        show()
        compose.onNodeWithText("Monitor").performClick()
        capture("06-monitor-live")
        scrollListTo("monitor-list", "RAM available")
        capture("07-monitor-resources")
        scrollListTo("monitor-list", "org.mozilla.firefox")
        capture("08-monitor-processes")
    }

    @Test fun captureBatteryUse() {
        show()
        compose.onNodeWithText("Monitor").performClick()
        compose.onNodeWithText("Battery use").assertIsDisplayed().performClick()
        scrollListTo("monitor-list", "Estimated power")
        capture("09-battery-flow")
        scrollListTo("monitor-list", "Time on battery")
        capture("10-battery-estimates")
        scrollListTo("monitor-list", spotify.label)
        capture("11-battery-apps")
    }

    @Test fun captureAuthorizedAccess() {
        show()
        compose.onNodeWithText("Access").performClick()
        capture("12-access-connected")
        val details = hasText(if (baseline) "Wireless debugging settings" else "Review settings, make changes and restore them.")
        compose.onNode(hasScrollAction()).performScrollToNode(details)
        compose.onNode(details).performScrollTo().assertIsDisplayed()
        capture("13-access-details")
    }

    @Test fun captureDetailControls() {
        show()
        openSignalDetail()
        compose.onNodeWithText("Allow background work").performScrollTo().assertIsDisplayed()
        capture("14-detail-background")
        compose.onNodeWithText("Allow during Doze").performScrollTo().assertIsDisplayed()
        capture("15-detail-battery")
        compose.onNodeWithText("Force stop app").performScrollTo().assertIsDisplayed()
        capture("16-detail-stop")
    }

    @Test fun captureExpandedDiagnostics() {
        show()
        openSignalDetail()
        compose.onNodeWithText("Resource diagnostics").performScrollTo().let { node ->
            if (!baseline) node.performClick()
        }
        compose.onNodeWithTag("app-process-row-1984").performScrollTo().assertIsDisplayed()
        capture("17-detail-diagnostics-processes")
        compose.onNodeWithText("Swap PSS").performScrollTo().assertIsDisplayed()
        capture("18-detail-diagnostics-memory")
        compose.onNodeWithTag("service-row-${signal.packageName}/.messages.MessageRetrievalService:${signal.packageName}")
            .performScrollTo().assertIsDisplayed()
        capture("27-detail-diagnostics-services")
    }

    @Test fun captureConfirmationAndCancel() {
        show()
        openSignalDetail()
        compose.onNodeWithText("Restrict background work").performScrollTo().performClick()
        compose.onNodeWithText("Restrict background work?").assertExists()
        capture("19-confirm-restriction")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Restrict background work?").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, appliedActions) }
        capture("20-restriction-cancelled")

        compose.onNodeWithText("Force stop app").performScrollTo().performClick()
        compose.onNodeWithText("Force stop Signal?").assertExists()
        capture("21-confirm-force-stop")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Force stop Signal?").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, forceStops) }
    }

    @Test fun captureDisconnectedAccess() {
        show(disconnectedState())
        compose.onNodeWithText("Access").performClick()
        capture("22-access-disconnected")
        if (!baseline) {
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Connection help"))
            compose.onNodeWithText("Connection help").performClick()
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Wireless debugging settings"))
        compose.onNodeWithText("Wireless debugging settings").performScrollTo().assertIsDisplayed()
        capture("23-access-connection-help")
    }

    @Test fun captureDisconnectedMonitor() {
        show(disconnectedState())
        compose.onNodeWithText("Monitor").performClick()
        capture("24-monitor-disconnected")
        compose.onNodeWithText("Battery use").assertIsDisplayed().performClick()
        capture("25-battery-disconnected")
    }

    private fun openSignalDetail() {
        compose.onNodeWithText("Apps").performClick()
        scrollListTo("apps-list", signal.label)
        compose.onNodeWithText(signal.label).performClick()
        compose.onNodeWithContentDescription("Close app details").assertExists()
    }

    private fun scrollListTo(listTag: String, text: String) {
        scrollListTo(listTag, hasText(text))
    }

    private fun scrollListTo(listTag: String, target: SemanticsMatcher) {
        // A section heading may fit while every value below it remains off-screen.
        // Bring a complete data row or the section's last relevant value into view.
        compose.onNodeWithTag(listTag).performScrollToNode(target)
        compose.onNode(target).performScrollTo().assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // Android dialog-window transitions run outside Compose's test clock.
        android.os.SystemClock.sleep(350)
        val context = instrumentation.targetContext
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "No screenshot returned for $name" }
        try {
            val folder = File(checkNotNull(context.getExternalFilesDir(null)), "ux-captures")
            check(folder.isDirectory || folder.mkdirs()) { "Cannot create capture directory: $folder" }
            val font = context.resources.configuration.fontScale.toString().replace('.', 'p')
            val prefix = if (baseline) "baseline-" else ""
            val output = File(folder, "$prefix$name-${bitmap.width}x${bitmap.height}-font$font.png")
            output.outputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "Cannot encode $output" }
            }
            Log.i("UiPolishCapture", "Saved ${output.absolutePath}")
        } finally {
            bitmap.recycle()
        }
    }

    private fun show(initial: UiState = readyState()): MutableState<UiState> {
        val state = mutableStateOf(initial)
        compose.setContent {
            BackgroundCheckTheme {
                BackgroundCheckApp(
                    state = state.value,
                    onRefresh = {}, onConnect = {},
                    onSelect = { app ->
                        state.value = state.value.copy(selected = app, policy = policy(app), diagnostics = diagnostics(app))
                    },
                    onDismissDetail = { state.value = state.value.copy(selected = null) },
                    onAction = { appliedActions++ }, onUndo = {}, onOpenAppSettings = {},
                    onOpenShizuku = {}, onOpenWirelessSettings = {}, onClearMessage = {},
                    onRefreshResources = {}, onRefreshEnergy = {},
                    onSetMonitorPaused = { state.value = state.value.copy(monitorPaused = it) },
                    onSetMonitorInterval = { state.value = state.value.copy(monitorIntervalMs = it) },
                    onMonitorVisible = { state.value = state.value.copy(monitoring = it) },
                    onForceStop = { forceStops++ },
                )
            }
        }
        return state
    }

    private fun readyState(): UiState {
        val spotifyPolicy = policy(spotify)
        val photosPolicy = policy(photos)
        return UiState(
            access = AccessState.READY,
            loading = false,
            apps = listOf(signal, firefox, spotify, photos, calendar, calculator, system),
            capturedAt = sampledAt,
            battery = BatteryInfo(
                percent = 73, temperatureC = 31.4f, voltageMv = 4020,
                currentMa = -284.0, powerMw = -1141.7, capturedAt = sampledAt,
                powerState = BatteryPowerState.DISCHARGING,
            ),
            changes = listOf(
                ChangeRecord(
                    "capture-spotify", spotify.packageName, spotify.label, "Restricted background work",
                    sampledAt - 18 * 60_000, spotifyPolicy, spotifyPolicy.copy(backgroundMode = "ignore"),
                ),
                ChangeRecord(
                    "capture-photos", photos.packageName, photos.label, "Restored background access",
                    sampledAt - 53 * 60_000, photosPolicy, photosPolicy.copy(backgroundMode = "ignore"), undone = true,
                ),
            ),
            resources = ResourceSnapshot(
                processesKnown = true, cpuPercent = 18.6, cpuCoreCount = 8,
                memory = SystemMemory(8_192_000, 3_604_480, 2_097_152, 1_802_240),
                sampleDurationMs = 1_000, capturedAt = sampledAt,
                processes = listOf(
                    ProcessResource(2431, 742, firefox.uid, "u0_a422", "org.mozilla.firefox", "org.mozilla.firefox", "R", 24.8, 286_720, 2_516_582),
                    ProcessResource(3128, 742, spotify.uid, "u0_a423", "com.spotify.music", "com.spotify.music", "S", 7.2, 164_864, 1_642_496),
                    ProcessResource(1984, 742, signal.uid, "u0_a421", "org.thoughtcrime.securesms", "org.thoughtcrime.securesms", "S", 1.4, 131_072, 1_048_576),
                    ProcessResource(2017, 742, signal.uid, "u0_a421", "Signal:message-service", "org.thoughtcrime.securesms:message-service", "S", 0.3, 32_768, 524_288),
                    ProcessResource(876, 1, 1000, "system", "system_server", "system_server", "S", 4.1, 393_216, 3_670_016),
                    ProcessResource(462, 1, 1000, "system", "surfaceflinger", "/system/bin/surfaceflinger", "S", 2.6, 61_440, 393_216),
                ),
            ),
            resourceHistory = listOf(12.4, 14.7, 10.8, 21.3, 28.1, 19.4, 16.8, 18.6).mapIndexed { index, cpu ->
                ResourceHistoryPoint(sampledAt - (7 - index) * 5_000L, cpu, 4_320_000L + index * 38_000L)
            },
            energy = EnergySnapshot(
                capturedAt = sampledAt, estimatedTotalMah = 864.3,
                batteryRealtimeMs = 6 * 3_600_000L + 24 * 60_000L,
                startClockTimeMs = sampledAt - 7 * 3_600_000L,
                components = listOf(
                    PowerComponent("scrn", 312.6), PowerComponent("cpu", 184.2),
                    PowerComponent("wifi", 42.8), PowerComponent("mobile_radio", 68.4),
                ),
                uids = listOf(
                    UidEnergy(spotify.uid, 142.6, 384_000, 84_321_024, 2_384_112, 2_480_000),
                    UidEnergy(firefox.uid, 80.2, 812_000, 145_987_212, 7_142_312, 126_000),
                    UidEnergy(signal.uid, 37.5, 118_000, 12_803_120, 2_192_002, 42_000),
                    UidEnergy(photos.uid, 22.0, 68_000, 3_143_721, 25_165_824, 12_000),
                    UidEnergy(calendar.uid, 4.8, 14_000, 421_888, 110_592, 3_000),
                ),
            ),
        )
    }

    private fun disconnectedState() = readyState().let { state ->
        state.copy(
            access = AccessState.NOT_RUNNING, capturedAt = null,
            apps = state.apps.map { it.copy(processState = ProcessState.UNKNOWN) },
            resources = null, energy = null, resourceHistory = emptyList(),
        )
    }

    private fun policy(app: AppEntry) = PolicyState(
        packageName = app.packageName, backgroundMode = "allow", legacyMode = null,
        dozeExempt = false, userExempt = false, standbyBucket = app.standbyBucket,
        capturedAt = sampledAt,
    )

    private fun diagnostics(app: AppEntry) = AppDiagnostics(
        packageName = app.packageName, pssKb = 96_384, privateDirtyKb = 58_368,
        swapPssKb = 2_048, capturedAt = sampledAt, servicesKnown = true,
        services = listOf(
            RunningService("${app.packageName}/.messages.MessageRetrievalService", app.packageName, true),
            RunningService("${app.packageName}/.jobs.KeepAliveService", "${app.packageName}:message-service", false),
        ),
    )
}
