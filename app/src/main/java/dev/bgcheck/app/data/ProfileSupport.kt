package dev.bgcheck.app.data

/** Profile identity is part of every app lookup, including the snapshot join and recovery. */
object ProfileSupport {
    fun merge(apps: List<AppEntry>, snapshots: Map<Int, Snapshot>): List<AppEntry> = apps.map { app ->
        val snapshot = snapshots[app.userId]?.takeIf { app.profile.available }
        if (snapshot == null) app.copy(processState = ProcessState.UNKNOWN, standbyBucket = null, dozeExempt = null)
        else app.copy(
            processState = snapshot.processes[app.packageName]
                ?: if (snapshot.processesKnown) ProcessState.NOT_RUNNING else ProcessState.UNKNOWN,
            standbyBucket = snapshot.buckets[app.packageName],
            dozeExempt = if (snapshot.exemptionsKnown)
                app.packageName in snapshot.exemptPackages || app.uid % 100000 in snapshot.exemptAppIds else null,
        )
    }

    fun requireAvailable(app: AppEntry, profiles: List<AndroidProfile>) {
        check(app.profile.userId == app.userId) { "App and profile identity do not match. Refresh the app list." }
        val current = profiles.singleOrNull { it.userId == app.userId }
            ?: error("This profile is no longer available. Refresh the app list.")
        check(current.available) { current.unavailableReason ?: "Unlock or resume this profile first." }
        check(app.profile.serialNumber == null || current.serialNumber == app.profile.serialNumber) {
            "This profile was recreated or its identity could not be verified. Refresh the app list."
        }
    }
}
