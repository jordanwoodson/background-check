package dev.bgcheck.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bgcheck.app.data.AccessState
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
    var mode by rememberSaveable { mutableStateOf(MonitorMode.LIVE) }
    var query by rememberSaveable { mutableStateOf("") }
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
    val focusManager = LocalFocusManager.current
    val processes = remember(state.resources, query, filter, sort, processDirection, appsByUid) {
        val needle = query.trim()
        state.resources?.processes.orEmpty().filter { process ->
            val packages = appsByUid[process.uid].orEmpty()
            val userApp = packages.any { !it.system }
            val matchesScope = when (filter) {
                MonitorProcessFilter.ALL -> true
                MonitorProcessFilter.APPS -> userApp
                MonitorProcessFilter.SYSTEM -> !userApp
            }
            matchesScope && (needle.isEmpty() || listOf(process.pid.toString(), process.parentPid.toString(), process.uid.toString(), process.user, process.name, process.command)
                .any { it.contains(needle, ignoreCase = true) } || packages.any { it.label.contains(needle, true) || it.packageName.contains(needle, true) })
        }.let { ListSorting.processes(it, sort, processDirection) }
    }
    val energy = remember(state.energy, query, energySort, energyDirection, appsByUid) {
        val needle = query.trim()
        state.energy?.uids.orEmpty().filter { item ->
            needle.isEmpty() || item.uid.toString().contains(needle) || appsByUid[item.uid].orEmpty()
                .any { it.label.contains(needle, true) || it.packageName.contains(needle, true) }
        }.let { ListSorting.energy(it, energySort, energyDirection, appsByUid) }
    }
    val components = remember(state.energy, componentSort, componentDirection) {
        ListSorting.components(state.energy?.components.orEmpty(), componentSort, componentDirection)
    }
    LazyColumn(
        modifier = Modifier.testTag("monitor-list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Eyebrow("WHAT’S USING YOUR DEVICE")
                Text("Resource monitor", style = MaterialTheme.typography.headlineLarge)
                Text("Inspect live activity and Android’s battery estimates.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MonitorMode.entries.forEach { item -> FilterChip(selected = mode == item, onClick = { mode = item; query = "" }, label = { Text(item.label) }) }
                }
            }
        }
        if (!ready) {
            item {
                InfoCard("Connect for resource readings", "Shizuku access is needed to read process, memory and per-app battery statistics. Any previous samples are no longer live.", Icons.Rounded.Info, accent = Lime)
                TextButton(onClick = onAccess) { Text("Set up access") }
            }
        }
        if (mode == MonitorMode.LIVE) {
            item {
                ResourceCard("Live sampling") {
                    StatusPill(when {
                        !ready -> "Disconnected"
                        state.monitorPaused -> "Paused"
                        state.resourceLoading -> "Sampling…"
                        state.monitoring -> "Live"
                        else -> "Waiting for sample"
                    }, ready && !state.monitorPaused)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onSetPaused(!state.monitorPaused) }, enabled = ready, modifier = Modifier.weight(1f)) {
                            Text(if (state.monitorPaused) "Resume" else "Pause")
                        }
                        OutlinedButton(onClick = onRefreshResources, enabled = ready && !state.resourceLoading, modifier = Modifier.weight(1f)) { Text("Refresh now") }
                    }
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3_000L, 5_000L, 10_000L).forEach { interval ->
                            FilterChip(selected = state.monitorIntervalMs == interval, onClick = { onSetInterval(interval) }, label = { Text("${interval / 1000} seconds") })
                        }
                    }
                    SourceNote("Only samples while this screen is open and the app is in the foreground. Faster sampling adds monitoring overhead.")
                    state.resources?.let { SourceNote("Sample ${resourceTimeLabel(it.capturedAt)} · CPU interval ${durationLabel(it.sampleDurationMs)}") }
                    if (state.resourceLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            item {
                val resources = state.resources
                ResourceCard("Whole device") {
                    ResourceValue("CPU · all cores", percent(resources?.cpuPercent), accent = true)
                    ResourceValue("CPU cores", resources?.cpuCoreCount?.toString() ?: "Unavailable")
                    ResourceValue("RAM available", memoryLabel(resources?.memory?.availableKb))
                    ResourceValue("RAM total", memoryLabel(resources?.memory?.totalKb))
                    ResourceValue("Swap used", resources?.memory?.let { memoryLabel((it.swapTotalKb - it.swapFreeKb).coerceAtLeast(0)) } ?: "Unavailable")
                    ResourceValue("Swap total", memoryLabel(resources?.memory?.swapTotalKb))
                    SourceNote("Device CPU is 0–100% of all cores. Available RAM includes memory Android can reclaim. Swap can include compressed RAM (zram).")
                }
            }
            item { ResourceTrends(state.resourceHistory, state.resources?.memory?.totalKb) }
            if (state.resources?.warnings?.isNotEmpty() == true) {
                item { InfoCard("Some live readings are unavailable", state.resources.warnings.joinToString("\n"), Icons.Rounded.WarningAmber, accent = Amber) }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Processes", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        label = { Text("Search processes") }, supportingText = { Text("Name, command, app, PID or UID") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear process search") } })
                }
            }
            item {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MonitorProcessFilter.entries.forEach { item -> FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item.label) }) }
                }
            }
            item {
                SortControls("process", ProcessSort.entries, sort, processDirection,
                    onSort = { sort = it }, onDirection = { processDirection = it })
            }
            item {
                SourceNote((if (state.resources?.processesKnown == true) "${processes.size} shown / ${state.resources.processes.size} sampled. " else "Process count unavailable. ") + "App processes match an installed non-system app UID; other processes appear under System / native. CPU: one core = 100%. RSS includes shared memory; do not add it as private app usage.")
            }
            if (state.resources?.processesKnown != true) {
                item { InfoCard(if (state.resourceLoading) "Reading processes…" else "Process list unavailable", "Refresh after connecting. Android or the device vendor may restrict some readings.", Icons.Rounded.Info) }
            } else if (processes.isEmpty()) {
                item { SourceNote(if (query.isNotBlank() || filter != MonitorProcessFilter.ALL) "No processes match this search and filter." else "No shell-visible processes were returned.") }
            }
            items(processes, key = { it.pid }) { process ->
                ProcessRow(process, appsByUid[process.uid].orEmpty(), sort, onClick = { selectedProcess = process; selectedAt = state.resources?.capturedAt })
            }
        } else {
            item { BatteryFlowCard(state.battery) }
            item {
                ResourceCard("Android battery estimates") {
                    OutlinedButton(onClick = onRefreshEnergy, enabled = ready && !state.energyLoading, modifier = Modifier.fillMaxWidth()) { Text("Refresh battery estimates") }
                    if (state.energyLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    ResourceValue("Estimated total", mahLabel(state.energy?.estimatedTotalMah), accent = true)
                    ResourceValue("Time on battery", durationLabel(state.energy?.batteryRealtimeMs))
                    ResourceValue("History begins", state.energy?.startClockTimeMs?.let { dateTimeLabel(it) } ?: "Unavailable")
                    state.energy?.let { SourceNote("Battery statistics sampled ${resourceTimeLabel(it.capturedAt)}") }
                    SourceNote("Cumulative estimates from Android’s current battery history, not instantaneous power. Each UID is counted once. History can reset after charging or a system reset. Missing readings mean unavailable, not zero.")
                }
            }
            if (state.energy?.warnings?.isNotEmpty() == true) {
                item { InfoCard("Some battery readings are unavailable", state.energy.warnings.joinToString("\n"), Icons.Rounded.WarningAmber, accent = Amber) }
            }
            if (state.energy?.components?.isNotEmpty() == true) {
                item {
                    ResourceCard("Device components") {
                        SortControls("component", ComponentSort.entries, componentSort, componentDirection,
                            onSort = { componentSort = it }, onDirection = { componentDirection = it })
                        components.forEach { ResourceValue(powerComponentLabel(it.name), mahLabel(it.estimatedMah)) }
                        SourceNote("System component estimates overlap the UID breakdown; do not add the two lists together.")
                    }
                }
            } else {
                item { SourceNote("Device component estimates are unavailable in this sample.") }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Battery use by UID", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        label = { Text("Search battery use") }, supportingText = { Text("App, package or UID") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear battery search") } })
                    SourceNote("Select a UID for CPU time, traffic, wakelocks and related apps.")
                }
            }
            item {
                SortControls("energy", EnergySort.entries, energySort, energyDirection,
                    onSort = { energySort = it }, onDirection = { energyDirection = it })
            }
            if (energy.isEmpty()) {
                item { InfoCard(if (state.energyLoading) "Reading battery estimates…" else if (query.isNotBlank()) "No matching UIDs" else "UID battery estimates unavailable", if (query.isNotBlank()) "Try another app, package or UID." else "No per-UID estimates were returned. Refresh after connecting; some Android builds do not expose these statistics.", Icons.Rounded.Info) }
            }
            items(energy, key = { it.uid }) { item ->
                val packages = appsByUid[item.uid].orEmpty()
                Surface(onClick = { selectedEnergy = item; selectedEnergyAt = state.energy?.capturedAt }, color = Panel, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(uidTitle(item.uid, packages), style = MaterialTheme.typography.titleMedium)
                        Text(mahLabel(item.estimatedMah), style = MaterialTheme.typography.headlineMedium, color = Lime)
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
    selectedProcess?.let { process ->
        ProcessDetailSheet(process, selectedAt, appsByUid[process.uid].orEmpty(), relatedSort, relatedDirection,
            onRelatedSort = { relatedSort = it }, onRelatedDirection = { relatedDirection = it }, onDismiss = { selectedProcess = null }, onSelect = { selectedProcess = null; onSelectApp(it) })
    }
    selectedEnergy?.let { item ->
        EnergyDetailSheet(item, selectedEnergyAt, appsByUid[item.uid].orEmpty(), relatedSort, relatedDirection,
            onRelatedSort = { relatedSort = it }, onRelatedDirection = { relatedDirection = it }, onDismiss = { selectedEnergy = null }, onSelect = { selectedEnergy = null; onSelectApp(it) })
    }
}

@Composable
private fun ProcessRow(process: ProcessResource, apps: List<AppEntry>, sort: ProcessSort, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Panel, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(process.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    process: ProcessResource, capturedAt: Long?, apps: List<AppEntry>, relatedSort: AppSort,
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
    energy: UidEnergy, capturedAt: Long?, apps: List<AppEntry>, relatedSort: AppSort,
    relatedDirection: SortDirection, onRelatedSort: (AppSort) -> Unit, onRelatedDirection: (SortDirection) -> Unit,
    onDismiss: () -> Unit, onSelect: (AppEntry) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Ink) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DetailSheetHeading("UID battery details", onDismiss)
            Text(uidTitle(energy.uid, apps), style = MaterialTheme.typography.headlineMedium)
            ResourceValue("UID", energy.uid.toString())
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
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
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
    Text("Apps with this UID", style = MaterialTheme.typography.titleLarge)
    SourceNote(when {
        apps.size > 1 -> "Shared UID: these apps share this identity. Resource use belongs to the UID and cannot reliably be assigned to just one package."
        apps.isEmpty() -> "No installed app matches this UID. It may belong to a native/system service, another user, or an isolated process."
        else -> "Matched by Android UID. Isolated child processes may use a different UID."
    })
    if (apps.isNotEmpty()) SortControls("related-apps", listOf(AppSort.NAME, AppSort.PACKAGE, AppSort.STATE),
        sort, direction, onSort, onDirection)
    sortedApps.forEach { app ->
        Button(onClick = { onSelect(app) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Open ${app.label}") }
        SelectionContainer { SourceNote(app.packageName) }
        if (sort == AppSort.STATE) SourceNote(app.processState.label)
    }
}

private fun uidTitle(uid: Int, apps: List<AppEntry>): String = when {
    apps.isEmpty() -> "UID $uid · system or unmatched"
    apps.size == 1 -> apps.first().label
    else -> apps.joinToString { it.label }
}
