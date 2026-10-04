package dev.bgcheck.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bgcheck.app.data.AccessState
import dev.bgcheck.app.data.AndroidProfile
import dev.bgcheck.app.data.AppEntry
import dev.bgcheck.app.data.AppSort
import dev.bgcheck.app.data.ComponentSort
import dev.bgcheck.app.data.EnergySort
import dev.bgcheck.app.data.ListSorting
import dev.bgcheck.app.data.ProcessSort
import dev.bgcheck.app.data.SortDirection
import dev.bgcheck.app.data.powerComponentLabel
import dev.bgcheck.app.data.ProcessResource
import dev.bgcheck.app.data.UiState
import dev.bgcheck.app.data.UidEnergy

private enum class MonitorMode(val label: String) { LIVE("Live processes"), ENERGY("Battery use") }
private enum class MonitorProcessFilter(val label: String) { ALL("All"), APPS("App processes"), SYSTEM("System / native") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MonitorScreen(
    state: UiState,
    onRefreshResources: () -> Unit,
    onRefreshEnergy: () -> Unit,
    onSetPaused: (Boolean) -> Unit,
    onSetInterval: (Long) -> Unit,
    onSelectApp: (AppEntry) -> Unit,
    onAccess: () -> Unit,
) {
    var profileId by rememberSaveable { mutableStateOf(ALL_PROFILES) }
    val profiles = state.visibleProfiles()
    var mode by rememberSaveable { mutableStateOf(MonitorMode.LIVE) }
    var processQuery by rememberSaveable { mutableStateOf("") }
    var energyQuery by rememberSaveable { mutableStateOf("") }
    val liveListState = rememberLazyListState()
    val energyListState = rememberLazyListState()
    val modeState = rememberSaveableStateHolder()
    var filter by rememberSaveable { mutableStateOf(MonitorProcessFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(ProcessSort.CPU) }
    var processDirection by rememberSaveable { mutableStateOf(ProcessSort.CPU.defaultDirection) }
    var energySort by rememberSaveable { mutableStateOf(EnergySort.BATTERY) }
    var energyDirection by rememberSaveable { mutableStateOf(EnergySort.BATTERY.defaultDirection) }
    var componentSort by rememberSaveable { mutableStateOf(ComponentSort.BATTERY) }
    var componentDirection by rememberSaveable { mutableStateOf(ComponentSort.BATTERY.defaultDirection) }
    var relatedSort by rememberSaveable { mutableStateOf(AppSort.NAME) }
    var relatedDirection by rememberSaveable { mutableStateOf(AppSort.NAME.defaultDirection) }
    var selectedProcess by remember { mutableStateOf<ProcessResource?>(null) }
    var selectedAt by remember { mutableStateOf<Long?>(null) }
    var selectedEnergy by remember { mutableStateOf<UidEnergy?>(null) }
    var selectedEnergyAt by remember { mutableStateOf<Long?>(null) }
    val appsByUid = remember(state.apps) { ListSorting.apps(state.apps, AppSort.NAME, SortDirection.ASCENDING).groupBy { it.uid } }
    val ready = state.access == AccessState.READY
    val largeText = LocalDensity.current.fontScale > 1.3f
    val hasResourceReadings = state.resources?.let { it.cpuPercent != null || it.memory != null || it.processesKnown } == true
    val focusManager = LocalFocusManager.current
    val processes = remember(state.resources, profileId, processQuery, filter, sort, processDirection, appsByUid) {
        val needle = processQuery.trim()
        state.resources?.processes.orEmpty().filter { process ->
            val packages = appsByUid[process.uid].orEmpty()
            val userApp = packages.any { !it.system }
            val matchesScope = when (filter) {
                MonitorProcessFilter.ALL -> true
                MonitorProcessFilter.APPS -> userApp
                MonitorProcessFilter.SYSTEM -> !userApp
            }
            (profileId == ALL_PROFILES || process.uid / 100000 == profileId) && matchesScope && (needle.isEmpty() || listOf(process.pid.toString(), process.parentPid.toString(), process.uid.toString(), process.user, process.name, process.command)
                .any { it.contains(needle, ignoreCase = true) } || packages.any { it.label.contains(needle, true) || it.packageName.contains(needle, true) })
        }.let { ListSorting.processes(it, sort, processDirection) }
    }
    val energy = remember(state.energy, profileId, energyQuery, energySort, energyDirection, appsByUid) {
        val needle = energyQuery.trim()
        state.energy?.uids.orEmpty().filter { item ->
            (profileId == ALL_PROFILES || item.uid / 100000 == profileId) &&
                (needle.isEmpty() || item.uid.toString().contains(needle) || appsByUid[item.uid].orEmpty()
                    .any { it.label.contains(needle, true) || it.packageName.contains(needle, true) })
        }.let { ListSorting.energy(it, energySort, energyDirection, appsByUid) }
    }
    val components = remember(state.energy, componentSort, componentDirection) {
        ListSorting.components(state.energy?.components.orEmpty(), componentSort, componentDirection)
    }
    // Capture the selected mode for this layout. Reading mutable mode inside the
    // lazy content could switch the outgoing list's data before it is disposed,
    // clamping that mode's saved position against the other mode's item count.
    val displayedMode = mode
    Column(Modifier.fillMaxSize()) {
        Surface(color = Ink) {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MonitorMode.entries.forEach { item ->
                    FilterChip(selected = mode == item, onClick = { focusManager.clearFocus(); mode = item },
                        modifier = Modifier.heightIn(min = 48.dp), label = { Text(item.label) })
                }
            }
        }
        // Give each mode a distinct lazy layout as well as a saved scroll position.
        // This prevents item anchoring or placement animations leaking between lists.
        key(displayedMode) {
            modeState.SaveableStateProvider(displayedMode.name) {
                LazyColumn(
                    state = if (displayedMode == MonitorMode.LIVE) liveListState else energyListState,
                    modifier = Modifier.weight(1f).testTag("monitor-list"),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "monitor-heading") {
                        Text("Resource monitor", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                    }
                    item(key = "profile-filters") {
                        ProfileFilters("monitor", profiles, profileId) { profileId = it; selectedProcess = null; selectedEnergy = null }
                        SourceNote("Profile filters apply to processes and UID battery use. Device CPU, RAM, battery flow and component totals cover the whole device.")
                    }
                    if (displayedMode == MonitorMode.ENERGY) {
                        item(key = "battery-flow") { BatteryFlowCard(state.battery) }
                    }
                    if (!ready) {
                        item(key = "monitor-access") { MonitorAccessCard(state.access, onAccess) }
                    } else if (displayedMode == MonitorMode.LIVE) {
                        item(key = "sampling") {
                            ResourceCard("Live sampling") {
                                FlowRow(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    StatusPill(when {
                                        state.resourceLoading -> "Sampling…"
                                        state.resources == null -> "Waiting for sample"
                                        !hasResourceReadings -> "Readings unavailable"
                                        state.monitorPaused -> "Paused"
                                        state.monitoring -> "Live"
                                        else -> "Snapshot"
                                    }, hasResourceReadings && state.monitoring && !state.monitorPaused)
                                    Text("${state.monitorIntervalMs / 1000}s interval", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(vertical = 6.dp))
                                }
                                if (largeText) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { onSetPaused(!state.monitorPaused) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(6.dp)) {
                                            Text(if (state.monitorPaused) "Resume" else "Pause")
                                        }
                                        OutlinedButton(onClick = onRefreshResources, enabled = !state.resourceLoading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(6.dp)) { Text("Refresh now") }
                                    }
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { onSetPaused(!state.monitorPaused) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(6.dp)) {
                                            Text(if (state.monitorPaused) "Resume" else "Pause")
                                        }
                                        OutlinedButton(onClick = onRefreshResources, enabled = !state.resourceLoading, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(6.dp)) { Text("Refresh now") }
                                    }
                                }
                                SourceNote(state.resources?.let { "Last sample ${resourceTimeLabel(it.capturedAt)}" } ?: "No sample yet")
                                Box(Modifier.fillMaxWidth().height(4.dp)) {
                                    if (state.resourceLoading) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Sampling resources" })
                                }
                            }
                        }
                        state.resources?.let { resources ->
                            item(key = "whole-device") {
                                ResourceCard("Whole device") {
                                    ResourceValue("CPU · all cores", percent(resources.cpuPercent), accent = true)
                                    ResourceValue("RAM available", memoryLabel(resources.memory?.availableKb), accent = true)
                                    Disclosure("Device readings", "CPU cores, RAM and swap") {
                                        ResourceValue("CPU cores", resources.cpuCoreCount?.toString() ?: "Unavailable")
                                        ResourceValue("RAM total", memoryLabel(resources.memory?.totalKb))
                                        ResourceValue("Swap used", resources.memory?.let { memoryLabel((it.swapTotalKb - it.swapFreeKb).coerceAtLeast(0)) } ?: "Unavailable")
                                        ResourceValue("Swap total", memoryLabel(resources.memory?.swapTotalKb))
                                        SourceNote("Device CPU is 0–100% of all cores. Available RAM includes memory Android can reclaim. Swap can include compressed RAM (zram).")
                                    }
                                }
                            }
                            item(key = "resource-trends") { ResourceTrends(state.resourceHistory, resources.memory?.totalKb) }
                            if (resources.warnings.isNotEmpty()) {
                                item(key = "resource-warnings") { InfoCard("Some live readings are unavailable", resources.warnings.joinToString("\n"), Icons.Rounded.WarningAmber, accent = Orange) }
                            }
                        }
                        item(key = "sampling-options") {
                            Disclosure("Sampling options") {
                                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(3_000L, 5_000L, 10_000L).forEach { interval ->
                                        FilterChip(selected = state.monitorIntervalMs == interval, onClick = { onSetInterval(interval) },
                                            modifier = Modifier.heightIn(min = 48.dp), label = { Text("${interval / 1000} seconds") })
                                    }
                                }
                                SourceNote("Only samples while this screen is open and the app is in the foreground. Faster sampling adds monitoring overhead.")
                                state.resources?.let { SourceNote("CPU interval ${durationLabel(it.sampleDurationMs)}") }
                            }
                        }
                        if (state.resources?.processesKnown != true) {
                            item(key = "process-unavailable") {
                                InfoCard(if (state.resourceLoading) "Reading processes…" else "Process list unavailable",
                                    if (state.resourceLoading) "The first sample takes a moment." else "Use Refresh now to try again. Android or the device vendor may restrict some readings.", Icons.Rounded.Info)
                            }
                        } else if (state.resources.processes.isEmpty()) {
                            item(key = "process-empty") { InfoCard("No processes observed", "No shell-visible processes were returned. Use Refresh now to take another sample.", Icons.Rounded.Info) }
                        } else {
                            item(key = "process-search") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Processes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                                    OutlinedTextField(value = processQuery, onValueChange = { processQuery = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                        label = { Text("Search processes") }, supportingText = { Text("Name, command, app, PID or UID") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                                        trailingIcon = { if (processQuery.isNotEmpty()) IconButton(onClick = { processQuery = "" }) { Icon(Icons.Rounded.Close, "Clear process search") } })
                                }
                            }
                            item(key = "process-filters") {
                                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MonitorProcessFilter.entries.forEach { item -> FilterChip(selected = filter == item, onClick = { filter = item },
                                        modifier = Modifier.heightIn(min = 48.dp), label = { Text(item.label) }) }
                                }
                            }
                            item(key = "process-sort") {
                                SortControls("process", ProcessSort.entries, sort, processDirection,
                                    onSort = { sort = it }, onDirection = { processDirection = it })
                            }
                            item(key = "process-explanation") {
                                Disclosure("${processes.size} shown · ${state.resources.processes.size} sampled", "How to read process values") {
                                    SourceNote("App processes match an installed non-system app UID; other processes appear under System / native. CPU: one core = 100%. RSS includes shared memory; do not add it as private app usage.")
                                }
                            }
                            if (processes.isEmpty()) {
                                item(key = "process-no-match") {
                                    InfoCard("No matching processes", "Try another search or reset the process filter.", Icons.Rounded.Search)
                                    TextButton(onClick = { processQuery = ""; filter = MonitorProcessFilter.ALL; profileId = ALL_PROFILES }) { Text("Reset search and filter") }
                                }
                            }
                            items(processes, key = { "process-${it.pid}" }) { process ->
                                ProcessRow(process, appsByUid[process.uid].orEmpty(), sort, profile = profiles.firstOrNull { it.userId == process.uid / 100000 } ?: AndroidProfile(process.uid / 100000),
                                    modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = tween(220)),
                                    onClick = { focusManager.clearFocus(); selectedProcess = process; selectedAt = state.resources.capturedAt })
                            }
                        }
                    } else {
                        item(key = "battery-estimates") {
                            ResourceCard("Android battery estimates") {
                                OutlinedButton(onClick = onRefreshEnergy, enabled = !state.energyLoading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(6.dp)) { Text("Refresh battery estimates") }
                                Box(Modifier.fillMaxWidth().height(4.dp)) {
                                    if (state.energyLoading) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Reading battery estimates" })
                                }
                                state.energy?.let { snapshot ->
                                    ResourceValue("Estimated total", mahLabel(snapshot.estimatedTotalMah), accent = true)
                                    ResourceValue("Time on battery", durationLabel(snapshot.batteryRealtimeMs))
                                    SourceNote("Whole-device battery statistics sampled ${resourceTimeLabel(snapshot.capturedAt)}")
                                    Disclosure("About these estimates", "Cumulative Android battery history") {
                                        ResourceValue("History begins", snapshot.startClockTimeMs?.let { dateTimeLabel(it) } ?: "Unavailable")
                                        SourceNote("Cumulative estimates from Android’s current battery history, not instantaneous power. Each UID is counted once. History can reset after charging or a system reset. Missing readings mean unavailable, not zero.")
                                    }
                                } ?: SourceNote(if (state.energyLoading) "Reading Android’s battery history…" else "Refresh to read Android’s battery history.")
                            }
                        }
                        state.energy?.let { snapshot ->
                            if (snapshot.warnings.isNotEmpty()) {
                                item(key = "energy-warnings") { InfoCard("Some battery readings are unavailable", snapshot.warnings.joinToString("\n"), Icons.Rounded.WarningAmber, accent = Orange) }
                            }
                            item(key = "battery-components") {
                                Disclosure("Device components", if (components.isEmpty()) "No component estimates in this sample" else "${components.size} component estimates") {
                                    if (components.isNotEmpty()) {
                                        SortControls("component", ComponentSort.entries, componentSort, componentDirection,
                                            onSort = { componentSort = it }, onDirection = { componentDirection = it })
                                        components.forEach { ResourceValue(powerComponentLabel(it.name), mahLabel(it.estimatedMah)) }
                                    } else SourceNote("Device component estimates are unavailable in this sample.")
                                    SourceNote("System component estimates overlap the UID breakdown; do not add the two lists together.")
                                }
                            }
                            if (snapshot.uids.isEmpty()) {
                                item(key = "energy-unavailable") { InfoCard("UID battery estimates unavailable", "No per-UID estimates were returned. Try refreshing; some Android builds do not expose these statistics.", Icons.Rounded.Info) }
                            } else {
                                item(key = "energy-search") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Battery use by UID", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                                        OutlinedTextField(value = energyQuery, onValueChange = { energyQuery = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                            label = { Text("Search battery use") }, supportingText = { Text("App, package or UID") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                                            trailingIcon = { if (energyQuery.isNotEmpty()) IconButton(onClick = { energyQuery = "" }) { Icon(Icons.Rounded.Close, "Clear battery search") } })
                                        SourceNote("Select a UID for CPU time, traffic, wakelocks and related apps.")
                                    }
                                }
                                item(key = "energy-sort") {
                                    SortControls("energy", EnergySort.entries, energySort, energyDirection,
                                        onSort = { energySort = it }, onDirection = { energyDirection = it })
                                }
                                if (energy.isEmpty()) {
                                    item(key = "energy-no-match") {
                                        InfoCard("No matching UIDs", "Try another app, package or UID.", Icons.Rounded.Search)
                                        TextButton(onClick = { energyQuery = "" }) { Text("Clear battery search") }
                                    }
                                }
                                items(energy, key = { "energy-${it.uid}" }) { item ->
                                    val packages = appsByUid[item.uid].orEmpty()
                                    Surface(onClick = { focusManager.clearFocus(); selectedEnergy = item; selectedEnergyAt = snapshot.capturedAt },
                                        modifier = Modifier.testTag("energy-row-${item.uid}").animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = tween(220)),
                                        color = Panel, shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Outline)) {
                                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                            Text(uidTitle(item.uid, packages), style = MaterialTheme.typography.titleMedium)
                                            ProfileLabel(profiles.firstOrNull { it.userId == item.uid / 100000 } ?: AndroidProfile(item.uid / 100000))
                                            Text(mahLabel(item.estimatedMah), style = MaterialTheme.typography.headlineMedium, color = Cyan)
                                            SourceNote("UID ${item.uid}${if (packages.size > 1) " · Shared by ${packages.size} apps" else ""}")
                                            SourceNote("CPU ${durationLabel(item.cpuTimeMs)} · Wakelock ${durationLabel(item.wakelockMs)}")
                                            when (energySort) {
                                                EnergySort.RECEIVED -> ResourceValue("Network received", bytesLabel(item.networkRxBytes))
                                                EnergySort.SENT -> ResourceValue("Network sent", bytesLabel(item.networkTxBytes))
                                                else -> Unit
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    selectedProcess?.let { process ->
        ProcessDetailSheet(process, selectedAt, appsByUid[process.uid].orEmpty(), profiles.firstOrNull { it.userId == process.uid / 100000 } ?: AndroidProfile(process.uid / 100000), relatedSort, relatedDirection,
            onRelatedSort = { relatedSort = it }, onRelatedDirection = { relatedDirection = it }, onDismiss = { selectedProcess = null }, onSelect = { selectedProcess = null; onSelectApp(it) })
    }
    selectedEnergy?.let { item ->
        EnergyDetailSheet(item, selectedEnergyAt, appsByUid[item.uid].orEmpty(), profiles.firstOrNull { it.userId == item.uid / 100000 } ?: AndroidProfile(item.uid / 100000), relatedSort, relatedDirection,
            onRelatedSort = { relatedSort = it }, onRelatedDirection = { relatedDirection = it }, onDismiss = { selectedEnergy = null }, onSelect = { selectedEnergy = null; onSelectApp(it) })
    }
}

@Composable
private fun MonitorAccessCard(access: AccessState, onAccess: () -> Unit) {
    val checking = access == AccessState.CHECKING || access == AccessState.CONNECTING
    ResourceCard(when (access) {
        AccessState.CHECKING -> "Checking connection…"
        AccessState.CONNECTING -> "Connecting to Shizuku…"
        AccessState.NEEDS_PERMISSION -> "Allow access for resource readings"
        AccessState.ERROR -> "Connection needs attention"
        else -> "Connect for resource readings"
    }) {
        SourceNote(if (checking) "Resource readings will appear when the connection is ready." else "Shizuku access is needed for process, memory and per-app battery readings.")
        if (checking) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Checking Shizuku connection" })
        else Button(onClick = onAccess, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (access == AccessState.NEEDS_PERMISSION) "Grant access" else "Set up access")
        }
    }
}

@Composable
private fun ProcessRow(process: ProcessResource, apps: List<AppEntry>, sort: ProcessSort, profile: AndroidProfile, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.testTag("process-row-${process.pid}"), color = Panel, shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Outline)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(process.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            ProfileLabel(profile)
            SourceNote("PID ${process.pid} · UID ${process.uid} · ${process.state}")
            if (apps.isNotEmpty()) SourceNote(apps.joinToString { it.label } + if (apps.size > 1) " · Shared UID" else "")
            ResourceValue("CPU", percent(process.cpuPercent), accent = true)
            ResourceValue("Resident RAM", memoryLabel(process.rssKb))
            if (sort == ProcessSort.VIRTUAL) ResourceValue("Virtual address space", memoryLabel(process.virtualKb))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProcessDetailSheet(
    process: ProcessResource, capturedAt: Long?, apps: List<AppEntry>, profile: AndroidProfile, relatedSort: AppSort,
    relatedDirection: SortDirection, onRelatedSort: (AppSort) -> Unit, onRelatedDirection: (SortDirection) -> Unit,
    onDismiss: () -> Unit, onSelect: (AppEntry) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Ink) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DetailSheetHeading("Process details", onDismiss)
            SelectionContainer { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(process.name, style = MaterialTheme.typography.headlineMedium)
                Text(process.command.ifBlank { "Command unavailable" }, style = MaterialTheme.typography.bodyMedium)
            } }
            ResourceValue("PID / parent PID", "${process.pid} / ${process.parentPid}")
            ResourceValue("UID / user", "${process.uid} / ${process.user}")
            ProfileLabel(profile)
            ResourceValue("Scheduler state", process.state)
            SourceNote("R: running · S: sleeping · D: uninterruptible sleep · T: stopped · Z: zombie. Additional flags depend on Android.")
            ResourceValue("CPU · one core = 100%", percent(process.cpuPercent), accent = true)
            ResourceValue("Resident (RSS)", memoryLabel(process.rssKb))
            ResourceValue("Virtual address space", memoryLabel(process.virtualKb))
            SourceNote("RSS includes shared resident pages. Virtual size includes reserved address space and is not physical RAM use. A process using several cores may exceed 100% CPU.")
            SourceNote("Selected sample ${capturedAt?.let { resourceTimeLabel(it) } ?: "unavailable"}. This detail is a snapshot; a PID may later exit or be reused.")
            RelatedApps(apps, relatedSort, relatedDirection, onRelatedSort, onRelatedDirection, onSelect)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnergyDetailSheet(
    energy: UidEnergy, capturedAt: Long?, apps: List<AppEntry>, profile: AndroidProfile, relatedSort: AppSort,
    relatedDirection: SortDirection, onRelatedSort: (AppSort) -> Unit, onRelatedDirection: (SortDirection) -> Unit,
    onDismiss: () -> Unit, onSelect: (AppEntry) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Ink) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DetailSheetHeading("UID battery details", onDismiss)
            Text(uidTitle(energy.uid, apps), style = MaterialTheme.typography.headlineMedium)
            ResourceValue("UID", energy.uid.toString())
            ProfileLabel(profile)
            ResourceValue("Estimated battery use", mahLabel(energy.estimatedMah), accent = true)
            ResourceValue("CPU time", durationLabel(energy.cpuTimeMs))
            ResourceValue("Network received", bytesLabel(energy.networkRxBytes))
            ResourceValue("Network sent", bytesLabel(energy.networkTxBytes))
            ResourceValue("Partial wakelocks", durationLabel(energy.wakelockMs))
            SourceNote("Cumulative measurements over Android’s battery history. Wakelocks can keep the CPU awake; their duration is not battery energy. CPU time can exceed elapsed time when multiple cores work.")
            SourceNote("Selected sample ${capturedAt?.let { resourceTimeLabel(it) } ?: "unavailable"}.")
            RelatedApps(apps, relatedSort, relatedDirection, onRelatedSort, onRelatedDirection, onSelect)
        }
    }
}

@Composable
private fun DetailSheetHeading(title: String, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close $title") }
    }
}

@Composable
private fun RelatedApps(
    apps: List<AppEntry>, sort: AppSort, direction: SortDirection, onSort: (AppSort) -> Unit,
    onDirection: (SortDirection) -> Unit, onSelect: (AppEntry) -> Unit,
) {
    val sortedApps = remember(apps, sort, direction) { ListSorting.apps(apps, sort, direction) }
    HorizontalDivider(color = Outline)
    Text("Apps with this UID", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    SourceNote(when {
        apps.size > 1 -> "Shared UID: these apps share this identity. Resource use belongs to the UID and cannot reliably be assigned to just one package."
        apps.isEmpty() -> "No installed app matches this UID. It may belong to a native/system service, another user, or an isolated process."
        else -> "Matched by Android UID. Isolated child processes may use a different UID."
    })
    if (apps.isNotEmpty()) SortControls("related-apps", listOf(AppSort.NAME, AppSort.PACKAGE, AppSort.STATE),
        sort, direction, onSort, onDirection)
    sortedApps.forEach { app ->
        Button(onClick = { onSelect(app) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Open ${app.label}") }
        ProfileLabel(app.profile)
        SelectionContainer { SourceNote(app.packageName) }
        if (sort == AppSort.STATE) SourceNote(app.processState.label)
    }
}

private fun uidTitle(uid: Int, apps: List<AppEntry>): String = when {
    apps.isEmpty() -> "UID $uid · system or unmatched"
    apps.size == 1 -> apps.first().label
    else -> apps.joinToString { it.label }
}
