package dev.bgcheck.app.network

import dev.bgcheck.network.domain.firewall.*
import dev.bgcheck.network.domain.model.*
import dev.bgcheck.network.utils.Constants
import org.junit.Assert.*
import org.junit.Test

class FirewallIntegrationTest {
    private fun rule() = FirewallRule(packageName = "dev.bgcheck.fixture", uid = 10123, appName = "Probe")

    @Test fun hostIdentityAndKernelStateAreIsolated() {
        assertTrue(Constants.App.isOwnApp("dev.bgcheck.app"))
        assertFalse(Constants.App.isOwnApp("io.github.dorumrr.de1984"))
        assertEquals("bgcheck", Constants.App.ROOT_NAME_PREFIX)
    }

    @Test fun networksRoamingAndOfflineHoldKeepRequestedBlocks() {
        val wifi = rule().copy(wifiBlocked = true)
        assertTrue(wifi.isBlockedOn(NetworkType.WIFI))
        assertFalse(wifi.isBlockedOn(NetworkType.MOBILE))
        assertTrue(wifi.isBlockedOn(NetworkType.NONE))
        assertTrue(rule().copy(mobileBlocked = true).isBlockedOn(NetworkType.ROAMING))
        assertFalse(wifi.copy(enabled = false).isBlockedOn(NetworkType.WIFI))
    }

    @Test fun sharedUidUsesStrictestRuleAndScreenOffCondition() {
        val rules = listOf(rule(), rule().copy(packageName = "dev.bgcheck.shared", blockWhenBackground = true))
        assertFalse(uidBlockedNow(rules, false, false, true, NetworkType.WIFI, true))
        assertTrue(uidBlockedNow(rules, false, false, true, NetworkType.WIFI, false))
        assertTrue(uidBlockedNow(listOf(rule(), rule().copy(mobileBlocked = true)), false, false, false, NetworkType.WIFI, true))
        assertFalse(uidBlockedNow(null, true, true, true, NetworkType.WIFI, true))
        assertTrue(uidBlockedNow(null, true, false, true, NetworkType.WIFI, true))
    }

    @Test fun allNetworkActionsIncludeLanAndRoaming() {
        val blocked = rule().blockAll()
        assertTrue(blocked.lanBlocked && blocked.blockWhenRoaming && blocked.wifiBlocked && blocked.mobileBlocked)
        assertTrue(blocked.allowAll().isFullyAllowed())
    }

    @Test fun backendProfileLimitsAreHonest() {
        assertFalse(FirewallBackendType.VPN.reachesUser(10, 0))
        assertTrue(FirewallBackendType.CONNECTIVITY_MANAGER.reachesUser(10, 0))
        assertTrue(FirewallBackendType.IPTABLES.reachesUser(10, 0))
        assertTrue(FirewallBackendType.NETWORK_POLICY_MANAGER.reachesUser(10, 0))
        assertFalse(FirewallBackendType.CONNECTIVITY_MANAGER.canBlockSystemUids())
    }

    @Test fun reinstallUsesCurrentUidAndRemovedPackagesLoseRules() {
        val installed = mapOf(0 to mapOf("dev.bgcheck.fixture" to 10456))
        assertEquals(setOf(10456), rulesByCurrentUid(listOf(rule()), installed).keys)
        assertTrue(rulesByCurrentUid(listOf(rule().copy(packageName = "gone.app")), installed).isEmpty())
    }
}
