package dev.bgcheck.app.data

/** Text contracts verified against AOSP and a Motorola Android 16 device. Unknown formats fail closed. */
object ShellParsers {
    private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)*")
    fun validPackage(value: String): Boolean = value.length <= 255 && packagePattern.matches(value)

    data class AppOp(val mode: String, val uidOverride: Boolean)

    fun appOp(output: String, operation: String): AppOp {
        val pattern = Regex("^(Uid mode: )?${Regex.escape(operation)}: (allow|ignore|deny|default|foreground)(?:;.*)?$")
        val matches = output.lineSequence().map(String::trim).mapNotNull(pattern::matchEntire).toList()
        val uid = matches.firstOrNull { it.groupValues[1].isNotEmpty() }
        val selected = uid ?: matches.firstOrNull()
        if (selected != null) {
            val mode = selected.groupValues[2].let { if (it == "default") "allow" else it }
            return AppOp(mode, uid != null)
        }
        val lines = output.lines().map(String::trim).filter(String::isNotEmpty)
        require(lines.size == 2 && lines[0] == "No operations." && lines[1] == "Default mode: allow") {
            "Android did not return a recognized $operation mode"
        }
        return AppOp("allow", false)
    }

    data class Allowlist(
        val system: Set<String>, val user: Set<String>, val exceptIdle: Set<String>,
        val systemAppIds: Set<Int>, val userAppIds: Set<Int>,
    ) {
        val exempt: Set<String> get() = system + user
        val exemptAppIds: Set<Int> get() = systemAppIds + userAppIds
    }

    fun allowlist(output: String): Allowlist {
        require(output.isNotBlank()) { "Android returned an empty exemption response" }
        val system = mutableSetOf<String>(); val user = mutableSetOf<String>(); val partial = mutableSetOf<String>()
        val systemIds = mutableSetOf<Int>(); val userIds = mutableSetOf<Int>()
        output.lineSequence().map(String::trim).filter(String::isNotEmpty).forEach { line ->
            val fields = line.split(',')
            require(fields.size == 3 && validPackage(fields[1])) { "Unrecognized Android exemption list" }
            val appId = fields[2].toIntOrNull()
            require(appId != null && appId in 0..99999) { "Unrecognized exemption app ID" }
            when (fields[0]) {
                "system" -> { system += fields[1]; systemIds += appId }
                "user" -> { user += fields[1]; userIds += appId }
                "system-excidle" -> partial += fields[1]
                else -> error("Unrecognized Android exemption type")
            }
        }
        return Allowlist(system, user, partial, systemIds, userIds)
    }

    fun buckets(output: String): Map<String, Int> = buildMap {
        require(output.isNotBlank()) { "Android returned an empty standby response" }
        output.lineSequence().map(String::trim).filter(String::isNotEmpty).forEach { line ->
            val pair = line.split(": ")
            require(pair.size == 2 && validPackage(pair[0])) { "Unrecognized Android standby list" }
            val value = pair[1].toIntOrNull()
            require(value != null && value >= 0) { "Unrecognized Android standby bucket" }
            put(pair[0], value)
        }
    }

    fun bucket(output: String): Int {
        val value = output.trim().toIntOrNull()
        require(value != null && value >= 0) { "Android did not return a standby bucket" }
        return value
    }

    fun processes(output: String, userId: Int): Map<String, ProcessState> {
        require(output.lineSequence().any { it.startsWith("ACTIVITY MANAGER RUNNING PROCESSES") }) {
            "Android did not return a process snapshot"
        }
        val result = mutableMapOf<String, Int>()
        var inRecord = false
        var recordUser: Int? = null
        var packages: List<String>? = null
        var state: Int? = null
        var count = 0
        fun finish() {
            if (!inRecord) return
            require(recordUser != null && packages != null && state != null) { "Android returned an incomplete process record" }
            if (recordUser == userId) packages!!.forEach { name ->
                result[name] = minOf(result[name] ?: Int.MAX_VALUE, state!!)
            }
        }
        val start = Regex("^\\s*\\*(?:APP|PERS)\\* UID \\d+ ProcessRecord\\{.*")
        val user = Regex("^user #(\\d+)\\s.*")
        val pkg = Regex("^packageList=\\{([^}]*)\\}.*")
        val proc = Regex("^curProcState=(-?\\d+)(?:\\s.*)?$")
        output.lineSequence().forEach { line ->
            if (start.matches(line)) {
                finish(); count++; inRecord = true; recordUser = null; packages = null; state = null
            } else if (inRecord) {
                val trimmed = line.trim()
                if (recordUser == null) user.matchEntire(trimmed)?.let { recordUser = it.groupValues[1].toInt() }
                if (packages == null) pkg.matchEntire(trimmed)?.let { match ->
                    packages = match.groupValues[1].split(',').map(String::trim).filter(String::isNotEmpty)
                    require(packages!!.all(::validPackage)) { "Unrecognized package in process snapshot" }
                }
                if (state == null) proc.matchEntire(trimmed)?.let { state = it.groupValues[1].toInt() }
            }
        }
        finish()
        require(count > 0) { "Android process snapshot contained no recognizable records" }
        return result.mapValues { (_, value) -> processState(value) }
    }

    fun processState(value: Int): ProcessState = when (value) {
        1, 2, 3, 6 -> ProcessState.FOREGROUND
        0, 4, 5, 10 -> ProcessState.SERVICE
        7, 8, 9, 11, 12, 13, 14, 15 -> ProcessState.BACKGROUND
        in 16..19 -> ProcessState.CACHED
        20 -> ProcessState.NOT_RUNNING
        else -> ProcessState.UNKNOWN
    }
}
