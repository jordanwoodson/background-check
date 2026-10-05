package dev.bgcheck.app.ui

import android.app.Application
import android.telephony.TelephonyManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.bgcheck.app.data.AppEntry
import dev.bgcheck.app.ui.network.InternetAdviceCard
import dev.bgcheck.app.ui.network.NetworkAccessControls
import dev.bgcheck.app.ui.network.RefreshNetworkOnSettingsChange
import dev.bgcheck.app.ui.network.applyNetworkControl
import dev.bgcheck.app.ui.network.firewallViewModelFactory
import dev.bgcheck.app.ui.network.rememberInternetRecommendation
import dev.bgcheck.app.ui.network.settingsViewModelFactory
import dev.bgcheck.network.De1984Application
import dev.bgcheck.network.domain.firewall.FirewallBackendType
import dev.bgcheck.network.domain.firewall.FirewallHealth
import dev.bgcheck.network.domain.firewall.NetworkPolicySupport
import dev.bgcheck.network.domain.model.InternetRecommendations
import dev.bgcheck.network.domain.model.PackageId
import dev.bgcheck.network.presentation.viewmodel.FirewallViewModel
import dev.bgcheck.network.presentation.viewmodel.SettingsViewModel

@Composable
internal fun AppDetailNetwork(app: AppEntry, onNetwork: () -> Unit, onSeeInNetwork: () -> Unit,
    onSettings: () -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val deps = (application as De1984Application).dependencies
    val model: FirewallViewModel = viewModel(factory = remember(application) { firewallViewModelFactory(application) })
    val settingsModel: SettingsViewModel = viewModel(factory = remember(application) { settingsViewModelFactory(application) })
    val state by model.uiState.collectAsStateWithLifecycle()
    val settings by settingsModel.uiState.collectAsStateWithLifecycle()
    val backend by deps.firewallManager.activeBackendType.collectAsStateWithLifecycle()
    val health by model.firewallHealth.collectAsStateWithLifecycle()
    val npmFull by NetworkPolicySupport.fullBlocking.collectAsStateWithLifecycle()
    val limited = backend == FirewallBackendType.NETWORK_POLICY_MANAGER && !npmFull
    val granular = deps.firewallManager.supportsGranularControl()
    val hasCellular = remember(context) {
        context.getSystemService(TelephonyManager::class.java)?.phoneType?.let { it != TelephonyManager.PHONE_TYPE_NONE } ?: false
    }
    // Reading state keeps the unfiltered lookup live even when a rule removes this row from a filter.
    val pkg = state.let { model.enforcedPackage(PackageId(app.packageName, app.userId)) }
        ?.takeIf { app.installed && it.uid == app.uid && it.userSerial == app.profile.serialNumber }
    LaunchedEffect(model) { model.loadNetworkPackages(); model.setUIReady() }
    LaunchedEffect(state.isRenderingUI) { if (state.isRenderingUI) model.setUIReady() }
    RefreshNetworkOnSettingsChange(model, settings)
    LaunchedEffect(state.error) {
        state.error?.let { onMessage(it); model.clearError() }
    }
    val recommendation = if (pkg != null) rememberInternetRecommendation(pkg) else remember(app.packageName, app.installed, app.system) {
        InternetRecommendations.forPackage(app.packageName, app.system, installed = app.installed)
    }
    Column(Modifier.testTag("app-detail-network"), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Text("Network access", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(appDetailNetworkStatus(pkg, app.installed, limited), style = MaterialTheme.typography.bodyMedium, color = Muted)
        InternetAdviceCard(recommendation)
        if (pkg != null && (!pkg.permissionsKnown || pkg.hasInternetPermission)) {
            if (!state.isFirewallEnabled && health is FirewallHealth.Healthy) {
                Text("The firewall is off. These rules apply when you turn it on.", style = MaterialTheme.typography.bodySmall, color = Muted)
                TextButton(onClick = onNetwork) { Text("Go to Network") }
            }
            NetworkAccessControls(pkg, backend, state.blockingContext, granular, limited, hasCellular,
                onChange = { control, blocked -> model.applyNetworkControl(pkg, control, blocked) }, onSettings = onSettings)
        }
        if (app.installed) TextButton(onClick = onSeeInNetwork) { Text("See in Network") }
    }
}
