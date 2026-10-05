package dev.bgcheck.app.ui.network

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.bgcheck.app.ui.*
import dev.bgcheck.network.De1984Dependencies
import dev.bgcheck.network.data.common.RootStatus
import dev.bgcheck.network.data.common.ShizukuStatus
import dev.bgcheck.network.domain.firewall.FirewallBackendType
import kotlinx.coroutines.launch

@Composable
internal fun rememberFirewallPermissionsContent(onShowShizuku: () -> Unit): @Composable ColumnScope.() -> Unit {
    val activity = requireNotNull(LocalActivity.current)
    val deps = remember(activity.application) { De1984Dependencies.getInstance(activity.application) }
    val model = networkSettingsPermissionViewModel()
    val settings = networkSettingsViewModel()
    val state by model.uiState.collectAsStateWithLifecycle()
    val root by settings.rootStatus.collectAsStateWithLifecycle()
    val shizuku by settings.shizukuStatus.collectAsStateWithLifecycle()
    val backend by settings.activeBackendType.collectAsStateWithLifecycle()
    val rootTest: FirewallPermissionsRootTest = viewModel(viewModelStoreOwner = activity as ViewModelStoreOwner, factory = remember(deps) { FirewallPermissionsRootTest.Factory(deps) })
    val test by rootTest.state.collectAsStateWithLifecycle()
    val showBanner by deps.superuserBannerState.showBanner.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var rationale by rememberSaveable { mutableStateOf(false) }
    var vpnConflict by rememberSaveable { mutableStateOf(false) }
    var privilegeDialog by rememberSaveable { mutableStateOf(false) }
    var previouslyRequested by remember { mutableStateOf(model.hasRequestedNotificationPermission()) }
    val notification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        model.markNotificationPermissionRequested()
        previouslyRequested = true
        model.refreshPermissions()
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { model.refreshPermissions() }
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        model.refreshPermissions()
        if (result.resultCode != Activity.RESULT_OK) message = "VPN permission was denied. You can grant it later or use a privileged backend."
    }
    fun openAppSettings() {
        runCatching { settingsLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${activity.packageName}".toUri())) }
            .onFailure { message = "Could not open app settings: ${it.message}" }
    }
    fun openNotifications() {
        runCatching { settingsLauncher.launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)) }
            .onFailure { openAppSettings() }
    }
    fun requestNotifications() {
        if (Build.VERSION.SDK_INT < 33) { openNotifications(); return }
        model.markNotificationPermissionRequested()
        previouslyRequested = true
        runCatching { notification.launch(Manifest.permission.POST_NOTIFICATIONS) }.onFailure { openNotifications() }
    }
    fun requestBattery() {
        try {
            val intent = deps.permissionManager.createBatteryOptimizationIntent()
            when {
                intent != null -> settingsLauncher.launch(intent)
                deps.permissionManager.isBatteryOptimizationDisabled() -> model.refreshPermissions()
                else -> settingsLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        } catch (_: Exception) {
            runCatching { settingsLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                .onFailure { openAppSettings() }
        }
    }
    fun requestVpn() {
        val activeBackend = deps.firewallManager.getActiveBackendType()
        if (activeBackend != null && activeBackend != FirewallBackendType.VPN) { model.refreshPermissions(); return }
        runCatching {
            val intent = VpnService.prepare(activity)
            if (intent == null) model.refreshPermissions() else vpnLauncher.launch(intent)
        }.onFailure { message = "Could not request VPN permission: ${it.message}" }
    }

    LifecycleResumeEffect(model) {
        model.refreshPermissions()
        previouslyRequested = model.hasRequestedNotificationPermission()
        scope.launch {
            deps.shizukuManager.checkShizukuStatus()
            deps.rootManager.forceRecheckRootStatus(retry = false)
            model.refreshPermissions()
        }
        onPauseOrDispose {}
    }
    LaunchedEffect(root, shizuku, backend, test) { model.refreshPermissions() }
    LaunchedEffect(showBanner) { if (showBanner) privilegeDialog = true }
    val showRationale = Build.VERSION.SDK_INT >= 33 && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
    val needsSettings = firewallNotificationNeedsSettings(Build.VERSION.SDK_INT, previouslyRequested, showRationale)
    val notificationsGranted = state.basicPermissions.firstOrNull { it.permission == "android.permission.POST_NOTIFICATIONS" }?.isGranted ?: state.hasBasicPermissions

    val content: @Composable ColumnScope.() -> Unit = {
        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.errorMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Red) }
        FirewallPermissionsRow("Notifications", "New-app alerts and warnings when protection needs attention.", firewallPermissionRowState(notificationsGranted, openSettings = needsSettings), "firewall-permission-notifications", !state.isLoading) {
            if (needsSettings) openNotifications()
            else if (showRationale) rationale = true
            else requestNotifications()
        }
        FirewallPermissionsRow("Battery optimization", "Keep firewall protection running when Android limits background activity.", firewallPermissionRowState(state.hasBatteryOptimizationExemption), "firewall-permission-battery", !state.isLoading, ::requestBattery)
        FirewallPermissionsRow("VPN", if (state.isUsingPrivilegedBackend) "The active backend works without Android’s VPN slot." else "Required when using the VPN backend. Android allows one VPN at a time.", firewallPermissionRowState(state.hasVpnPermission, state.isUsingPrivilegedBackend), "firewall-permission-vpn", !state.isLoading) {
            if (deps.firewallManager.isAnotherVpnActive()) vpnConflict = true else requestVpn()
        }
        FirewallPermissionsRow("Advanced access", "Root, Shizuku or system permissions enable privileged firewall controls.", firewallPermissionRowState(state.hasAdvancedPermissions, openSettings = true), "firewall-permission-advanced", !state.isLoading, onShowShizuku)
        FirewallPermissionsRow("Root access (optional)", when (root) {
            RootStatus.ROOTED_WITH_PERMISSION -> "Root enables iptables and boot protection when boot scripts are supported."
            RootStatus.CHECKING -> "Checking root access…"
            RootStatus.ROOTED_NO_PERMISSION -> if (settings.hasRequestedRootPermission()) "Root permission was denied. Allow Background Check in your root manager (Magisk, KernelSU or APatch), then try again." else "Grant superuser permission in your root manager when prompted."
            RootStatus.NOT_ROOTED -> "No root detected. Magisk, KernelSU and APatch provide root on supported devices; installing a manager alone does not root a phone. Shizuku is available through the connection controls above."
        }, firewallPermissionRowState(root == RootStatus.ROOTED_WITH_PERMISSION), "firewall-permission-root", root != RootStatus.CHECKING, rootTest::start)
        Text("Shizuku unlocks ConnectivityManager on supported Android 13+ devices and the legacy NetworkPolicyManager backend. Shizuku in root mode can also unlock iptables. Use the Shizuku connection controls above.", style = MaterialTheme.typography.bodySmall, color = Muted)
    }

    if (rationale) NetworkSettingsDialog("Allow firewall notifications?", "Notifications report new apps and warn when firewall protection is down or a backend needs access. Allow them so these changes remain visible when the app is closed.", { rationale = false }, confirm = "Grant", cancel = "Cancel", onConfirm = { rationale = false; requestNotifications() })
    if (vpnConflict) NetworkSettingsDialog("Replace the active VPN?", "Granting permission for the firewall’s VPN may disconnect your other VPN. A privileged backend can work alongside it.", { vpnConflict = false }, confirm = "Continue", cancel = "Cancel", onConfirm = { vpnConflict = false; requestVpn() })
    test?.let { rootState ->
        val result = rootState.result
        val outcome = result?.let(::firewallPermissionsRootOutcome)
        val title = when (outcome) {
            null -> "Testing root access"
            FirewallPermissionsRootOutcome.GRANTED -> "Root access granted"
            FirewallPermissionsRootOutcome.TIMEOUT -> "Root test timed out"
            FirewallPermissionsRootOutcome.NO_ACCESS -> "No root access"
            FirewallPermissionsRootOutcome.DENIED -> "Root access denied"
        }
        val detail = when (outcome) {
            null -> "Allow Background Check in the root manager prompt. This test waits up to 30 seconds."
            FirewallPermissionsRootOutcome.GRANTED -> "Background Check has superuser access.\n\n${result?.stdout.orEmpty()}"
            FirewallPermissionsRootOutcome.TIMEOUT -> "The permission request timed out. The root manager may not be responding, or the prompt may still be waiting for an answer. Try again."
            FirewallPermissionsRootOutcome.NO_ACCESS -> "The su command could not run. Use the Shizuku controls above to set up privileged access without root, or check your root installation."
            FirewallPermissionsRootOutcome.DENIED -> "The root manager refused access. Open Magisk, KernelSU or APatch and allow Background Check, then try again."
        }
        NetworkSettingsDialog(title, detail, rootTest::dismiss, confirm = if (rootState.testing) "Cancel" else "OK", extra = if (outcome != null && outcome != FirewallPermissionsRootOutcome.GRANTED) {
            { TextButton(rootTest::start, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Try again") } }
        } else null)
    }
    if (privilegeDialog) FirewallPermissionsPrivilegeDialog(root, shizuku,
        onGrant = { if (root == RootStatus.ROOTED_NO_PERMISSION) rootTest.start() else onShowShizuku() },
        onOpenSetup = onShowShizuku,
        onDismiss = { privilegeDialog = false; deps.superuserBannerState.hideBanner() })
    message?.let { NetworkSettingsDialog("Firewall permissions", it, { message = null }) }
    return content
}

@Composable
internal fun FirewallPermissionsSection(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.testTag("firewall-permissions")) { NetworkSettingsCard("Firewall permissions", content) }
}

@Composable
private fun FirewallPermissionsRow(title: String, description: String, state: FirewallPermissionRowState, tag: String, enabled: Boolean = true, onGrant: () -> Unit) {
    val details: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(state.label, style = MaterialTheme.typography.labelMedium, color = if (state.label == "Granted") Green else if (state.label == "Not required") Cyan else Orange)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            details()
            state.action?.let { OutlinedButton(onGrant, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(it) } }
        }
    } else {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { details() }
            state.action?.let { OutlinedButton(onGrant, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(it) } }
        }
    }
}
