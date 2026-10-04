package dev.bgcheck.app.data

/** CPU uses one core = 100%; RSS includes shared resident pages. Null means unavailable. */
data class ProcessResource(
    val pid: Int,
    val parentPid: Int,
    val uid: Int,
    val user: String,
    val name: String,
    val command: String,
    val state: String,
    val cpuPercent: Double?,
    val rssKb: Long?,
    val virtualKb: Long?,
)

data class SystemMemory(
    val totalKb: Long,
    val availableKb: Long,
    val swapTotalKb: Long,
    val swapFreeKb: Long,
)

data class ResourceSnapshot(
    val processes: List<ProcessResource> = emptyList(),
    val processesKnown: Boolean = false,
    val cpuPercent: Double? = null,
    val cpuCoreCount: Int? = null,
    val memory: SystemMemory? = null,
    val sampleDurationMs: Long = 1_000,
    val capturedAt: Long = System.currentTimeMillis(),
    val warnings: List<String> = emptyList(),
)

data class ResourceHistoryPoint(val capturedAt: Long, val cpuPercent: Double?, val usedMemoryKb: Long?)

/** Android battery estimates belong to a UID, which can be shared by several packages. */
data class UidEnergy(
    val uid: Int,
    val estimatedMah: Double? = null,
    val cpuTimeMs: Long? = null,
    val networkRxBytes: Long? = null,
    val networkTxBytes: Long? = null,
    val wakelockMs: Long? = null,
)

data class PowerComponent(val name: String, val estimatedMah: Double)

data class EnergySnapshot(
    val uids: List<UidEnergy> = emptyList(),
    val components: List<PowerComponent> = emptyList(),
    val estimatedTotalMah: Double? = null,
    val batteryRealtimeMs: Long? = null,
    val startClockTimeMs: Long? = null,
    val capturedAt: Long = System.currentTimeMillis(),
    val warnings: List<String> = emptyList(),
)

data class RunningService(val component: String, val process: String?, val foreground: Boolean)

data class AppDiagnostics(
    val packageName: String,
    val pssKb: Long? = null,
    val privateDirtyKb: Long? = null,
    val swapPssKb: Long? = null,
    val services: List<RunningService> = emptyList(),
    val servicesKnown: Boolean = false,
    val capturedAt: Long = System.currentTimeMillis(),
    val warnings: List<String> = emptyList(),
    val userId: Int = 0,
)
