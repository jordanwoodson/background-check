package dev.bgcheck.app.network

import dev.bgcheck.network.domain.firewall.rulesByCurrentUid
import dev.bgcheck.network.domain.model.FirewallBackupCodec
import dev.bgcheck.network.domain.model.FirewallRule
import dev.bgcheck.network.domain.model.FirewallRulesBackup
import org.junit.Assert.*
import org.junit.Test

class NetworkProfileIdentityTest {
    private val work = FirewallRule("example.app", userId = 10, uid = 1010123, appName = "Work")

    @Test fun missingProfileNeverReusesSavedUid() {
        val current = mapOf(0 to mapOf("example.app" to 10123))
        assertTrue(rulesByCurrentUid(listOf(work), current).isEmpty())
    }

    @Test fun unreadableProfileNeverReusesSavedUid() {
        val current = mapOf(0 to mapOf("example.app" to 10123), 10 to emptyMap())
        assertTrue(rulesByCurrentUid(listOf(work), current).isEmpty())
    }

    @Test fun absentWorkPackageNeverBorrowsPersonalIdentity() {
        val current = mapOf(0 to mapOf("example.app" to 10123), 10 to mapOf("other.app" to 1010123))
        assertTrue(rulesByCurrentUid(listOf(work), current).isEmpty())
    }

    @Test fun currentUidBelongsToSelectedProfile() {
        val current = mapOf(0 to mapOf("example.app" to 10123), 10 to mapOf("example.app" to 1010456))
        assertEquals(setOf(1010456), rulesByCurrentUid(listOf(work), current).keys)
    }

    @Test fun backupRejectsUidBelongingToAnotherUser() {
        val json = """{"version":1,"exportDate":10,"appVersion":"1.0","rulesCount":1,"rules":[{"packageName":"example.app","userId":10,"uid":10123,"appName":"Work"}]}"""
        try {
            FirewallBackupCodec.decode(json)
            fail("Wrong-profile UID accepted")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun backupRejectsRecreatedAndLegacySecondaryProfiles() {
        val current = mapOf(0 to 0L, 10 to 42L)
        fun rejects(rule: FirewallRule) {
            try {
                FirewallBackupCodec.validateProfiles(FirewallRulesBackup(exportDate = 1, appVersion = "test", rulesCount = 1, rules = listOf(rule)), current, 0)
                fail("Unverified profile accepted")
            } catch (_: IllegalArgumentException) { }
        }
        rejects(work.copy(userSerial = 41))
        rejects(work)
        FirewallBackupCodec.validateProfiles(FirewallRulesBackup(exportDate = 1, appVersion = "test", rulesCount = 1,
            rules = listOf(work.copy(userSerial = 42))), current, 0)
    }
}
