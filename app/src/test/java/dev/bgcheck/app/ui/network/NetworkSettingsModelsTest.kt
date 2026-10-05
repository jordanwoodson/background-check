package dev.bgcheck.app.ui.network

import dev.bgcheck.network.domain.firewall.FirewallBackendType
import dev.bgcheck.network.domain.firewall.FirewallMode
import dev.bgcheck.network.domain.model.CaptivePortalPreset
import dev.bgcheck.network.presentation.viewmodel.ImportUninstalledPreview
import dev.bgcheck.network.utils.ShellResult
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class NetworkSettingsModelsTest {
    private fun options(
        root: Boolean = false,
        shizuku: Boolean = false,
        shizukuRoot: Boolean = false,
        sdk: Int = 36,
        usable: Set<FirewallMode>? = null,
        failed: Set<FirewallMode> = emptySet(),
        active: FirewallBackendType? = null,
    ) = networkSettingsBackendOptions(root, shizuku, shizukuRoot, sdk, usable, failed, active).associateBy { it.mode }

    @Test fun unprivilegedDeviceOffersAutoAndVpnAndExplainsOtherChoices() {
        val rows = options()
        assertTrue(rows.getValue(FirewallMode.AUTO).available)
        assertEquals("Auto (recommended)", rows.getValue(FirewallMode.AUTO).label)
        assertTrue(rows.getValue(FirewallMode.VPN).available)
        assertTrue(rows.getValue(FirewallMode.CONNECTIVITY_MANAGER).requirement!!.contains("Shizuku"))
        assertTrue(rows.getValue(FirewallMode.IPTABLES).requirement!!.contains("root"))
        assertFalse(rows.getValue(FirewallMode.NETWORK_POLICY_MANAGER).available)
    }

    @Test fun connectivityRequiresAndroid13EvenWithShizuku() {
        val row = options(shizuku = true, sdk = 32).getValue(FirewallMode.CONNECTIVITY_MANAGER)
        assertFalse(row.available)
        assertEquals("Requires Android 13 or later.", row.requirement)
        assertTrue(options(shizuku = true, sdk = 33).getValue(FirewallMode.CONNECTIVITY_MANAGER).available)
    }

    @Test fun adbShizukuDoesNotMakeIptablesAvailable() {
        assertFalse(options(shizuku = true).getValue(FirewallMode.IPTABLES).available)
        assertTrue(options(shizuku = true, shizukuRoot = true).getValue(FirewallMode.IPTABLES).available)
        assertTrue(options(root = true).getValue(FirewallMode.IPTABLES).available)
    }

    @Test fun realProbeOverridesPrivilegeEstimate() {
        val rows = options(root = true, shizuku = true, usable = setOf(FirewallMode.AUTO, FirewallMode.VPN))
        assertFalse(rows.getValue(FirewallMode.IPTABLES).available)
        assertFalse(rows.getValue(FirewallMode.CONNECTIVITY_MANAGER).available)
        assertTrue(rows.getValue(FirewallMode.CONNECTIVITY_MANAGER).requirement!!.contains("Not supported"))
    }

    @Test fun failedStartStaysUnavailableEvenAfterSuccessfulProbe() {
        assertFalse(options(root = true, usable = FirewallMode.entries.toSet(), failed = setOf(FirewallMode.IPTABLES)).getValue(FirewallMode.IPTABLES).available)
        // Until the probe answers the legacy picker uses privileges, without applying failed modes.
        assertTrue(options(root = true, failed = setOf(FirewallMode.IPTABLES)).getValue(FirewallMode.IPTABLES).available)
    }

    @Test fun runningBackendRemainsAvailableAcrossTransientProbeAndPrivilegeLoss() {
        val row = options(usable = emptySet(), failed = setOf(FirewallMode.IPTABLES), active = FirewallBackendType.IPTABLES).getValue(FirewallMode.IPTABLES)
        assertTrue(row.available)
        assertNull(row.requirement)
    }

    @Test fun limitedBackendExplainsBackgroundDataRestriction() {
        assertTrue(options(shizuku = true).getValue(FirewallMode.NETWORK_POLICY_MANAGER).explanation.contains("Foreground traffic can still pass"))
    }

    @Test fun everyActiveBackendMapsToItsPickerMode() {
        assertNull(networkSettingsActiveMode(null))
        FirewallBackendType.entries.forEach { backend ->
            assertEquals(backend.name, networkSettingsActiveMode(backend)!!.name)
        }
    }

    @Test fun portalPresetRequiresBothExactUrls() {
        CaptivePortalPreset.entries.filter { it != CaptivePortalPreset.CUSTOM }.forEach { preset ->
            assertEquals(preset, networkSettingsMatchingPreset(preset.httpUrl, preset.httpsUrl))
            assertEquals(CaptivePortalPreset.CUSTOM, networkSettingsMatchingPreset(preset.httpUrl, "https://example.test/check"))
            assertEquals(CaptivePortalPreset.CUSTOM, networkSettingsMatchingPreset("${preset.httpUrl}/", preset.httpsUrl))
        }
    }

    @Test fun missingUrlsAndEmptyCustomAreCustom() {
        assertEquals(CaptivePortalPreset.CUSTOM, networkSettingsMatchingPreset(null, null))
        assertEquals(CaptivePortalPreset.CUSTOM, networkSettingsMatchingPreset("", ""))
        assertEquals(CaptivePortalPreset.CUSTOM, networkSettingsMatchingPreset(CaptivePortalPreset.GOOGLE.httpUrl, null))
    }

    @Test fun documentNamesHaveStableDateAndExtension() {
        val date = LocalDate.of(2026, 1, 2)
        assertEquals("background-check-firewall-backup-2026-01-02.json", networkSettingsBackupFileName(date))
        assertEquals("background-check-retained-packages-2026-01-02.txt", networkSettingsPackageFileName(date))
    }

    @Test fun permissionRowsDistinguishGrantedNeededAndNotRequired() {
        assertEquals(FirewallPermissionRowState("Granted", null), firewallPermissionRowState(true))
        assertEquals(FirewallPermissionRowState("Needed", "Grant"), firewallPermissionRowState(false))
        assertEquals(FirewallPermissionRowState("Needed", "Open"), firewallPermissionRowState(false, openSettings = true))
        assertEquals(FirewallPermissionRowState("Not required", null), firewallPermissionRowState(false, notRequired = true))
        assertEquals(FirewallPermissionRowState("Not required", null), firewallPermissionRowState(true, notRequired = true))
    }

    @Test fun notificationFirstRequestAndRationaleCanRequestAgain() {
        assertFalse(firewallNotificationNeedsSettings(33, previouslyRequested = false, rationale = false))
        assertFalse(firewallNotificationNeedsSettings(36, previouslyRequested = true, rationale = true))
        assertTrue(firewallNotificationNeedsSettings(36, previouslyRequested = true, rationale = false))
        assertTrue(firewallNotificationNeedsSettings(32, previouslyRequested = false, rationale = false))
    }

    @Test fun rootTimeoutIsDistinctFromUnavailableAndDenied() {
        assertEquals(FirewallPermissionsRootOutcome.TIMEOUT, firewallPermissionsRootOutcome(ShellResult(-1, "", "", timedOut = true)))
        assertEquals(FirewallPermissionsRootOutcome.NO_ACCESS, firewallPermissionsRootOutcome(ShellResult(-1, "", "su not found")))
        assertEquals(FirewallPermissionsRootOutcome.DENIED, firewallPermissionsRootOutcome(ShellResult(1, "", "permission denied")))
        assertEquals(FirewallPermissionsRootOutcome.GRANTED, firewallPermissionsRootOutcome(ShellResult(0, "uid=0(root)", "")))
    }

    @Test fun importPreviewSeparatesMissingAndProtectedAndKeepsProfileScope() {
        val preview = ImportUninstalledPreview(3, listOf("a.package" to 10), listOf("missing.package"), listOf("protected.package"), profileNames = mapOf(10 to "Work (User 10)"), legacyProfile = "Work (User 10)")
        val text = networkSettingsImportPreview(preview)
        assertTrue(text.contains("only from Work (User 10)"))
        assertTrue(text.contains("a.package · Work (User 10)"))
        assertTrue(text.contains("Removable: 1 of 3"))
        assertTrue(text.contains("Missing: 1\n• missing.package"))
        assertTrue(text.contains("Protected and skipped: 1\n• protected.package"))
        assertTrue(networkSettingsImportPreview(preview.copy(legacyProfile = null)).contains("only from the profiles recorded in this file"))
    }
}
