package dev.bgcheck.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.bgcheck.app.data.AccessState
import dev.bgcheck.app.data.UiState
import dev.bgcheck.app.ui.network.FirewallPermissionsSection
import dev.bgcheck.app.ui.network.rememberFirewallPermissionsContent
import kotlinx.coroutines.launch

internal fun accessStatusLabel(access: AccessState): String = when (access) {
    AccessState.READY -> "Connected"
    AccessState.CHECKING -> "Checking…"
    AccessState.CONNECTING -> "Connecting…"
    AccessState.NEEDS_PERMISSION -> "Allow access"
    AccessState.NOT_RUNNING -> "Set up access"
    AccessState.ERROR -> "Reconnect"
}

@Composable
internal fun AccessScreen(state: UiState, onConnect: () -> Unit, onOpenShizuku: () -> Unit, onOpenWirelessSettings: () -> Unit) {
    val ready = state.access == AccessState.READY
    val connecting = state.access in setOf(AccessState.CHECKING, AccessState.CONNECTING)
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val firewallPermissions = rememberFirewallPermissionsContent(onShowShizuku = { scope.launch { list.animateScrollToItem(1) } })
    LazyColumn(modifier = Modifier.testTag("access-list"), state = list, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Eyebrow("access / shizuku")
                Text("Connection", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                Text(if (ready) "Everything is ready on this device." else "Enable process readings and app controls.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Panel), border = BorderStroke(1.dp, if (ready) Green.copy(alpha = 0.4f) else Outline)) {
                Column(Modifier.fillMaxWidth().animateContentSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(if (ready) Icons.Rounded.CheckCircle else Icons.Rounded.Key, null, tint = if (ready) Green else Purple, modifier = Modifier.size(28.dp))
                        Text(when (state.access) {
                            AccessState.READY -> "You’re connected"
                            AccessState.CHECKING -> "Checking access"
                            AccessState.CONNECTING -> "Connecting to Shizuku"
                            AccessState.NEEDS_PERMISSION -> "Allow Background Check"
                            AccessState.NOT_RUNNING -> "Start Shizuku"
                            AccessState.ERROR -> "Let’s reconnect"
                        }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    }
                    Text(when (state.access) {
                        AccessState.READY -> "Process readings, battery estimates and verified app controls are available."
                        AccessState.NEEDS_PERMISSION -> state.accessMessage ?: "Shizuku is running. Grant access, then choose Allow in the permission prompt."
                        AccessState.CHECKING, AccessState.CONNECTING -> "Waiting for Shizuku to respond…"
                        AccessState.ERROR -> state.accessMessage ?: "Check that Shizuku is running, then try again."
                        AccessState.NOT_RUNNING -> "Open Shizuku and start its service, then return here. Your connection will be checked automatically."
                    }, style = MaterialTheme.typography.bodyMedium, color = Muted)
                    if (connecting) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Checking Shizuku connection" })
                    if (!ready && !connecting) {
                        Button(onClick = if (state.access == AccessState.NOT_RUNNING) onOpenShizuku else onConnect,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(24.dp)) {
                            Text(when (state.access) {
                                AccessState.NEEDS_PERMISSION -> "Grant access"
                                AccessState.NOT_RUNNING -> "Open Shizuku"
                                else -> "Try again"
                            }, Modifier.weight(1f))
                            Icon(Icons.Rounded.ArrowForward, null, Modifier.size(18.dp))
                        }
                        if (state.access == AccessState.NOT_RUNNING) TextButton(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text("Check connection") }
                        else TextButton(onClick = onOpenShizuku, modifier = Modifier.fillMaxWidth()) { Text("Open Shizuku") }
                    }
                }
            }
        }
        if (ready) item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Access enabled", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Capability("Processes & resources", "Inspect CPU, memory and running apps.")
                Capability("Battery estimates", "Explore Android’s battery history.")
                Capability("App controls", "Review settings, make changes and restore them.")
            }
        }
        item {
            // A newly connected session always starts with onboarding tucked away.
            key(ready) {
                Disclosure("Connection help", summary = if (ready) "Restart or troubleshoot Shizuku" else "Pairing, setup and troubleshooting") {
                    Text("Shizuku uses Android’s wireless debugging to provide access with your permission. No root is needed.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                    SetupStep("01", "Open Shizuku", "Install Shizuku if needed, then choose Start via wireless debugging.", "Open Shizuku", onOpenShizuku)
                    SetupStep("02", "Pair with wireless debugging", "In Developer options, enable Wireless debugging. Open Pair device with pairing code and enter the code in Shizuku’s pairing notification.", "Wireless debugging settings", onOpenWirelessSettings)
                    SetupStep("03", "Start, then allow access", "Tap Start in Shizuku. Return here and allow Background Check when prompted.", if (state.access == AccessState.NEEDS_PERMISSION) "Grant access" else "Check connection", onConnect, !connecting)
                    InfoCard("After a restart", "Start Shizuku again after restarting your phone. Your installed app list remains available without a connection.", Icons.Rounded.Refresh)
                }
            }
        }
        item { FirewallPermissionsSection(firewallPermissions) }
        item { InfoCard("Local by design", "No internet permission or analytics. Resource sampling runs while Monitor is open. Firewall controls live in the Network tab. Your data stays on this device.", Icons.Rounded.Shield, accent = Green) }
    }
}

@Composable
private fun Capability(title: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.Check, null, tint = Green, modifier = Modifier.size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
private fun SetupStep(number: String, title: String, body: String, action: String, onClick: () -> Unit, enabled: Boolean = true) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Text(number, style = MaterialTheme.typography.labelLarge, color = Green)
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Muted)
            OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(24.dp)) {
                Text(action, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}
