package dev.bgcheck.app.data

data class BatteryComparisonPackage(val packageName: String, val label: String, val firstInstallTime: Long?)

data class BatteryUidIdentity(
    val uid: Int,
    val profileName: String,
    val userSerial: Long?,
    val profileAvailable: Boolean,
    val packages: List<BatteryComparisonPackage>,
)

data class BatteryComparisonSample(
    val energy: EnergySnapshot,
    val identities: List<BatteryUidIdentity>,
    val elapsedRealtimeMs: Long? = null,
    val bootCount: Int? = null,
) {
    companion object {
        /** Read energy and inventory afresh before calling; UI filters must not remove shared-UID peers. */
        fun capture(energy: EnergySnapshot, apps: List<AppEntry>, profiles: List<AndroidProfile>,
            elapsedRealtimeMs: Long? = null, bootCount: Int? = null): BatteryComparisonSample {
            val byUid = apps.filter { it.installed }.groupBy { it.uid }
            return BatteryComparisonSample(energy, energy.uids.map { reading ->
                val profile = profiles.singleOrNull { it.userId == reading.uid / 100000 }
                val peers = byUid[reading.uid].orEmpty()
                BatteryUidIdentity(reading.uid, profile?.name ?: "User ${reading.uid / 100000}",
                    profile?.serialNumber,
                    profile?.available == true && peers.all {
                        it.profile.available && it.profile.userId == profile.userId && it.profile.serialNumber == profile.serialNumber
                    },
                    peers.map { BatteryComparisonPackage(it.packageName, it.label, it.firstInstallTime) }.sortedBy { it.packageName })
            }, elapsedRealtimeMs = elapsedRealtimeMs, bootCount = bootCount)
        }
    }
}

data class BatteryComparisonSession(val baseline: BatteryComparisonSample, val later: BatteryComparisonSample? = null)

data class BatteryComparisonRow(
    val uid: Int,
    val identity: BatteryUidIdentity?,
    val delta: UidEnergy? = null,
    val unavailableReason: String? = null,
)

data class BatteryComparisonResult(
    val intervalMs: Long? = null,
    val rows: List<BatteryComparisonRow> = emptyList(),
    val unavailableReason: String? = null,
    val wallClockSpanMs: Long? = null,
    val intervalNote: String? = null,
)

object BatteryComparison {
    fun calculate(session: BatteryComparisonSession): BatteryComparisonResult {
        val later = session.later ?: return BatteryComparisonResult()
        val baseline = session.baseline
        val start = baseline.energy
        val end = later.energy
        val wallClockSpan = (end.capturedAt - start.capturedAt).takeIf { start.capturedAt > 0 && end.capturedAt > start.capturedAt }
        val sameBoot = baseline.bootCount != null && later.bootCount != null && baseline.bootCount == later.bootCount
        val crossedBoot = baseline.bootCount != null && later.bootCount != null && later.bootCount > baseline.bootCount
        val startElapsed = baseline.elapsedRealtimeMs
        val endElapsed = later.elapsedRealtimeMs
        val monotonicKnown = sameBoot && startElapsed != null && endElapsed != null
        val interval = if (sameBoot && startElapsed != null && endElapsed != null && startElapsed >= 0 && endElapsed > startElapsed)
            endElapsed - startElapsed else null
        val intervalNote = when {
            interval != null -> null
            crossedBoot -> "Elapsed duration is unavailable across a device reboot. The recorded clock span can be affected by device clock corrections."
            else -> "These samples have no verified elapsed duration. The recorded clock span can be affected by device clock corrections."
        }
        val unavailable = when {
            start.capturedAt <= 0 || end.capturedAt <= 0 -> "A sample has an invalid recording time. Restart the comparison."
            monotonicKnown && interval == null -> "The later reading's device uptime is not newer than the baseline. Restart the comparison."
            baseline.bootCount != null && later.bootCount != null && later.bootCount < baseline.bootCount ->
                "The later reading comes from an older device boot. Restart the comparison."
            start.startClockTimeMs == null || end.startClockTimeMs == null ->
                "Android did not provide a battery-history identifier for both samples. History continuity cannot be verified."
            start.startClockTimeMs != end.startClockTimeMs ->
                "Android battery history reset between samples. Restart the comparison with a fresh baseline."
            start.batteryRealtimeMs != null && end.batteryRealtimeMs != null && end.batteryRealtimeMs < start.batteryRealtimeMs ->
                "Android's cumulative battery time moved backwards. Restart the comparison with a fresh baseline."
            start.uids.map { it.uid }.distinct().size != start.uids.size || end.uids.map { it.uid }.distinct().size != end.uids.size ||
                baseline.identities.map { it.uid }.distinct().size != baseline.identities.size ||
                later.identities.map { it.uid }.distinct().size != later.identities.size ->
                "A sample contains duplicate UID identities. Take a fresh baseline before comparing."
            else -> null
        }
        if (unavailable != null) return BatteryComparisonResult(intervalMs = interval, unavailableReason = unavailable,
            wallClockSpanMs = wallClockSpan, intervalNote = intervalNote)
        val before = start.uids.associateBy { it.uid }
        val after = end.uids.associateBy { it.uid }
        val oldIdentities = baseline.identities.associateBy { it.uid }
        val newIdentities = later.identities.associateBy { it.uid }
        val rows = (before.keys + after.keys).sorted().map { uid ->
            val old = before[uid]
            val new = after[uid]
            val originalIdentity = oldIdentities[uid]
            val currentIdentity = newIdentities[uid]
            val reason = when {
                old == null -> "This UID has no baseline reading. Its interval usage is unavailable."
                new == null -> "This UID is missing from the later reading. Its interval usage is unavailable."
                originalIdentity == null || currentIdentity == null -> "The installed identity could not be verified for both samples."
                !originalIdentity.profileAvailable || !currentIdentity.profileAvailable ->
                    "This profile was unavailable or its identity changed during sampling. Resume it and restart the comparison."
                originalIdentity.userSerial == null || currentIdentity.userSerial == null ->
                    "The profile serial number is unavailable, so profile continuity cannot be verified."
                originalIdentity.userSerial != currentIdentity.userSerial ->
                    "This profile was recreated. Restart the comparison for its current apps."
                originalIdentity.packages.isEmpty() || currentIdentity.packages.isEmpty() ->
                    "No installed packages could be attributed to this UID. Its installation identity is unverified."
                !originalIdentity.hasKnownPackages() || !currentIdentity.hasKnownPackages() ->
                    "A package installation identity is unavailable. Refresh app access and restart the comparison."
                originalIdentity.packageInstalls() != currentIdentity.packageInstalls() ->
                    "A package was reinstalled or this UID's package membership changed. Restart the comparison."
                !validCounters(old) || !validCounters(new) -> "Android returned an invalid counter for this UID. No changes are calculated."
                countersMovedBackwards(old, new) ->
                    "A cumulative counter for this UID moved backwards. Its history may have reset; restart the comparison."
                else -> null
            }
            BatteryComparisonRow(uid, currentIdentity ?: originalIdentity,
                delta = if (reason == null && old != null && new != null) UidEnergy(uid,
                    difference(old.estimatedMah, new.estimatedMah), difference(old.cpuTimeMs, new.cpuTimeMs),
                    difference(old.networkRxBytes, new.networkRxBytes), difference(old.networkTxBytes, new.networkTxBytes),
                    difference(old.wakelockMs, new.wakelockMs)) else null,
                unavailableReason = reason)
        }
        return BatteryComparisonResult(intervalMs = interval, rows = rows, wallClockSpanMs = wallClockSpan, intervalNote = intervalNote)
    }

    internal fun validCounters(value: UidEnergy): Boolean = value.uid >= 0 &&
        (value.estimatedMah == null || value.estimatedMah.isFinite() && value.estimatedMah >= 0) &&
        listOf(value.cpuTimeMs, value.wakelockMs, value.networkRxBytes, value.networkTxBytes).all { it == null || it >= 0 }

    private fun BatteryUidIdentity.hasKnownPackages(): Boolean =
        packages.map { it.packageName }.distinct().size == packages.size &&
            packages.all { it.packageName.isNotBlank() && it.firstInstallTime != null && it.firstInstallTime > 0 }

    private fun BatteryUidIdentity.packageInstalls(): Map<String, Long?> = packages.associate { it.packageName to it.firstInstallTime }

    private fun countersMovedBackwards(before: UidEnergy, after: UidEnergy): Boolean =
        (before.estimatedMah != null && after.estimatedMah != null && after.estimatedMah < before.estimatedMah) ||
            listOf(before.cpuTimeMs to after.cpuTimeMs, before.wakelockMs to after.wakelockMs,
                before.networkRxBytes to after.networkRxBytes, before.networkTxBytes to after.networkTxBytes)
                .any { (old, new) -> old != null && new != null && new < old }

    private fun difference(before: Long?, after: Long?): Long? = if (before == null || after == null) null else after - before
    private fun difference(before: Double?, after: Double?): Double? = if (before == null || after == null) null else after - before
}
