package dev.bgcheck.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.bgcheck.network.domain.model.NetworkChangeRecord

@Composable
internal fun NetworkHistoryCard(record: NetworkChangeRecord, busy: Boolean, onUndo: (String) -> Unit) {
    var confirm by remember(record.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().testTag("history-network-${record.id}"),
        colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text(record.label, style = MaterialTheme.typography.titleMedium)
                    Text("${record.profileName} · User ${record.userId}", style = MaterialTheme.typography.labelSmall, color = Cyan)
                    Text(dateTimeLabel(record.timestamp), style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                if (record.undone) Text("Restored", color = Muted)
                else if (record.recovery) Text("Needs recovery", color = Orange, style = MaterialTheme.typography.labelSmall)
                else if (record.canUndo) TextButton(onClick = { confirm = true }, enabled = !busy) { Text("Undo") }
            }
            Text(record.description, style = MaterialTheme.typography.bodyMedium)
            Text(record.packageName, style = MaterialTheme.typography.bodySmall, color = Muted)
            Text("Before: ${record.before.summary()}", style = MaterialTheme.typography.bodySmall, color = Muted)
            Text("${if (record.recovery) "After (unverified)" else "After"}: ${record.after.summary()}", style = MaterialTheme.typography.bodySmall, color = Cyan)
            if (record.recovery && !record.undone) Text(
                record.recoveryReason ?: "This change was interrupted or could not be verified. Open Network and inspect the current setting before changing it.",
                style = MaterialTheme.typography.bodySmall, color = Orange)
            else if (!record.undone && !record.canUndo) Text("Undo unavailable: installation identity or the exact preceding setting was not verified.", style = MaterialTheme.typography.bodySmall, color = Orange)
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Restore this change?") },
        text = { Text("Restore ${record.label} in ${record.profileName} to: ${record.before.summary()}. The current installation and setting will be checked first. Conflicting changes are left untouched.") },
        confirmButton = { TextButton(onClick = { confirm = false; onUndo(record.id) }, enabled = !busy) { Text("Restore") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }, containerColor = Panel)
}
