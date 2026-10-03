package dev.bgcheck.app

import android.app.Application
import android.os.Process
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.bgcheck.app.data.*
import dev.bgcheck.app.shell.ShizukuBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

class BackgroundViewModel(application: Application) : AndroidViewModel(application) {
    private val bridge = ShizukuBridge(application)
    private val inventory = AppInventory(application)
    private val engine = PolicyEngine(bridge::execute, Process.myUid() / 100000)
    private val preferences = application.getSharedPreferences("policy_history", 0)
    private val gate = Mutex()
    private var detailJob: Job? = null
    private var detailRevision = 0
    private var refreshAgain = false
    private val mutableState = MutableStateFlow(UiState(changes = ChangeHistory.decode(preferences.getString("changes", null))))
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            bridge.access.collectLatest { access ->
                mutableState.update { old ->
                    if (access == AccessState.READY) old.copy(access = access)
                    else old.copy(access = access, policy = null,
                        apps = old.apps.map { it.copy(processState = ProcessState.UNKNOWN, dozeExempt = null, standbyBucket = null) },
                        capturedAt = null)
                }
                if (access == AccessState.READY) refresh()
            }
        }
        viewModelScope.launch { bridge.message.collectLatest { message -> mutableState.update { it.copy(accessMessage = message) } } }
        refresh()
    }

    fun onResume() { bridge.check(); refresh() }
    fun connect() { bridge.requestPermission() }
    fun clearMessage() { mutableState.update { it.copy(message = null) } }

    fun refresh() {
        if (state.value.busy) return
        if (state.value.refreshing) { refreshAgain = true; return }
        mutableState.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            try {
                gate.withLock {
                    val apps = inventory.load()
                    val battery = inventory.battery()
                    val snapshot = if (bridge.access.value == AccessState.READY) engine.snapshot() else null
                    val connected = bridge.access.value == AccessState.READY
                    val merged = apps.map { app ->
                        if (snapshot == null || !connected) app else app.copy(
                            processState = snapshot.processes[app.packageName]
                                ?: if (snapshot.processesKnown) ProcessState.NOT_RUNNING else ProcessState.UNKNOWN,
                            standbyBucket = snapshot.buckets[app.packageName],
                            dozeExempt = if (snapshot.exemptionsKnown) app.packageName in snapshot.exemptPackages || app.uid % 100000 in snapshot.exemptAppIds else null,
                        )
                    }
                    mutableState.update { old -> old.copy(apps = merged, battery = battery, loading = false,
                        capturedAt = snapshot?.capturedAt.takeIf { connected }, warnings = snapshot?.warnings.orEmpty(),
                        selected = old.selected?.let { selected -> merged.firstOrNull { it.packageName == selected.packageName } }) }
                }
                state.value.selected?.let { loadDetail(it) }
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
        mutableState.update { it.copy(selected = app, policy = null) }
        loadDetail(app)
    }

    private fun loadDetail(app: AppEntry) {
        val revision = ++detailRevision
        detailJob?.cancel()
        if (bridge.access.value != AccessState.READY) {
            mutableState.update { it.copy(detailLoading = false) }
            return
        }
        mutableState.update { it.copy(detailLoading = true) }
        detailJob = viewModelScope.launch {
            try {
                val policy = gate.withLock { engine.read(app) }
                if (bridge.access.value == AccessState.READY) mutableState.update {
                    if (detailRevision == revision && it.selected?.packageName == app.packageName) it.copy(policy = policy) else it
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableState.update { if (detailRevision == revision && it.selected?.packageName == app.packageName) it.copy(message = e.message ?: "Couldn't read this app's policy.") else it }
            } finally {
                mutableState.update { if (detailRevision == revision && it.selected?.packageName == app.packageName) it.copy(detailLoading = false) else it }
            }
        }
    }

    fun dismissDetail() {
        detailRevision++
        detailJob?.cancel()
        mutableState.update { it.copy(selected = null, policy = null, detailLoading = false) }
    }

    fun apply(action: ControlAction) {
        val app = state.value.selected ?: return
        val before = state.value.policy ?: return
        if (state.value.busy || bridge.access.value != AccessState.READY) return
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                gate.withLock {
                    val description = when(action) {
                        ControlAction.ALLOW_BACKGROUND -> "Background activity allowed"
                        ControlAction.RESTRICT_BACKGROUND -> "Background activity restricted"
                        ControlAction.EXEMPT_DOZE -> "Doze exemption added"
                        ControlAction.OPTIMIZE_DOZE -> "Doze exemption removed"
                    }
                    lateinit var pending: ChangeRecord
                    val after = engine.apply(app, before, action) { verifiedBefore, intended ->
                        pending = ChangeRecord(UUID.randomUUID().toString(), app.packageName, app.label, description,
                            System.currentTimeMillis(), verifiedBefore, intended, recovery = true)
                        val pendingRecords = (listOf(pending) + state.value.changes).take(50)
                        check(saveHistory(pendingRecords)) { "Couldn't save the previous policy. No settings were changed." }
                        mutableState.update { it.copy(changes = pendingRecords) }
                    }
                    val record = pending.copy(after = after, recovery = false)
                    val records = state.value.changes.map { if (it.id == pending.id) record else it }
                    val persisted = saveHistory(records)
                    mutableState.update { old -> old.copy(changes = records,
                        policy = if (old.selected?.packageName == app.packageName) after else old.policy,
                        message = if (persisted) "$description. Verified on device." else "$description. Verified, but history could not be saved. Undo is available until you close the app.",
                        apps = old.apps.map { if(it.packageName == app.packageName) it.copy(dozeExempt = after.dozeExempt, standbyBucket = after.standbyBucket) else it }) }
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
        val app = state.value.apps.firstOrNull { it.packageName == record.packageName } ?: run {
            mutableState.update { it.copy(message = "This app is no longer installed.") }; return
        }
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                gate.withLock {
                    val restored = if (record.recovery) engine.recover(app, record.before, record.after)
                        else engine.restore(app, record.before, record.after) {
                            val pendingRecords = state.value.changes.map { if (it.id == record.id) it.copy(recovery = true) else it }
                            check(saveHistory(pendingRecords)) { "Couldn't save the recovery checkpoint. No settings were changed." }
                            mutableState.update { it.copy(changes = pendingRecords) }
                        }
                    val records = state.value.changes.map { if(it.id == record.id) it.copy(undone = true, recovery = false) else it }
                    val persisted = saveHistory(records)
                    mutableState.update { old -> old.copy(changes = records,
                        policy = if (old.selected?.packageName == app.packageName) restored else old.policy,
                        apps = old.apps.map { if(it.packageName == app.packageName) it.copy(dozeExempt = restored.dozeExempt) else it },
                        message = if (persisted) "Previous policy restored and verified for ${app.label}." else "Previous policy restored. History could not be saved.") }
                }
            } catch(e: Exception) {
                if(e is CancellationException) throw e
                mutableState.update { it.copy(message = e.message ?: "Couldn't undo this change. Refresh and check the app's policy.") }
            } finally { mutableState.update { it.copy(busy = false) } }
        }
    }

    private suspend fun saveHistory(records: List<ChangeRecord>): Boolean = withContext(Dispatchers.IO) {
        preferences.edit().putString("changes", ChangeHistory.encode(records)).commit()
    }
    override fun onCleared() { bridge.close(); super.onCleared() }
}
