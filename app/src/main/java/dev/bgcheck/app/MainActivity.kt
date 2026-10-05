package dev.bgcheck.app

import android.content.Intent
import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Process
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import dev.bgcheck.app.ui.network.*
import dev.bgcheck.network.De1984Application
import dev.bgcheck.network.data.service.PackageMonitoringService
import dev.bgcheck.network.data.multiuser.HiddenApiHelper
import dev.bgcheck.network.presentation.viewmodel.FirewallViewModel
import kotlinx.coroutines.launch
import android.content.ComponentName
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bgcheck.app.ui.BackgroundCheckApp
import dev.bgcheck.app.ui.BackgroundCheckTheme

class MainActivity : ComponentActivity() {
    private val dependencies get() = (application as De1984Application).dependencies
    private val firewallModel: FirewallViewModel by viewModels { firewallViewModelFactory(application) }
    private var networkRequest by mutableStateOf<NetworkRequest?>(null)
    private var networkVisible = false
    private var vpnFallback = false
    private var startAfterNotifications = false
    private var notificationFallback = false
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (startAfterNotifications) {
            startAfterNotifications = false
            if (notificationFallback) enableVpnFallbackAfterNotifications() else startFirewallAfterNotifications()
        }
    }
    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (vpnFallback) startVpnFallback() else firewallModel.onVpnPermissionGranted()
        } else if (!vpnFallback) firewallModel.onVpnPermissionDenied()
        vpnFallback = false
    }
    private val batteryOptimization = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    private val model: BackgroundViewModel by viewModels()
    private val historyExport = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(model::exportChanges)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vpnFallback = savedInstanceState?.getBoolean("network.vpnFallback") ?: false
        startAfterNotifications = savedInstanceState?.getBoolean("network.startAfterNotifications") ?: false
        notificationFallback = savedInstanceState?.getBoolean("network.notificationFallback") ?: false
        networkRequest = savedInstanceState?.getString("network.destination")?.let {
            NetworkRequest(NetworkDestination.valueOf(it), savedInstanceState.getString("network.package"),
                savedInstanceState.getInt("network.user"))
        }
        if (savedInstanceState == null) consumeNetworkIntent(intent)
        PackageMonitoringService.startMonitoring(this)
        lifecycleScope.launch {
            firewallModel.uiState.collect { state ->
                if (state.shouldRequestBatteryOptimization) {
                    firewallModel.clearBatteryOptimizationRequest()
                    dependencies.permissionManager.createBatteryOptimizationIntent()?.let {
                        runCatching { batteryOptimization.launch(it) }
                    }
                }
            }
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            BackgroundCheckTheme {
                CompositionLocalProvider(dev.bgcheck.app.ui.LocalInspectionPreferences provides getSharedPreferences("inspection_preferences", MODE_PRIVATE)) {
                BackgroundCheckApp(
                    state = model.state.collectAsStateWithLifecycle().value,
                    onRefresh = model::refresh, onConnect = model::connect, onSelect = model::select,
                    onDismissDetail = model::dismissDetail, onAction = model::apply, onUndo = model::undo,
                    onOpenAppSettings = { app ->
                        runCatching {
                            requireNotNull(getSystemService(LauncherApps::class.java)).startAppDetailsActivity(
                                ComponentName(app.packageName, ""), UserHandle.getUserHandleForUid(app.uid), null, null,
                            )
                        }.onFailure { model.openAppSettings(app) }
                    },
                    onOpenShizuku = { open(packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                        ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))) },
                    onOpenWirelessSettings = { open(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) },
                    onClearMessage = model::clearMessage,
                    onRefreshResources = model::refreshResources,
                    onRefreshEnergy = model::refreshEnergy,
                    onSetMonitorPaused = model::setMonitorPaused,
                    onSetMonitorInterval = model::setMonitorInterval,
                    onMonitorVisible = model::setMonitorVisible,
                    onForceStop = model::forceStop,
                    onTogglePin = model::togglePin,
                    onUndoNetwork = model::undoNetwork,
                    onExportChanges = { historyExport.launch("background-check-changes.json") },
                    onStartComparison = { model.captureComparison(restart = true) },
                    onCompareBattery = { model.captureComparison(restart = false) },
                    onClearComparison = model::clearComparison,
                    networkRequest = networkRequest,
                    onNetworkRequestHandled = { networkRequest = null },
                    onStartFirewall = ::startFirewall,
                    onEnableVpn = ::enableVpnFallback,
                    onNetworkVisible = { visible ->
                        val becameVisible = visible && !networkVisible
                        networkVisible = visible
                        if (becameVisible && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) checkNetworkOnResume()
                    },
                )
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        model.onResume()
        if (networkVisible) checkNetworkOnResume()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeNetworkIntent(intent)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("network.vpnFallback", vpnFallback)
        outState.putBoolean("network.startAfterNotifications", startAfterNotifications)
        outState.putBoolean("network.notificationFallback", notificationFallback)
        networkRequest?.let {
            outState.putString("network.destination", it.destination.name)
            outState.putString("network.package", it.packageName)
            outState.putInt("network.user", it.userId)
        }
        super.onSaveInstanceState(outState)
    }
    private fun consumeNetworkIntent(intent: Intent?) {
        if (intent == null) return
        val request = parseNetworkRequest(intent.action, intent.getStringExtra("dev.bgcheck.network.PACKAGE"),
            if (intent.hasExtra("dev.bgcheck.network.USER_ID")) intent.getIntExtra("dev.bgcheck.network.USER_ID", 0) else null,
            intent.getStringExtra("package_name"),
            if (intent.hasExtra("user_id")) intent.getIntExtra("user_id", 0) else null, Process.myUid() / 100000)
        if (request != null) {
            networkRequest = request
            intent.action = null
            listOf("dev.bgcheck.network.PACKAGE", "dev.bgcheck.network.USER_ID", "package_name", "user_id").forEach(intent::removeExtra)
        }
    }
    private fun startFirewall() = requestFirewallStart(fallback = false)
    private fun enableVpnFallback() = requestFirewallStart(fallback = true)
    private fun requestFirewallStart(fallback: Boolean) {
        val permissions = dependencies.permissionManager
        if (Build.VERSION.SDK_INT >= 33 && !permissions.hasNotificationPermission() && !permissions.hasRequestedNotificationPermission()) {
            permissions.markNotificationPermissionRequested()
            startAfterNotifications = true
            notificationFallback = fallback
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (fallback) enableVpnFallbackAfterNotifications() else startFirewallAfterNotifications()
    }
    private fun startFirewallAfterNotifications() {
        firewallModel.startFirewall()?.let {
            vpnFallback = false
            vpnPermission.launch(it)
        }
    }
    private fun enableVpnFallbackAfterNotifications() {
        runCatching { android.net.VpnService.prepare(this) }.onSuccess { intent ->
            if (intent == null) startVpnFallback() else {
                vpnFallback = true
                vpnPermission.launch(intent)
            }
        }.onFailure { Toast.makeText(this, it.message ?: "Couldn't request VPN permission", Toast.LENGTH_LONG).show() }
    }
    private fun startVpnFallback() {
        lifecycleScope.launch {
            runCatching { dependencies.firewallManager.startVpnFallbackManually() }.onSuccess {
                if (dependencies.firewallManager.isActive()) {
                    dependencies.permissionManager.createBatteryOptimizationIntent()?.let { batteryOptimization.launch(it) }
                }
            }.onFailure { Toast.makeText(this@MainActivity, it.message ?: "Couldn't start VPN", Toast.LENGTH_LONG).show() }
        }
    }
    private fun checkNetworkOnResume() {
        HiddenApiHelper.clearDisabledPackagesCache()
        lifecycleScope.launch {
            dependencies.shizukuManager.checkShizukuStatus()
            if (dependencies.shizukuManager.isShizukuAvailable() && !dependencies.shizukuManager.hasShizukuPermission &&
                !dependencies.shizukuManager.hasUserDeniedPermission) dependencies.shizukuManager.requestShizukuPermission()
            dependencies.rootManager.forceRecheckRootStatus()
            dependencies.firewallManager.checkBackendShouldSwitch()
        }
    }
    override fun onPause() { model.onPause(); super.onPause() }
    private fun open(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, "Couldn't open this screen. You can open it from Android Settings.", Toast.LENGTH_LONG).show()
        }
    }
}
