package dev.bgcheck.app.ui.network

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
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
                    Text(pkg.name, style = MaterialTheme.typography.titleMedium, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                    if (!compact) {
                        Text(pkg.packageName, style = MaterialTheme.typography.bodySmall, color = Muted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (compact && !selecting) Toggles()
            }
            if (!compact) {
                Text("Internet advice: ${rememberInternetRecommendation(pkg).advice.label}",
                    style = MaterialTheme.typography.bodySmall, color = Cyan, maxLines = 2, overflow = TextOverflow.Ellipsis)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = if (large) 1 else 2) {
                    FlowRow(Modifier.weight(1f).align(Alignment.CenterVertically),
                        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (pkg.isWorkProfile) NetworkBadge("Work")
                        if (pkg.isCloneProfile) NetworkBadge("Clone")
                        if (pkg.isSystemCritical) NetworkBadge("System-critical")
                        if (pkg.isVpnApp) NetworkBadge("VPN")
                        if (pkg.permissionsKnown && !pkg.hasInternetPermission) NetworkBadge("No internet")
                    }
                    if (!selecting) Box(Modifier.align(Alignment.CenterVertically)) { Toggles() }
                }
            }
        }
    }
}

@Composable
private fun NetworkBadge(text: String) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(PanelRaised).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(Muted))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Muted,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}
