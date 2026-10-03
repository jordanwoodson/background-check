package dev.bgcheck.app.data

/** Parsers for fixed shell telemetry commands. Unsupported sources never become zero. */
object ResourceParsers {
    data class AppMemory(val pssKb: Long, val privateDirtyKb: Long, val swapPssKb: Long)
    private val space = Regex("\\s+")

    /** Toybox -n 2: discard its initial frame and parse only the measured interval. */
    fun top(output: String): ResourceSnapshot {
        val lines = output.lines().map(String::trim)
        val frames = lines.indices.filter { lines[it].startsWith("Tasks:") }
        require(frames.size == 2) { "Android did not return two complete CPU samples" }
        val frame = lines.drop(frames.last())
        val header = frame.indexOfFirst {
            it.replace('[', ' ').replace(']', ' ').split(space) ==
                listOf("PID", "PPID", "UID", "%CPU", "RSS", "VSZ", "S", "USER", "CMDLINE")
        }
        require(header >= 0) { "Android returned an unsupported process table" }
        val cpuLine = frame.take(header).firstOrNull { Regex("^\\d+(?:\\.\\d+)?%cpu\\s.*").matches(it) }
        val cpuParts = cpuLine?.let { Regex("(\\d+(?:\\.\\d+)?)%([a-z]+)").findAll(it)
            .associate { match -> match.groupValues[2] to match.groupValues[1].toDouble() } }.orEmpty()
        val capacity = cpuParts["cpu"]?.takeIf { it > 0 }
        val idle = cpuParts["idle"]
        val ioWait = cpuParts["iow"]
        // I/O wait is not CPU execution. Device CPU is normalized across all cores.
        val deviceCpu = if (capacity != null && idle != null && ioWait != null && idle + ioWait <= capacity)
            ((capacity - idle - ioWait) / capacity * 100).coerceIn(0.0, 100.0) else null
        val processes = frame.drop(header + 1).filter(String::isNotBlank).map { line ->
            val fields = line.split(space, limit = 9)
            require(fields.size == 9) { "Android returned an incomplete process row" }
            val pid = fields[0].toIntOrNull()
            val parent = fields[1].toIntOrNull()
            val uid = fields[2].toIntOrNull()
            require(pid != null && pid > 0 && parent != null && parent >= 0 && uid != null && uid >= 0) {
                "Android returned an invalid process identity"
            }
            require(fields[6].length == 1 && fields[6][0] in "RSDTtXZPIxKW") { "Unknown process scheduler state" }
            val command = fields[8]
            val name = if (command.startsWith('[') && command.endsWith(']')) command
                else command.substringBefore(' ').substringAfterLast('/').ifBlank { command }
            ProcessResource(pid, parent, uid, fields[7], name, command, fields[6],
                optionalDouble(fields[3]), optionalLong(fields[4]), optionalLong(fields[5]))
        }
        require(processes.isNotEmpty()) { "Android returned no recognizable processes" }
        require(processes.map { it.pid }.distinct().size == processes.size) {
            "Android returned duplicate process IDs; thread rows cannot be counted as processes"
        }
        return ResourceSnapshot(processes = processes, processesKnown = true,
            cpuPercent = deviceCpu, cpuCoreCount = capacity?.div(100)?.toInt()?.takeIf { it > 0 },
            warnings = if (deviceCpu == null) listOf("Whole-device CPU is unavailable in this Android output.") else emptyList())
    }

    fun memory(output: String): SystemMemory {
        val values = output.lineSequence().mapNotNull {
            Regex("^(MemTotal|MemAvailable|SwapTotal|SwapFree):\\s+(\\d+)\\s+kB$").matchEntire(it.trim())
        }.associate { it.groupValues[1] to long(it.groupValues[2]) }
        val total = requireNotNull(values["MemTotal"]) { "Total RAM is unavailable" }
        val available = requireNotNull(values["MemAvailable"]) { "Available RAM is unavailable" }
        val swapTotal = requireNotNull(values["SwapTotal"]) { "Swap capacity is unavailable" }
        val swapFree = requireNotNull(values["SwapFree"]) { "Free swap is unavailable" }
        require(total > 0 && available <= total && swapFree <= swapTotal) { "Android returned inconsistent memory values" }
        return SystemMemory(total, available, swapTotal, swapFree)
    }

    /** Android 11–16 checkin versions 35/36; all retained records use a numeric prefix. */
    fun energy(output: String): EnergySnapshot {
        val rows = output.lineSequence().filter(String::isNotBlank).map { it.split(',') }.toList()
        val version = rows.singleOrNull { it.size >= 5 && it.take(4) == listOf("9", "0", "i", "vers") }
        require(version?.get(4)?.toIntOrNull() in setOf(35, 36)) { "Unsupported Android battery statistics format" }
        val warnings = mutableListOf<String>()
        val uidValues = linkedMapOf<Int, UidEnergy>()
        val components = linkedMapOf<String, PowerComponent>()
        val invalidComponents = mutableSetOf<String>()
        val seen = mutableSetOf<String>()
        var total: Double? = null
        var realtime: Long? = null
        var start: Long? = null
        val types = setOf("bt", "pws", "pwi", "cpu", "awl", "nt")
        rows.filter { it.size >= 4 && it[0] == "9" && it[2] == "l" && it[3] in types }.forEach { fields ->
            val type = fields[3]
            val uid = fields[1].toIntOrNull()?.takeIf { it >= 0 }
            if (uid == null) {
                warnings += "A battery record contained an invalid UID."
                return@forEach
            }
            val key = "$uid:$type:${if (type == "pwi") fields.getOrNull(4).orEmpty() else ""}"
            val unnamedComponent = type == "pwi" && uid == 0 && fields.getOrNull(4) == "???"
            val componentName = if (unnamedComponent) "Other components" else fields.getOrNull(4)
            try {
                // AOSP emits ??? for each component missing a legacy CSV label. These are
                // distinct components and must be added, unlike duplicate named/UID records.
                require(unnamedComponent || seen.add(key)) { "duplicate record" }
                fun at(index: Int): String = requireNotNull(fields.getOrNull(index)) { "incomplete record" }
                when (type) {
                    "bt" -> if (uid == 0) {
                        realtime = long(at(5)); start = long(at(9)).takeIf { it > 0 }
                    }
                    "pws" -> if (uid == 0) total = number(at(5))
                    "pwi" -> if (at(4) == "uid") {
                        uidValues[uid] = (uidValues[uid] ?: UidEnergy(uid)).copy(estimatedMah = number(at(5)))
                    } else if (uid == 0) {
                        val name = requireNotNull(componentName)
                        val power = number(at(5)) + if (unnamedComponent) components[name]?.estimatedMah ?: 0.0 else 0.0
                        require(power.isFinite()) { "component sum is out of range" }
                        if (name !in invalidComponents) components[name] = PowerComponent(name, power)
                    }
                    "cpu" -> uidValues[uid] = (uidValues[uid] ?: UidEnergy(uid))
                        .copy(cpuTimeMs = Math.addExact(long(at(4)), long(at(5))))
                    "awl" -> uidValues[uid] = (uidValues[uid] ?: UidEnergy(uid)).copy(wakelockMs = long(at(4)))
                    "nt" -> uidValues[uid] = (uidValues[uid] ?: UidEnergy(uid)).copy(
                        networkRxBytes = Math.addExact(long(at(4)), long(at(6))),
                        networkTxBytes = Math.addExact(long(at(5)), long(at(7))))
                }
            } catch (_: IllegalArgumentException) {
                clearMetric(uidValues, uid, type, fields.getOrNull(4))
                if (type == "pws") total = null
                if (type == "bt") { realtime = null; start = null }
                if (type == "pwi" && fields.getOrNull(4) != "uid" && componentName != null) {
                    components.remove(componentName)
                    invalidComponents += componentName
                }
                warnings += "Some Android battery $type records are unavailable or malformed."
            } catch (_: ArithmeticException) {
                clearMetric(uidValues, uid, type, fields.getOrNull(4))
                warnings += "Some Android battery $type values exceeded the supported range."
            }
        }
        if (total == null) warnings += "Android did not provide a total battery estimate."
        if (uidValues.isEmpty()) warnings += "Android did not provide per-UID battery statistics."
        return EnergySnapshot(uids = uidValues.values.toList(), components = components.values.toList(),
            estimatedTotalMah = total, batteryRealtimeMs = realtime, startClockTimeMs = start,
            warnings = warnings.distinct())
    }

    private fun clearMetric(values: MutableMap<Int, UidEnergy>, uid: Int, type: String, label: String?) {
        val value = values[uid] ?: UidEnergy(uid)
        when (type) {
            "pwi" -> if (label == "uid") values[uid] = value.copy(estimatedMah = null)
            "cpu" -> values[uid] = value.copy(cpuTimeMs = null)
            "awl" -> values[uid] = value.copy(wakelockMs = null)
            "nt" -> values[uid] = value.copy(networkRxBytes = null, networkTxBytes = null)
        }
    }

    fun appMemory(output: String): AppMemory {
        require(output.lineSequence().any { it.startsWith("Applications Memory Usage (in Kilobytes):") }) {
            "Android did not provide package memory details; the app may not be running"
        }
        val records = linkedMapOf<Int, AppMemory?>()
        var pid: Int? = null
        output.lineSequence().forEach { line ->
            Regex("^\\*\\* MEMINFO in pid (\\d+) \\[.*] \\*\\*$").matchEntire(line.trim())?.let { header ->
                pid = header.groupValues[1].toInt()
                require(!records.containsKey(pid)) { "Duplicate memory process record" }
                records[pid!!] = null
            }
            if (pid != null && Regex("^TOTAL\\s+\\d.*").matches(line.trim())) {
                val fields = line.trim().split(space)
                require(fields.size >= 5 && records[pid] == null) { "Incomplete or duplicated memory total" }
                records[pid!!] = AppMemory(long(fields[1]), long(fields[2]), long(fields[4]))
            }
        }
        require(records.isNotEmpty() && records.values.none { it == null }) { "Android returned incomplete package memory details" }
        fun sum(select: (AppMemory) -> Long) = records.values.fold(0L) { value, record -> Math.addExact(value, select(record!!)) }
        return AppMemory(sum { it.pssKb }, sum { it.privateDirtyKb }, sum { it.swapPssKb })
    }

    fun services(output: String, packageName: String, userId: Int): List<RunningService> {
        require(output.lineSequence().any { it.startsWith("ACTIVITY MANAGER SERVICES") }) {
            "Android did not provide running services"
        }
        val result = mutableListOf<RunningService>()
        var component: String? = null
        var user: Int? = null
        var process: String? = null
        var running = false
        var processAssociationKnown = false
        var foreground = false
        var records = 0
        fun finish() {
            if (component?.substringBefore('/') == packageName && user == userId) {
                require(processAssociationKnown) { "Android returned an incomplete service process association" }
                if (running) result += RunningService(component!!, process, foreground)
            }
        }
        output.lineSequence().map(String::trim).forEach { line ->
            if (line.startsWith("* ServiceRecord{")) {
                finish()
                val match = Regex("^\\* ServiceRecord\\{\\S+ u(\\d+) (\\S+/\\S+?)(?:\\s.*|\\})$").matchEntire(line)
                require(match != null) { "Android returned an unsupported service record" }
                user = match.groupValues[1].toInt()
                component = match.groupValues[2]
                process = null; running = false; processAssociationKnown = false; foreground = false; records++
            } else {
                if (line.startsWith("processName=")) process = line.substringAfter('=').substringBefore(' ')
                if (line == "app=null") { processAssociationKnown = true; running = false }
                Regex("^app=ProcessRecord\\{[^\\s}]+ (\\d+):[^}]+\\}$").matchEntire(line)?.let {
                    val pid = it.groupValues[1].toIntOrNull()
                    processAssociationKnown = pid != null
                    running = pid != null && pid > 0
                }
                if (Regex("(?:^|\\s)isForeground=true(?:\\s|$)").containsMatchIn(line)) foreground = true
            }
        }
        finish()
        require(records > 0 || output.lineSequence().any { it.trim() == "(nothing)" }) { "Android returned an incomplete services snapshot" }
        return result.distinct()
    }

    private fun long(value: String): Long = requireNotNull(value.toLongOrNull()?.takeIf { it >= 0 }) { "Invalid nonnegative integer" }
    private fun number(value: String): Double = requireNotNull(value.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }) { "Invalid nonnegative number" }
    private fun optionalLong(value: String): Long? = if (value in setOf("-", "?", "N/A")) null else long(value)
    private fun optionalDouble(value: String): Double? = if (value in setOf("-", "?", "N/A")) null else number(value)
}
