package dev.bgcheck.app.data

import org.junit.Assert.*
import org.junit.Test

class ProfileSupportTest {
    private val work = AndroidProfile(10, "Work", true, 42)
    private val app = AppEntry("dev.example", "Example", 1010001, false, profile = work)

    @Test fun snapshotDoesNotBorrowPersonalPolicyForWorkCopy() {
        val personal = app.copy(uid = 10001, profile = AndroidProfile(0, "Personal", serialNumber = 0))
        val snapshots = mapOf(0 to Snapshot(processes = mapOf("dev.example" to ProcessState.FOREGROUND),
            buckets = mapOf("dev.example" to 10), processesKnown = true),
            10 to Snapshot(processesKnown = true, buckets = mapOf("dev.example" to 45)))
        val result = ProfileSupport.merge(listOf(personal, app), snapshots)
        assertEquals(ProcessState.FOREGROUND, result[0].processState)
        assertEquals(ProcessState.NOT_RUNNING, result[1].processState)
        assertEquals(45, result[1].standbyBucket)
    }

    @Test fun pausedProfileDoesNotBecomeAnObservedEmptySnapshot() {
        val result = ProfileSupport.merge(listOf(app.copy(profile = work.copy(available = false))),
            mapOf(10 to Snapshot(processesKnown = true, exemptionsKnown = true)))
        assertEquals(ProcessState.UNKNOWN, result.single().processState)
        assertNull(result.single().dozeExempt)
    }

    @Test fun mutationRefusesRemovedRecreatedLockedOrUnknownSerialProfile() {
        listOf(emptyList(), listOf(work.copy(serialNumber = 43)), listOf(work.copy(available = false)),
            listOf(work.copy(serialNumber = null))).forEach { profiles ->
            assertThrows(IllegalStateException::class.java) { ProfileSupport.requireAvailable(app, profiles) }
        }
        ProfileSupport.requireAvailable(app, listOf(work))
    }
}
