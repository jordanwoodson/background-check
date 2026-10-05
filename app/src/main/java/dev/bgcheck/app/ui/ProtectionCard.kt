package dev.bgcheck.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.bgcheck.app.data.AccessState
import dev.bgcheck.app.data.ProtectionSummary

@Composable
fun ProtectionCard(access: AccessState, protection: ProtectionSummary, onNetwork: () -> Unit) {
    Surface(color = Panel, shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().testTag("protection-status")) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Access and protection", style = MaterialTheme.typography.titleMedium)
            ResourceValue("Shizuku access", accessStatusLabel(access))
            ResourceValue("Firewall", protection.status)
            ResourceValue("Active backend", protection.backend)
            ResourceValue("Profiles in scope", protection.profileScope)
            Text(protection.detail, style = MaterialTheme.typography.bodySmall, color = if (protection.active) Muted else Orange)
            TextButton(onClick = onNetwork) { Text("Manage firewall") }
        }
    }
}
