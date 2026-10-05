package dev.bgcheck.app.ui.network

import androidx.compose.runtime.Composable
import dev.bgcheck.network.data.common.RootStatus
import dev.bgcheck.network.data.common.ShizukuStatus

@Composable
internal fun FirewallPermissionsPrivilegeDialog(rootStatus: RootStatus, shizukuStatus: ShizukuStatus, onGrant: () -> Unit, onOpenSetup: () -> Unit, onDismiss: () -> Unit) {
    val canGrant = shizukuStatus == ShizukuStatus.RUNNING_NO_PERMISSION || rootStatus == RootStatus.ROOTED_NO_PERMISSION
    val message = when {
        shizukuStatus == ShizukuStatus.NOT_INSTALLED && rootStatus == RootStatus.NOT_ROOTED -> "This action needs root or Shizuku access. Open permission setup to review your options."
        shizukuStatus == ShizukuStatus.INSTALLED_NOT_RUNNING && rootStatus == RootStatus.NOT_ROOTED -> "Shizuku is installed but is not running. Start it using the connection controls in Access, then retry this action."
        else -> "This action needs privileged access. Grant root permission or allow Background Check in the Shizuku connection controls, then retry."
    }
    NetworkSettingsDialog("Privileged access required", message, onDismiss, confirm = if (canGrant) "Grant access" else "Open permission setup", cancel = "Cancel", onConfirm = {
        onDismiss()
        if (canGrant) onGrant() else onOpenSetup()
    })
}
