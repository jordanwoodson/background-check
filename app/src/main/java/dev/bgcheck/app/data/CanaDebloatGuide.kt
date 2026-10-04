package dev.bgcheck.app.data

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject

enum class DebloatRating(val label: String, val explanation: String) {
    RECOMMENDED("Recommended", "Cana's list recommends removal. Read the package notes and warnings first."),
    ADVANCED("Advanced", "Replacement or additional review is needed before removal. Features may depend on this package."),
    EXPERT("Expert", "Cana's list advises caution: removal may be unsafe or break features."),
    UNSAFE("Unsafe", "Cana's list advises against removal because essential functionality may break."),
    NOT_RATED("Not rated by Cana", "Cana's bundled list has no rating for this package. Review its purpose and dependencies before considering removal."),
}

data class DebloatAlternative(val packageName: String, val label: String, val url: String, val note: String?)

data class CanaDebloatEntry(
    val rating: DebloatRating,
    val category: String? = null,
    val description: String? = null,
    val warning: String? = null,
    val dependencies: List<String> = emptyList(),
    val neededBy: List<String> = emptyList(),
    val references: List<String> = emptyList(),
    val alternatives: List<DebloatAlternative> = emptyList(),
)

object CanaDebloatGuide {
    const val REVISION = "10c82498154523d29ba0f268e4ab8316ab19c16b"
    const val SOURCE_URL = "https://github.com/MuntashirAkon/android-debloat-list/tree/$REVISION"
    private val unrated = CanaDebloatEntry(DebloatRating.NOT_RATED)
    private val entries by lazy {
        requireNotNull(javaClass.getResourceAsStream("/cana-debloat-list.zip")) {
            "The bundled Cana debloat source is missing"
        }.use(::parseCanaDebloatArchive)
    }

    // AppInventory calls this on Dispatchers.IO before rendering package rows.
    fun preload() { entries }

    fun lookup(packageName: String): CanaDebloatEntry = entries[packageName] ?: unrated
}

/** Read the pinned, unmodified source archive in memory. No archive paths are extracted to disk. */
internal fun parseCanaDebloatArchive(input: InputStream): Map<String, CanaDebloatEntry> {
    val categories = listOf("aosp", "carrier", "google", "misc", "oem")
    val files = mutableMapOf<String, String>()
    var expanded = 0
    var count = 0
    ZipInputStream(input).use { zip ->
        val buffer = ByteArray(8192)
        while (true) {
            val entry = zip.nextEntry ?: break
            require(++count <= 1000) { "Too many entries in debloat source" }
            val contents = ByteArrayOutputStream()
            while (true) {
                val size = zip.read(buffer)
                if (size < 0) break
                expanded += size
                require(expanded <= 10 * 1024 * 1024) { "Debloat source exceeds size limit" }
                contents.write(buffer, 0, size)
            }
            val path = entry.name.substringAfter('/', "")
            if (path in categories.map { "$it.json" } ||
                path.startsWith("suggestions/") && path.endsWith(".json") && path.count { it == '/' } == 1) {
                require(files.put(path, contents.toString("UTF-8")) == null) { "Duplicate source file: $path" }
            }
        }
    }
    fun JSONObject.text(key: String): String? = (opt(key) as? String)?.takeIf { it.isNotBlank() }
    fun JSONObject.strings(key: String): List<String> = optJSONArray(key)?.let { values ->
        List(values.length()) { values.getString(it) }.filter { it.isNotBlank() }
    }.orEmpty()
    return buildMap {
        categories.forEach { category ->
            val records = JSONArray(requireNotNull(files["$category.json"]) { "Missing source category: $category" })
            for (i in 0 until records.length()) {
                val record = records.getJSONObject(i)
                val id = requireNotNull(record.text("id"))
                require(!containsKey(id)) { "Duplicate package: $id" }
                val rating = when (record.getString("removal")) {
                    "delete" -> DebloatRating.RECOMMENDED
                    "replace" -> DebloatRating.ADVANCED
                    "caution" -> DebloatRating.EXPERT
                    "unsafe" -> DebloatRating.UNSAFE
                    else -> error("Unknown removal recommendation for $id")
                }
                val alternatives = record.text("suggestions")?.let { name ->
                    val values = JSONArray(requireNotNull(files["suggestions/$name.json"]) { "Missing replacements: $name" })
                    List(values.length()) { index ->
                        val item = values.getJSONObject(index)
                        DebloatAlternative(requireNotNull(item.text("id")), requireNotNull(item.text("label")),
                            requireNotNull(item.text("repo")), item.text("reason"))
                    }
                }.orEmpty()
                put(id, CanaDebloatEntry(rating, category, record.getString("description"), record.text("warning"),
                    record.strings("dependencies"), record.strings("required_by"), record.strings("web"), alternatives))
            }
        }
        require(isNotEmpty()) { "Debloat source is empty" }
    }
}

val AppEntry.debloatRecommendation: CanaDebloatEntry get() = CanaDebloatGuide.lookup(packageName)
