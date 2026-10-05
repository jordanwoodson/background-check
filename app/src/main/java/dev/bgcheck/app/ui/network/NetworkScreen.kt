package dev.bgcheck.app.ui.network

import android.app.Application
import android.content.Intent
import android.os.Process
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.bgcheck.app.data.AndroidProfile
import dev.bgcheck.app.data.AppEntry
import dev.bgcheck.app.data.UiState
import dev.bgcheck.app.ui.*
import dev.bgcheck.network.De1984Application
import dev.bgcheck.network.R
import dev.bgcheck.network.data.multiuser.HiddenApiHelper
import dev.bgcheck.network.domain.firewall.*
import dev.bgcheck.network.domain.model.NetworkPackage
import dev.bgcheck.network.domain.model.PackageId
import dev.bgcheck.network.domain.model.ProfileFilter
import dev.bgcheck.network.presentation.viewmodel.FirewallViewModel
import dev.bgcheck.network.presentation.viewmodel.SettingsViewModel
import dev.bgcheck.network.utils.CompactListPreferences
import dev.bgcheck.network.utils.Constants
import dev.bgcheck.network.utils.NetworkList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun NetworkScreen(mainState: UiState, snackbar: SnackbarHostState, request: NetworkRequest?,
    onRequestHandled: () -> Unit, onStartFirewall: () -> Unit, onEnableVpn: () -> Unit,
    onOpenAppSettings: (AppEntry) -> Unit, onAppDetails: (AppEntry) -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val deps = (application as De1984Application).dependencies
    val model: FirewallViewModel = viewModel(factory = remember(application) { firewallViewModelFactory(application) })
    val settingsModel: SettingsViewModel = viewModel(factory = remember(application) { settingsViewModelFactory(application) })
    val state by model.uiState.collectAsStateWithLifecycle()
    val settings by settingsModel.uiState.collectAsStateWithLifecycle()
    var previousPolicy by remember { mutableStateOf(settings.defaultFirewallPolicy) }
    var previousAllowCritical by remember { mutableStateOf(settings.allowCriticalPackageFirewall) }
    LaunchedEffect(settings.defaultFirewallPolicy, settings.allowCriticalPackageFirewall) {
        if (previousPolicy != settings.defaultFirewallPolicy) model.refreshDefaultPolicy()
        else if (previousAllowCritical != settings.allowCriticalPackageFirewall) model.repaintForCriticalPackagesSetting()
        previousPolicy = settings.defaultFirewallPolicy
        previousAllowCritical = settings.allowCriticalPackageFirewall
    }
    val health by model.firewallHealth.collectAsStateWithLifecycle()
    val backend by deps.firewallManager.activeBackendType.collectAsStateWithLifecycle()
    val npmFull by NetworkPolicySupport.fullBlocking.collectAsStateWithLifecycle()
    val granular = deps.firewallManager.supportsGranularControl()
    val limited = backend == FirewallBackendType.NETWORK_POLICY_MANAGER && !npmFull
    val hasCellular = remember { context.getSystemService(TelephonyManager::class.java)?.phoneType?.let { it != TelephonyManager.PHONE_TYPE_NONE } ?: false }
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var suggestionDismissed by rememberSaveable { mutableStateOf(false) }
    var compact by rememberSaveable { mutableStateOf(CompactListPreferences.isCompact(context, NetworkList.FIREWALL)) }
    var selectedSheet by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingUser by rememberSaveable { mutableIntStateOf(Process.myUid() / 100000) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectionEntries by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var batchOpen by rememberSaveable { mutableStateOf(false) }
    var batchBackend by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmStop by rememberSaveable { mutableStateOf(false) }
    var confirmRule by remember { mutableStateOf<RuleChange?>(null) }
    var resumeVersion by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumeVersion++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val profiles by produceState(emptyList<HiddenApiHelper.UserProfile>(), resumeVersion, state.hasWorkProfile, state.hasCloneProfile, state.isLoadingData) {
        value = withContext(Dispatchers.IO) { HiddenApiHelper.getUsers(context) }
    }
    val profileChoices = networkProfileChoices(profiles.map { ProfileChoice(ProfileFilter.key(it.userId, it.serialNumber), "${it.displayName} (user ${it.userId})") }, state.filterState.profileFilter)
    val notificationsEnabled = remember(resumeVersion, health) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val selection = selectionEntries.mapNotNull(NetworkSelection::decode)
    val selectedPackages = selection.mapNotNull { entry -> model.enforcedPackage(entry.id)?.takeIf { it.identity == entry.identity &&
        selectionRefusal(it, backend, state.blockingContext) == null && profiles.any { profile -> profile.userId == it.userId && profile.serialNumber == it.userSerial && profile.available } } }
    val sheetIdentity = selectedSheet?.let(NetworkSelection::decodeSheet)
    val sheetPackage = sheetIdentity?.let { entry -> model.enforcedPackage(entry.id)?.takeIf { it.identity == entry.identity } }
    val apps = remember(state.packages, state.searchQuery) {
        val query = state.searchQuery.trim()
        state.packages.filter { query.isEmpty() || it.name.contains(query, true) || it.packageName.contains(query, true) }
    }
    fun message(text: String) { scope.launch { snackbar.showSnackbar(text, withDismissAction = true) } }
    fun resetFilters() {
        model.setPackageTypeFilter(Constants.Packages.TYPE_ALL)
        model.setNetworkStateFilter(null)
        model.setInternetOnlyFilter(false)
        model.setProfileFilter("All")
        model.setSearchQuery("")
        focus.clearFocus()
    }
    fun exitSelection() { selectionMode = false; selectionEntries = emptyList(); batchOpen = false }
    fun select(pkg: NetworkPackage) {
        val refusal = selectionRefusal(pkg, backend, state.blockingContext)
        if (refusal != null) { message(refusal); return }
        if (selection.size >= Constants.Packages.MultiSelect.MAX_SELECTION_COUNT && selection.none { it.id == pkg.id }) {
            message("Maximum 50 apps can be selected at once"); return
        }
        selectionMode = true
        selectionEntries = toggleSelection(selection, pkg).map(NetworkSelection::encode)
    }
    fun stop() {
        if (settings.confirmFirewallStop) confirmStop = true else model.stopFirewall()
    }
    fun apply(change: RuleChange, undo: Boolean = false) {
        val blocked = if (undo) !change.blocked else change.blocked
        val pkg = change.pkg
        when (change.control) {
            NetworkControl.WIFI -> model.setWifiBlocking(pkg.packageName, pkg.userId, blocked, pkg.identity)
            NetworkControl.MOBILE -> model.setMobileBlocking(pkg.packageName, pkg.userId, blocked, pkg.identity)
            NetworkControl.ROAMING -> model.setRoamingBlocking(pkg.packageName, pkg.userId, blocked, pkg.identity)
            NetworkControl.LAN -> model.setLanBlocking(pkg.packageName, pkg.userId, blocked, pkg.identity)
            NetworkControl.BACKGROUND -> model.setBackgroundBlocking(pkg.packageName, pkg.userId, blocked, pkg.identity)
            NetworkControl.ALL -> model.setAllNetworkBlocking(pkg.packageName, pkg.userId, blocked, pkg.identity)
        }
    }
    fun quickToggle(pkg: NetworkPackage, control: NetworkControl) {
        if (limited) { selectedSheet = NetworkSelection(pkg.id, pkg.identity).encode(); return }
        val target = if (granular) control else NetworkControl.ALL
        val change = RuleChange(pkg, target, !pkg.blockedOn(target))
        if (settings.confirmRuleChanges) confirmRule = change else {
            apply(change)
            scope.launch {
                val result = snackbar.showSnackbar("${target.label} ${if (change.blocked) "blocked" else "allowed"} for ${pkg.name}", "Undo", withDismissAction = true)
                if (result == SnackbarResult.ActionPerformed) apply(change, undo = true)
            }
        }
    }
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) focus.clearFocus() }
    LaunchedEffect(model) { model.loadNetworkPackages(); model.setUIReady() }
    LaunchedEffect(state.isRenderingUI) { if (state.isRenderingUI) model.setUIReady() }
    LaunchedEffect(state.error) {
        state.error?.let { model.clearError(); snackbar.showSnackbar(it, withDismissAction = true) }
    }
    LaunchedEffect(request) {
        request?.let {
            settingsOpen = false
            selectedSheet = null
            exitSelection()
            if (it.destination != NetworkDestination.OPEN) suggestionDismissed = true
            when (it.destination) {
                NetworkDestination.OPEN -> it.packageName?.let { name ->
                    resetFilters()
                    model.setSearchQuery(name)
                    pendingPackage = name
                    pendingUser = it.userId
                }
                NetworkDestination.START -> onStartFirewall()
                NetworkDestination.VPN_FALLBACK -> onEnableVpn()
                NetworkDestination.TOGGLE -> if (deps.firewallManager.isActive()) stop() else onStartFirewall()
            }
            onRequestHandled()
        }
    }
    LaunchedEffect(pendingPackage, pendingUser, state.isLoading, state.packages) {
        val name = pendingPackage ?: return@LaunchedEffect
        if (!state.isLoading) {
            val pkg = model.enforcedPackage(PackageId(name, pendingUser))
            if (pkg != null) selectedSheet = NetworkSelection(pkg.id, pkg.identity).encode()
            else message("This app is unavailable in User $pendingUser. Refresh the list or check the profile.")
            pendingPackage = null
        }
    }
    LaunchedEffect(state.packages, state.isLoadingData, backend, profiles) {
        if (!state.isLoadingData && profiles.isNotEmpty()) {
            selectionEntries = currentSelection(selection, selectedPackages).map(NetworkSelection::encode)
            if (batchOpen && (selectedPackages.isEmpty() || batchBackend != backend?.name)) batchOpen = false
        }
    }
    LaunchedEffect(selectedSheet, state.packages, state.isLoadingData) {
        if (selectedSheet != null && !state.isLoadingData && sheetPackage == null) {
            selectedSheet = null
            message("This app's installation changed or is unavailable. Refresh the list before changing its rules.")
        }
    }
    BackHandler(enabled = selectionMode && !batchOpen && !settingsOpen && selectedSheet == null) { exitSelection() }
    if (settingsOpen) {
        NetworkSettingsScreen(onBack = { settingsOpen = false })
        return
    }
    Column(Modifier.fillMaxSize().testTag("network-screen")) {
        LazyColumn(state = listState, modifier = Modifier.weight(1f).testTag("network-list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Eyebrow("firewall"); StatusPill(firewallStatus(state.isFirewallEnabled, health), state.isFirewallEnabled && health !is FirewallHealth.Down && health !is FirewallHealth.StopFailed)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Network", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f).semantics { heading() })
                        IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Rounded.Settings, "Firewall settings", tint = Purple) }
                    }
                }
            }
            item {
                Surface(color = Panel, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Firewall", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(if (state.isFirewallEnabled) "On" else "Off", style = MaterialTheme.typography.bodySmall, color = Muted)
                            Switch(checked = state.isFirewallEnabled, onCheckedChange = { if (it) { suggestionDismissed = true; onStartFirewall() } else stop() },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("network-firewall-switch").semantics { contentDescription = "Firewall ${if (state.isFirewallEnabled) "on" else "off"}" }, colors = SwitchDefaults.colors(checkedThumbColor = Green, checkedTrackColor = Green.copy(alpha = 0.2f)))
                        }
                        ResourceValue("Active backend", mainState.protection.backend)
                        ResourceValue("Profiles in scope", mainState.protection.profileScope)
                        Text(mainState.protection.detail, color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (health !is FirewallHealth.Healthy) item {
                val title = FirewallHealthPresenter.title(context, health).orEmpty()
                val text = FirewallHealthPresenter.message(context, health).orEmpty()
                val alertsBlocked = FirewallHealthPresenter.reliesOnNotification(health) && !notificationsEnabled
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoCard(title, text + if (alertsBlocked) "\n\n${stringResource(R.string.firewall_down_notifications_off)}" else "",
                        Icons.Rounded.WarningAmber, accent = if (FirewallHealthPresenter.isCritical(health)) Red else Orange)
                    FirewallHealthPresenter.action(health)?.let { action ->
                        TextButton(onClick = {
                            when (action) {
                                FirewallHealthAction.CHOOSE_BACKEND -> settingsOpen = true
                                FirewallHealthAction.RETRY -> onStartFirewall()
                                FirewallHealthAction.RETRY_STOP -> model.stopFirewall()
                                FirewallHealthAction.REAPPLY_RULES -> deps.firewallManager.reapplyRules()
                                FirewallHealthAction.ENABLE_VPN, FirewallHealthAction.REPLACE_VPN -> onEnableVpn()
                            }
                        }) { Text(if (action == FirewallHealthAction.REAPPLY_RULES) "Reapply" else stringResource(action.label)) }
                    }
                    if (alertsBlocked) TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
                            .onFailure { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }
                    }) { Text("Notification settings") }
                }
            }
            if (!state.isFirewallEnabled && health is FirewallHealth.Healthy && settings.showFirewallStartPrompt && !suggestionDismissed) item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoCard("Turn on the firewall?", "Apply your saved rules to control apps' network access.", Icons.Rounded.Shield, accent = Cyan)
                    Row {
                        TextButton(onClick = { suggestionDismissed = true; onStartFirewall() }) { Text("Start") }
                        TextButton(onClick = { suggestionDismissed = true }) { Text("Not now") }
                    }
                }
            }
            if (limited) item { InfoCard("Limited protection", stringResource(R.string.network_policy_limited), Icons.Rounded.WarningAmber, accent = Orange) }
            if (state.profileWarnings.isNotEmpty()) item { InfoCard("Profile access", state.profileWarnings.joinToString("\n"), Icons.Rounded.WarningAmber, accent = Orange) }
            item {
                if (selectionMode) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${selection.size} selected", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { batchBackend = backend?.name; batchOpen = true }, enabled = selectedPackages.isNotEmpty()) { Text("Rules") }
                    IconButton(onClick = { exitSelection() }) { Icon(Icons.Rounded.Close, "Close selection") }
                } else OutlinedTextField(value = state.searchQuery, onValueChange = model::setSearchQuery,
                    modifier = Modifier.fillMaxWidth().testTag("network-search"), singleLine = true,
                    label = { Text("Search apps") }, placeholder = { Text("Name or package") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = if (state.searchQuery.isNotEmpty()) ({ IconButton(onClick = { model.setSearchQuery("") }) { Icon(Icons.Rounded.Close, "Clear search") } }) else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    shape = RoundedCornerShape(24.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Panel, focusedContainerColor = Panel, unfocusedBorderColor = Comment, focusedBorderColor = Purple))
            }
            item {
                Disclosure("Filters", networkFilterSummary(state.filterState, profileChoices, compact)) {
                    NetworkChips(networkPackageTypeChoices, state.filterState.packageType, model::setPackageTypeFilter)
                    NetworkChips(networkAccessChoices, state.filterState.networkState ?: "Any") {
                        model.setNetworkStateFilter(it.takeUnless { value -> networkFilterMatches(value, "Any") })
                    }
                    FilterChip(selected = state.filterState.internetOnly, onClick = { model.setInternetOnlyFilter(!state.filterState.internetOnly) },
                        label = { Text("Internet-capable only") }, modifier = Modifier.heightIn(min = 48.dp), colors = appFilterColors())
                    NetworkChips(profileChoices, state.filterState.profileFilter, model::setProfileFilter)
                    NetworkChips(listOf(ProfileChoice("comfortable", "Comfortable"), ProfileChoice("compact", "Compact")), if (compact) "compact" else "comfortable") {
                        compact = networkFilterMatches(it, "compact")
                        CompactListPreferences.save(context, NetworkList.FIREWALL, compact)
                    }
                }
            }
            item { Text("${apps.size} ${if (apps.size == 1) "app" else "apps"}${if (state.filterState.internetOnly) " · internet-capable only" else ""}", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(vertical = 6.dp)) }
            if (state.isLoadingData && apps.isEmpty()) item { InfoCard("Finding your apps", "Reading network permissions and saved firewall rules.", Icons.Rounded.Apps) }
            else if (apps.isEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoCard(if (state.scanFailed) "Couldn't read apps" else if (state.packages.isEmpty() && state.searchQuery.isEmpty()) "No apps found" else "No matching apps",
                        if (state.scanFailed) "The app scan failed. Check profile access and try again." else "Try another app name or reset your search and filters.",
                        if (state.scanFailed) Icons.Rounded.WarningAmber else Icons.Rounded.Search)
                    if (state.scanFailed) TextButton(onClick = { model.loadNetworkPackages(forceRefresh = true) }) { Text("Retry") }
                    TextButton(onClick = { resetFilters() }) { Text("Reset filters") }
                }
            } else items(apps, key = { "${it.userId}:${it.packageName}:${it.userSerial}" }) { pkg ->
                NetworkRow(pkg, compact, backend, state.blockingContext, hasCellular, limited,
                    selectionMode, selection.any { it.id == pkg.id }, onClick = {
                        focus.clearFocus()
                        if (selectionMode) select(pkg) else selectedSheet = NetworkSelection(pkg.id, pkg.identity).encode()
                    }, onLongClick = { focus.clearFocus(); select(pkg) }, onToggle = { quickToggle(pkg, it) })
            }
        }
    }
    if (sheetPackage != null) {
        val mainApp = mainState.apps.firstOrNull { it.packageName == sheetPackage.packageName && it.userId == sheetPackage.userId &&
            it.uid == sheetPackage.uid && it.profile.serialNumber == sheetPackage.userSerial }
        val profileName = profiles.firstOrNull { it.userId == sheetPackage.userId }?.displayName ?:
            if (sheetPackage.isWorkProfile) "Work" else if (sheetPackage.isCloneProfile) "Clone" else if (sheetPackage.userId == 0) "Personal" else "User ${sheetPackage.userId}"
        NetworkAppSheet(sheetPackage, mainApp, profileName, backend, state.blockingContext, granular, limited, hasCellular,
            onDismiss = { selectedSheet = null }, onSettings = { selectedSheet = null; settingsOpen = true },
            onOpenAppSettings = { onOpenAppSettings(mainApp ?: AppEntry(sheetPackage.packageName, sheetPackage.name, sheetPackage.uid.takeIf { it >= 0 } ?: sheetPackage.userId * 100000,
                sheetPackage.type == dev.bgcheck.network.domain.model.PackageType.SYSTEM,
                profile = AndroidProfile(sheetPackage.userId, profileName, sheetPackage.isWorkProfile, sheetPackage.userSerial))) },
            onAppDetails = onAppDetails, onChange = { control, blocked -> apply(RuleChange(sheetPackage, control, blocked)) })
    }
    if (batchOpen && selectedPackages.isNotEmpty()) NetworkBatchSheet(selectedPackages, granular, limited, hasCellular,
        backend == FirewallBackendType.IPTABLES, onDismiss = { batchOpen = false }, onChange = { control, blocked ->
            val pairs = selectedPackages.map { it.packageName to it.userId }
            val expected = selectedPackages.associate { it.id to it.identity }
            when (control) {
                NetworkControl.WIFI -> model.batchSetWifiBlocking(pairs, blocked, expected)
                NetworkControl.MOBILE -> model.batchSetMobileBlocking(pairs, blocked, expected)
                NetworkControl.ROAMING -> model.batchSetRoamingBlocking(pairs, blocked, expected)
                NetworkControl.LAN -> model.batchSetLanBlocking(pairs, blocked, expected)
                else -> model.batchSetAllNetworkBlocking(pairs, blocked, expected)
            }
        }, onAllowAll = {
            model.batchAllowPackages(selectedPackages.map { it.id }, selectedPackages.associate { it.id to it.identity }); exitSelection()
        }, onBlockAll = {
            model.batchBlockPackages(selectedPackages.map { it.id }, selectedPackages.associate { it.id to it.identity }); exitSelection()
        })
    if (confirmStop) AlertDialog(onDismissRequest = { confirmStop = false }, title = { Text("Turn off the firewall?") },
        text = { Text("Saved network rules will stop protecting your apps.") }, containerColor = Panel,
        confirmButton = { TextButton(onClick = { confirmStop = false; model.stopFirewall() }) { Text("Turn off") } },
        dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Cancel") } })
    confirmRule?.let { change ->
        AlertDialog(onDismissRequest = { confirmRule = null }, title = { Text("${if (change.blocked) "Block" else "Allow"} ${change.control.label.lowercase()}?") },
            text = { Text("Change ${change.control.label.lowercase()} access for ${change.pkg.name} in User ${change.pkg.userId}.") }, containerColor = Panel,
            confirmButton = { TextButton(onClick = { confirmRule = null; apply(change) }) { Text(if (change.blocked) "Block" else "Allow") } },
            dismissButton = { TextButton(onClick = { confirmRule = null }) { Text("Cancel") } })
    }
    state.batchProgress?.let { progress ->
        AlertDialog(onDismissRequest = {}, title = { Text("Applying rules") }, containerColor = Panel,
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("${progress.current} of ${progress.total} apps"); LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) } }, confirmButton = {})
    }
    state.batchBlockResult?.let { result ->
        AlertDialog(onDismissRequest = model::clearBatchBlockResult, title = { Text("Rules updated") }, containerColor = Panel,
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${result.succeeded.size} succeeded · ${result.failed.size} failed")
                if (result.succeeded.isNotEmpty()) Text("Updated: ${result.succeeded.joinToString()}")
                if (result.failed.isNotEmpty()) Text("Failed: ${result.failed.joinToString()}", color = Orange)
            } }, confirmButton = { TextButton(onClick = model::clearBatchBlockResult) { Text("Done") } })
    }
}

private data class RuleChange(val pkg: NetworkPackage, val control: NetworkControl, val blocked: Boolean)

@Composable
private fun NetworkChips(choices: List<ProfileChoice>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { choice ->
            FilterChip(selected = networkFilterMatches(selected, choice.value), onClick = { onSelect(choice.value) }, label = { Text(choice.label) },
                modifier = Modifier.heightIn(min = 48.dp), colors = appFilterColors())
        }
    }
}
