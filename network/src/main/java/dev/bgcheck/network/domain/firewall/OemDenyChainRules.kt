package dev.bgcheck.network.domain.firewall

/** Complete Android TrafficController owner-map snapshot, including isolated and retired UIDs. */
object OemDenyChainRules {
    fun parse(dump: String): Set<Int> {
        val lines = dump.lineSequence().map { it.trim() }.toList()
        val start = lines.indexOf("sUidOwnerMap:")
        val end = lines.indexOf("sUidPermissionMap:")
        require(start >= 0 && end > start) { "Cannot inspect Android's OEM firewall rules" }
        return lines.subList(start + 1, end).filter { it.isNotEmpty() }.mapNotNull { line ->
            val columns = line.split(Regex("\\s+"))
            val uid = columns.first().toIntOrNull()
            require(uid != null && uid >= 0 && columns.size > 1 && columns.none { it.contains("UNKNOWN", true) }) {
                "Incomplete Android firewall rule snapshot"
            }
            uid.takeIf { "OEM_DENY_3_MATCH" in columns }
        }.toSet()
    }
}
