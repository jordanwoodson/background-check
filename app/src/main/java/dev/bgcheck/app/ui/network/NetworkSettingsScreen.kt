package dev.bgcheck.app.ui.network

import androidx.compose.runtime.Composable

/** Firewall settings page shown inside the Network tab. Replaced by the full implementation. */
@Composable
internal fun NetworkSettingsScreen(onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    androidx.compose.material3.Text("Firewall settings")
}
