package dev.bgcheck.app.network

import dev.bgcheck.network.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class NetworkChangeHistoryTest {
    private val before = FirewallRule("dev.bgcheck.fixture", userId = 10, uid = 1010123,
        appName = "Probe", userSerial = 42, wifiBlocked = false, createdAt = 100, updatedAt = 100)
    private val after = before.copy(wifiBlocked = true, lanBlocked = true, updatedAt = 200)
    private val identity = NetworkInstallationIdentity(1010123, 42, 90)
    private val record = NetworkChangeRecord(id = "change", packageName = before.packageName,
        label = "Probe", userId = 10, uid = 1010123, userSerial = 42, firstInstallTime = 90,
        profileName = "Work", timestamp = 200, kind = NetworkChangeKind.FIREWALL_RULE,
        before = NetworkChangeValue(firewallRule = before), after = NetworkChangeValue(firewallRule = after))

    @Test fun codecPreservesProfileInstallationAndEveryFirewallAxis() {
        val source = record.copy(after = NetworkChangeValue(firewallRule = after.copy(
            mobileBlocked = true, blockWhenBackground = true, blockWhenRoaming = true, enabled = false)))
        val restored = NetworkChangeHistoryCodec.decode(NetworkChangeHistoryCodec.encode(listOf(source))).single()
        assertEquals(source, restored)
        assertTrue(restored.after.summary().contains("Background"))
        assertEquals(90L, restored.firstInstallTime)
    }

    @Test fun codecRejectsCrossPackageAndCrossProfileRecords() {
        val invalid = listOf(
            record.copy(after = NetworkChangeValue(firewallRule = after.copy(packageName = "another.app"))),
            record.copy(userId = 0), record.copy(userSerial = 43),
            record.copy(kind = NetworkChangeKind.PACKAGE_ENABLED))
        invalid.forEach { assertTrue(NetworkChangeHistoryCodec.decode(NetworkChangeHistoryCodec.encode(listOf(it))).isEmpty()) }
    }

    @Test fun guardRejectsReinstalledAppAndRecreatedOrPausedProfile() {
        for (current in listOf(identity.copy(uid = 1010124), identity.copy(userSerial = 43),
            identity.copy(firstInstallTime = 91), identity.copy(firstInstallTime = null))) {
            rejects { NetworkChangeGuard.requireUndo(record, current, available = true, currentValue = record.after) }
        }
        rejects { NetworkChangeGuard.requireUndo(record, identity, available = false, currentValue = record.after) }
        rejects { NetworkChangeGuard.requireUndo(record.copy(undone = true), identity, true, record.after) }
    }

    @Test fun guardRejectsNewerRuleAndAllowsIrrelevantTimestampChanges() {
        rejects { NetworkChangeGuard.requireUndo(record, identity, true, NetworkChangeValue(firewallRule = before)) }
        rejects { NetworkChangeGuard.requireUndo(record, identity, true,
            NetworkChangeValue(firewallRule = after.copy(blockWhenBackground = true))) }
        NetworkChangeGuard.requireUndo(record, identity, true,
            NetworkChangeValue(firewallRule = after.copy(updatedAt = 500, appName = "Renamed")))
    }

    @Test fun packageUndoRejectsChangedEnabledValue() {
        val enabledRecord = record.copy(kind = NetworkChangeKind.PACKAGE_ENABLED,
            before = NetworkChangeValue(packageEnabled = true, packageEnabledSetting = 0), after = NetworkChangeValue(packageEnabled = false, packageEnabledSetting = 3))
        rejects { NetworkChangeGuard.requireUndo(enabledRecord, identity, true, NetworkChangeValue(packageEnabled = true, packageEnabledSetting = 0)) }
        NetworkChangeGuard.requireUndo(enabledRecord, identity, true, NetworkChangeValue(packageEnabled = false, packageEnabledSetting = 3))
        assertEquals(enabledRecord, NetworkChangeHistoryCodec.decode(NetworkChangeHistoryCodec.encode(listOf(enabledRecord))).single())
    }

    @Test fun replaceBackupDiffIncludesDeletedRulesAndDoesNotRecordMetadataOnlyChanges() {
        val removed = before.copy(packageName = "dev.bgcheck.removed", uid = 1010124)
        val added = before.copy(packageName = "dev.bgcheck.added", uid = 1010125)
        val changes = NetworkChangeHistoryCodec.firewallDiff(listOf(before, removed), listOf(after, added))
        assertEquals(3, changes.size)
        assertNull(changes.single { it.first?.packageName == removed.packageName }.second)
        assertNull(changes.single { it.second?.packageName == added.packageName }.first)
        assertTrue(NetworkChangeHistoryCodec.firewallDiff(listOf(before),
            listOf(before.copy(appName = "Renamed", updatedAt = 900))).isEmpty())
    }

    @Test fun malformedRecordsDoNotHideOtherValidHistory() {
        val valid = NetworkChangeHistoryCodec.encode(listOf(record))
        val withBadRecord = valid.replaceFirst("[", "[{\"id\":\"broken\"},")
        assertEquals(listOf(record), NetworkChangeHistoryCodec.decode(withBadRecord))
        assertTrue(NetworkChangeHistoryCodec.decode("broken json").isEmpty())
    }

    @Test fun replacingAnOldInstallationProducesSeparateNonContradictoryHistory() {
        val recreated = after.copy(userSerial = 43, uid = 1010124)
        val changes = NetworkChangeHistoryCodec.firewallDiff(listOf(before), listOf(recreated))
        assertEquals(2, changes.size)
        assertEquals(before, changes.single { it.second == null }.first)
        assertEquals(recreated, changes.single { it.first == null }.second)
    }

    private fun rejects(block: () -> Unit) {
        try { block(); fail("Unsafe Undo accepted") } catch (_: IllegalArgumentException) { }
    }
}
