package dev.bgcheck.network.domain.model

/** Stored profile filters bind to a profile incarnation, not a reusable Android user number. */
object ProfileFilter {
    fun key(userId: Int, serial: Long?): String = "user:$userId:${serial ?: -1}"

    fun matches(filter: String, userId: Int, serial: Long?, work: Boolean, clone: Boolean): Boolean =
        when (filter.lowercase()) {
            "personal" -> !work && !clone
            "work" -> work
            "clone" -> clone
            "all" -> true
            else -> filter == key(userId, serial)
        }
}
