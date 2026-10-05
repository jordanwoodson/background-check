package dev.bgcheck.app.ui.network

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bgcheck.app.ui.*
import dev.bgcheck.network.data.common.RootStatus
import dev.bgcheck.network.data.common.ShizukuStatus
import dev.bgcheck.network.domain.firewall.FirewallMode
import dev.bgcheck.network.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun NetworkSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val model = networkSettingsViewModel()
    val firewall = networkSettingsFirewallViewModel()
    val permissions = networkSettingsPermissionViewModel()
    val state by model.uiState.collectAsStateWithLifecycle()
    val root by model.rootStatus.collectAsStateWithLifecycle()
    val shizuku by model.shizukuStatus.collectAsStateWithLifecycle()
    val backend by model.activeBackendType.collectAsStateWithLifecycle()
    val usable by model.usableModes.collectAsStateWithLifecycle()
    val failed by model.startFailedModes.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    var logs by rememberSaveable { mutableStateOf(false) }
    var warning by rememberSaveable { mutableStateOf<String?>(null) }
    var infoTitle by rememberSaveable { mutableStateOf("Firewall settings") }
    var info by rememberSaveable { mutableStateOf<String?>(null) }
    var vpnInFlight by rememberSaveable { mutableStateOf(false) }
    var license by rememberSaveable { mutableStateOf(false) }
    var licenseText by remember { mutableStateOf<String?>(null) }
    val backup = rememberNetworkSettingsBackupActions(model)
    val hasRoot = root == RootStatus.ROOTED_WITH_PERMISSION
    val hasShizuku = shizuku == ShizukuStatus.RUNNING_WITH_PERMISSION
    val options = networkSettingsBackendOptions(hasRoot, hasShizuku, hasShizuku && model.isShizukuRootMode(), Build.VERSION.SDK_INT, usable, failed, backend)

    BackHandler(enabled = !logs, onBack = onBack)
    LifecycleResumeEffect(model) {
        model.refreshUsableModes()
        model.checkBootProtectionAvailability()
        model.loadCaptivePortalSettings()
        permissions.refreshPermissions()
        onPauseOrDispose {}
    }
    val vpnConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vpnInFlight = false
        model.clearVpnPermissionRequired()
        if (result.resultCode == Activity.RESULT_OK) {
            model.onVpnPermissionGranted()
        } else {
            infoTitle = "VPN permission"
            info = "VPN permission was denied."
            if (model.uiState.value.firewallMode == FirewallMode.VPN) model.setFirewallMode(FirewallMode.AUTO, forceEvenIfOtherVpnActive = true)
        }
        permissions.refreshPermissions()
    }
    LaunchedEffect(state.vpnPermissionRequired) {
        if (state.vpnPermissionRequired && !vpnInFlight) {
            try {
                val intent = model.checkVpnPermissionNeeded()
                if (intent == null) {
                    model.clearVpnPermissionRequired()
                    model.onVpnPermissionGranted()
                } else {
                    vpnInFlight = true
                    vpnConsent.launch(intent)
                }
            } catch (e: Exception) {
                vpnInFlight = false
                model.clearVpnPermissionRequired()
                infoTitle = "VPN permission"
                info = "Could not request VPN permission: ${e.message}"
                if (model.uiState.value.firewallMode == FirewallMode.VPN) model.setFirewallMode(FirewallMode.AUTO, forceEvenIfOtherVpnActive = true)
            }
        }
    }
    LaunchedEffect(license) {
        if (license && licenseText == null) licenseText = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("DE1984-LICENSE.txt").bufferedReader().use { it.readText() } }
                .getOrElse { "Could not read DE1984-LICENSE.txt: ${it.message}" }
        }
    }
    fun openUpstream() {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/dorumrr/de1984".toUri())) }
            .onFailure { infoTitle = "De1984"; info = "Could not open the upstream link: ${it.message}" }
    }
    fun setCriticalFirewall(allow: Boolean) {
        model.setAllowCriticalPackageFirewall(allow)
        firewall.repaintForCriticalPackagesSetting()
    }

    Box(Modifier.fillMaxSize().testTag("network-settings")) {
        if (logs) {
            NetworkLogsScreen { logs = false }
        } else {
            LazyColumn(Modifier.fillMaxSize().testTag("network-settings-list"), state = list, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item(key = "heading") { NetworkSettingsHeader("Firewall settings", "Back to Network", onBack) }
                item(key = "protection") {
                    NetworkSettingsCard("Protection") {
                        Text("Default policy", style = MaterialTheme.typography.titleMedium)
                        Box(Modifier.testTag("network-settings-default-allow")) {
                            PolicyChoice("Allow by default", "Apps can connect unless you block them.", state.defaultFirewallPolicy == Constants.Settings.POLICY_ALLOW_ALL, !state.isLoading) { model.setDefaultFirewallPolicy(Constants.Settings.POLICY_ALLOW_ALL) }
                        }
                        Box(Modifier.testTag("network-settings-default-block")) {
                            PolicyChoice("Block by default", "Apps are blocked unless you allow them; critical packages stay protected.", state.defaultFirewallPolicy == Constants.Settings.POLICY_BLOCK_ALL, !state.isLoading) { model.setDefaultFirewallPolicy(Constants.Settings.POLICY_BLOCK_ALL) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Backend", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            IconButton({ infoTitle = "Firewall backends"; info = networkSettingsBackendInfo }, Modifier.testTag("network-settings-backend-info")) { Icon(Icons.Rounded.Info, "Explain firewall backends", tint = Cyan) }
                        }
                        Text(networkSettingsActiveMode(backend)?.let { "Active: ${networkSettingsBackendLabel(it)}" } ?: "Firewall not running.", style = MaterialTheme.typography.bodySmall, color = Cyan, modifier = Modifier.testTag("network-settings-active-backend"))
                        options.forEach { option ->
                            NetworkSettingsBackendRow(option, state.firewallMode == option.mode) {
                                if (option.available) model.setFirewallMode(option.mode)
                                else { infoTitle = "${option.label} unavailable"; info = "${option.explanation}\n\n${option.requirement}" }
                            }
                        }
                        HorizontalDivider(color = Outline)
                        NetworkSettingsToggle("Boot protection", when {
                            !state.bootProtectionAvailable && state.bootProtection -> "Boot protection is still installed, but root access is unavailable. Every boot blocks apps for up to 2 minutes, then the script lifts itself. Retry removal can recover a denied root grant."
                            !state.bootProtectionAvailable -> "Requires root and Magisk boot scripts."
                            else -> "Block all traffic during startup until the firewall activates. Changing this restarts your phone."
                        }, state.bootProtection, "network-settings-boot", state.bootProtectionAvailable && !state.bootProtectionRemovalInProgress) { warning = if (it) "boot-enable" else "boot-disable" }
                        if (state.bootProtection && !state.bootProtectionAvailable) NetworkSettingsAction("Retry removal", "Request root again and try to remove the boot script. Your phone will restart after removal.", "network-settings-boot-retry", !state.bootProtectionRemovalInProgress) { warning = "boot-retry" }
                    }
                }
                item(key = "behaviour") {
                    NetworkSettingsCard("Behavior") {
                        NetworkSettingsToggle("New-app notifications", "Notify when a newly installed app needs a firewall rule.", state.newAppNotifications, "network-settings-new-apps", onChange = model::setNewAppNotifications)
                        NetworkSettingsToggle("Suggest turning on the firewall", "Show a start suggestion when protection is off.", state.showFirewallStartPrompt, "network-settings-start-prompt", onChange = model::setShowFirewallStartPrompt)
                        NetworkSettingsToggle("Confirm quick rule changes", "Ask before changing a rule from an app row.", state.confirmRuleChanges, "network-settings-confirm-rules", onChange = model::setConfirmRuleChanges)
                        NetworkSettingsToggle("Confirm before stopping the firewall", "Ask before turning off firewall protection.", state.confirmFirewallStop, "network-settings-confirm-stop", onChange = model::setConfirmFirewallStop)
                    }
                }
                item(key = "safety") {
                    NetworkSettingsCard("Safety overrides") {
                        NetworkSettingsToggle("Allow firewall rules on critical packages", "Allow changes for system-critical packages and VPN apps.", state.allowCriticalPackageFirewall, "network-settings-critical-firewall") { if (it) warning = "critical-firewall" else setCriticalFirewall(false) }
                        NetworkSettingsToggle("Allow uninstalling critical packages", "Allow removal of packages Android may need to work.", state.allowCriticalPackageUninstall, "network-settings-critical-uninstall") { if (it) warning = "critical-uninstall" else model.setAllowCriticalPackageUninstall(false) }
                    }
                }
                item(key = "captive-portal") { NetworkSettingsCaptivePortal(state, model, onRestore = { warning = "portal-restore" }, onReset = { warning = "portal-reset" }) }
                item(key = "backup") {
                    NetworkSettingsCard("Backup and restore") {
                        NetworkSettingsAction("Back up firewall rules", "Save a JSON backup of your rules.", "network-settings-backup", !state.isLoading, backup.backup)
                        NetworkSettingsAction("Restore firewall rules", "Preview a JSON backup, then merge or replace your rules.", "network-settings-restore", !state.isLoading, backup.restore)
                        NetworkSettingsAction("Export retained-package list", "Save removed system packages with their profile identities.", "network-settings-export-packages", (hasRoot || hasShizuku) && !state.isLoading, backup.export)
                        NetworkSettingsAction("Import retained-package list", "Preview apps to uninstall from the profiles in a saved list.", "network-settings-import-packages", (hasRoot || hasShizuku) && !state.isLoading, backup.import)
                        if (!hasRoot && !hasShizuku) Text("Package-list export and import require root or Shizuku access. Connect in the Access tab.", style = MaterialTheme.typography.bodySmall, color = Orange)
                    }
                }
                item(key = "diagnostics") { NetworkSettingsCard("Diagnostics") { NetworkSettingsAction("App logs", "View, clear or share firewall diagnostics.", "network-settings-logs") { logs = true } } }
                item(key = "about") {
                    NetworkSettingsCard("About") {
                        Text("Firewall and package controls adapted from De1984 (MIT).", style = MaterialTheme.typography.bodyMedium)
                        Text("Copyright 2025 Doru Moraru", style = MaterialTheme.typography.bodySmall, color = Muted)
                        ResourceValue("App version", "${state.appVersion} (${state.buildNumber})")
                        NetworkSettingsAction("Doru Moraru · De1984 upstream", "github.com/dorumrr/de1984", "network-settings-upstream", onClick = ::openUpstream)
                        NetworkSettingsAction("DE1984-LICENSE.txt", "Read the bundled MIT license and copyright notice.", "network-settings-license") { license = true }
                    }
                }
            }
        }
    }

    if (state.showVpnConflictWarning) NetworkSettingsDialog("Replace the active VPN?", "Android allows one VPN at a time. Using the firewall’s VPN backend will disconnect the other VPN. Continue only if you want to replace it.", model::cancelPendingModeChange, confirm = "Replace VPN", cancel = "Cancel", onConfirm = model::confirmPendingModeChange)
    warning?.let { choice ->
        val title = when (choice) {
            "boot-enable" -> "Enable boot protection?"
            "boot-disable", "boot-retry" -> "Remove boot protection?"
            "critical-firewall" -> "Allow critical firewall rules?"
            "critical-uninstall" -> "Allow critical package removal?"
            "portal-restore" -> "Restore original captive portal settings?"
            else -> "Reset captive portal to Google defaults?"
        }
        val message = when (choice) {
            "boot-enable" -> "Your phone will restart immediately. All traffic is blocked during boot until the firewall starts. If it cannot start, network access may be unavailable.\n\nRecovery: open Network → Firewall settings and remove boot protection. If needed, use ADB and root:\nadb shell\nsu\nrm ${Constants.BootProtection.BOOT_SCRIPT_PATH}\nreboot"
            "boot-disable", "boot-retry" -> "Your phone will restart immediately after the boot script is removed. Apps may send data during boot before the firewall starts."
            "critical-firewall" -> "This allows blocking system-critical packages (Wi-Fi, Bluetooth, Download Manager) and VPN apps. Blocking them may break connectivity, prevent Wi-Fi or Bluetooth from working, or cause VPN reconnection problems. Recovery may require ADB or a factory reset. Enable only if you understand these effects."
            "critical-uninstall" -> "Removing system-critical packages can stop Android from working or prevent your phone from booting. Recovery may require a factory reset. Enable only if you understand these effects."
            "portal-restore" -> "Restore the captive portal settings captured before your first change?"
            else -> "Enable captive portal detection and use Google’s default check URLs?"
        }
        NetworkSettingsDialog(title, message, { warning = null }, confirm = if (choice.startsWith("boot")) "Continue and restart" else "Continue", cancel = "Cancel", onConfirm = {
            warning = null
            when (choice) {
                "boot-enable" -> model.setBootProtection(true)
                "boot-disable" -> model.setBootProtection(false)
                "boot-retry" -> model.retryRemoveBootProtection()
                "critical-firewall" -> setCriticalFirewall(true)
                "critical-uninstall" -> model.setAllowCriticalPackageUninstall(true)
                "portal-restore" -> model.restoreOriginalCaptivePortalSettings()
                "portal-reset" -> model.resetCaptivePortalToGoogleDefaults()
            }
        })
    }
    if (state.bootProtectionRemovalInProgress && state.error == null) NetworkSettingsProgress("Boot protection", "Requesting root and removing boot protection…")
    if (state.isRebooting) NetworkSettingsProgress("Boot protection", "Boot protection removed. Restarting your phone…")
    state.error?.let { NetworkSettingsDialog("Could not change settings", it, model::clearError) }
    state.message?.let { NetworkSettingsDialog(state.messageTitleRes?.let(context::getString) ?: "Success", it, model::clearMessage) }
    info?.let { NetworkSettingsDialog(infoTitle, it, { info = null }) }
    if (license) NetworkSettingsDialog("De1984 license", licenseText ?: "Loading DE1984-LICENSE.txt…", { license = false })
}

@Composable
private fun NetworkSettingsBackendRow(option: NetworkSettingsBackendOption, current: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (current) PanelRaised else Panel, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, if (current) Green.copy(alpha = 0.6f) else Outline),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("network-settings-backend-${option.mode.name.lowercase()}").semantics { selected = current }) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(option.label, style = MaterialTheme.typography.titleMedium, color = if (!option.available) Muted else if (current) Green else Foreground)
                Text(option.explanation, style = MaterialTheme.typography.bodySmall, color = Muted)
                Text(option.requirement ?: if (current) "Current setting · Available" else "Available", style = MaterialTheme.typography.bodySmall, color = if (option.available) Green else Orange)
            }
            if (current) Icon(Icons.Rounded.Check, null, tint = Green, modifier = Modifier.size(20.dp))
        }
    }
}

private const val networkSettingsBackendInfo = "Auto chooses the best available backend, preferring iptables, then supported ConnectivityManager, with VPN as a fallback.\n\nVPN works without root or Shizuku. It filters packets in user space, supports Wi-Fi, mobile data and roaming rules, uses Android’s only VPN slot, and controls apps in this profile.\n\nConnectivityManager requires Shizuku, Android 13 or later, and supported UID firewall APIs. It blocks all networks together with independent rules across profiles. It works alongside other VPN apps.\n\niptables requires root or Shizuku in root mode. It filters traffic in the kernel, supports Wi-Fi, mobile data, roaming and local network rules, and works alongside other VPN apps.\n\nNetworkPolicyManager is a legacy Shizuku backend for older devices. It restricts background data; foreground traffic may still pass, and Wi-Fi blocking can vary by device. It leaves the VPN slot free. Prefer ConnectivityManager when available.\n\nA privilege grant does not guarantee the device supports a backend. Unavailable rows explain their requirements."
