package dev.bgcheck.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import dev.bgcheck.app.R
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bgcheck.app.data.AccessState
import dev.bgcheck.app.data.AppEntry
import dev.bgcheck.app.data.PackageType
import dev.bgcheck.app.data.matchesPackage
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
    onOpenAppSettings: (AppEntry) -> Unit,
    onOpenShizuku: () -> Unit,
    onOpenWirelessSettings: () -> Unit,
    onClearMessage: () -> Unit,
    onRefreshResources: () -> Unit,
    onRefreshEnergy: () -> Unit,
    onSetMonitorPaused: (Boolean) -> Unit,
    onSetMonitorInterval: (Long) -> Unit,
    onMonitorVisible: (Boolean) -> Unit,
    onForceStop: () -> Unit,
    onOpenNetwork: (AppEntry?) -> Unit = {},
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
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    Scaffold(
        containerColor = Ink,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!wide && LocalDensity.current.fontScale > 1.3f) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(24.dp)).background(Panel)) {
                    Page.entries.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { item ->
                                val selected = page == item
                                Row(
                                    Modifier.weight(1f).heightIn(min = 56.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .selectable(selected, role = Role.Tab, onClick = { page = item })
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(item.icon, null, Modifier.size(20.dp), tint = if (selected) Purple else Muted)
                                    Spacer(Modifier.width(8.dp))
                                    Text(item.label, style = MaterialTheme.typography.labelMedium, color = if (selected) Purple else Muted)
                                }
                            }
                        }
                    }
                }
            } else if (!wide) {
                NavigationBar(modifier = Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(28.dp)), containerColor = Panel, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0)) {
                    Page.entries.forEach { item ->
                        NavigationBarItem(
                            selected = page == item,
                            onClick = { page = item },
                            icon = { Icon(item.icon, contentDescription = null, modifier = Modifier.size(23.dp)) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Purple, selectedTextColor = Purple, indicatorColor = MaterialTheme.colorScheme.primaryContainer, unselectedIconColor = Muted, unselectedTextColor = Muted),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (wide) {
                NavigationRail(modifier = Modifier.verticalScroll(rememberScrollState()), containerColor = Panel, windowInsets = WindowInsets(0, 0, 0, 0)) {
                    Page.entries.forEach { item ->
                        NavigationRailItem(selected = page == item, onClick = { page = item },
                            icon = { Icon(item.icon, null) }, label = { Text(item.label) })
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxSize()) {
                AppHeader(state, onRefresh, onOpenNetwork = { onOpenNetwork(null) })
                AnimatedContent(
                    targetState = page, modifier = Modifier.weight(1f), label = "Page transition",
                    transitionSpec = { (fadeIn(tween(200, 40)) + slideInVertically(tween(220)) { it / 40 }) togetherWith fadeOut(tween(100)) },
                ) { currentPage ->
                    pageState.SaveableStateProvider(currentPage.name) {
                        when (currentPage) {
                            Page.OVERVIEW -> OverviewScreen(state, onAccess = { page = Page.ACCESS }, onApps = { page = Page.APPS }, onSelect = onSelect, onUndo = onUndo)
                            Page.APPS -> AppsScreen(state, onSelect, onAccess = { page = Page.ACCESS })
                            Page.MONITOR -> MonitorScreen(state, onRefreshResources, onRefreshEnergy, onSetMonitorPaused, onSetMonitorInterval, onSelect, onAccess = { page = Page.ACCESS })
                            Page.ACCESS -> AccessScreen(state, onConnect, onOpenShizuku, onOpenWirelessSettings)
                        }
                    }
                }
            }
        }
    }
    state.selected?.let { app ->
        detailState.SaveableStateProvider("${app.key}:${app.profile.serialNumber}") {
            AppDetailSheet(state, app, onDismissDetail, onAction, onOpenAppSettings,
                onConnect = { onDismissDetail(); page = Page.ACCESS }, onForceStop = onForceStop,
                onRetry = { onClearMessage(); onRefresh() }, onSelectPackage = onSelect,
                onOpenNetwork = { onOpenNetwork(app) })
        }
    }
}

@Composable
private fun AppHeader(state: UiState, onRefresh: () -> Unit, onOpenNetwork: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.brand_mark), null, Modifier.size(34.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Background Check", style = MaterialTheme.typography.titleMedium)
            if (LocalDensity.current.fontScale <= 1.3f) {
                Text("Battery · apps · privacy", style = MaterialTheme.typography.labelSmall, color = Muted)
            }
        }
        IconButton(onClick = onOpenNetwork) { Icon(Icons.Rounded.Shield, "Open Network", tint = Purple) }
        if (state.loading || state.refreshing) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp).semantics { contentDescription = "Refreshing app and battery information" }, strokeWidth = 2.dp)
            }
        } else {
            IconButton(onClick = onRefresh, enabled = !state.busy) { Icon(Icons.Rounded.Refresh, "Refresh app and battery information", tint = Muted) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewScreen(state: UiState, onAccess: () -> Unit, onApps: () -> Unit, onSelect: (AppEntry) -> Unit, onUndo: (ChangeRecord) -> Unit) {
    var profileId by rememberSaveable { mutableStateOf(ALL_PROFILES) }
    val profiles = state.visibleProfiles()
    val scopedApps = state.apps.filter { profileId == ALL_PROFILES || it.userId == profileId }
    var showAllChanges by rememberSaveable { mutableStateOf(false) }
    var activeSort by rememberSaveable { mutableStateOf(AppSort.NAME) }
    var activeDirection by rememberSaveable { mutableStateOf(SortDirection.ASCENDING) }
    var historySort by rememberSaveable { mutableStateOf(HistorySort.TIME) }
    var historyDirection by rememberSaveable { mutableStateOf(SortDirection.DESCENDING) }
    val known = state.access == AccessState.READY && scopedApps.any { it.processState != ProcessState.UNKNOWN }
    val foreground = scopedApps.count { it.processState == ProcessState.FOREGROUND }
    val services = scopedApps.count { it.processState == ProcessState.SERVICE }
    val background = scopedApps.count { it.processState == ProcessState.BACKGROUND }
    val cached = scopedApps.count { it.processState == ProcessState.CACHED }
    val activeApps = remember(scopedApps, activeSort, activeDirection) {
        ListSorting.apps(scopedApps.filter { it.processState in setOf(ProcessState.FOREGROUND, ProcessState.SERVICE, ProcessState.BACKGROUND) }, activeSort, activeDirection).take(4)
    }
    val changes = remember(state.changes, profileId, historySort, historyDirection) {
        ListSorting.history(state.changes.filter { profileId == ALL_PROFILES || it.userId == profileId }, historySort, historyDirection)
    }
    LazyColumn(modifier = Modifier.testTag("overview-list"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Eyebrow("device overview", modifier = Modifier.align(Alignment.CenterVertically))
                    Surface(onClick = onAccess, color = Color.Transparent, modifier = Modifier.heightIn(min = 48.dp).align(Alignment.CenterVertically)) {
                        Box(contentAlignment = Alignment.Center) {
                            StatusPill(accessStatusLabel(state.access), state.access == AccessState.READY)
                        }
                    }
                }
                Text("System overview", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            }
        }
        item { BatteryCard(state.battery) }
        item { ProfileFilters("overview", profiles, profileId) { profileId = it } }
        if (state.access in setOf(AccessState.NOT_RUNNING, AccessState.NEEDS_PERMISSION, AccessState.ERROR)) {
            item { AccessPrompt(state.access, onAccess) }
        }
        if (state.access == AccessState.READY) item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading("Process snapshot", if (known) "As of ${timeLabel(state.capturedAt)}" else if (state.loading || state.refreshing) "Reading processes…" else "Reading unavailable")
                if (LocalDensity.current.fontScale > 1.3f) {
                    ProcessMetric("Foreground", if (known) foreground else null, "Visible or supporting UI", Modifier.fillMaxWidth(), Green)
                    ProcessMetric("Services", if (known) services else null, "Service process present", Modifier.fillMaxWidth(), Cyan)
                    ProcessMetric("Background", if (known) background else null, "Observed in memory", Modifier.fillMaxWidth(), Orange)
                    ProcessMetric("Cached", if (known) cached else null, "Kept for quick reopening", Modifier.fillMaxWidth(), Purple)
                } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProcessMetric("Foreground", if (known) foreground else null, "Visible or supporting UI", Modifier.weight(1f), Green)
                    ProcessMetric("Services", if (known) services else null, "Service process present", Modifier.weight(1f), Cyan)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProcessMetric("Background", if (known) background else null, "Observed in memory", Modifier.weight(1f), Orange)
                    ProcessMetric("Cached", if (known) cached else null, "Kept for quick reopening", Modifier.weight(1f), Purple)
                }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Info, null, tint = Muted, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                    Text("A process in memory does not mean an app is draining your battery. This is a snapshot, not energy use.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
        if (state.warnings.isNotEmpty()) {
            item { InfoCard("Some readings are unavailable", state.warnings.joinToString("\n"), Icons.Rounded.WarningAmber, accent = Orange) }
        }
        if (known && activeApps.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Active apps", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onApps) { Text("All apps"); Spacer(Modifier.width(4.dp)); Icon(Icons.Rounded.ArrowForward, null, Modifier.size(16.dp)) }
                }
            }
            item {
                SortControls("active-apps", AppSort.entries, activeSort, activeDirection,
                    onSort = { activeSort = it }, onDirection = { activeDirection = it })
            }
            items(activeApps, key = { "active-${it.key}" }) { AppRow(it, sort = activeSort, modifier = Modifier.animateItem(), onClick = { onSelect(it) }) }
        }
        item { SectionHeading("Your changes", "${changes.size} recorded") }
        if (changes.isEmpty()) {
            item { InfoCard("No changes recorded", "Changes you make here will appear here, with a way to restore the previous setting.", Icons.Rounded.History) }
        } else {
            item {
                SortControls("history", HistorySort.entries, historySort, historyDirection,
                    onSort = { historySort = it }, onDirection = { historyDirection = it })
            }
            items(if (showAllChanges) changes else changes.take(5), key = { "history-${it.id}" }) { record ->
                val profile = profiles.firstOrNull { it.userId == record.userId }
                val sameProfile = profile?.available == true && (record.userSerial == null || profile.serialNumber == null || record.userSerial == profile.serialNumber)
                HistoryCard(record, state.access == AccessState.READY && !state.busy && sameProfile, onUndo, connected = state.access == AccessState.READY)
            }
            if (changes.size > 5) {
                item {
                    TextButton(onClick = { showAllChanges = !showAllChanges }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (showAllChanges) "Show fewer changes" else "Show all ${changes.size} changes")
                    }
                }
            }
        }
        item { Text("Local data · no analytics", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BatteryCard(battery: BatteryInfo) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Panel, contentColor = Foreground), border = androidx.compose.foundation.BorderStroke(1.dp, Outline)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Eyebrow("battery · whole device")
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(battery.percent?.let { "$it%" } ?: "—", style = MaterialTheme.typography.displayMedium, color = Green)
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(if (battery.powerState == BatteryPowerState.CHARGING) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull, null, tint = Green)
                    Text(battery.powerState.label, style = MaterialTheme.typography.labelLarge, color = Cyan)
                }
            }
            battery.percent?.let {
                LinearProgressIndicator(progress = { (it / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp), color = Green, trackColor = Outline)
            }
            ResourceValue("Temperature", battery.temperatureC?.let { String.format(Locale.getDefault(), "%.1f °C", it) } ?: "Unavailable")
            ResourceValue("Battery saver", if (battery.percent == null) "Unavailable" else if (battery.saver) "On" else "Off")
        }
    }
}

@Composable
private fun SectionHeading(title: String, trailing: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun ProcessMetric(title: String, count: Int?, description: String, modifier: Modifier, accent: Color) {
    Card(modifier = modifier, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Panel), border = androidx.compose.foundation.BorderStroke(1.dp, Outline)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
                Text(title, style = MaterialTheme.typography.labelMedium, color = Muted)
            }
            Text(count?.toString() ?: "—", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 37.sp), color = accent)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Muted, minLines = 2)
        }
    }
}

@Composable
private fun AccessPrompt(access: AccessState, onAccess: () -> Unit) {
    Surface(onClick = onAccess, color = Panel, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(19.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Key, null, tint = Green, modifier = Modifier.size(22.dp))
                Text(if (access == AccessState.NEEDS_PERMISSION) "Allow access" else "Connect to Shizuku", style = MaterialTheme.typography.titleMedium)
            }
            Text(if (access == AccessState.NEEDS_PERMISSION) "Shizuku is running. One permission enables process readings and app controls." else "See running apps and manage background settings. You can browse installed apps anytime.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Set up access", style = MaterialTheme.typography.labelLarge, color = Green)
                Icon(Icons.Rounded.ArrowForward, null, tint = Green, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun HistoryCard(record: ChangeRecord, canUndo: Boolean, onUndo: (ChangeRecord) -> Unit, connected: Boolean) {
    val affectsAllProfiles = record.before.userExempt != record.after.userExempt || record.before.dozeExempt != record.after.dozeExempt
    var confirmUndo by remember(record.id) { mutableStateOf(false) }
    Card(modifier = Modifier.testTag("history-record-${record.id}"), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(record.packageName, 34.dp, userId = record.userId, userSerial = record.userSerial)
                Column(Modifier.weight(1f)) {
                    Text(record.label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${record.profileName} · User ${record.userId}", style = MaterialTheme.typography.labelSmall, color = Cyan)
                    Text(dateTimeLabel(record.timestamp), style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                if (record.undone) {
                    Text("Restored", style = MaterialTheme.typography.labelMedium, color = Muted)
                } else {
                    TextButton(onClick = { if (affectsAllProfiles) confirmUndo = true else onUndo(record) }, enabled = canUndo) { Text(if (record.recovery) "Restore" else "Undo") }
                }
            }
            Text(record.description, style = MaterialTheme.typography.bodyMedium, color = Muted)
            if (affectsAllProfiles) Text("Doze exemption · applies across all Android profiles", style = MaterialTheme.typography.bodySmall, color = Orange)
            if (record.recovery && !record.undone) Text(
                if (connected) "This change could not be verified. Restore the previous policy; conflicting changes will be left untouched." else "Connect to restore the previous policy. Conflicting changes will be left untouched.",
                style = MaterialTheme.typography.bodySmall, color = Orange,
            )
        }
    }
    if (confirmUndo) AlertDialog(
        onDismissRequest = { confirmUndo = false },
        title = { Text("Restore Doze setting across profiles?") },
        text = { Text("Restore the previous Doze exemption for ${record.label}. This affects every copy of this app, including personal and work profiles. The original change was made from ${record.profileName} (User ${record.userId}).") },
        confirmButton = { TextButton(onClick = { confirmUndo = false; onUndo(record) }, enabled = canUndo) { Text("Restore across profiles") } },
        dismissButton = { TextButton(onClick = { confirmUndo = false }) { Text("Cancel") } },
        containerColor = Panel,
    )
}

private enum class ProcessFilter(val label: String) { ALL("Any state"), ACTIVE("Active"), CACHED("Cached"), NOT_OBSERVED("Not observed") }

@Composable
private fun AppsScreen(state: UiState, onSelect: (AppEntry) -> Unit, onAccess: () -> Unit) {
    val focusManager = LocalFocusManager.current
    var profileId by rememberSaveable { mutableStateOf(ALL_PROFILES) }
    val profiles = state.visibleProfiles()
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(PackageType.USER) }
    var process by rememberSaveable { mutableStateOf(ProcessFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(AppSort.NAME) }
    var direction by rememberSaveable { mutableStateOf(SortDirection.ASCENDING) }
    val effectiveProcess = if (state.access == AccessState.READY && type != PackageType.UNINSTALLED) process else ProcessFilter.ALL
    val apps = remember(state.apps, state.uninstalledApps, profileId, query, type, effectiveProcess, sort, direction) {
        ListSorting.apps((state.apps + state.uninstalledApps).filter { app ->
            (profileId == ALL_PROFILES || app.userId == profileId) && matchesPackage(app, type, query) &&
                when (effectiveProcess) {
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
                Text("Your apps", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                label = { Text("Search apps") },
                placeholder = { Text("Name, package or purpose") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search") } }) else null,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Panel, focusedContainerColor = Panel, unfocusedBorderColor = Comment, focusedBorderColor = Purple),
            )
        }
        item {
            Disclosure("Filters", "${type.label} · ${effectiveProcess.label}" + if (profileId == ALL_PROFILES) "" else " · ${profiles.firstOrNull { it.userId == profileId }?.name ?: "User $profileId"}") {
                SortControls("apps", AppSort.entries, sort, direction, onSort = { sort = it }, onDirection = { direction = it })
                ProfileFilters("apps", profiles, profileId) { profileId = it }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PackageType.entries.forEach { item ->
                        FilterChip(selected = type == item, onClick = { type = item }, label = { Text(item.label) },
                            modifier = Modifier.heightIn(min = 48.dp), colors = appFilterColors())
                    }
                }
                if (type == PackageType.UNINSTALLED) {
                    Text("Not installed for the shown profile. Only packages Android still has a record of are shown; this is not a complete uninstall history.", style = MaterialTheme.typography.bodySmall, color = Muted)
                } else if (state.access == AccessState.READY) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProcessFilter.entries.forEach { item ->
                            FilterChip(selected = process == item, onClick = { process = item }, label = { Text(item.label) },
                                modifier = Modifier.heightIn(min = 48.dp), colors = appFilterColors())
                        }
                    }
                } else if (state.access !in setOf(AccessState.CHECKING, AccessState.CONNECTING)) {
                    Surface(onClick = onAccess, color = PanelRaised, shape = RoundedCornerShape(24.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Rounded.Key, null, tint = Green, modifier = Modifier.size(19.dp))
                            Text("Connect to see process and battery settings", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), color = Foreground)
                            Icon(Icons.Rounded.ArrowForward, null, tint = Muted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        item {
            val scope = when {
                type == PackageType.UNINSTALLED -> "Retained by Android"
                type == PackageType.ALL -> "Installed and retained packages"
                state.capturedAt != null && state.access == AccessState.READY -> "Snapshot ${timeLabel(state.capturedAt)}"
                else -> "Installed in the selected profiles"
            }
            Text("${apps.size} ${if (apps.size == 1) "app" else "apps"} · $scope", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(vertical = 6.dp))
        }
        if (state.loading && state.apps.isEmpty()) {
            item { InfoCard("Finding your apps", "Reading the apps installed on this device.", Icons.Rounded.Apps) }
        } else if (apps.isEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoCard("No matching apps", "Try another app name or reset your search and filters.", Icons.Rounded.Search)
                    TextButton(onClick = { query = ""; type = PackageType.ALL; process = ProcessFilter.ALL; profileId = ALL_PROFILES; focusManager.clearFocus() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Reset filters") }
                }
            }
        } else {
            items(apps, key = { it.key }) { app -> AppRow(app, sort = sort, modifier = Modifier.animateItem(), onClick = { focusManager.clearFocus(); onSelect(app) }) }
        }
    }
}

@Composable
private fun appFilterColors() = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = Purple, containerColor = Ink, labelColor = Muted)
