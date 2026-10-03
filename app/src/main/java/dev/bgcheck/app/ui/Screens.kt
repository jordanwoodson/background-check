package dev.bgcheck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bgcheck.app.data.AccessState
import dev.bgcheck.app.data.AppEntry
import dev.bgcheck.app.data.AppSort
import dev.bgcheck.app.data.BatteryInfo
import dev.bgcheck.app.data.BatteryPowerState
import dev.bgcheck.app.data.ChangeRecord
import dev.bgcheck.app.data.ControlAction
import dev.bgcheck.app.data.HistorySort
import dev.bgcheck.app.data.ListSorting
import dev.bgcheck.app.data.ProcessState
import dev.bgcheck.app.data.SortDirection
import dev.bgcheck.app.data.UiState
import java.util.Locale

private enum class Page(val label: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Rounded.Insights),
    APPS("Apps", Icons.Rounded.Apps),
    MONITOR("Monitor", Icons.Rounded.Memory),
    ACCESS("Access", Icons.Rounded.Key),
}

@Composable
fun BackgroundCheckApp(
    state: UiState,
    onRefresh: () -> Unit,
    onConnect: () -> Unit,
    onSelect: (AppEntry) -> Unit,
    onDismissDetail: () -> Unit,
    onAction: (ControlAction) -> Unit,
    onUndo: (ChangeRecord) -> Unit,
    onOpenAppSettings: (String) -> Unit,
    onOpenShizuku: () -> Unit,
    onOpenWirelessSettings: () -> Unit,
    onClearMessage: () -> Unit,
    onRefreshResources: () -> Unit,
    onRefreshEnergy: () -> Unit,
    onSetMonitorPaused: (Boolean) -> Unit,
    onSetMonitorInterval: (Long) -> Unit,
    onMonitorVisible: (Boolean) -> Unit,
    onForceStop: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(Page.OVERVIEW) }
    val pageState = rememberSaveableStateHolder()
    val detailState = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    DisposableEffect(page == Page.MONITOR) {
        onMonitorVisible(page == Page.MONITOR)
        onDispose { onMonitorVisible(false) }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it, withDismissAction = true)
            onClearMessage()
        }
    }
    Scaffold(
        containerColor = Ink,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Ink, tonalElevation = 0.dp) {
                Page.entries.forEach { item ->
                    NavigationBarItem(
                        selected = page == item,
                        onClick = { page = item },
                        icon = { Icon(item.icon, contentDescription = null, modifier = Modifier.size(23.dp)) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Lime, selectedTextColor = Lime, indicatorColor = PanelRaised, unselectedIconColor = Muted, unselectedTextColor = Muted),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AppHeader(state, onRefresh, onAccess = { page = Page.ACCESS })
            pageState.SaveableStateProvider(page.name) {
                when (page) {
                    Page.OVERVIEW -> OverviewScreen(state, onAccess = { page = Page.ACCESS }, onApps = { page = Page.APPS }, onSelect = onSelect, onUndo = onUndo)
                    Page.APPS -> AppsScreen(state, onSelect, onAccess = { page = Page.ACCESS })
                    Page.MONITOR -> MonitorScreen(state, onRefreshResources, onRefreshEnergy, onSetMonitorPaused, onSetMonitorInterval, onSelect, onAccess = { page = Page.ACCESS })
                    Page.ACCESS -> AccessScreen(state, onConnect, onOpenShizuku, onOpenWirelessSettings)
                }
            }
        }
    }
    state.selected?.let { app ->
        detailState.SaveableStateProvider(app.packageName) {
            AppDetailSheet(state, app, onDismissDetail, onAction, onOpenAppSettings, onConnect, onForceStop)
        }
    }
}

@Composable
private fun AppHeader(state: UiState, onRefresh: () -> Unit, onAccess: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(Lime), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Tune, null, tint = Ink, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(9.dp))
        Text("BACKGROUND CHECK", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp), modifier = Modifier.weight(1f))
        if (state.loading || state.refreshing) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        } else {
            IconButton(onClick = onRefresh, enabled = !state.busy) { Icon(Icons.Rounded.Refresh, "Refresh app and battery information", tint = Muted) }
        }
    }
}

@Composable
private fun OverviewScreen(state: UiState, onAccess: () -> Unit, onApps: () -> Unit, onSelect: (AppEntry) -> Unit, onUndo: (ChangeRecord) -> Unit) {
    var showAllChanges by rememberSaveable { mutableStateOf(false) }
    var activeSort by rememberSaveable { mutableStateOf(AppSort.NAME) }
    var activeDirection by rememberSaveable { mutableStateOf(SortDirection.ASCENDING) }
    var historySort by rememberSaveable { mutableStateOf(HistorySort.TIME) }
    var historyDirection by rememberSaveable { mutableStateOf(SortDirection.DESCENDING) }
    val known = state.access == AccessState.READY && state.apps.any { it.processState != ProcessState.UNKNOWN }
    val foreground = state.apps.count { it.processState == ProcessState.FOREGROUND }
    val services = state.apps.count { it.processState == ProcessState.SERVICE }
    val background = state.apps.count { it.processState == ProcessState.BACKGROUND }
    val cached = state.apps.count { it.processState == ProcessState.CACHED }
    val activeApps = remember(state.apps, activeSort, activeDirection) {
        ListSorting.apps(state.apps.filter { it.processState in setOf(ProcessState.FOREGROUND, ProcessState.SERVICE, ProcessState.BACKGROUND) }, activeSort, activeDirection).take(4)
    }
    val changes = remember(state.changes, historySort, historyDirection) {
        ListSorting.history(state.changes, historySort, historyDirection)
    }
    LazyColumn(modifier = Modifier.testTag("overview-list"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("YOUR DEVICE, IN VIEW")
                    Surface(onClick = onAccess, color = Color.Transparent) {
                        StatusPill(if (state.access == AccessState.READY) "Connected" else "Set up access", state.access == AccessState.READY)
                    }
                }
                Text("A little more\npeace of mind.", style = MaterialTheme.typography.headlineLarge)
                Text("See what’s running. Choose what stays active.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        }
        item { BatteryCard(state.battery) }
        if (state.access != AccessState.READY) {
            item { AccessPrompt(onAccess) }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading("Behind the scenes", if (known) "As of ${timeLabel(state.capturedAt)}" else if (state.access == AccessState.READY) "Reading unavailable" else "Access needed")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProcessMetric("Foreground", if (known) foreground else null, "Visible or supporting UI", Modifier.weight(1f), Lime)
                    ProcessMetric("Services", if (known) services else null, "Service process present", Modifier.weight(1f), Color(0xFFB9D9CF))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProcessMetric("Background", if (known) background else null, "Observed in memory", Modifier.weight(1f), Amber)
                    ProcessMetric("Cached", if (known) cached else null, "Kept for quick reopening", Modifier.weight(1f), Color(0xFFBFC7E0))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Info, null, tint = Muted, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                    Text("A process in memory does not mean an app is draining your battery. This is a snapshot, not energy use.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
        if (state.warnings.isNotEmpty()) {
            item { InfoCard("Some readings are unavailable", state.warnings.joinToString("\n"), Icons.Rounded.WarningAmber, accent = Amber) }
        }
        if (known && activeApps.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("In the picture", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onApps) { Text("All apps"); Spacer(Modifier.width(4.dp)); Icon(Icons.Rounded.ArrowForward, null, Modifier.size(16.dp)) }
                }
            }
            item {
                SortControls("active-apps", AppSort.entries, activeSort, activeDirection,
                    onSort = { activeSort = it }, onDirection = { activeDirection = it })
            }
            items(activeApps, key = { "active-${it.packageName}" }) { AppRow(it, sort = activeSort, onClick = { onSelect(it) }) }
        }
        item { SectionHeading("Your changes", "${state.changes.size} recorded") }
        if (changes.isEmpty()) {
            item { InfoCard("A clean slate", "Changes you make here will appear here, with a way to restore the previous setting.", Icons.Rounded.History) }
        } else {
            item {
                SortControls("history", HistorySort.entries, historySort, historyDirection,
                    onSort = { historySort = it }, onDirection = { historyDirection = it })
            }
            items(if (showAllChanges) changes else changes.take(5), key = { "history-${it.id}" }) { record ->
                HistoryCard(record, state.access == AccessState.READY && !state.busy, onUndo)
            }
            if (changes.size > 5) {
                item {
                    TextButton(onClick = { showAllChanges = !showAllChanges }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (showAllChanges) "Show fewer changes" else "Show all ${changes.size} changes")
                    }
                }
            }
        }
        item { Text("On your device. Under your control.", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) }
    }
}

@Composable
private fun BatteryCard(battery: BatteryInfo) {
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Lime, contentColor = Ink)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("BATTERY RIGHT NOW", Ink.copy(alpha = 0.72f))
                Icon(if (battery.powerState == BatteryPowerState.CHARGING) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull, null, tint = Ink)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(battery.percent?.toString() ?: "—", style = MaterialTheme.typography.displayLarge.copy(fontSize = 78.sp, lineHeight = 80.sp), color = Ink)
                if (battery.percent != null) Text("%", style = MaterialTheme.typography.headlineMedium, color = Ink, modifier = Modifier.padding(bottom = 8.dp))
                Text(battery.powerState.label, style = MaterialTheme.typography.labelLarge, color = Ink.copy(alpha = 0.76f), textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f).padding(start = 12.dp, bottom = 10.dp))
            }
            battery.percent?.let {
                LinearProgressIndicator(progress = { (it / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp), color = Ink, trackColor = Ink.copy(alpha = 0.13f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Temperature", style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = 0.7f))
                    Text(battery.temperatureC?.let { String.format(Locale.getDefault(), "%.1f °C", it) } ?: "Unavailable", style = MaterialTheme.typography.titleMedium, color = Ink)
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.End) {
                    Text("Battery saver", style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = 0.7f))
                    Text(if (battery.percent == null) "Unavailable" else if (battery.saver) "On" else "Off", style = MaterialTheme.typography.titleMedium, color = Ink)
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, trailing: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun ProcessMetric(title: String, count: Int?, description: String, modifier: Modifier, accent: Color) {
    Card(modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
                Text(title, style = MaterialTheme.typography.labelMedium, color = Muted)
            }
            Text(count?.toString() ?: "—", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 37.sp), color = Cream)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Muted, minLines = 2)
        }
    }
}

@Composable
private fun AccessPrompt(onAccess: () -> Unit) {
    Surface(onClick = onAccess, color = Panel, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(19.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Key, null, tint = Lime, modifier = Modifier.size(22.dp))
                Text("Meet the background", style = MaterialTheme.typography.titleMedium)
            }
            Text("Connect Shizuku to see running apps and manage background settings. You can browse installed apps anytime.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Set up access", style = MaterialTheme.typography.labelLarge, color = Lime)
                Icon(Icons.Rounded.ArrowForward, null, tint = Lime, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun HistoryCard(record: ChangeRecord, canUndo: Boolean, onUndo: (ChangeRecord) -> Unit) {
    Card(modifier = Modifier.testTag("history-record-${record.id}"), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(record.packageName, 34.dp)
                Column(Modifier.weight(1f)) {
                    Text(record.label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(dateTimeLabel(record.timestamp), style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                if (record.undone) {
                    Text("Restored", style = MaterialTheme.typography.labelMedium, color = Muted)
                } else {
                    TextButton(onClick = { onUndo(record) }, enabled = canUndo) { Text(if (record.recovery) "Restore" else "Undo") }
                }
            }
            Text(record.description, style = MaterialTheme.typography.bodyMedium, color = Muted)
            if (record.recovery && !record.undone) Text(
                "This change was interrupted or could not be verified. Reconnect and restore the previous policy. Any conflicting changes will be left untouched.",
                style = MaterialTheme.typography.bodySmall, color = Amber,
            )
        }
    }
}

private enum class AppType(val label: String) { USER("Your apps"), ALL("All apps"), SYSTEM("System") }
private enum class ProcessFilter(val label: String) { ALL("Any state"), ACTIVE("Active"), CACHED("Cached"), NOT_OBSERVED("Not observed") }

@Composable
private fun AppsScreen(state: UiState, onSelect: (AppEntry) -> Unit, onAccess: () -> Unit) {
    val focusManager = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(AppType.USER) }
    var process by rememberSaveable { mutableStateOf(ProcessFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(AppSort.NAME) }
    var direction by rememberSaveable { mutableStateOf(SortDirection.ASCENDING) }
    val apps = remember(state.apps, query, type, process, sort, direction) {
        ListSorting.apps(state.apps.filter { app ->
            (query.isBlank() || app.label.contains(query, true) || app.packageName.contains(query, true)) &&
                (type == AppType.ALL || app.system == (type == AppType.SYSTEM)) &&
                when (process) {
                    ProcessFilter.ALL -> true
                    ProcessFilter.ACTIVE -> app.processState in setOf(ProcessState.FOREGROUND, ProcessState.SERVICE, ProcessState.BACKGROUND)
                    ProcessFilter.CACHED -> app.processState == ProcessState.CACHED
                    ProcessFilter.NOT_OBSERVED -> app.processState == ProcessState.NOT_RUNNING
                }
        }, sort, direction)
    }
    LazyColumn(modifier = Modifier.testTag("apps-list"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                Eyebrow("A CLOSER LOOK")
                Text("Your apps", style = MaterialTheme.typography.headlineLarge)
                Text("A place for everything running behind the screen.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                placeholder = { Text("Search apps or packages") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search") } }) else null,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Panel, focusedContainerColor = Panel, unfocusedBorderColor = Outline, focusedBorderColor = Lime),
            )
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppType.entries.forEach { item -> FilterChip(selected = type == item, onClick = { type = item }, label = { Text(item.label) }, modifier = Modifier.heightIn(min = 48.dp), colors = appFilterColors()) }
            }
        }
        if (state.access == AccessState.READY) {
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProcessFilter.entries.forEach { item -> FilterChip(selected = process == item, onClick = { process = item }, label = { Text(item.label) }, modifier = Modifier.heightIn(min = 48.dp), colors = appFilterColors()) }
                }
            }
        } else {
            item {
                Surface(onClick = onAccess, color = PanelRaised, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.Key, null, tint = Lime, modifier = Modifier.size(19.dp))
                        Text("Connect to see process and battery settings", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), color = Cream)
                        Icon(Icons.Rounded.ArrowForward, null, tint = Muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        item {
            SortControls("apps", AppSort.entries, sort, direction, onSort = { sort = it }, onDirection = { direction = it })
        }
        item {
            Text("${apps.size} ${if (apps.size == 1) "app" else "apps"}" + if (state.capturedAt != null && state.access == AccessState.READY) " · Snapshot ${timeLabel(state.capturedAt)}" else " · Installed on this device", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(vertical = 6.dp))
        }
        if (state.loading && state.apps.isEmpty()) {
            item { InfoCard("Finding your apps", "Reading the apps installed on this device.", Icons.Rounded.Apps) }
        } else if (apps.isEmpty()) {
            item { InfoCard("Nothing here yet", if (query.isNotBlank()) "Try another app name or package, or change the filters." else "No apps match these filters. Try All apps or Any state.", Icons.Rounded.Search) }
        } else {
            items(apps, key = { it.packageName }) { app -> AppRow(app, sort = sort, onClick = { focusManager.clearFocus(); onSelect(app) }) }
        }
    }
    LaunchedEffect(state.access) { if (state.access != AccessState.READY) process = ProcessFilter.ALL }
}

@Composable
private fun appFilterColors() = FilterChipDefaults.filterChipColors(selectedContainerColor = Lime, selectedLabelColor = Ink, containerColor = Ink, labelColor = Muted)

@Composable
private fun AccessScreen(state: UiState, onConnect: () -> Unit, onOpenShizuku: () -> Unit, onOpenWirelessSettings: () -> Unit) {
    val ready = state.access == AccessState.READY
    val connecting = state.access in setOf(AccessState.CHECKING, AccessState.CONNECTING)
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Eyebrow("THE KEY TO THE DETAILS")
                Text("A closer connection.", style = MaterialTheme.typography.headlineLarge)
                Text("Shizuku lets Background Check read and change Android’s app settings with your permission.", style = MaterialTheme.typography.bodyLarge, color = Muted)
            }
        }
        item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = if (ready) Lime else Panel)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val foreground = if (ready) Ink else Cream
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(if (ready) Icons.Rounded.CheckCircle else Icons.Rounded.Key, null, tint = if (ready) Ink else Lime, modifier = Modifier.size(29.dp))
                        Text(when (state.access) {
                            AccessState.READY -> "You’re connected"
                            AccessState.CHECKING -> "Checking access"
                            AccessState.CONNECTING -> "Connecting to Shizuku"
                            AccessState.NEEDS_PERMISSION -> "One permission away"
                            AccessState.NOT_RUNNING -> "Let’s get connected"
                            AccessState.ERROR -> "Connection needs attention"
                        }, style = MaterialTheme.typography.titleLarge, color = foreground)
                    }
                    Text(state.accessMessage ?: when (state.access) {
                        AccessState.READY -> "Live process snapshots and verified app controls are available."
                        AccessState.NEEDS_PERMISSION -> "Shizuku is running. Allow Background Check in the permission prompt to continue."
                        AccessState.CHECKING, AccessState.CONNECTING -> "Waiting for the service to respond."
                        AccessState.ERROR -> "Open Shizuku to check its service, then try connecting again."
                        AccessState.NOT_RUNNING -> "Start Shizuku using wireless debugging, then come back here. No root needed."
                    }, style = MaterialTheme.typography.bodyMedium, color = foreground.copy(alpha = 0.77f))
                    if (connecting) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    if (!ready) {
                        Button(onClick = onConnect, enabled = !connecting, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(16.dp)) {
                            Text(if (state.access == AccessState.NEEDS_PERMISSION) "Grant access" else "Check connection")
                            Spacer(Modifier.width(9.dp))
                            Icon(Icons.Rounded.ArrowForward, null, Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (ready) "Keep this connection handy" else "Your first connection", style = MaterialTheme.typography.titleLarge)
                Text("Use Shizuku’s wireless debugging setup on Android 11 or later. Your phone and its Wi-Fi connection stay in your hands.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                SetupStep("01", "Open Shizuku", "Install Shizuku if needed, then choose Start via wireless debugging. Shizuku shows the pairing steps.", "Open Shizuku", onOpenShizuku)
                SetupStep("02", "Pair with wireless debugging", "In Developer options, enable Wireless debugging. Open Pair device with pairing code, and enter that code in Shizuku’s pairing notification.", "Wireless debugging settings", onOpenWirelessSettings)
                SetupStep("03", "Start, then allow access", "Return to Shizuku and tap Start. Come back here, check the connection, and allow Background Check when asked.", if (state.access == AccessState.NEEDS_PERMISSION) "Grant access" else "Check connection", onConnect, enabled = !connecting)
            }
        }
        item { InfoCard("After a restart", "Shizuku usually needs to be started again after your phone restarts. If access drops, open Shizuku and reconnect. Your installed app list is still available.", Icons.Rounded.Refresh) }
        item { InfoCard("Local by design", "Background Check reads settings on this device. It has no internet permission, analytics, or always-running monitoring service.", Icons.Rounded.Shield, accent = Lime) }
    }
}

@Composable
private fun SetupStep(number: String, title: String, body: String, action: String, onClick: () -> Unit, enabled: Boolean = true) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Text(number, style = MaterialTheme.typography.labelLarge, color = Lime)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Muted)
            OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                Text(action, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}
