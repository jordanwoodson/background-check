package dev.bgcheck.app.ui.network

import org.junit.Assert.*
import org.junit.Test

class FirewallPermissionsSectionTest {
    @Test fun optionalPermissionsKeepSetupActionsWithoutClaimingTheyAreNeeded() {
        assertEquals(FirewallPermissionRowState("Optional · not set up", "Grant"),
            optionalFirewallPermissionState(firewallPermissionRowState(false), optional = true))
        assertEquals(FirewallPermissionRowState("Optional · not set up", "Open"),
            optionalFirewallPermissionState(firewallPermissionRowState(false, openSettings = true), optional = true))
    }

    @Test fun requiredGrantedAndNotRequiredStatesRemainDistinct() {
        assertEquals(FirewallPermissionRowState("Needed", "Grant"),
            optionalFirewallPermissionState(firewallPermissionRowState(false), optional = false))
        listOf(true, false).forEach { optional ->
            assertEquals(FirewallPermissionRowState("Granted", null),
                optionalFirewallPermissionState(firewallPermissionRowState(true), optional))
            assertEquals(FirewallPermissionRowState("Not required", null),
                optionalFirewallPermissionState(firewallPermissionRowState(false, notRequired = true), optional))
        }
    }
}
