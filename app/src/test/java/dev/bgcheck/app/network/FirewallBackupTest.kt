package dev.bgcheck.app.network

import dev.bgcheck.network.domain.model.FirewallBackupCodec
import org.junit.Assert.*
import org.junit.Test

class FirewallBackupTest {
    private val valid = """{"version":1,"exportDate":10,"appVersion":"1.0","rulesCount":1,"rules":[{"packageName":"dev.bgcheck.fixture","uid":10123,"appName":"Probe","wifiBlocked":true,"lanBlocked":true,"blockWhenBackground":true}]}"""
    @Test fun decodesUpstreamFormatAndPreservesEveryRuleAxis() {
        val rule = FirewallBackupCodec.decode(valid).rules.single()
        assertEquals("dev.bgcheck.fixture", rule.packageName)
        assertTrue(rule.wifiBlocked && rule.lanBlocked && rule.blockWhenBackground)
        assertFalse(rule.mobileBlocked)
    }
    @Test fun rejectsShellMetacharactersBeforePrivilegedIdentityLookup() {
        rejects(valid.replace("dev.bgcheck.fixture", "test;touch /data/local/tmp/injected"))
    }
    @Test fun rejectsInvalidVersionCountAndProfile() {
        rejects(valid.replace("\"version\":1", "\"version\":2"))
        rejects(valid.replace("\"rulesCount\":1", "\"rulesCount\":2"))
        rejects(valid.replace("\"uid\":10123", "\"userId\":-1,\"uid\":10123"))
    }
    @Test fun preservesProfileSerialForRestoreValidation() {
        val rule = FirewallBackupCodec.decode(valid.replace("\"uid\":10123", "\"uid\":10123,\"userSerial\":42")).rules.single()
        assertEquals(42L, rule.userSerial)
    }
    private fun rejects(json: String) {
        try { FirewallBackupCodec.decode(json); fail("Invalid backup accepted") }
        catch (expected: IllegalArgumentException) { }
    }
}
