package dev.bgcheck.network.domain.model

/** A removal-list entry belongs to one incarnation of an Android profile. */
data class PackageListEntry(val packageName: String, val userId: Int, val userSerial: Long)

data class PackageListDocument(
    val entries: List<PackageListEntry> = emptyList(),
    val legacyPackageNames: List<String> = emptyList(),
) {
    val requiresProfileSelection: Boolean get() = legacyPackageNames.isNotEmpty()
    val count: Int get() = entries.size + legacyPackageNames.size
}

/** Human-readable exports retain separate personal/work copies of the same package. */
object PackageListCodec {
    const val HEADER = "# Background Check package list v2"
    private const val VERSION_PREFIX = "# Background Check package list v"
    private const val MAX_BYTES = 4 * 1024 * 1024
    private const val MAX_ENTRIES = 10000
    private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*")

    fun encode(entries: List<PackageListEntry>): String {
        validate(entries)
        return buildString {
            appendLine(HEADER)
            appendLine("# Columns: Android user ID, profile serial, package name (tab-separated)")
            appendLine("# Import removes these packages only from the recorded profiles.")
            entries.distinct().forEach { appendLine("${it.userId}\t${it.userSerial}\t${it.packageName}") }
        }
    }

    fun decode(content: String): PackageListDocument {
        require(content.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Package list is too large." }
        val lines = content.removePrefix("\uFEFF").lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val version = lines.firstOrNull { it.startsWith(VERSION_PREFIX) }
        require(version == null || version == HEADER) { "Unsupported package-list version." }
        val rows = lines.filterNot { it.startsWith("#") }
        require(rows.size <= MAX_ENTRIES) { "Too many packages in this list." }
        if (version == null) {
            rows.forEach(::validatePackage)
            return PackageListDocument(legacyPackageNames = rows.distinct())
        }
        val entries = rows.map { line ->
            val parts = line.split('\t')
            require(parts.size == 3) { "Invalid profile-bound package-list row." }
            PackageListEntry(parts[2], requireNotNull(parts[0].toIntOrNull()) { "Invalid Android user ID." },
                requireNotNull(parts[1].toLongOrNull()) { "Invalid profile serial." })
        }
        validate(entries)
        return PackageListDocument(entries = entries.distinct())
    }

    /** Only an explicit user choice can give legacy name-only files a target profile. */
    fun resolve(document: PackageListDocument, currentSerials: Map<Int, Long>, legacyTargetUserId: Int? = null): List<PackageListEntry> {
        val entries = if (document.requiresProfileSelection) {
            require(document.entries.isEmpty()) { "Mixed legacy and profile-bound entries are invalid." }
            val userId = requireNotNull(legacyTargetUserId) { "Choose a target profile for this name-only package list." }
            val serial = requireNotNull(currentSerials[userId]) { "Selected profile is unavailable." }
            document.legacyPackageNames.map { PackageListEntry(it, userId, serial) }
        } else document.entries
        validate(entries)
        entries.forEach { entry ->
            require(currentSerials[entry.userId] == entry.userSerial) {
                "Profile ${entry.userId} is unavailable or no longer matches this list. No packages were removed."
            }
        }
        return entries.distinct()
    }

    private fun validate(entries: List<PackageListEntry>) {
        require(entries.size <= MAX_ENTRIES) { "Too many packages in this list." }
        entries.forEach {
            validatePackage(it.packageName)
            require(it.userId in 0..21473 && it.userSerial >= 0) { "Invalid profile identity." }
        }
        require(entries.groupBy { it.userId }.values.all { rows -> rows.map { it.userSerial }.distinct().size == 1 }) {
            "The list contains conflicting identities for one Android profile."
        }
    }

    private fun validatePackage(name: String) {
        require(name.length in 1..255 && packagePattern.matches(name)) { "Invalid package name in package list." }
    }
}
