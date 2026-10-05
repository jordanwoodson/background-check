package dev.bgcheck.app.ui.network

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.bgcheck.app.ui.*
import dev.bgcheck.network.domain.model.NetworkPackage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NetworkBatchSheet(packages: List<NetworkPackage>, granular: Boolean, limited: Boolean,
    hasCellular: Boolean, iptables: Boolean, onDismiss: () -> Unit,
    onChange: (NetworkControl, Boolean) -> Unit, onAllowAll: () -> Unit, onBlockAll: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Ink,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), modifier = Modifier.testTag("network-batch-sheet")) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Rules for ${packages.size} selected apps", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(networkBatchStatus(packages), color = Muted)
            val appDescription = "${packages.size} selected apps, ${packages.map(::networkProfileLabel).distinct().joinToString()}"
            val controls = if (granular) listOf(NetworkControl.WIFI, NetworkControl.MOBILE) +
                (if (hasCellular) listOf(NetworkControl.ROAMING) else emptyList()) +
                (if (iptables) listOf(NetworkControl.LAN) else emptyList()) else listOf(NetworkControl.ALL)
            controls.forEach { control ->
                val blocked = packages.map { it.asSaved().blockedOn(control) }
                NetworkAccessSwitch(if (limited) "Background data" else control.label,
                    allowed = blocked.none { it }, enabled = packages.isNotEmpty(), description = batchStatus(blocked),
                    appDescription = appDescription) {
                    onChange(control, !it)
                }
            }
            if (limited) Text("Only metered background data is restricted on this device. Foreground and unmetered traffic remain allowed.", color = Orange)
            Button(onClick = onAllowAll, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = packages.isNotEmpty()) { Text("Allow all") }
            OutlinedButton(onClick = onBlockAll, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = packages.isNotEmpty()) { Text("Block all") }
        }
    }
}
