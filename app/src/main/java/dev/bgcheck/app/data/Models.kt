package dev.bgcheck.app.data

enum class ProcessState(val label: String) {
    FOREGROUND("Foreground"), SERVICE("Service"), BACKGROUND("Background"), CACHED("Cached"), NOT_RUNNING("Not observed"), UNKNOWN("Unknown")
}
data class AppEntry(
    val packageName: String,
    val label: String,
    val uid: Int,
    val system: Boolean,
    val enabled: Boolean = true,
    val targetSdk: Int = 35,
    val processState: ProcessState = ProcessState.UNKNOWN,
    val standbyBucket: Int? = null,
    val dozeExempt: Boolean? = null,
    val protectedReason: String? = null,
    val installed: Boolean = true,
    val description: PackageDescription = PackageDescriptions.describe(packageName),
    val associations: List<PackageAssociation> = PackageDescriptions.associations(packageName),
    val hasInternetPermission: Boolean? = null,
)
enum class BatteryPowerState(val label: String) {
    CHARGING("Charging"), FULL("Full"), PLUGGED_IDLE("Plugged in · not charging"),
    DISCHARGING("Discharging"), UNKNOWN("Unavailable")
}
data class BatteryInfo(
    val percent: Int? = null,
    val charging: Boolean = false,
    val temperatureC: Float? = null,
    val voltageMv: Int? = null,
    val saver: Boolean = false,
    val currentMa: Double? = null,
    val powerMw: Double? = null,
    val capturedAt: Long? = null,
    val powerState: BatteryPowerState = BatteryPowerState.UNKNOWN,
)
enum class AccessState { CHECKING, NOT_RUNNING, NEEDS_PERMISSION, CONNECTING, READY, ERROR }
data class PolicyState(
    val packageName: String,
    val backgroundMode: String?,
    val legacyMode: String?,
    val dozeExempt: Boolean?,
    val systemExempt: Boolean = false,
    val userExempt: Boolean? = null,
    val uidOverride: Boolean = false,
    val standbyBucket: Int? = null,
    val capturedAt: Long = System.currentTimeMillis(),
    val error: String? = null,
)
enum class ControlAction { ALLOW_BACKGROUND, RESTRICT_BACKGROUND, EXEMPT_DOZE, OPTIMIZE_DOZE }
data class ChangeRecord(
    val id: String,
    val packageName: String,
    val label: String,
    val description: String,
    val timestamp: Long,
    val before: PolicyState,
    val after: PolicyState,
    val undone: Boolean = false,
    val recovery: Boolean = false,
)
data class Snapshot(
    val processes: Map<String, ProcessState> = emptyMap(),
    val buckets: Map<String, Int> = emptyMap(),
    val exemptPackages: Set<String> = emptySet(),
    val exemptAppIds: Set<Int> = emptySet(),
    val processesKnown: Boolean = false,
    val exemptionsKnown: Boolean = false,
    val capturedAt: Long = System.currentTimeMillis(),
    val warnings: List<String> = emptyList(),
)
data class UiState(
    val access: AccessState = AccessState.CHECKING,
    val accessMessage: String? = null,
    val apps: List<AppEntry> = emptyList(),
    val uninstalledApps: List<AppEntry> = emptyList(),
    val battery: BatteryInfo = BatteryInfo(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val capturedAt: Long? = null,
    val warnings: List<String> = emptyList(),
    val selected: AppEntry? = null,
    val policy: PolicyState? = null,
    val detailLoading: Boolean = false,
    val busy: Boolean = false,
    val changes: List<ChangeRecord> = emptyList(),
    val message: String? = null,
    val resources: ResourceSnapshot? = null,
    val energy: EnergySnapshot? = null,
    val resourceLoading: Boolean = false,
    val energyLoading: Boolean = false,
    val monitorPaused: Boolean = false,
    val monitorIntervalMs: Long = 5_000,
    val monitoring: Boolean = false,
    val resourceHistory: List<ResourceHistoryPoint> = emptyList(),
    val diagnostics: AppDiagnostics? = null,
    val diagnosticsLoading: Boolean = false,
)
fun bucketLabel(bucket: Int?): String = when (bucket) {
    5 -> "Exempt"; 10 -> "Active"; 20 -> "Working set"; 30 -> "Frequent"
    40 -> "Rare"; 45 -> "Restricted"; 50 -> "Never used"
    null -> "Unknown"; else -> "Bucket $bucket"
}
