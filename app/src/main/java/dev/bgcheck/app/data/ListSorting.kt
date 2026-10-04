package dev.bgcheck.app.data

enum class SortDirection(val label: String) {
    ASCENDING("Ascending"), DESCENDING("Descending");
    fun reversed() = if (this == ASCENDING) DESCENDING else ASCENDING
}

interface SortKey {
    val label: String
    val defaultDirection: SortDirection
}

enum class AppSort(override val label: String, override val defaultDirection: SortDirection = SortDirection.ASCENDING) : SortKey {
    NAME("Name"), PACKAGE("Package"), UID("UID"), STATE("Process state"), STANDBY("Standby group")
}
enum class HistorySort(override val label: String, override val defaultDirection: SortDirection = SortDirection.ASCENDING) : SortKey {
    TIME("Date", SortDirection.DESCENDING), APP("App name"), PACKAGE("Package"), ACTION("Action"), STATUS("Status")
}
enum class ProcessSort(override val label: String, override val defaultDirection: SortDirection = SortDirection.ASCENDING) : SortKey {
    CPU("CPU", SortDirection.DESCENDING), MEMORY("Resident memory", SortDirection.DESCENDING),
    VIRTUAL("Virtual memory", SortDirection.DESCENDING), NAME("Name"), PID("PID"), UID("UID"), STATE("State")
}
enum class EnergySort(override val label: String, override val defaultDirection: SortDirection = SortDirection.DESCENDING) : SortKey {
    BATTERY("Estimated battery use"), CPU("CPU time"), WAKELOCK("Wakelocks"), RECEIVED("Network received"), SENT("Network sent"),
    NAME("App name", SortDirection.ASCENDING), UID("UID", SortDirection.ASCENDING)
}
enum class ComponentSort(override val label: String, override val defaultDirection: SortDirection = SortDirection.ASCENDING) : SortKey {
    BATTERY("Estimated battery use", SortDirection.DESCENDING), NAME("Name")
}
enum class ServiceSort(override val label: String, override val defaultDirection: SortDirection = SortDirection.ASCENDING) : SortKey {
    NAME("Component name"), PROCESS("Process name"), FOREGROUND("Foreground status", SortDirection.DESCENDING)
}

/** Null readings stay last in either direction. Stable tie-breakers avoid jumps on refresh. */
object ListSorting {
    fun apps(items: List<AppEntry>, key: AppSort, direction: SortDirection): List<AppEntry> {
        val comparator: Comparator<AppEntry> = when (key) {
            AppSort.NAME -> text(direction) { it.label }
            AppSort.PACKAGE -> text(direction) { it.packageName }
            AppSort.UID -> by(direction) { it.uid }
            AppSort.STATE -> by(direction) { it.processState.takeUnless { state -> state == ProcessState.UNKNOWN }?.ordinal }
            AppSort.STANDBY -> by(direction) { it.standbyBucket }
        }
        return items.sortedWith(comparator.thenBy { it.packageName }.thenBy { it.userId })
    }

    fun history(items: List<ChangeRecord>, key: HistorySort, direction: SortDirection): List<ChangeRecord> {
        val comparator: Comparator<ChangeRecord> = when (key) {
            HistorySort.TIME -> by(direction) { it.timestamp }
            HistorySort.APP -> text(direction) { it.label }
            HistorySort.PACKAGE -> text(direction) { it.packageName }
            HistorySort.ACTION -> text(direction) { it.description }
            HistorySort.STATUS -> text(direction) { if (it.undone) "Restored" else if (it.recovery) "Needs recovery" else "Applied" }
        }
        return items.sortedWith(comparator.thenByDescending { it.timestamp }.thenBy { it.id })
    }

    fun processes(items: List<ProcessResource>, key: ProcessSort, direction: SortDirection): List<ProcessResource> {
        val comparator: Comparator<ProcessResource> = when (key) {
            ProcessSort.CPU -> by(direction) { it.cpuPercent }
            ProcessSort.MEMORY -> by(direction) { it.rssKb }
            ProcessSort.VIRTUAL -> by(direction) { it.virtualKb }
            ProcessSort.NAME -> text(direction) { it.name }
            ProcessSort.PID -> by(direction) { it.pid }
            ProcessSort.UID -> by(direction) { it.uid }
            ProcessSort.STATE -> text(direction) { it.state }
        }
        return items.sortedWith(comparator.thenBy { it.pid })
    }

    fun energy(items: List<UidEnergy>, key: EnergySort, direction: SortDirection, appsByUid: Map<Int, List<AppEntry>>): List<UidEnergy> {
        val names = if (key == EnergySort.NAME) appsByUid.mapValues { (_, group) ->
            apps(group, AppSort.NAME, SortDirection.ASCENDING).joinToString { it.label }
        } else emptyMap()
        val comparator: Comparator<UidEnergy> = when (key) {
            EnergySort.BATTERY -> by(direction) { it.estimatedMah }
            EnergySort.CPU -> by(direction) { it.cpuTimeMs }
            EnergySort.WAKELOCK -> by(direction) { it.wakelockMs }
            EnergySort.RECEIVED -> by(direction) { it.networkRxBytes }
            EnergySort.SENT -> by(direction) { it.networkTxBytes }
            EnergySort.NAME -> text(direction) { names[it.uid] }
            EnergySort.UID -> by(direction) { it.uid }
        }
        return items.sortedWith(comparator.thenBy { it.uid })
    }

    fun components(items: List<PowerComponent>, key: ComponentSort, direction: SortDirection): List<PowerComponent> {
        val comparator: Comparator<PowerComponent> = when (key) {
            ComponentSort.BATTERY -> by(direction) { it.estimatedMah }
            ComponentSort.NAME -> text(direction) { powerComponentLabel(it.name) }
        }
        return items.sortedWith(comparator.thenBy { it.name })
    }

    fun services(items: List<RunningService>, key: ServiceSort, direction: SortDirection): List<RunningService> {
        val comparator: Comparator<RunningService> = when (key) {
            ServiceSort.NAME -> text(direction) { it.component }
            ServiceSort.PROCESS -> text(direction) { it.process }
            ServiceSort.FOREGROUND -> by(direction) { it.foreground }
        }
        return items.sortedWith(comparator.thenBy { it.component }.thenBy { it.process.orEmpty() })
    }

    private fun <T, K : Comparable<K>> by(direction: SortDirection, value: (T) -> K?): Comparator<T> =
        compare(direction, naturalOrder(), value)

    private fun <T> text(direction: SortDirection, value: (T) -> String?): Comparator<T> =
        compare(direction, String.CASE_INSENSITIVE_ORDER) { value(it)?.takeIf(String::isNotBlank) }

    private fun <T, K> compare(direction: SortDirection, order: Comparator<K>, value: (T) -> K?): Comparator<T> =
        Comparator { a, b ->
            val left = value(a)
            val right = value(b)
            when {
                left == null && right == null -> 0
                left == null -> 1
                right == null -> -1
                direction == SortDirection.ASCENDING -> order.compare(left, right)
                else -> order.compare(right, left)
            }
        }
}

fun powerComponentLabel(name: String): String = when (name) {
    "scrn" -> "Screen"
    "cpu" -> "CPU"
    "blue" -> "Bluetooth"
    "cell" -> "Mobile radio"
    "gnss" -> "Location (GNSS)"
    "wifi" -> "Wi-Fi"
    "ambi" -> "Ambient display"
    "idle" -> "Device idle"
    else -> name.replaceFirstChar { it.titlecase() }
}
