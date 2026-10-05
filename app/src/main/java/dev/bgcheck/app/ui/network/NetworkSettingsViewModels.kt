package dev.bgcheck.app.ui.network

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.bgcheck.network.De1984Dependencies
import dev.bgcheck.network.presentation.viewmodel.FirewallViewModel
import dev.bgcheck.network.presentation.viewmodel.SettingsViewModel
import dev.bgcheck.network.ui.permissions.PermissionSetupViewModel

@Composable
internal fun networkSettingsViewModel(): SettingsViewModel {
    val activity = requireNotNull(LocalActivity.current)
    return viewModel(viewModelStoreOwner = activity as ViewModelStoreOwner, factory = remember(activity.application) { settingsViewModelFactory(activity.application) })
}

@Composable
internal fun networkSettingsPermissionViewModel(): PermissionSetupViewModel {
    val activity = requireNotNull(LocalActivity.current)
    val deps = remember(activity.application) { De1984Dependencies.getInstance(activity.application) }
    return viewModel(viewModelStoreOwner = activity as ViewModelStoreOwner, factory = remember(deps) {
        PermissionSetupViewModel.Factory(activity.application, deps.permissionManager, deps.firewallManager)
    })
}

@Composable
internal fun networkSettingsFirewallViewModel(): FirewallViewModel {
    val activity = requireNotNull(LocalActivity.current)
    return viewModel(viewModelStoreOwner = activity as ViewModelStoreOwner, factory = remember(activity.application) { firewallViewModelFactory(activity.application) })
}
