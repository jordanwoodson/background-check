package dev.bgcheck.app.ui.network

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bgcheck.app.ui.*
import dev.bgcheck.network.domain.firewall.BlockingContext
import dev.bgcheck.network.domain.firewall.FirewallBackendType
import dev.bgcheck.network.domain.model.NetworkPackage

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NetworkRow(pkg: NetworkPackage, compact: Boolean,
    backend: FirewallBackendType?, blockingContext: BlockingContext, hasCellular: Boolean, limited: Boolean,
    selecting: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit,
    onToggle: (NetworkControl) -> Unit) {
    val enabled = selectionRefusal(pkg, backend, blockingContext) == null
    val large = LocalDensity.current.fontScale > 1.3f
    Surface(color = Panel, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()
        .testTag("network-row-${pkg.userId}:${pkg.packageName}")
        .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Select app")
        .alpha(if (enabled) 1f else 0.6f)) {
        @Composable fun Toggles() {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val controls = if (limited) listOf(NetworkControl.ALL) else
                    listOf(NetworkControl.WIFI, NetworkControl.MOBILE) + if (hasCellular) listOf(NetworkControl.ROAMING) else emptyList()
                controls.forEach { control ->
                    val presentation = togglePresentation(pkg, control, backend, blockingContext)
                    IconToggleButton(checked = presentation.allowed, onCheckedChange = { onToggle(control) },
                        enabled = presentation.enabled, modifier = Modifier.size(48.dp)
                            .semantics { contentDescription = if (limited) "Background data ${if (presentation.allowed) "allowed" else "restricted"} for ${pkg.name}. Double-tap for details" else presentation.description },
                        colors = IconButtonDefaults.iconToggleButtonColors(
                            containerColor = Red.copy(alpha = 0.08f), contentColor = Red,
                            checkedContainerColor = androidx.compose.ui.graphics.Color.Transparent, checkedContentColor = Green)) {
                        val icon = when (control) {
                            NetworkControl.WIFI -> if (presentation.allowed) Icons.Rounded.Wifi else Icons.Rounded.WifiOff
                            NetworkControl.MOBILE -> if (presentation.allowed) Icons.Rounded.SignalCellularAlt else Icons.Rounded.SignalCellularOff
                            NetworkControl.ROAMING -> if (presentation.allowed) Icons.Rounded.Public else Icons.Rounded.PublicOff
                            else -> if (presentation.allowed) Icons.Rounded.DataUsage else Icons.Rounded.DataSaverOff
                        }
                        Icon(icon, null, Modifier.size(22.dp))
                    }
                }
            }
        }
        Column(Modifier.padding(if (compact) 10.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (selecting) Checkbox(checked = selected, onCheckedChange = { onClick() }, enabled = enabled,
                    modifier = Modifier.size(48.dp), colors = CheckboxDefaults.colors(checkedColor = Purple))
                AppIcon(pkg.packageName, if (compact) 36.dp else 46.dp, pkg.userId, pkg.userSerial)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(pkg.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!compact) {
                        Text(pkg.packageName, style = MaterialTheme.typography.bodySmall, color = Muted)
                        Text("Internet advice: ${rememberInternetRecommendation(pkg).advice.label}", style = MaterialTheme.typography.bodySmall, color = Cyan)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (pkg.isWorkProfile) StatusPill("Work")
                        if (pkg.isCloneProfile) StatusPill("Clone")
                        if (pkg.isSystemCritical) StatusPill("System-critical")
                        if (pkg.isVpnApp) StatusPill("VPN")
                        if (pkg.permissionsKnown && !pkg.hasInternetPermission) StatusPill("No internet")
                    }
                }
                if (!large && !selecting) Toggles()
            }
            if (large && !selecting) Toggles()
        }
    }
}
