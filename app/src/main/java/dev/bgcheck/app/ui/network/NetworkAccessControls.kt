package dev.bgcheck.app.ui.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.bgcheck.app.ui.*
import dev.bgcheck.network.R
import dev.bgcheck.network.domain.firewall.BlockingContext
import dev.bgcheck.network.domain.firewall.FirewallBackendType
import dev.bgcheck.network.domain.firewall.UnblockableReason
import dev.bgcheck.network.domain.firewall.unblockableReason
import dev.bgcheck.network.domain.model.NetworkPackage

/** Controls edit this installation's saved rule; the explanation describes actual enforcement. */
@Composable
internal fun NetworkAccessControls(
    pkg: NetworkPackage,
    backend: FirewallBackendType?,
    blockingContext: BlockingContext,
    granular: Boolean,
    limited: Boolean,
    hasCellular: Boolean,
    onChange: (NetworkControl, Boolean) -> Unit,
    onSettings: () -> Unit,
) {
    val saved = pkg.asSaved()
    val protected = (pkg.isSystemCritical || pkg.isVpnApp) && !pkg.paintedAllowCritical
    val reason = backend.unblockableReason(pkg, blockingContext)
    val identityKnown = pkg.uid >= 0 && pkg.userSerial != null
    val enabled = identityKnown && !protected && reason?.fixableHere != false
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (protected) {
            InfoCard("Protected app", stringResource(if (pkg.isSystemCritical) R.string.protection_banner_message_firewall_system
                else R.string.protection_banner_message_firewall_vpn), Icons.Rounded.WarningAmber, accent = Orange)
            TextButton(onClick = onSettings) { Text("Firewall settings") }
        }
        if (!identityKnown) InfoCard("Installation unavailable", "This app's installation could not be verified. Refresh the list before changing its rules.", Icons.Rounded.WarningAmber, accent = Orange)
        val explanation = when {
            protected -> if (pkg.isSystemCritical) R.string.firewall_system_critical_info else R.string.firewall_vpn_app_info
            reason != null -> reasonMessage(reason)
            !pkg.hasInternetPermission -> R.string.firewall_no_internet_info
            (pkg.isSystemCritical || pkg.isVpnApp) && !granular -> R.string.firewall_critical_allowed_info
            pkg.isVpnApp -> R.string.firewall_vpn_app_info
            limited -> R.string.network_policy_limited
            backend == FirewallBackendType.CONNECTIVITY_MANAGER -> R.string.firewall_connectivity_manager_info
            backend == FirewallBackendType.VPN -> R.string.firewall_vpn_info_message
            else -> null
        }
        explanation?.let {
            val message = stringResource(it).replace("turn on the switch for this app", "block access using this app's own rule")
            InfoCard("About these rules", message, Icons.Rounded.Info, accent = Orange)
        }
        val subtitle = if (reason?.fixableHere == false) stringResource(
            if (reason == UnblockableReason.OTHER_PROFILE_FOLLOWS_OWN_PROFILE) R.string.firewall_other_profile_follows_subtitle
            else R.string.firewall_backend_cannot_block_uid) else null
        if (granular) {
            listOf(NetworkControl.WIFI, NetworkControl.MOBILE).plus(if (hasCellular) listOf(NetworkControl.ROAMING) else emptyList()).forEach { control ->
                NetworkAccessSwitch(control.label, !saved.blockedOn(control), enabled, subtitle) { onChange(control, !it) }
            }
        } else {
            NetworkAccessSwitch(if (limited) "Background data" else "Internet access", !saved.blockedOn(NetworkControl.ALL), enabled,
                subtitle ?: if (limited) stringResource(R.string.network_policy_limited) else "Wi-Fi, mobile data and roaming") {
                onChange(NetworkControl.ALL, !it)
            }
        }
        val lanEnabled = granular && backend == FirewallBackendType.IPTABLES && enabled
        NetworkAccessSwitch("Local network", !saved.lanBlocked, lanEnabled,
            if (backend != FirewallBackendType.IPTABLES || !granular) stringResource(R.string.firewall_lan_requires_root) else subtitle) {
            onChange(NetworkControl.LAN, !it)
        }
        val fullyBlocked = if (granular) saved.isFullyBlocked else saved.blockedOn(NetworkControl.ALL)
        if (enabled && !fullyBlocked) {
            NetworkAccessSwitch("Screen-off access", !saved.backgroundBlocked, true,
                "Allow network access while the screen is off. This is separate from background-work and Doze settings.") {
                onChange(NetworkControl.BACKGROUND, !it)
            }
        }
    }
}

private fun reasonMessage(reason: UnblockableReason): Int = when (reason) {
    UnblockableReason.UNKNOWN_UID -> R.string.firewall_unknown_uid_info
    UnblockableReason.OTHER_PROFILE_UNREACHABLE -> R.string.firewall_other_profile_info
    UnblockableReason.OTHER_PROFILE_FOLLOWS_OWN_PROFILE -> R.string.firewall_other_profile_follows_info
    UnblockableReason.PLATFORM_REFUSES_SYSTEM_UID -> R.string.firewall_system_uid_info
    UnblockableReason.SHARED_WITH_PROTECTED_PACKAGE -> R.string.firewall_shared_uid_info
    UnblockableReason.NO_RULE_IN_PROTECTED_UID -> R.string.firewall_no_rule_protected_uid_info
    UnblockableReason.SIBLING_RULE_OVERRIDES_DEFAULT -> R.string.firewall_sibling_rule_info
}

@Composable
internal fun NetworkAccessSwitch(label: String, allowed: Boolean, enabled: Boolean, description: String? = null,
    onAllowed: (Boolean) -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        val large = LocalDensity.current.fontScale > 1.3f
        @Composable fun Title(modifier: Modifier) {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(if (allowed) "Allowed" else "Blocked", style = MaterialTheme.typography.bodySmall, color = if (allowed) Green else Red)
                description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
            }
        }
        @Composable fun Toggle() {
            Switch(checked = allowed, onCheckedChange = onAllowed, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "$label ${if (allowed) "allowed" else "blocked"}" },
                colors = SwitchDefaults.colors(checkedThumbColor = Green, checkedTrackColor = Green.copy(alpha = 0.2f),
                    uncheckedThumbColor = Red, uncheckedTrackColor = Red.copy(alpha = 0.1f)))
        }
        if (large) Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Title(Modifier.fillMaxWidth()); Toggle() }
        else Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Title(Modifier.weight(1f)); Toggle()
        }
    }
}
