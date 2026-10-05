package dev.bgcheck.app.ui.network

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.bgcheck.network.domain.model.NetworkPackage
import dev.bgcheck.network.presentation.viewmodel.FirewallViewModel
import dev.bgcheck.network.presentation.viewmodel.SettingsUiState

internal fun FirewallViewModel.applyNetworkControl(pkg: NetworkPackage, control: NetworkControl, blocked: Boolean) {
    when (control) {
        NetworkControl.WIFI -> setWifiBlocking(pkg.packageName, pkg.userId, blocked, pkg.identity)
        NetworkControl.MOBILE -> setMobileBlocking(pkg.packageName, pkg.userId, blocked, pkg.identity)
        NetworkControl.ROAMING -> setRoamingBlocking(pkg.packageName, pkg.userId, blocked, pkg.identity)
        NetworkControl.LAN -> setLanBlocking(pkg.packageName, pkg.userId, blocked, pkg.identity)
        NetworkControl.BACKGROUND -> setBackgroundBlocking(pkg.packageName, pkg.userId, blocked, pkg.identity)
        NetworkControl.ALL -> setAllNetworkBlocking(pkg.packageName, pkg.userId, blocked, pkg.identity)
    }
}

internal fun FirewallViewModel.applyBatchNetworkControl(packages: List<NetworkPackage>, control: NetworkControl, blocked: Boolean) {
    val pairs = packages.map { it.packageName to it.userId }
    val expected = packages.associate { it.id to it.identity }
    when (control) {
        NetworkControl.WIFI -> batchSetWifiBlocking(pairs, blocked, expected)
        NetworkControl.MOBILE -> batchSetMobileBlocking(pairs, blocked, expected)
        NetworkControl.ROAMING -> batchSetRoamingBlocking(pairs, blocked, expected)
        NetworkControl.LAN -> batchSetLanBlocking(pairs, blocked, expected)
        else -> batchSetAllNetworkBlocking(pairs, blocked, expected)
    }
}

@Composable
internal fun RefreshNetworkOnSettingsChange(model: FirewallViewModel, settings: SettingsUiState) {
    var previousPolicy by remember { mutableStateOf(settings.defaultFirewallPolicy) }
    var previousAllowCritical by remember { mutableStateOf(settings.allowCriticalPackageFirewall) }
    LaunchedEffect(settings.defaultFirewallPolicy, settings.allowCriticalPackageFirewall) {
        if (previousPolicy != settings.defaultFirewallPolicy) model.refreshDefaultPolicy()
        else if (previousAllowCritical != settings.allowCriticalPackageFirewall) model.repaintForCriticalPackagesSetting()
        previousPolicy = settings.defaultFirewallPolicy
        previousAllowCritical = settings.allowCriticalPackageFirewall
    }
}
