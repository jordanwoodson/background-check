package dev.bgcheck.app.ui.network

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import dev.bgcheck.app.ui.*

@Composable
internal fun NetworkSettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Panel), border = BorderStroke(1.dp, Outline)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        }
    }
}

@Composable
internal fun NetworkSettingsHeader(title: String, backDescription: String, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onBack, modifier = Modifier.testTag("network-page-back")) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, backDescription)
        }
        Eyebrow("network / settings")
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    }
}

@Composable
internal fun NetworkSettingsToggle(title: String, description: String, checked: Boolean, tag: String, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)
        .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
    val text: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) Foreground else Muted)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            text()
            Switch(checked, onCheckedChange = null, enabled = enabled)
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { text() }
            Switch(checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

@Composable
internal fun NetworkSettingsAction(title: String, description: String? = null, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)) {
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) Foreground else Muted)
                description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
            }
            Icon(Icons.Rounded.ArrowForward, null, tint = Muted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun NetworkSettingsDialog(title: String, message: String, onDismiss: () -> Unit, confirm: String = "OK", onConfirm: () -> Unit = onDismiss, cancel: String? = null, extra: (@Composable () -> Unit)? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text(title) },
        text = { Text(message, Modifier.heightIn(max = networkSettingsDialogMaxHeight()).verticalScroll(rememberScrollState()), color = Muted) },
        confirmButton = {
            Column(Modifier.fillMaxWidth()) {
                TextButton(onConfirm, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(confirm) }
                extra?.invoke()
            }
        },
        dismissButton = cancel?.let { { TextButton(onDismiss, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(it) } } },
    )
}

@Composable
internal fun networkSettingsDialogMaxHeight(): Dp {
    val windowHeight = LocalWindowInfo.current.containerSize.height
    return with(LocalDensity.current) { (windowHeight / 2).toDp() }
}

@Composable
internal fun NetworkSettingsProgress(title: String, message: String) {
    AlertDialog(
        onDismissRequest = {}, containerColor = Panel,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { Text(message, color = Muted); LinearProgressIndicator(Modifier.fillMaxWidth()) } },
        confirmButton = {},
    )
}
