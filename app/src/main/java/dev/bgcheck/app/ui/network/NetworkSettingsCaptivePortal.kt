package dev.bgcheck.app.ui.network

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.bgcheck.app.ui.*
import dev.bgcheck.network.domain.model.CaptivePortalMode
import dev.bgcheck.network.domain.model.CaptivePortalPreset
import dev.bgcheck.network.presentation.viewmodel.SettingsUiState
import dev.bgcheck.network.presentation.viewmodel.SettingsViewModel

@Composable
internal fun NetworkSettingsCaptivePortal(state: SettingsUiState, model: SettingsViewModel, onRestore: () -> Unit, onReset: () -> Unit) {
    val settings = state.captivePortalSettings
    val preset = networkSettingsMatchingPreset(settings?.httpUrl, settings?.httpsUrl)
    var custom by rememberSaveable { mutableStateOf(false) }
    var http by rememberSaveable(settings?.httpUrl) { mutableStateOf(settings?.httpUrl.orEmpty()) }
    var https by rememberSaveable(settings?.httpsUrl) { mutableStateOf(settings?.httpsUrl.orEmpty()) }
    val enabled = state.captivePortalHasPrivileges && !state.captivePortalLoading
    NetworkSettingsCard("Captive portal") {
        Text("Android uses these URLs to detect sign-in pages on public Wi-Fi.", style = MaterialTheme.typography.bodySmall, color = Muted)
        if (state.captivePortalLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Loading captive portal settings…", color = Muted)
        }
        state.captivePortalError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Red) }
        settings?.let {
            ResourceValue("Current mode", networkSettingsPortalModeLabel(it.mode))
            // Long URLs must wrap instead of squeezing the value into a second column.
            Text("HTTP: ${it.httpUrl ?: "Not set"}", style = MaterialTheme.typography.bodySmall, color = Muted)
            Text("HTTPS: ${it.httpsUrl ?: "Not set"}", style = MaterialTheme.typography.bodySmall, color = Muted)
            ResourceValue("Matching preset", networkSettingsPortalPresetLabel(preset))
        }
        if (!state.captivePortalHasPrivileges) Text("Changing captive portal settings requires root or Shizuku access. Connect in the Access tab.", style = MaterialTheme.typography.bodySmall, color = Orange)
        Disclosure("Detection mode", summary = settings?.let { networkSettingsPortalModeLabel(it.mode) }) {
            CaptivePortalMode.entries.forEach { mode ->
                Box(Modifier.testTag("network-settings-portal-mode-${mode.name.lowercase()}")) {
                    PolicyChoice(networkSettingsPortalModeLabel(mode), when (mode) {
                        CaptivePortalMode.FORCED_OFF -> "Completely turn off captive portal detection."
                        CaptivePortalMode.DISABLED -> "Disable captive portal detection."
                        CaptivePortalMode.ENABLED -> "Detect networks that require sign-in (default)."
                    }, settings?.mode == mode, enabled) { model.setCaptivePortalDetectionMode(mode) }
                }
            }
        }
        Disclosure("URL preset", summary = networkSettingsPortalPresetLabel(preset)) {
            CaptivePortalPreset.entries.forEach { option ->
                Box(Modifier.testTag("network-settings-portal-preset-${option.name.lowercase()}")) {
                    PolicyChoice(networkSettingsPortalPresetLabel(option), when (option) {
                        CaptivePortalPreset.GOOGLE -> "Use Google’s connectivity checks."
                        CaptivePortalPreset.GRAPHENEOS -> "Use GrapheneOS connectivity checks."
                        CaptivePortalPreset.KUKETZ -> "Use Kuketz connectivity checks."
                        CaptivePortalPreset.CLOUDFLARE -> "Use Cloudflare connectivity checks."
                        CaptivePortalPreset.CUSTOM -> "Set your own HTTP and HTTPS check URLs."
                    }, if (option == CaptivePortalPreset.CUSTOM) custom || preset == option else !custom && preset == option, enabled) {
                        custom = option == CaptivePortalPreset.CUSTOM
                        if (!custom) model.applyCaptivePortalPreset(option)
                    }
                }
            }
        }
        if (custom || (settings != null && preset == CaptivePortalPreset.CUSTOM)) {
            OutlinedTextField(http, { http = it }, enabled = enabled, label = { Text("HTTP URL") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth().testTag("network-settings-portal-http"), colors = networkSettingsTextFieldColors())
            OutlinedTextField(https, { https = it }, enabled = enabled, label = { Text("HTTPS URL") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth().testTag("network-settings-portal-https"), colors = networkSettingsTextFieldColors())
            OutlinedButton(onClick = { model.setCustomCaptivePortalUrls(http.trim(), https.trim()) }, enabled = enabled && http.isNotBlank() && https.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("network-settings-portal-save")) { Text("Save custom URLs") }
        }
        NetworkSettingsAction("Restore original", "Restore the settings captured before your first change.", "network-settings-portal-restore", enabled && state.captivePortalOriginalCaptured, onRestore)
        NetworkSettingsAction("Reset to Google defaults", "Enable detection with Google’s default URLs.", "network-settings-portal-reset", enabled, onReset)
    }
}

@Composable
private fun networkSettingsTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Panel, unfocusedContainerColor = Panel,
    focusedBorderColor = Purple, unfocusedBorderColor = Comment,
)

internal fun networkSettingsPortalModeLabel(mode: CaptivePortalMode): String = when (mode) {
    CaptivePortalMode.FORCED_OFF -> "Forced off"
    CaptivePortalMode.DISABLED -> "Disabled"
    CaptivePortalMode.ENABLED -> "Enabled"
}

internal fun networkSettingsPortalPresetLabel(preset: CaptivePortalPreset): String = when (preset) {
    CaptivePortalPreset.GOOGLE -> "Google"
    CaptivePortalPreset.GRAPHENEOS -> "GrapheneOS"
    CaptivePortalPreset.KUKETZ -> "Kuketz"
    CaptivePortalPreset.CLOUDFLARE -> "Cloudflare"
    CaptivePortalPreset.CUSTOM -> "Custom"
}
