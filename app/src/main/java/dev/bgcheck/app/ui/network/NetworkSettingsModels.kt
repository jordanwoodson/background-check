package dev.bgcheck.app.ui.network

import dev.bgcheck.network.domain.firewall.FirewallBackendType
import dev.bgcheck.network.domain.firewall.FirewallMode
import dev.bgcheck.network.domain.model.CaptivePortalPreset
import java.time.LocalDate

internal data class NetworkSettingsBackendOption(
    val mode: FirewallMode,
    val label: String,
    val explanation: String,
    val available: Boolean,
    val requirement: String? = null,
)

internal fun networkSettingsBackendLabel(mode: FirewallMode): String = when (mode) {
    FirewallMode.AUTO -> "Auto (recommended)"
    FirewallMode.VPN -> "VPN"
    FirewallMode.CONNECTIVITY_MANAGER -> "ConnectivityManager"
    FirewallMode.IPTABLES -> "iptables"
    FirewallMode.NETWORK_POLICY_MANAGER -> "NetworkPolicyManager"
}

internal fun networkSettingsActiveMode(backend: FirewallBackendType?): FirewallMode? = when (backend) {
    FirewallBackendType.VPN -> FirewallMode.VPN
    FirewallBackendType.CONNECTIVITY_MANAGER -> FirewallMode.CONNECTIVITY_MANAGER
    FirewallBackendType.IPTABLES -> FirewallMode.IPTABLES
    FirewallBackendType.NETWORK_POLICY_MANAGER -> FirewallMode.NETWORK_POLICY_MANAGER
    null -> null
}

internal fun networkSettingsBackendOptions(
    hasRoot: Boolean,
    hasShizuku: Boolean,
    hasShizukuRoot: Boolean,
    sdk: Int,
    usableModes: Set<FirewallMode>?,
    failedModes: Set<FirewallMode>,
    activeBackend: FirewallBackendType?,
): List<NetworkSettingsBackendOption> {
    val activeMode = networkSettingsActiveMode(activeBackend)
    return listOf(FirewallMode.AUTO, FirewallMode.VPN, FirewallMode.CONNECTIVITY_MANAGER,
        FirewallMode.IPTABLES, FirewallMode.NETWORK_POLICY_MANAGER).map { mode ->
        val explanation = when (mode) {
            FirewallMode.AUTO -> "Automatically choose the best available backend."
            FirewallMode.VPN -> "Use Android’s VPN slot to filter apps in this profile."
            FirewallMode.CONNECTIVITY_MANAGER -> "Block apps independently across profiles using Android’s UID firewall APIs."
            FirewallMode.IPTABLES -> "Kernel-level blocking with local network and per-network controls."
            FirewallMode.NETWORK_POLICY_MANAGER -> "Legacy backend: restrict background data. Foreground traffic can still pass."
        }
        val requirement = when (mode) {
            FirewallMode.AUTO, FirewallMode.VPN -> null
            FirewallMode.CONNECTIVITY_MANAGER -> when {
                sdk < 33 -> "Requires Android 13 or later."
                !hasShizuku -> "Requires Shizuku access. Connect in the Access tab."
                else -> null
            }
            FirewallMode.IPTABLES -> if (!hasRoot && !hasShizukuRoot) "Requires root or Shizuku running in root mode." else null
            FirewallMode.NETWORK_POLICY_MANAGER -> if (!hasShizuku) "Requires Shizuku access. Connect in the Access tab." else null
        }
        // Before the probe answers, use the same privilege estimate as the legacy picker.
        val unavailable = when {
            mode == activeMode -> null
            requirement != null -> requirement
            usableModes != null && mode != activeMode && (mode !in usableModes || mode in failedModes) ->
                "Not supported on this device. The availability check or a previous start failed."
            else -> null
        }
        NetworkSettingsBackendOption(mode, networkSettingsBackendLabel(mode), explanation, unavailable == null, unavailable)
    }
}

internal fun networkSettingsMatchingPreset(httpUrl: String?, httpsUrl: String?): CaptivePortalPreset =
    CaptivePortalPreset.entries.firstOrNull {
        it != CaptivePortalPreset.CUSTOM && it.httpUrl == httpUrl && it.httpsUrl == httpsUrl
    } ?: CaptivePortalPreset.CUSTOM

internal fun networkSettingsBackupFileName(date: LocalDate): String = "background-check-firewall-backup-$date.json"
internal fun networkSettingsPackageFileName(date: LocalDate): String = "background-check-retained-packages-$date.txt"

internal data class FirewallPermissionRowState(val label: String, val action: String?)

internal fun firewallPermissionRowState(granted: Boolean, notRequired: Boolean = false, openSettings: Boolean = false): FirewallPermissionRowState = when {
    notRequired -> FirewallPermissionRowState("Not required", null)
    granted -> FirewallPermissionRowState("Granted", null)
    else -> FirewallPermissionRowState("Needed", if (openSettings) "Open" else "Grant")
}

internal fun firewallNotificationNeedsSettings(sdk: Int, previouslyRequested: Boolean, rationale: Boolean): Boolean =
    sdk < 33 || (previouslyRequested && !rationale)
