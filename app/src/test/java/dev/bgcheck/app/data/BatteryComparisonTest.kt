package dev.bgcheck.app.data

import org.junit.Assert.*
import org.junit.Test

class BatteryComparisonTest {
    private val identity = BatteryUidIdentity(10101, "Personal", 0, true,
        listOf(BatteryComparisonPackage("dev.example", "Example", 100)))
    private val before = UidEnergy(10101, 10.0, 100, 1_000, 2_000, 300)
    private val after = UidEnergy(10101, 12.5, 350, 1_600, 2_080, 400)
    private fun sample(reading: UidEnergy = before, time: Long = 1_000,
        ids: List<BatteryUidIdentity> = listOf(identity), start: Long? = 1, onBattery: Long? = time,
        elapsed: Long? = time, boot: Int? = 1) =
        BatteryComparisonSample(EnergySnapshot(uids = listOf(reading), batteryRealtimeMs = onBattery,
            startClockTimeMs = start, capturedAt = time), ids, elapsedRealtimeMs = elapsed, bootCount = boot)
    private fun result(later: BatteryComparisonSample = sample(after, 61_000), baseline: BatteryComparisonSample = sample()) =
        BatteryComparison.calculate(BatteryComparisonSession(baseline, later))

    @Test fun subtractsMatchingUidCountersOverRecordedInterval() {
        val result = result()
        assertEquals(60_000L, result.intervalMs)
        assertNull(result.unavailableReason)
        val row = result.rows.single()
        assertNull(row.unavailableReason)
        assertEquals(UidEnergy(10101, 2.5, 250, 600, 80, 100), row.delta)
    }

    @Test fun missingCountersStayUnavailableWhileRealZeroRemainsZero() {
        val row = result(sample(after.copy(cpuTimeMs = null, estimatedMah = 10.0), 61_000)).rows.single()
        assertNull(row.delta!!.cpuTimeMs)
        assertEquals(0.0, row.delta.estimatedMah!!, 0.0)
        assertEquals(600L, row.delta.networkRxBytes)
    }

    @Test fun changedHistoryStartRejectsAllDeltasEvenWhenCountersGrew() {
        val result = result(sample(after, 61_000, start = 2))
        assertNotNull(result.unavailableReason)
        assertTrue(result.rows.all { it.delta == null })
    }

    @Test fun unknownHistoryContinuityDoesNotProduceDeltas() {
        val result = result(sample(after, 61_000, start = null))
        assertNotNull(result.unavailableReason)
        assertTrue(result.rows.all { it.delta == null })
    }

    @Test fun decreasedDeviceBatteryRuntimeRejectsSession() {
        val result = result(sample(after, 61_000, onBattery = 999))
        assertNotNull(result.unavailableReason)
        assertTrue(result.rows.all { it.delta == null })
    }

    @Test fun backwardsAndEqualSampleTimesAreRejected() {
        listOf(900L, 1_000L).forEach { time ->
            assertNotNull(result(sample(after, time)).unavailableReason)
        }
    }

    @Test fun anyBackwardsUidCounterInvalidatesThatUid() {
        listOf(after.copy(estimatedMah = 9.9), after.copy(cpuTimeMs = 99),
            after.copy(wakelockMs = 299), after.copy(networkRxBytes = 999),
            after.copy(networkTxBytes = 1_999)).forEach { reading ->
            val row = result(sample(reading, 61_000)).rows.single()
            assertNull(row.delta)
            assertNotNull(row.unavailableReason)
        }
    }

    @Test fun newAndMissingUidsDoNotAssumeZeroBaseline() {
        val added = sample(after.copy(uid = 10102), 61_000,
            ids = listOf(identity.copy(uid = 10102)))
        val rows = result(added).rows
        assertEquals(setOf(10101, 10102), rows.map { it.uid }.toSet())
        assertTrue(rows.all { it.delta == null && it.unavailableReason != null })
    }

    @Test fun profileRecreationIsNotTheSameIdentity() {
        val row = result(sample(after, 61_000, ids = listOf(identity.copy(userSerial = 5)))).rows.single()
        assertNull(row.delta)
        assertNotNull(row.unavailableReason)
    }

    @Test fun packageReinstallationAtSameUidIsNotTheSameIdentity() {
        val replacement = identity.copy(packages = listOf(identity.packages.single().copy(firstInstallTime = 2_000)))
        val row = result(sample(after, 61_000, ids = listOf(replacement))).rows.single()
        assertNull(row.delta)
        assertNotNull(row.unavailableReason)
    }

    @Test fun missingPackageInstallIdentityOrSerialBlocksAttribution() {
        listOf(identity.copy(userSerial = null), identity.copy(packages = listOf(identity.packages.single().copy(firstInstallTime = null))),
            identity.copy(packages = emptyList())).forEach { unknown ->
            val row = result(sample(after, 61_000, ids = listOf(unknown))).rows.single()
            assertNull(row.delta)
            assertNotNull(row.unavailableReason)
        }
    }

    @Test fun pausedProfileIsUnavailableEvenWithMatchingSavedIdentity() {
        val row = result(sample(after, 61_000, ids = listOf(identity.copy(profileAvailable = false)))).rows.single()
        assertNull(row.delta)
        assertNotNull(row.unavailableReason)
    }

    @Test fun sharedUidCountsOnceAndMembershipChangesInvalidateAttribution() {
        val shared = identity.copy(packages = identity.packages + BatteryComparisonPackage("dev.shared", "Shared", 200))
        val baseline = sample(ids = listOf(shared))
        val valid = result(sample(after, 61_000, ids = listOf(shared.copy(packages = shared.packages.reversed()))), baseline)
        assertEquals(2.5, valid.rows.single().delta!!.estimatedMah!!, 0.0)
        assertEquals(2, valid.rows.single().identity!!.packages.size)
        val changed = result(sample(after, 61_000), baseline).rows.single()
        assertNull(changed.delta)
        assertNotNull(changed.unavailableReason)
    }

    @Test fun samePackageInPersonalAndWorkProfilesRemainsSeparate() {
        val work = identity.copy(uid = 1_010_101, profileName = "Work", userSerial = 10)
        val baseline = sample().let { it.copy(energy = it.energy.copy(uids = listOf(before, before.copy(uid = work.uid))), identities = listOf(identity, work)) }
        val later = sample(after, 61_000).let { it.copy(energy = it.energy.copy(uids = listOf(after, after.copy(uid = work.uid, estimatedMah = 15.0))), identities = listOf(identity, work)) }
        val rows = result(later, baseline).rows.associateBy { it.uid }
        assertEquals(2.5, rows.getValue(10101).delta!!.estimatedMah!!, 0.0)
        assertEquals(5.0, rows.getValue(work.uid).delta!!.estimatedMah!!, 0.0)
        assertEquals("Work", rows.getValue(work.uid).identity!!.profileName)
    }

    @Test fun invalidCounterAndDuplicateUidAreNeverCompared() {
        val invalid = result(sample(after.copy(estimatedMah = Double.NaN), 61_000)).rows.single()
        assertNull(invalid.delta)
        assertNotNull(invalid.unavailableReason)
        val duplicate = sample(after, 61_000).let { it.copy(energy = it.energy.copy(uids = listOf(after, after))) }
        assertNotNull(result(duplicate).unavailableReason)
    }

    @Test fun capturePreservesSharedUidPackagesButExcludesUninstalledEntries() {
        val personal = AndroidProfile(0, serialNumber = 0)
        val apps = listOf(
            AppEntry("dev.example", "Example", 10101, false, profile = personal, firstInstallTime = 100),
            AppEntry("dev.shared", "Shared", 10101, false, profile = personal, firstInstallTime = 200),
            AppEntry("dev.removed", "Removed", 10101, false, installed = false, profile = personal, firstInstallTime = 50))
        val captured = BatteryComparisonSample.capture(sample().energy, apps, listOf(personal))
        val capturedIdentity = captured.identities.single()
        assertEquals(setOf("dev.example", "dev.shared"), capturedIdentity.packages.map { it.packageName }.toSet())
        assertEquals(0L, capturedIdentity.userSerial)
        assertTrue(capturedIdentity.profileAvailable)
    }

    @Test fun captureDoesNotTrustStaleAppProfileSerial() {
        val original = AndroidProfile(10, "Work", true, 10)
        val recreated = original.copy(serialNumber = 11)
        val app = AppEntry("dev.example", "Example", 1_010_101, false, profile = original, firstInstallTime = 100)
        val captured = BatteryComparisonSample.capture(sample(before.copy(uid = app.uid)).energy, listOf(app), listOf(recreated))
        assertFalse(captured.identities.single().profileAvailable)
    }

    @Test fun sameBootUsesMonotonicTimeWhenWallClockMovesBackwards() {
        val baseline = sample(time = 100_000, elapsed = 1_000, onBattery = 1_000)
        val later = sample(after, time = 90_000, elapsed = 61_000, onBattery = 61_000)
        val result = result(later, baseline)
        assertNull(result.unavailableReason)
        assertEquals(60_000L, result.intervalMs)
        assertEquals(2.5, result.rows.single().delta!!.estimatedMah!!, 0.0)
    }

    @Test fun wallClockJumpDoesNotInflateMonotonicElapsedDuration() {
        val result = result(sample(after, time = 36_061_000, elapsed = 61_000))
        assertEquals(60_000L, result.intervalMs)
    }

    @Test fun legacySamplesExposeClockSpanWithoutClaimingElapsedDuration() {
        val result = result(sample(after, 61_000, elapsed = null, boot = null),
            sample(elapsed = null, boot = null))
        assertNull(result.unavailableReason)
        assertNull(result.intervalMs)
        assertEquals(60_000L, result.wallClockSpanMs)
        assertNotNull(result.intervalNote)
        assertNotNull(result.rows.single().delta)
    }

    @Test fun crossingRebootKeepsSafeCountersButCannotUseUptimeAsDuration() {
        val result = result(sample(after, 61_000, elapsed = 200, boot = 2))
        assertNull(result.unavailableReason)
        assertNull(result.intervalMs)
        assertEquals(60_000L, result.wallClockSpanMs)
        assertNotNull(result.intervalNote)
        assertNotNull(result.rows.single().delta)
    }

    @Test fun rebootWithClockCorrectionKeepsCountersAndMarksDurationUnavailable() {
        val result = result(sample(after, 900, onBattery = 61_000, elapsed = 200, boot = 2))
        assertNull(result.unavailableReason)
        assertNull(result.intervalMs)
        assertNull(result.wallClockSpanMs)
        assertNotNull(result.intervalNote)
        assertNotNull(result.rows.single().delta)
    }

    @Test fun sameBootBackwardsMonotonicTimeRejectsStaleSample() {
        val result = result(sample(after, 61_000, elapsed = 500))
        assertNotNull(result.unavailableReason)
        assertTrue(result.rows.all { it.delta == null })
    }

    @Test fun olderBootCounterRejectsStaleSample() {
        val result = result(sample(after, 61_000, boot = 0))
        assertNotNull(result.unavailableReason)
        assertTrue(result.rows.all { it.delta == null })
    }

    @Test fun captureRetainsCallerClockMetadata() {
        val captured = BatteryComparisonSample.capture(sample().energy, emptyList(), emptyList(), 88_000, 12)
        assertEquals(88_000L, captured.elapsedRealtimeMs)
        assertEquals(12, captured.bootCount)
    }
}
