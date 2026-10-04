package dev.bgcheck.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.bgcheck.app.data.AccessState
import dev.bgcheck.app.data.AppEntry
import dev.bgcheck.app.data.ControlAction
import dev.bgcheck.app.data.ListSorting
import dev.bgcheck.app.data.ProcessSort
import dev.bgcheck.app.data.ServiceSort
import dev.bgcheck.app.data.SortDirection
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
    onForceStop: () -> Unit,
    onRetry: () -> Unit = {},
    onSelectPackage: (AppEntry) -> Unit = {},
) {
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val compactHeight = with(LocalDensity.current) { windowHeight.toDp() < 480.dp }
    val confirmationModifier = if (compactHeight) Modifier.widthIn(max = 560.dp).fillMaxWidth() else Modifier
    val confirmationProperties = DialogProperties(usePlatformDefaultWidth = !compactHeight)
    val policy = state.policy?.takeIf { app.installed && it.packageName == app.packageName }
    var confirmation by remember(app.packageName) { mutableStateOf<ControlAction?>(null) }
    var confirmForceStop by remember(app.packageName) { mutableStateOf(false) }
    // Keep the full result available after the global snackbar has timed out.
    var sheetMessage by remember(app.packageName) { mutableStateOf(state.message) }
    LaunchedEffect(app.packageName, state.message) {
        state.message?.let { sheetMessage = it }
    }
    // Keep these in the caller's saveable registry, above the sheet's dialog composition.
    var processSort by rememberSaveable(app.packageName) { mutableStateOf(ProcessSort.PID) }
    var processDirection by rememberSaveable(app.packageName) { mutableStateOf(SortDirection.ASCENDING) }
    var serviceSort by rememberSaveable(app.packageName) { mutableStateOf(ServiceSort.NAME) }
    var serviceDirection by rememberSaveable(app.packageName) { mutableStateOf(SortDirection.ASCENDING) }
    val sharedApps = state.apps.filter { it.uid == app.uid }
    val forceStopRestriction = when {
        app.protectedReason != null -> app.protectedReason
        app.system -> "System apps cannot be force-stopped here."
        app.uid % 100_000 < 10_000 -> "Core Android UIDs cannot be force-stopped here."
        sharedApps.size > 1 -> "This UID is shared by several apps. Force stop is unavailable because it may affect other packages."
        sharedApps.none { it.packageName == app.packageName } -> "App identity is unavailable. Refresh the installed app list."
        else -> null
    }
    val ready = state.access == AccessState.READY
    val canForceStop = ready && !state.busy && !state.detailLoading && forceStopRestriction == null
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
        contentColor = Foreground,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("APP DETAILS")
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.Close, "Close app details", tint = Muted) }
                }
                if (state.detailLoading || state.busy) {
                    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(if (state.busy) "Applying and checking your change…" else "Reading current settings…", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
                sheetMessage?.takeIf { !state.busy && !state.detailLoading }?.let { message ->
                    Surface(color = Panel, shape = RoundedCornerShape(6.dp)) {
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(message, modifier = Modifier.weight(1f).padding(vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                                style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { sheetMessage = null }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Rounded.Close, "Dismiss update", tint = Muted)
                            }
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app.packageName, 62.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(app.label, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = Muted)
                        Text((if (app.system) "System app" else "User app") + if (!app.installed) " · Uninstalled" else "", style = MaterialTheme.typography.labelMedium, color = Green)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("What this package does", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    SelectionContainer { Text(app.description.text, style = MaterialTheme.typography.bodyMedium) }
                    Text(app.description.source.label, style = MaterialTheme.typography.bodySmall, color = Muted)
                    PackageSources(app)
                }
                AssociatedPackages(state, app, onSelectPackage)
                if (!app.installed) {
                    InfoCard("Uninstalled for this user", "Android still retains a record of this package. It may retain app data, be part of the system image, or be installed for another user. Live readings and app controls are unavailable here.", Icons.Rounded.Info)
                } else {
                    if (!ready) {
                        val connecting = state.access in setOf(AccessState.CHECKING, AccessState.CONNECTING)
                        val accessTitle = when (state.access) {
                            AccessState.CHECKING -> "Checking access"
                            AccessState.CONNECTING -> "Connecting to Shizuku"
                            AccessState.NEEDS_PERMISSION -> "Permission needed"
                            AccessState.NOT_RUNNING -> "Start Shizuku for app controls"
                            AccessState.ERROR -> "Connection needs attention"
                            AccessState.READY -> "Connected"
                        }
                        val accessDescription = when (state.access) {
                            AccessState.CHECKING, AccessState.CONNECTING -> "App controls will be available once access is verified."
                            AccessState.NEEDS_PERMISSION -> "Shizuku is running. Allow Background Check to read and change app settings."
                            AccessState.NOT_RUNNING -> "Open the access guide to start Shizuku, then return to this app."
                            AccessState.ERROR -> "Review the connection in Access, or use Android app settings below."
                            AccessState.READY -> "App controls are available."
                        }
                        InfoCard(accessTitle, accessDescription, Icons.Rounded.Lock, accent = Green)
                        Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = !connecting, shape = RoundedCornerShape(6.dp)) {
                            Text(if (connecting) "Please wait…" else if (state.access == AccessState.NOT_RUNNING) "Set up access" else "Review access")
                        }
                    } else if (policy == null && !state.detailLoading && !state.busy) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoCard("Settings unavailable", "Read this app’s current settings before making a change.", Icons.Rounded.Info)
                            TextButton(onClick = { sheetMessage = null; onRetry() }) { Text("Retry reading settings") }
                        }
                    }
                    app.protectedReason?.let { InfoCard("Protected app", it + " Use Android Settings to review this app manually.", Icons.Rounded.Lock, accent = Orange) }
                    policy?.error?.let { error ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoCard("Settings could not be verified", error, Icons.Rounded.WarningAmber, accent = Orange)
                            TextButton(onClick = { sheetMessage = null; onRetry() }, enabled = ready && !state.detailLoading && !state.busy) { Text("Retry reading settings") }
                        }
                    }
                    if (app.system && app.protectedReason == null) {
                        InfoCard("Part of your phone’s software", "Changes to a system app can affect features elsewhere on your phone. Only adjust settings you recognize.", Icons.Rounded.WarningAmber, accent = Orange)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Text("Background access", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                        Text("Control work while you’re away. Restrictions can delay notifications, syncing and scheduled tasks.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                        if (policy != null && ready) {
                            DetailValue("Current rule", backgroundLabel(policy.backgroundMode))
                            if (legacyRelevant && policy.legacyMode != policy.backgroundMode) DetailValue("Legacy rule", backgroundLabel(policy.legacyMode))
                        }
                        if (policy?.uidOverride == true) {
                            Text("An app-group rule overrides this setting. Quick changes are unavailable; review the app in Android Settings.", style = MaterialTheme.typography.bodySmall, color = Orange)
                        } else if (policy?.backgroundMode != null && policy.backgroundMode !in writableModes) {
                            Text("This Android rule needs to be managed in Android Settings. Quick changes are unavailable.", style = MaterialTheme.typography.bodySmall, color = Orange)
                        } else if (policy != null && !backgroundKnown && !state.detailLoading) {
                            Text("Android did not return all background rules. Controls stay off until they can be verified.", style = MaterialTheme.typography.bodySmall, color = Orange)
                        }
                        PolicyChoice("Allow background work", "Let the app work while you’re away.", selected = policy?.backgroundMode == "allow" && (!legacyRelevant || policy.legacyMode == "allow"), enabled = canChangeBackground, onClick = { confirmation = ControlAction.ALLOW_BACKGROUND })
                        PolicyChoice("Restrict background work", "Reduce background work; some features may be delayed.", selected = policy?.backgroundMode in setOf("ignore", "deny") && (!legacyRelevant || policy?.legacyMode in setOf("ignore", "deny")), enabled = canChangeBackground, onClick = { confirmation = ControlAction.RESTRICT_BACKGROUND })
                        Text("These rules may differ from the battery modes in Motorola Settings.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    HorizontalDivider(color = Outline)
                    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Text("Battery optimization", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                        Text("Doze pauses activity while your phone is idle. It is separate from background access.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                        DetailValue("Doze exemption", if (!ready) "Access needed" else when (policy?.dozeExempt) { true -> "Exempt"; false -> "Not exempt"; null -> "Unknown" })
                        if (policy?.systemExempt == true) {
                            Text("Android includes this app in its built-in exemption list. That exemption cannot be removed here.", style = MaterialTheme.typography.bodySmall, color = Orange)
                        }
                        PolicyChoice("Keep optimized", "Let Android pause activity while your phone is idle.", selected = policy?.dozeExempt == false, enabled = canChangeDoze && policy?.systemExempt == false, onClick = { confirmation = ControlAction.OPTIMIZE_DOZE })
                        PolicyChoice("Allow during Doze", "Let this app do more while the phone is idle.", selected = policy?.dozeExempt == true, enabled = canChangeDoze && policy?.systemExempt == false, onClick = { confirmation = ControlAction.EXEMPT_DOZE })
                    }
                    HorizontalDivider(color = Outline)
                    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Text("Stop this app", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                        Text("End the app’s current processes and background activity. Unsaved work may be lost. Notifications and scheduled work can stop until you open the app again. There is no Undo for force stop.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                        forceStopRestriction?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Orange) }
                        OutlinedButton(onClick = { confirmForceStop = true }, enabled = canForceStop, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Force stop app") }
                    }
                    policy?.takeIf { ready }?.let {
                        Text("Settings checked ${dateTimeLabel(it.capturedAt)}. Changes are read back before being reported as applied.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    sheetMessage?.let { message ->
                        SelectionContainer { InfoCard("Update", message, Icons.Rounded.Info, accent = Green) }
                    }
                    OutlinedButton(onClick = { onOpenAppSettings(app.packageName) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Rounded.Settings, null, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Open Android app settings", modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(17.dp))
                    }
                    key(app.packageName) {
                        Disclosure("Resource diagnostics", summary = "Processes, battery, memory and services") {
                            DetailValue("Process", if (ready) app.processState.label else "Access needed")
                            DetailValue("Standby group", if (ready) bucketLabel(policy?.standbyBucket ?: app.standbyBucket) else "Access needed")
                            SourceNote(when {
                                !ready -> "Connect Shizuku for a process snapshot. Installed apps can still be browsed."
                                state.capturedAt != null -> "Process snapshot at ${timeLabel(state.capturedAt)}. Being in memory does not measure battery use."
                                state.refreshing -> "Reading the process snapshot…"
                                else -> "Process snapshot unavailable. Refresh to try again."
                            })
                            HorizontalDivider(color = Outline)
                            AppResourceDiagnostics(
                                state, app, processSort, processDirection, serviceSort, serviceDirection,
                                onProcessSort = { processSort = it }, onProcessDirection = { processDirection = it },
                                onServiceSort = { serviceSort = it }, onServiceDirection = { serviceDirection = it },
                            )
                        }
                    }
                }
            }
        }
    }
    if (confirmForceStop) {
        AlertDialog(
            modifier = confirmationModifier,
            properties = confirmationProperties,
            onDismissRequest = { confirmForceStop = false },
            title = { Text("Force stop ${app.label}?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This ends the app’s activity now. Unsaved work may be lost, and notifications, syncing and scheduled work may stop until you open the app again.")
                    Text("Force stop has no Undo. Your background and battery optimization settings remain in place.")
                    if (!canForceStop) Text("App identity or access changed. Close this dialog and refresh before continuing.", color = Orange)
                }
            },
            confirmButton = { TextButton(onClick = { confirmForceStop = false; sheetMessage = null; onForceStop() }, enabled = canForceStop) { Text("Force stop now") } },
            dismissButton = { TextButton(onClick = { confirmForceStop = false }) { Text("Cancel") } },
            containerColor = Panel,
        )
    }
    confirmation?.let { action ->
        val allowed = when (action) {
            ControlAction.ALLOW_BACKGROUND, ControlAction.RESTRICT_BACKGROUND -> canChangeBackground
            ControlAction.EXEMPT_DOZE, ControlAction.OPTIMIZE_DOZE -> canChangeDoze && policy?.systemExempt == false
        }
        AlertDialog(
            modifier = confirmationModifier,
            properties = confirmationProperties,
            onDismissRequest = { confirmation = null },
            icon = if (compactHeight) null else { { Icon(Icons.Rounded.WarningAmber, null, tint = Orange) } },
            title = { Text(actionTitle(action)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("For ${app.label}: " + actionExplanation(action))
                    if (action == ControlAction.EXEMPT_DOZE || action == ControlAction.OPTIMIZE_DOZE) Text("Doze exemptions apply to the package across Android users. This may also affect this app in another user or work profile.", style = MaterialTheme.typography.bodySmall)
                    if (app.system) Text("This is a system app. Changing its behavior could affect other phone features.", color = Orange)
                    Text("The current setting will be saved so you can undo this change.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    if (!allowed) Text("Access or settings changed. Close this dialog and refresh before continuing.", color = Orange)
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmation = null; sheetMessage = null; onAction(action) }, enabled = allowed) { Text(actionConfirm(action)) }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } },
            containerColor = Panel,
        )
    }
}

@Composable
private fun AppResourceDiagnostics(
    state: UiState,
    app: AppEntry,
    processSort: ProcessSort,
    processDirection: SortDirection,
    serviceSort: ServiceSort,
    serviceDirection: SortDirection,
    onProcessSort: (ProcessSort) -> Unit,
    onProcessDirection: (SortDirection) -> Unit,
    onServiceSort: (ServiceSort) -> Unit,
    onServiceDirection: (SortDirection) -> Unit,
) {
    val resources = state.resources
    val uidApps = state.apps.filter { it.uid == app.uid }
    val processes = ListSorting.processes(resources?.processes.orEmpty().filter { it.uid == app.uid }, processSort, processDirection)
    val energy = state.energy?.uids?.firstOrNull { it.uid == app.uid }
    val diagnostics = state.diagnostics?.takeIf { it.packageName == app.packageName }
    val services = ListSorting.services(diagnostics?.services.orEmpty(), serviceSort, serviceDirection)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ResourceValue("Android UID", app.uid.toString())
        if (uidApps.size > 1) {
            Text("Shared UID · ${uidApps.size} apps", style = MaterialTheme.typography.titleMedium, color = Orange)
            SourceNote("${uidApps.joinToString { it.label }} share this UID. All process and battery figures below belong to the shared UID, not solely to ${app.label}.")
        } else {
            SourceNote("Processes match this app’s UID. Isolated processes using a different UID are not included.")
        }
        if (state.access != AccessState.READY) SourceNote("Shizuku access is needed for diagnostics.")
        Text("Processes with this UID", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        when {
            state.resourceLoading && resources == null -> SourceNote("Sampling processes…")
            resources?.processesKnown != true -> SourceNote(if (state.access == AccessState.READY) "Process readings unavailable. Open Monitor and refresh." else "Connect Shizuku to read this app’s processes.")
            processes.isEmpty() -> SourceNote("No processes with this UID were observed in the sample.")
            else -> {
                SortControls(id = "app-process",
                    options = listOf(ProcessSort.CPU, ProcessSort.MEMORY, ProcessSort.VIRTUAL, ProcessSort.NAME, ProcessSort.PID, ProcessSort.STATE),
                    selected = processSort, direction = processDirection,
                    onSort = onProcessSort, onDirection = onProcessDirection)
                processes.forEach { process ->
                    Column(Modifier.testTag("app-process-row-${process.pid}"), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(process.name, style = MaterialTheme.typography.bodyMedium)
                        SourceNote("PID ${process.pid} · parent ${process.parentPid} · ${process.state}")
                        ResourceValue("CPU · one core = 100%", percent(process.cpuPercent), accent = true)
                        ResourceValue("Resident (RSS)", memoryLabel(process.rssKb))
                        ResourceValue("Virtual memory", memoryLabel(process.virtualKb))
                    }
                }
            }
        }
        resources?.let { SourceNote("Process sample ${resourceTimeLabel(it.capturedAt)}. RSS includes shared memory and must not be added as private usage. Virtual memory is address space, not physical RAM use.") }
        HorizontalDivider(color = Outline)
        if (state.energyLoading && state.energy == null) {
            SourceNote("Reading battery estimates…")
        } else {
            ResourceValue("UID battery estimate", mahLabel(energy?.estimatedMah), accent = true)
            ResourceValue("UID CPU time", durationLabel(energy?.cpuTimeMs))
            ResourceValue("UID partial wakelocks", durationLabel(energy?.wakelockMs))
            state.energy?.let { SourceNote("Android battery history · ${durationLabel(it.batteryRealtimeMs)} on battery · sampled ${resourceTimeLabel(it.capturedAt)}. This is cumulative estimated use, not live watts.") }
                ?: SourceNote("Battery estimates unavailable. Open Monitor → Battery use to read Android’s history.")
        }
        HorizontalDivider(color = Outline)
        Text("Package memory and services", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (state.diagnosticsLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            SourceNote(if (diagnostics == null) "Reading detailed memory and running services…" else "Refreshing diagnostics; the previous sample remains below.")
        }
        if (!state.diagnosticsLoading || diagnostics != null) {
            ResourceValue("PSS", memoryLabel(diagnostics?.pssKb))
            ResourceValue("Private dirty", memoryLabel(diagnostics?.privateDirtyKb))
            ResourceValue("Swap PSS", memoryLabel(diagnostics?.swapPssKb))
            SourceNote("PSS apportions shared memory; private dirty is modified memory private to a process. Swap PSS apportions swapped pages. Package reports may include shared processes.")
        }
        when {
            state.diagnosticsLoading && diagnostics == null -> Unit
            diagnostics?.servicesKnown != true -> SourceNote("Running services unavailable.")
            services.isEmpty() -> SourceNote("No running services returned for this package.")
            else -> {
                SortControls(id = "services", options = ServiceSort.entries, selected = serviceSort,
                    direction = serviceDirection, onSort = onServiceSort, onDirection = onServiceDirection)
                services.forEach { service ->
                    Column(Modifier.testTag("service-row-${service.component}:${service.process.orEmpty()}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(service.component, style = MaterialTheme.typography.bodyMedium)
                        SourceNote("${if (service.foreground) "Foreground service" else "Service"} · ${service.process ?: "Process unavailable"}")
                    }
                }
            }
        }
        diagnostics?.let {
            SourceNote("Package diagnostics ${resourceTimeLabel(it.capturedAt)}")
            if (it.warnings.isNotEmpty()) Text(it.warnings.joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = Orange)
        }
    }
}

@Composable
private fun DetailValue(label: String, value: String) {
    ResourceValue(label, value)
}

@Composable
private fun PolicyChoice(title: String, description: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val current = selected
    Surface(
        onClick = onClick,
        enabled = enabled && !selected,
        modifier = Modifier.semantics { this.selected = current },
        color = if (selected) PanelRaised else Panel,
        contentColor = if (enabled || selected) Foreground else Muted,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Green.copy(alpha = 0.6f) else Outline),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (selected) Green else if (enabled) Foreground else Muted)
                Text(description, style = MaterialTheme.typography.bodySmall, color = Muted)
                if (selected) Text("Current setting", style = MaterialTheme.typography.labelMedium, color = Green)
            }
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Green, modifier = Modifier.size(20.dp))
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
