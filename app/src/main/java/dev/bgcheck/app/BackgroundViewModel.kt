package dev.bgcheck.app

import android.app.Application
import android.os.Process
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.bgcheck.app.data.*
import dev.bgcheck.app.shell.ShizukuBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

class BackgroundViewModel(application: Application) : AndroidViewModel(application) {
    private val bridge = ShizukuBridge(application)
    private val inventory = AppInventory(application)
    private fun engine(userId: Int) = PolicyEngine(bridge::execute, userId)
    private val monitor = ResourceMonitor(bridge::execute)

    private val preferences = application.getSharedPreferences("policy_history", 0)
    private val inspectionPreferences = application.getSharedPreferences("inspection_preferences", 0)
    private val networkDependencies = dev.bgcheck.network.De1984Dependencies.getInstance(application)
    private val comparisonStore = BatteryComparisonStore(application)
    private val gate = Mutex()
    private val pinGate = Mutex()
    private var comparisonJob: Job? = null
    private var detailJob: Job? = null
    private var diagnosticsJob: Job? = null
    private var resourceJob: Job? = null
    private var energyJob: Job? = null
    private var pollingJob: Job? = null
    private var foreground = false
    private var monitorVisible = false
    private var detailRevision = 0
    private var refreshAgain = false
    private val mutableState = MutableStateFlow(UiState(
        changes = ChangeHistory.decode(preferences.getString("changes", null), Process.myUid() / 100000),
        pins = PinnedApp.decode(inspectionPreferences.getString("pins", null)),
        monitorIntervalMs = inspectionPreferences.getLong("monitor.interval", 5_000).takeIf { it in setOf(3_000L, 5_000L, 10_000L) } ?: 5_000,
        batteryComparison = comparisonStore.load(),
    ))
    val state = mutableState.asStateFlow()

    private val profileReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            detailRevision++
            detailJob?.cancel(); diagnosticsJob?.cancel()
            mutableState.update { it.copy(policy = null, diagnostics = null) }
            refresh()
        }
    }
    private val packageCallback = object : android.content.pm.LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: android.os.UserHandle) { refresh() }
        override fun onPackageAdded(packageName: String, user: android.os.UserHandle) { refresh() }
        override fun onPackageChanged(packageName: String, user: android.os.UserHandle) { refresh() }
        override fun onPackagesAvailable(packageNames: Array<out String>, user: android.os.UserHandle, replacing: Boolean) { refresh() }
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: android.os.UserHandle, replacing: Boolean) { refresh() }
    }

    init {
        viewModelScope.launch {
            val manager = networkDependencies.firewallManager
            combine(manager.firewallState, manager.firewallHealth,
                dev.bgcheck.network.domain.firewall.NetworkPolicySupport.fullBlocking,
                state.map { it.profiles }.distinctUntilChanged(), manager.activeBackendType) { lifecycle, health, npmFull, profiles, activeBackend ->
                ProtectionSummary.from(lifecycle, health, profiles, Process.myUid() / 100000, npmFull, activeBackend)
            }.collect { summary -> mutableState.update { it.copy(protection = summary) } }
        }
        viewModelScope.launch {
            networkDependencies.networkChangeHistory.records.collect { records ->
                mutableState.update { it.copy(networkChanges = records) }
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(application, profileReceiver, android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(android.content.Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(android.content.Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(android.content.Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(android.content.Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
        }, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        application.getSystemService(android.content.pm.LauncherApps::class.java)
            .registerCallback(packageCallback, android.os.Handler(android.os.Looper.getMainLooper()))
        viewModelScope.launch {
            bridge.access.collectLatest { access ->
                mutableState.update { old ->
                    if (access == AccessState.READY) old.copy(access = access)
                    else old.copy(access = access, policy = null,
                        apps = old.apps.map { it.copy(processState = ProcessState.UNKNOWN, dozeExempt = null, standbyBucket = null) },
                        capturedAt = null, resources = null, energy = null, diagnostics = null, resourceHistory = emptyList())
                }
                if (access == AccessState.READY) refresh()
                else {
                    detailRevision++
                    detailJob?.cancel(); resourceJob?.cancel(); energyJob?.cancel(); diagnosticsJob?.cancel(); comparisonJob?.cancel()
                    mutableState.update { it.copy(detailLoading = false, diagnosticsLoading = false) }
                }
                restartPolling()
            }
        }
        viewModelScope.launch { bridge.message.collectLatest { message -> mutableState.update { it.copy(accessMessage = message) } } }
        refresh()
    }

    fun onResume() { foreground = true; bridge.check(); refresh(); restartPolling() }
    fun onPause() {
        foreground = false
        detailRevision++
        pollingJob?.cancel(); resourceJob?.cancel(); energyJob?.cancel(); detailJob?.cancel(); diagnosticsJob?.cancel(); comparisonJob?.cancel()
        mutableState.update { it.copy(monitoring = false, detailLoading = false, diagnosticsLoading = false) }
    }
    fun connect() { bridge.requestPermission() }
    fun clearMessage() { mutableState.update { it.copy(message = null) } }

    fun setMonitorVisible(visible: Boolean) {
        if (monitorVisible == visible) return
        monitorVisible = visible
        restartPolling()
    }

    fun setMonitorPaused(paused: Boolean) {
        mutableState.update { it.copy(monitorPaused = paused) }
        restartPolling()
    }

    fun setMonitorInterval(intervalMs: Long) {
        if (intervalMs !in setOf(3_000L, 5_000L, 10_000L)) return
        inspectionPreferences.edit().putLong("monitor.interval", intervalMs).apply()
        mutableState.update { it.copy(monitorIntervalMs = intervalMs) }
        restartPolling()
    }

    fun togglePin(app: AppEntry) {
        viewModelScope.launch {
            pinGate.withLock {
                val existing = state.value.pins.firstOrNull { it.matches(app) }
                val pin = existing ?: PinnedApp.from(app)
                if (pin == null) {
                    mutableState.update { it.copy(message = "Refresh to verify this installation before pinning it.") }
                    return@withLock
                }
                val pins = if (existing != null) state.value.pins - pin else state.value.pins + pin
                val saved = withContext(Dispatchers.IO) { inspectionPreferences.edit().putString("pins", PinnedApp.encode(pins)).commit() }
                mutableState.update { if (saved) it.copy(pins = pins) else it.copy(message = "Couldn't save pinned apps. Try again.") }
            }
        }
    }

    fun captureComparison(restart: Boolean) {
        if (!foreground || bridge.access.value != AccessState.READY || state.value.busy || state.value.batteryComparisonLoading) return
        val baseline = state.value.batteryComparison
        if (!restart && baseline == null) return
        mutableState.update { it.copy(batteryComparisonLoading = true) }
        comparisonJob = viewModelScope.launch {
            try {
                val sample = gate.withLock {
                    val inventoryBefore = inventory.read()
                    val energy = monitor.energy()
                    val elapsedAt = SystemClock.elapsedRealtime()
                    val bootCount = runCatching { android.provider.Settings.Global.getInt(
                        getApplication<Application>().contentResolver, android.provider.Settings.Global.BOOT_COUNT) }.getOrNull()?.takeIf { it >= 0 }
                    check(energy.uids.isNotEmpty() && energy.startClockTimeMs != null) { "Battery history is unavailable. Reconnect and try again." }
                    val inventoryAfter = inventory.read()
                    check(inventoryBefore.apps.filter { it.installed }.map { listOf(it.key, it.uid, it.profile.serialNumber, it.firstInstallTime) }.toSet() ==
                        inventoryAfter.apps.filter { it.installed }.map { listOf(it.key, it.uid, it.profile.serialNumber, it.firstInstallTime) }.toSet()) {
                        "App installations changed during sampling. Try again."
                    }
                    BatteryComparisonSample.capture(energy, inventoryAfter.apps, inventoryAfter.profiles, elapsedAt, bootCount)
                }
                val session = if (restart) BatteryComparisonSession(sample) else baseline!!.copy(later = sample)
                // Once a complete sample is ready, finish its durable save and publication together.
                // Cancellation during commit must not leave a new disk baseline with an old UI baseline.
                withContext(NonCancellable) {
                    withContext(Dispatchers.IO) { comparisonStore.save(session) }
                    mutableState.update { it.copy(batteryComparison = session) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableState.update { it.copy(message = e.message ?: "Couldn't save battery comparison.") }
            } finally { mutableState.update { it.copy(batteryComparisonLoading = false) } }
        }
    }

    fun clearComparison() {
        if (state.value.batteryComparisonLoading) return
        mutableState.update { it.copy(batteryComparisonLoading = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { comparisonStore.clear() }
                mutableState.update { it.copy(batteryComparison = null) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableState.update { it.copy(message = e.message ?: "Couldn't clear comparison.") }
            } finally { mutableState.update { it.copy(batteryComparisonLoading = false) } }
        }
    }

    fun undoNetwork(id: String) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { networkDependencies.networkChangeUndo.undo(id) }
                mutableState.update { it.copy(message = result.getOrElse { e -> e.message ?: "Couldn't restore this change." }) }
            } finally { mutableState.update { it.copy(busy = false) }; refresh() }
        }
    }

    fun exportChanges(uri: android.net.Uri) {
        val current = state.value
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val json = org.json.JSONObject().apply {
                        put("formatVersion", 1); put("exportedAt", System.currentTimeMillis())
                        put("policies", org.json.JSONArray(ChangeHistory.encode(current.changes)))
                        put("network", org.json.JSONArray(networkDependencies.networkChangeHistory.exportJson()))
                    }.toString(2)
                    val resolver = getApplication<Application>().contentResolver
                    checkNotNull(resolver.openOutputStream(uri, "wt")) { "Couldn't open the export document." }.bufferedWriter().use { it.write(json) }
                }
                mutableState.update { it.copy(message = "Change history exported.") }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableState.update { it.copy(message = "Export failed: ${e.message ?: "document unavailable"}") }
            }
        }
    }

    private fun restartPolling() {
        pollingJob?.cancel()
        val active = foreground && monitorVisible && !state.value.monitorPaused && bridge.access.value == AccessState.READY
        mutableState.update { it.copy(monitoring = active) }
        if (!active) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                val started = SystemClock.elapsedRealtime()
                refreshResources()
                resourceJob?.join()
                if (state.value.energy?.let { System.currentTimeMillis() - it.capturedAt >= 60_000 } != false) refreshEnergy()
                delay((state.value.monitorIntervalMs - (SystemClock.elapsedRealtime() - started)).coerceAtLeast(100))
            }
        }
    }

    fun refreshResources() {
        if (bridge.access.value != AccessState.READY || state.value.busy || resourceJob?.isActive == true) return
        mutableState.update { it.copy(resourceLoading = true) }
        resourceJob = viewModelScope.launch {
            try {
                val snapshot = gate.withLock { monitor.sample() }
                val battery = inventory.battery()
                if (bridge.access.value == AccessState.READY) mutableState.update {
                    val point = ResourceHistoryPoint(snapshot.capturedAt, snapshot.cpuPercent,
                        snapshot.memory?.let { memory -> memory.totalKb - memory.availableKb })
                    it.copy(resources = snapshot, battery = battery, resourceHistory = (it.resourceHistory + point).takeLast(60))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (bridge.access.value == AccessState.READY) mutableState.update {
                    it.copy(resources = ResourceSnapshot(warnings = listOf(e.message ?: "Resource readings unavailable.")))
                }
            } finally { mutableState.update { it.copy(resourceLoading = false) } }
        }
    }

    fun refreshEnergy() {
        if (bridge.access.value != AccessState.READY || state.value.busy || energyJob?.isActive == true) return
        mutableState.update { it.copy(energyLoading = true) }
        energyJob = viewModelScope.launch {
            try {
                val battery = inventory.battery()
                mutableState.update { it.copy(battery = battery) }
                val energy = gate.withLock { monitor.energy() }
                if (bridge.access.value == AccessState.READY) mutableState.update { it.copy(energy = energy) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (bridge.access.value == AccessState.READY) mutableState.update {
                    it.copy(energy = EnergySnapshot(warnings = listOf(e.message ?: "Battery attribution unavailable.")))
                }
            } finally { mutableState.update { it.copy(energyLoading = false) } }
        }
    }

    fun onPackageActionCompleted(message: String) {
        networkDependencies.packageDataSource.invalidateCache()
        mutableState.update { it.copy(message = message) }
        refresh()
    }

    fun refresh() {
        if (state.value.busy) return
        if (state.value.refreshing) { refreshAgain = true; return }
        mutableState.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            try {
                gate.withLock {
                    val result = inventory.read()
                    val (apps, uninstalledApps) = result.apps.partition { it.installed }
                    val battery = inventory.battery()
                    val snapshots = if (bridge.access.value == AccessState.READY)
                        result.profiles.filter { it.available }.associate { it.userId to engine(it.userId).snapshot() }
                        else emptyMap()
                    val connected = bridge.access.value == AccessState.READY
                    val merged = ProfileSupport.merge(apps, snapshots.takeIf { connected }.orEmpty())
                    mutableState.update { old ->
                        val selected = old.selected?.let { prior -> (merged + uninstalledApps).firstOrNull {
                            it.key == prior.key && it.uid == prior.uid && it.profile.serialNumber == prior.profile.serialNumber
                        } }
                        old.copy(apps = merged, uninstalledApps = uninstalledApps, profiles = result.profiles,
                            battery = battery, loading = false,
                            capturedAt = snapshots.values.maxOfOrNull { it.capturedAt }.takeIf { connected },
                            warnings = result.warnings + snapshots.flatMap { (id, snapshot) -> snapshot.warnings.map {
                                "${result.profiles.firstOrNull { p -> p.userId == id }?.name ?: "User $id"}: $it"
                            } }, selected = selected,
                            policy = old.policy.takeIf { selected != null && selected.profile.available },
                            diagnostics = old.diagnostics.takeIf { selected != null && selected.profile.available })
                    }
                }
                if (foreground) {
                    state.value.selected?.let { loadDetail(it) }
                    refreshResources(); refreshEnergy()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableState.update { it.copy(loading = false, message = e.message ?: "Couldn't refresh. Try again.") }
            } finally {
                mutableState.update { it.copy(refreshing = false) }
                if (refreshAgain) { refreshAgain = false; refresh() }
            }
        }
    }

    fun select(app: AppEntry) {
        detailJob?.cancel()
        diagnosticsJob?.cancel()
        mutableState.update { it.copy(selected = app, policy = null, diagnostics = null, message = null) }
        loadDetail(app)
    }

    private fun loadDetail(app: AppEntry) {
        val revision = ++detailRevision
        detailJob?.cancel()
        diagnosticsJob?.cancel()
        if (!app.installed || !app.profile.available || !foreground || bridge.access.value != AccessState.READY) {
            mutableState.update { it.copy(detailLoading = false, diagnosticsLoading = false, policy = null, diagnostics = null) }
            return
        }
        mutableState.update { it.copy(detailLoading = true) }
        detailJob = viewModelScope.launch {
            try {
                val policy = gate.withLock { engine(app.userId).read(app) }
                if (bridge.access.value == AccessState.READY) mutableState.update {
                    if (detailRevision == revision && it.selected?.key == app.key) it.copy(policy = policy) else it
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableState.update {
                    if (detailRevision == revision && it.selected?.key == app.key)
                        it.copy(policy = null, message = e.message ?: "Couldn't read this app's policy.") else it
                }
            } finally {
                mutableState.update { if (detailRevision == revision && it.selected?.key == app.key) it.copy(detailLoading = false) else it }
            }
        }
        mutableState.update { it.copy(diagnosticsLoading = true) }
        diagnosticsJob = viewModelScope.launch {
            try {
                val diagnostics = gate.withLock { monitor.diagnostics(app) }
                if (bridge.access.value == AccessState.READY) mutableState.update {
                    if (detailRevision == revision && it.selected?.key == app.key) it.copy(diagnostics = diagnostics) else it
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableState.update {
                    if (detailRevision == revision && it.selected?.key == app.key)
                        it.copy(diagnostics = AppDiagnostics(app.packageName, userId = app.userId, warnings = listOf(e.message ?: "App diagnostics unavailable."))) else it
                }
            } finally {
                mutableState.update { if (detailRevision == revision) it.copy(diagnosticsLoading = false) else it }
            }
        }
    }

    fun dismissDetail() {
        detailRevision++
        detailJob?.cancel()
        diagnosticsJob?.cancel()
        mutableState.update { it.copy(selected = null, policy = null, detailLoading = false, diagnostics = null, diagnosticsLoading = false) }
    }

    fun forceStop() {
        val app = state.value.selected ?: return
        if (state.value.busy || bridge.access.value != AccessState.READY) return
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val result = gate.withLock { ProcessManager(bridge::execute, app.userId).forceStop(verifiedApp(app)) }
                mutableState.update { it.copy(message = result.message) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableState.update { it.copy(message = e.message ?: "Force stop could not be verified.") }
            } finally {
                mutableState.update { it.copy(busy = false) }
                refresh()
            }
        }
    }

    fun apply(action: ControlAction) {
        val app = state.value.selected ?: return
        val before = state.value.policy ?: return
        if (state.value.busy || bridge.access.value != AccessState.READY) return
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                gate.withLock {
                    val description = when(action) {
                        ControlAction.ALLOW_BACKGROUND -> "Background activity allowed"
                        ControlAction.RESTRICT_BACKGROUND -> "Background activity restricted"
                        ControlAction.EXEMPT_DOZE -> "Doze exemption added across profiles"
                        ControlAction.OPTIMIZE_DOZE -> "Doze exemption removed across profiles"
                    }
                    lateinit var pending: ChangeRecord
                    val after = engine(app.userId).apply(verifiedApp(app), before, action) { verifiedBefore, intended ->
                        pending = ChangeRecord(UUID.randomUUID().toString(), app.packageName, app.label, description,
                            System.currentTimeMillis(), verifiedBefore, intended, recovery = true, profileName = app.profile.name)
                        val pendingRecords = (listOf(pending) + state.value.changes).take(50)
                        check(saveHistory(pendingRecords)) { "Couldn't save the previous policy. No settings were changed." }
                        mutableState.update { it.copy(changes = pendingRecords) }
                    }
                    val record = pending.copy(after = after, recovery = false)
                    val records = state.value.changes.map { if (it.id == pending.id) record else it }
                    val persisted = saveHistory(records)
                    mutableState.update { old -> old.copy(changes = records,
                        policy = if (old.selected?.key == app.key) after else old.policy,
                        message = if (persisted) "$description. Verified on device." else "$description. Verified, but history could not be saved. Undo is available until you close the app.",
                        apps = old.apps.map { if (it.packageName == app.packageName) it.copy(dozeExempt = after.dozeExempt,
                            standbyBucket = if (it.key == app.key) after.standbyBucket else it.standbyBucket) else it }) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableState.update { it.copy(message = e.message ?: "The change couldn't be verified. Refresh before trying again.") }
                state.value.selected?.let { loadDetail(it) }
            } finally { mutableState.update { it.copy(busy = false) } }
        }
    }

    fun undo(record: ChangeRecord) {
        if (state.value.busy || record.undone || bridge.access.value != AccessState.READY) return
        val app = state.value.apps.firstOrNull { it.packageName == record.packageName && it.userId == record.userId &&
            (record.userSerial == null || record.userSerial == it.profile.serialNumber) } ?: run {
            mutableState.update { it.copy(message = "This app or its original profile is no longer available.") }; return
        }
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                gate.withLock {
                    val verified = verifiedApp(app)
                    val restored = if (record.recovery) engine(app.userId).recover(verified, record.before, record.after)
                        else engine(app.userId).restore(verified, record.before, record.after) {
                            val pendingRecords = state.value.changes.map { if (it.id == record.id) it.copy(recovery = true) else it }
                            check(saveHistory(pendingRecords)) { "Couldn't save the recovery checkpoint. No settings were changed." }
                            mutableState.update { it.copy(changes = pendingRecords) }
                        }
                    val records = state.value.changes.map { if(it.id == record.id) it.copy(undone = true, recovery = false) else it }
                    val persisted = saveHistory(records)
                    mutableState.update { old -> old.copy(changes = records,
                        policy = if (old.selected?.key == app.key) restored else old.policy,
                        apps = old.apps.map { if(it.packageName == app.packageName) it.copy(dozeExempt = restored.dozeExempt) else it },
                        message = if (persisted) "Previous policy restored and verified for ${app.label}." else "Previous policy restored. History could not be saved.") }
                }
            } catch(e: Exception) {
                if(e is CancellationException) throw e
                mutableState.update { it.copy(message = e.message ?: "Couldn't undo this change. Refresh and check the app's policy.") }
            } finally { mutableState.update { it.copy(busy = false) } }
        }
    }

    /** Re-read profile incarnation and protected defaults immediately before changing anything. */
    private suspend fun verifiedApp(app: AppEntry): AppEntry {
        val fresh = inventory.read()
        ProfileSupport.requireAvailable(app, fresh.profiles)
        val target = fresh.apps.singleOrNull { it.key == app.key && it.installed }
            ?: error("This app is no longer installed in the selected profile.")
        check(target.uid == app.uid) { "This app was reinstalled. Refresh before changing it." }
        check(target.protectedReason == null) { target.protectedReason ?: "This app is protected." }
        return target
    }

    fun openAppSettings(app: AppEntry) {
        viewModelScope.launch {
            try {
                gate.withLock {
                    ProfileSupport.requireAvailable(app, inventory.profiles())
                    val output = bridge.execute(listOf("am", "start", "--user", app.userId.toString(), "-a",
                        "android.settings.APPLICATION_DETAILS_SETTINGS", "-d", "package:${app.packageName}"))
                    check(!output.contains("Error:", true) && !output.contains("Exception", true)) { output.take(300) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableState.update { it.copy(message = "Couldn't open ${app.profile.name} app settings: ${e.message}") }
            }
        }
    }

    private suspend fun saveHistory(records: List<ChangeRecord>): Boolean = withContext(Dispatchers.IO) {
        preferences.edit().putString("changes", ChangeHistory.encode(records)).commit()
    }
    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(profileReceiver)
        getApplication<Application>().getSystemService(android.content.pm.LauncherApps::class.java).unregisterCallback(packageCallback)
        bridge.close()
        super.onCleared()
    }
}
