package dev.bgcheck.app.data

import dev.bgcheck.network.domain.model.NetworkChangeRecord
import java.util.Locale

data class TimelineEntry(val policy: ChangeRecord? = null, val network: NetworkChangeRecord? = null) {
    val id get() = policy?.id ?: "network-${network!!.id}"
    val packageName get() = policy?.packageName ?: network!!.packageName
    val label get() = policy?.label ?: network!!.label
    val timestamp get() = policy?.timestamp ?: network!!.timestamp
    val userId get() = policy?.userId ?: network!!.userId
    val profileName get() = policy?.profileName ?: network!!.profileName
    val description get() = policy?.description ?: network!!.description
    val status get() = if (policy?.undone == true || network?.undone == true) "Restored" else if (policy?.recovery == true || network?.recovery == true) "Needs recovery" else "Applied"
    val before get() = policy?.before?.summary() ?: network!!.before.summary()
    val after get() = policy?.after?.summary() ?: network!!.after.summary()
}

fun PolicyState.summary(): String = "Background: ${backgroundMode ?: "unavailable"}; " +
    (if (legacyMode != null) "legacy background: $legacyMode; " else "") +
    "Doze: ${when (dozeExempt) { true -> "exempt"; false -> "optimized"; null -> "unavailable" }}"

object ChangeTimeline {
    fun entries(policies: List<ChangeRecord>, network: List<NetworkChangeRecord>, profileId: Int,
        query: String, sort: HistorySort, direction: SortDirection): List<TimelineEntry> {
        val needle = query.trim()
        val entries = (policies.map { TimelineEntry(policy = it) } + network.map { TimelineEntry(network = it) })
            .filter { entry -> (profileId < 0 || entry.userId == profileId) && (needle.isEmpty() ||
                listOf(entry.label, entry.packageName, entry.profileName, entry.description, entry.status, entry.before, entry.after, entry.network?.recoveryReason.orEmpty())
                    .any { it.contains(needle, ignoreCase = true) }) }
        val comparator = if (sort == HistorySort.TIME) compareBy<TimelineEntry> { it.timestamp } else compareBy {
            when (sort) {
                HistorySort.APP -> it.label
                HistorySort.PACKAGE -> it.packageName
                HistorySort.ACTION -> it.description
                HistorySort.STATUS -> it.status
                HistorySort.TIME -> ""
            }.lowercase(Locale.ROOT)
        }
        return entries.sortedWith((if (direction == SortDirection.DESCENDING) comparator.reversed() else comparator)
            .thenByDescending { it.timestamp }.thenBy { it.id })
    }
}
