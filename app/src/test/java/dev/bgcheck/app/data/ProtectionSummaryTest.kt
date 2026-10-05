package dev.bgcheck.app.data

import dev.bgcheck.network.data.firewall.FirewallManager.FirewallState
import dev.bgcheck.network.domain.firewall.FirewallBackendType
import dev.bgcheck.network.domain.firewall.FirewallHealth
import org.junit.Assert.*
import org.junit.Test

class ProtectionSummaryTest {
    private val profiles = listOf(AndroidProfile(0, "Personal", serialNumber = 0), AndroidProfile(10, "Work", true, 42))

    @Test fun failedStartDoesNotLabelTheAttemptedBackendActive() {
        val summary = ProtectionSummary.from(
            FirewallState.Error("Could not start", FirewallBackendType.CONNECTIVITY_MANAGER),
            FirewallHealth.Down(FirewallHealth.Down.Reason.START_FAILED, FirewallBackendType.CONNECTIVITY_MANAGER),
            profiles, 0, false)
        assertEquals("None", summary.backend)
        assertFalse(summary.active)
    }

    @Test fun failedSwitchNamesTheBackendThatRemainsActive() {
        val summary = ProtectionSummary.from(
            FirewallState.Error("Switch failed; previous protection retained", FirewallBackendType.CONNECTIVITY_MANAGER),
            FirewallHealth.Healthy, profiles, 0, false, activeBackend = FirewallBackendType.VPN)
        assertEquals("Local VPN", summary.backend)
        assertEquals("Error", summary.status)
    }

    @Test fun stoppedFirewallDoesNotClaimProtection() {
        val summary = ProtectionSummary.from(FirewallState.Stopped, FirewallHealth.Healthy, profiles, 0, false)
        assertEquals("Off", summary.status)
        assertFalse(summary.active)
        assertTrue(summary.profileScope.contains("None"))
    }

    @Test fun vpnScopeIsOnlyTheHostingProfile() {
        val summary = ProtectionSummary.from(FirewallState.Running(FirewallBackendType.VPN), FirewallHealth.Healthy, profiles, 0, false)
        assertTrue(summary.active)
        assertTrue(summary.profileScope.contains("Personal"))
        assertTrue(summary.detail.contains("Work"))
    }

    @Test fun stockNpmNeverClaimsFullInternetProtection() {
        val summary = ProtectionSummary.from(FirewallState.Running(FirewallBackendType.NETWORK_POLICY_MANAGER), FirewallHealth.Healthy, profiles, 0, false)
        assertEquals("Limited", summary.status)
        assertTrue(summary.detail.contains("metered"))
    }

    @Test fun startingBackendDoesNotClaimActiveProtection() {
        val summary = ProtectionSummary.from(FirewallState.Starting(FirewallBackendType.VPN), FirewallHealth.Healthy, profiles, 0, false)
        assertFalse(summary.active)
        assertEquals("Starting", summary.status)
    }
}
