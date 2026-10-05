package dev.bgcheck.app.ui.network

import android.app.Application
import androidx.compose.runtime.saveable.listSaver
import dev.bgcheck.network.De1984Application
import dev.bgcheck.network.presentation.viewmodel.FirewallViewModel
import dev.bgcheck.network.presentation.viewmodel.SettingsViewModel

internal fun firewallViewModelFactory(application: Application): FirewallViewModel.Factory {
    val deps = (application as De1984Application).dependencies
    return FirewallViewModel.Factory(application, deps.provideGetNetworkPackagesUseCase(),
        deps.provideManageNetworkAccessUseCase(), deps.superuserBannerState, deps.permissionManager,
        deps.firewallManager, deps.firewallRepository, deps.packageDataChanged)
}

internal fun settingsViewModelFactory(application: Application): SettingsViewModel.Factory {
    val deps = (application as De1984Application).dependencies
    return SettingsViewModel.Factory(application, deps.permissionManager, deps.rootManager,
        deps.shizukuManager, deps.firewallManager, deps.firewallRepository, deps.captivePortalManager,
        deps.bootProtectionManager, deps.provideSmartPolicySwitchUseCase(), deps.packageRepository)
}

internal val networkRequestSaver = listSaver<NetworkRequest?, String>(
    save = { request -> request?.let { listOf(it.destination.name, it.packageName.orEmpty(), it.userId.toString()) } ?: emptyList() },
    restore = { values -> values.takeIf { it.size == 3 }?.let {
        NetworkRequest(NetworkDestination.valueOf(it[0]), it[1].takeIf(String::isNotEmpty), it[2].toInt())
    } },
)
