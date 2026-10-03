package dev.bgcheck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bgcheck.app.data.AccessState
import dev.bgcheck.app.data.AppEntry
import dev.bgcheck.app.data.ControlAction
import dev.bgcheck.app.data.PolicyState
import dev.bgcheck.app.data.UiState
import dev.bgcheck.app.data.bucketLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppDetailSheet(
    state: UiState,
    app: AppEntry,
    onDismiss: () -> Unit,
    onAction: (ControlAction) -> Unit,
    onOpenAppSettings: (String) -> Unit,
    onConnect: () -> Unit,
) {
    val policy = state.policy?.takeIf { it.packageName == app.packageName }
    var confirmation by remember(app.packageName) { mutableStateOf<ControlAction?>(null) }
    val ready = state.access == AccessState.READY
    val writableModes = setOf("allow", "ignore", "deny", "default")
    val canChange = ready && !state.busy && !state.detailLoading && policy != null && policy.error == null && app.protectedReason == null &&
        policy.uidOverride == false && policy.backgroundMode in writableModes && policy.userExempt != null &&
        (app.targetSdk >= 26 || policy.legacyMode in writableModes)
    val legacyRelevant = app.targetSdk < 26
    val backgroundKnown = policy?.backgroundMode != null && (!legacyRelevant || policy.legacyMode != null)
    val canChangeBackground = canChange && backgroundKnown && policy?.uidOverride == false
    val canChangeDoze = canChange && policy?.dozeExempt != null
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ink,
        contentColor = Cream,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("APP DETAILS")
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close app details", tint = Muted) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(15.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app.packageName, 62.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(app.label, style = MaterialTheme.typography.headlineMedium)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = Muted)
                    Text(if (app.system) "System app" else "Installed app", style = MaterialTheme.typography.labelMedium, color = Lime)
                }
            }
            if (state.detailLoading || state.busy) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(if (state.busy) "Applying and checking your change…" else "Reading current settings…", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Eyebrow("OBSERVED ON THIS DEVICE")
                    DetailValue("Process", if (ready) app.processState.label else "Access needed")
                    DetailValue("Standby group", if (ready) bucketLabel(policy?.standbyBucket ?: app.standbyBucket) else "Access needed")
                    HorizontalDivider(color = Outline)
                    Text(if (ready && state.capturedAt != null) "Process snapshot at ${timeLabel(state.capturedAt)}. Being in memory does not measure battery use." else "Connect Shizuku for a process snapshot. Installed apps can still be browsed.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
            if (!ready) {
                InfoCard("Access is needed for controls", "Start Shizuku and allow Background Check to read these settings before making a change.", Icons.Rounded.Lock, accent = Lime)
                Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = state.access !in setOf(AccessState.CHECKING, AccessState.CONNECTING)) { Text("Connect Shizuku") }
            }
            app.protectedReason?.let { InfoCard("Protected app", it + " Use Android Settings to review this app manually.", Icons.Rounded.Lock, accent = Amber) }
            policy?.error?.let { InfoCard("Settings could not be verified", it, Icons.Rounded.WarningAmber, accent = Amber) }
            if (app.system && app.protectedReason == null) {
                InfoCard("Part of your phone’s software", "Changes to a system app can affect features elsewhere on your phone. Only adjust settings you recognize.", Icons.Rounded.WarningAmber, accent = Amber)
            }
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("Background access", style = MaterialTheme.typography.titleLarge)
                Text("Choose whether Android lets this app work when you’re not using it. Restricting it can delay notifications, syncing, and scheduled tasks.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                if (policy != null && ready) {
                    DetailValue("Current rule", backgroundLabel(policy.backgroundMode))
                    if (legacyRelevant && policy.legacyMode != policy.backgroundMode) DetailValue("Legacy rule", backgroundLabel(policy.legacyMode))
                }
                if (policy?.uidOverride == true) {
                    Text("An app-group rule overrides this setting. Quick changes are unavailable; review the app in Android Settings.", style = MaterialTheme.typography.bodySmall, color = Amber)
                } else if (policy?.backgroundMode != null && policy.backgroundMode !in writableModes) {
                    Text("This Android rule needs to be managed in Android Settings. Quick changes are unavailable.", style = MaterialTheme.typography.bodySmall, color = Amber)
                } else if (policy != null && !backgroundKnown && !state.detailLoading) {
                    Text("Android did not return all background rules. Controls stay off until they can be verified.", style = MaterialTheme.typography.bodySmall, color = Amber)
                }
                PolicyChoice("Allow background work", "Let the app work while you’re away.", selected = policy?.backgroundMode == "allow" && (!legacyRelevant || policy.legacyMode == "allow"), enabled = canChangeBackground, onClick = { confirmation = ControlAction.ALLOW_BACKGROUND })
                PolicyChoice("Restrict background work", "Reduce background work; some features may be delayed.", selected = policy?.backgroundMode in setOf("ignore", "deny") && (!legacyRelevant || policy?.legacyMode in setOf("ignore", "deny")), enabled = canChangeBackground, onClick = { confirmation = ControlAction.RESTRICT_BACKGROUND })
                Text("These are Android background rules. They may differ from the battery-mode choices in Motorola Settings.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            HorizontalDivider(color = Outline)
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("Battery optimization", style = MaterialTheme.typography.titleLarge)
                Text("When the phone sits idle, Android pauses some activity to save battery. This is called Doze and is separate from background access.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                DetailValue("Doze exemption", if (!ready) "Access needed" else when (policy?.dozeExempt) { true -> "Exempt"; false -> "Not exempt"; null -> "Unknown" })
                if (policy?.systemExempt == true) {
                    Text("Android includes this app in its built-in exemption list. That exemption cannot be removed here.", style = MaterialTheme.typography.bodySmall, color = Amber)
                }
                PolicyChoice("Keep optimized", "Let Android pause activity while your phone is idle.", selected = policy?.dozeExempt == false, enabled = canChangeDoze && policy?.systemExempt == false, onClick = { confirmation = ControlAction.OPTIMIZE_DOZE })
                PolicyChoice("Allow during Doze", "Let this app do more while the phone is idle.", selected = policy?.dozeExempt == true, enabled = canChangeDoze && policy?.systemExempt == false, onClick = { confirmation = ControlAction.EXEMPT_DOZE })
            }
            policy?.takeIf { ready }?.let {
                Text("Settings checked ${dateTimeLabel(it.capturedAt)}. Changes are read back before being reported as applied.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            state.message?.let { InfoCard("Update", it, Icons.Rounded.Info, accent = Lime) }
            OutlinedButton(onClick = { onOpenAppSettings(app.packageName) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Rounded.Settings, null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(10.dp))
                Text("Open Android app settings", modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(17.dp))
            }
        }
    }
    confirmation?.let { action ->
        val allowed = when (action) {
            ControlAction.ALLOW_BACKGROUND, ControlAction.RESTRICT_BACKGROUND -> canChangeBackground
            ControlAction.EXEMPT_DOZE, ControlAction.OPTIMIZE_DOZE -> canChangeDoze && policy?.systemExempt == false
        }
        AlertDialog(
            onDismissRequest = { confirmation = null },
            icon = { Icon(Icons.Rounded.WarningAmber, null, tint = Amber) },
            title = { Text(actionTitle(action)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("For ${app.label}: " + actionExplanation(action))
                    if (action == ControlAction.EXEMPT_DOZE || action == ControlAction.OPTIMIZE_DOZE) Text("Doze exemptions apply to the package across Android users. This may also affect this app in another user or work profile.", style = MaterialTheme.typography.bodySmall)
                    if (app.system) Text("This is a system app. Changing its behavior could affect other phone features.", color = Amber)
                    Text("The current setting will be saved so you can undo this change.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    if (!allowed) Text("Access or settings changed. Close this dialog and refresh before continuing.", color = Amber)
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmation = null; onAction(action) }, enabled = allowed) { Text(actionConfirm(action)) }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } },
            containerColor = Panel,
        )
    }
}

@Composable
private fun DetailValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun PolicyChoice(title: String, description: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled && !selected,
        color = if (selected) PanelRaised else Panel,
        contentColor = if (enabled || selected) Cream else Muted,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Lime.copy(alpha = 0.6f) else Outline),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (selected) Lime else if (enabled) Cream else Muted)
                Text(description, style = MaterialTheme.typography.bodySmall, color = Muted)
                if (selected) Text("Current setting", style = MaterialTheme.typography.labelMedium, color = Lime)
            }
            if (selected) Icon(Icons.Rounded.Check, contentDescription = "Current setting", tint = Lime, modifier = Modifier.size(20.dp))
            else Icon(if (enabled) Icons.Rounded.ArrowForward else Icons.Rounded.Lock, contentDescription = if (enabled) null else "Unavailable", tint = Muted, modifier = Modifier.size(18.dp))
        }
    }
}

private fun backgroundLabel(mode: String?): String = when (mode) {
    "allow" -> "Allowed"
    "ignore", "deny" -> "Restricted"
    "default" -> "System default"
    "foreground" -> "Foreground only"
    null -> "Unknown"
    else -> mode
}

private fun actionTitle(action: ControlAction): String = when (action) {
    ControlAction.ALLOW_BACKGROUND -> "Allow background work?"
    ControlAction.RESTRICT_BACKGROUND -> "Restrict background work?"
    ControlAction.EXEMPT_DOZE -> "Allow activity during Doze?"
    ControlAction.OPTIMIZE_DOZE -> "Restore battery optimization?"
}

private fun actionExplanation(action: ControlAction): String = when (action) {
    ControlAction.ALLOW_BACKGROUND -> "the app can do work when you’re not using it. This can improve timely updates, but may use more battery. Battery optimization stays separate."
    ControlAction.RESTRICT_BACKGROUND -> "Android will limit background work. Notifications, syncing, uploads, and scheduled tasks may be delayed or stop until you open the app."
    ControlAction.EXEMPT_DOZE -> "the app will be exempt from Doze battery optimization. It may use the network and do more work while the phone is idle, which can use more battery. Other Android limits still apply."
    ControlAction.OPTIMIZE_DOZE -> "Android can defer network access and background work while the phone is idle. Updates may arrive later. Its separate background-access rule will stay as it is."
}

private fun actionConfirm(action: ControlAction): String = when (action) {
    ControlAction.ALLOW_BACKGROUND -> "Allow background work"
    ControlAction.RESTRICT_BACKGROUND -> "Restrict background work"
    ControlAction.EXEMPT_DOZE -> "Allow during Doze"
    ControlAction.OPTIMIZE_DOZE -> "Keep optimized"
}
