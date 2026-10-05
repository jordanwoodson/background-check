package dev.bgcheck.app.ui.network

import dev.bgcheck.network.domain.firewall.BlockingContext
import dev.bgcheck.network.domain.firewall.FirewallBackendType
import dev.bgcheck.network.domain.firewall.FirewallHealth
import dev.bgcheck.network.domain.model.FirewallFilterState
import dev.bgcheck.network.domain.model.NetworkPackage
import dev.bgcheck.network.domain.model.PackageType
import dev.bgcheck.network.domain.model.UidRuleAggregate
import dev.bgcheck.network.utils.Constants
import org.junit.Assert.*
import org.junit.Test

class NetworkLogicTest {
    private fun pkg(index: Int = 0, user: Int = 0) = NetworkPackage("app.$index", user, user * 100000 + 10000 + index,
        "App $index", "", true, PackageType.USER, userSerial = user.toLong() + 1)

    @Test fun legacyDeepLinkDefaultsToOwnUser() {
        assertEquals(NetworkRequest(NetworkDestination.OPEN, "app.chrome", 10),
            parseNetworkRequest(null, "app.chrome", null, null, null, 10))
        assertEquals(12, parseNetworkRequest(null, "app.chrome", 12, null, null, 0)?.userId)
        assertNull(parseNetworkRequest(null, "  ", null, null, null, 0))
        assertNull(parseNetworkRequest("unrelated", null, null, null, null, 0))
    }

    @Test fun notificationActionUsesItsOwnExtrasAndUser() {
        assertEquals(NetworkRequest(NetworkDestination.OPEN, "work.app", 10), parseNetworkRequest(
            Constants.Notifications.ACTION_OPEN_FIREWALL, "legacy.app", 0, "work.app", 10, 0))
        assertEquals(NetworkRequest(NetworkDestination.OPEN, null, 12), parseNetworkRequest(
            Constants.Notifications.ACTION_OPEN_FIREWALL, null, null, null, null, 12))
    }

    @Test fun pendingDeepLinkWaitsForRealScanCompletionEvenAfterFilterReset() {
        val initial = dev.bgcheck.network.presentation.viewmodel.FirewallUiState()
        assertEquals(PendingNetworkPackageDecision.Wait, pendingNetworkPackageDecision(initial.hasCompletedPackageScan, null))
        val reset = initial.copy(isLoadingData = false, isRenderingUI = false, searchQuery = "app.0")
        assertFalse(reset.isLoading)
        assertEquals(PendingNetworkPackageDecision.Wait, pendingNetworkPackageDecision(reset.hasCompletedPackageScan, null))
        assertEquals(PendingNetworkPackageDecision.Wait, pendingNetworkPackageDecision(reset.hasCompletedPackageScan, pkg(user = 10)))
        val failed = reset.copy(scanFailed = true)
        assertEquals(PendingNetworkPackageDecision.Wait, pendingNetworkPackageDecision(failed.hasCompletedPackageScan, null))
    }

    @Test fun completedDeepLinkDoesNotWaitForRenderingOrLaterRefreshes() {
        val completed = dev.bgcheck.network.presentation.viewmodel.FirewallUiState(
            hasCompletedPackageScan = true, isLoadingData = false, isRenderingUI = true)
        val work = pkg(user = 10)
        assertTrue(completed.isLoading)
        assertEquals(PendingNetworkPackageDecision.Open(work), pendingNetworkPackageDecision(completed.hasCompletedPackageScan, work))
        assertEquals(PendingNetworkPackageDecision.Unavailable, pendingNetworkPackageDecision(completed.hasCompletedPackageScan, null))
        val refreshing = completed.copy(isLoadingData = true, isRenderingUI = false)
        assertTrue(refreshing.isLoading)
        assertEquals(PendingNetworkPackageDecision.Open(work), pendingNetworkPackageDecision(refreshing.hasCompletedPackageScan, work))
    }

    @Test fun operationalActionsDoNotOpenAnInjectedPackage() {
        mapOf(Constants.Firewall.ACTION_REQUEST_VPN_PERMISSION to NetworkDestination.START,
            Constants.Firewall.ACTION_TOGGLE_FIREWALL to NetworkDestination.TOGGLE,
            Constants.Notifications.ACTION_ENABLE_VPN_FALLBACK to NetworkDestination.VPN_FALLBACK).forEach { (action, destination) ->
            assertEquals(NetworkRequest(destination, null, 0), parseNetworkRequest(action, "legacy", 10, "injected", null, 0))
        }
    }

    @Test fun savedUnavailableProfileRemainsVisibleAndEscapable() {
        val choices = networkProfileChoices(listOf(ProfileChoice("user:0:1", "Personal (user 0)")), "user:10:123")
        assertEquals(listOf("All", "user:0:1", "user:10:123"), choices.map { it.value })
        assertEquals("Unavailable profile (10)", choices.last().label)
        assertEquals(listOf("All", "Personal", "Work"), networkProfileChoices(emptyList(), "Work").map { it.value })
        assertEquals(listOf("All", "Personal"), networkProfileChoices(emptyList(), "Personal").map { it.value })
        assertEquals(listOf("All"), networkProfileChoices(emptyList(), "All").map { it.value })
        assertEquals(listOf("All"), networkProfileChoices(emptyList(), "all").map { it.value })
        assertEquals(listOf("All", "Personal"), networkProfileChoices(emptyList(), "personal").map { it.value })
        assertEquals("Work", networkProfileChoices(emptyList(), "work").last().label)
        assertEquals(listOf("All", "user:10:9"), networkProfileChoices(
            listOf(ProfileChoice("user:10:9", "Work (user 10)")), "USER:10:9").map { it.value })
    }

    @Test fun summaryReflectsEveryFilterAndDensity() {
        val choices = listOf(ProfileChoice("user:10:9", "Work (user 10)"))
        assertEquals("System apps · Blocked · Work (user 10) · Internet-capable only · Compact",
            networkFilterSummary(FirewallFilterState("System", "Blocked", true, "user:10:9"), choices, true))
        assertEquals("All apps · Any access · All profiles", networkFilterSummary(FirewallFilterState("All", null, false),
            networkProfileChoices(emptyList(), "All"), false))
    }

    @Test fun storedFiltersSelectChipsRegardlessOfCaseAndSendCanonicalValues() {
        mapOf("aLL" to Constants.Packages.TYPE_ALL, "USER" to Constants.Packages.TYPE_USER,
            "System" to Constants.Packages.TYPE_SYSTEM).forEach { (stored, expected) ->
            assertEquals(expected, networkPackageTypeChoices.single { networkFilterMatches(stored, it.value) }.value)
        }
        mapOf("any" to "Any", "ALLOWED" to Constants.Firewall.STATE_ALLOWED,
            "blocked" to Constants.Firewall.STATE_BLOCKED).forEach { (stored, expected) ->
            assertEquals(expected, networkAccessChoices.single { networkFilterMatches(stored, it.value) }.value)
        }
        val profiles = networkProfileChoices(listOf(ProfileChoice("user:10:9", "Work (user 10)")), "all")
        assertEquals("All", profiles.single { networkFilterMatches("all", it.value) }.value)
        assertEquals("user:10:9", profiles.single { networkFilterMatches("USER:10:9", it.value) }.value)
        assertFalse(networkFilterMatches("user:10:9", "user:10:99"))
    }

    @Test fun summaryMapsStoredCaseVariantsToReadableLabels() {
        val profiles = networkProfileChoices(listOf(ProfileChoice("user:10:9", "Work (user 10)")), "all")
        listOf("all", "All", "ALL").forEach { value ->
            assertEquals("All apps · Any access · All profiles",
                networkFilterSummary(FirewallFilterState(value, null, false, value), profiles, false))
        }
        assertEquals("User apps · Blocked · Work (user 10) · Internet-capable only · Compact",
            networkFilterSummary(FirewallFilterState("user", "blocked", true, "USER:10:9"), profiles, true))
        assertEquals("System apps · Allowed · All profiles",
            networkFilterSummary(FirewallFilterState("SYSTEM", "allowed", false, "all"), profiles, false))
        assertEquals("All apps · Any access · All profiles",
            networkFilterSummary(FirewallFilterState("all", "ANY", false, "all"), emptyList(), false))
        assertEquals("User apps · Any access · Unavailable profile (12)",
            networkFilterSummary(FirewallFilterState("User", null, false, "user:12:99"),
                networkProfileChoices(emptyList(), "user:12:99"), false))
    }

    @Test fun togglePresentationUsesEnforcedFlagsAndRefusesProtectedApps() {
        val pkg = pkg().copy(wifiBlocked = true, savedRule = UidRuleAggregate(wifiBlocked = false))
        assertFalse(togglePresentation(pkg, NetworkControl.WIFI, null, BlockingContext()).allowed)
        assertTrue(togglePresentation(pkg, NetworkControl.WIFI, null, BlockingContext()).description.contains("Double-tap to allow"))
        assertTrue(pkg.asSaved().blockedOn(NetworkControl.WIFI).not())
        assertFalse(togglePresentation(pkg.copy(isSystemCritical = true, paintedAllowCritical = false), NetworkControl.WIFI, null, BlockingContext()).enabled)
        assertTrue(togglePresentation(pkg.copy(isSystemCritical = true, paintedAllowCritical = true), NetworkControl.WIFI, null, BlockingContext()).enabled)
    }

    @Test fun backendRefusalAndFixableSavedRulesHaveDifferentSelectionRules() {
        val work = pkg(user = 10)
        assertNotNull(selectionRefusal(work, FirewallBackendType.VPN, BlockingContext(ownUserId = 0)))
        val protectedUid = pkg().copy(paintedAllowCritical = true)
        assertNull(selectionRefusal(protectedUid, FirewallBackendType.IPTABLES,
            BlockingContext(criticalOrVpnUids = setOf(protectedUid.uid), allowCritical = true, blockAllDefault = true)))
    }

    @Test fun rowAndEveryToggleDescribeTheProfileAndAndroidUser() {
        val personal = pkg().copy(name = "Chrome")
        val work = pkg(user = 10).copy(name = "Chrome", isWorkProfile = true)
        val clone = pkg(user = 11).copy(name = "Chrome", isCloneProfile = true)
        mapOf(personal to "Chrome, Personal profile (user 0)", work to "Chrome, Work profile (user 10)",
            clone to "Chrome, Clone profile (user 11)").forEach { (pkg, description) ->
            assertEquals(description, networkAppDescription(pkg))
            NetworkControl.entries.forEach { control ->
                val toggle = togglePresentation(pkg, control, null, BlockingContext())
                assertEquals("${control.label} allowed for $description. Double-tap to block", toggle.description)
            }
            assertEquals("Wi-Fi blocked for $description. Double-tap to allow",
                togglePresentation(pkg.copy(wifiBlocked = true), NetworkControl.WIFI, null, BlockingContext()).description)
            assertEquals("Screen-off access blocked for $description. Controls unavailable",
                networkToggleDescription("Screen-off access", false, false, description))
        }
        assertEquals("User profile (user 12)", networkProfileLabel(pkg(user = 12)))
    }

    @Test fun selectionTogglesAndHonoursLimitWithoutDroppingExistingApps() {
        val fifty = (0 until Constants.Packages.MultiSelect.MAX_SELECTION_COUNT).map { NetworkSelection(pkg(it).id, pkg(it).identity) }
        assertEquals(fifty, toggleSelection(fifty, pkg(51)))
        assertEquals(49, toggleSelection(fifty, pkg(0)).size)
        assertEquals(1, toggleSelection(emptyList(), pkg()).size)
    }

    @Test fun restoredSelectionBindsUidAndProfileIncarnation() {
        val original = pkg(user = 10)
        val entry = NetworkSelection(original.id, original.identity)
        assertEquals(entry, NetworkSelection.decode(entry.encode()))
        assertEquals(listOf(entry), currentSelection(listOf(entry), listOf(original)))
        assertTrue(currentSelection(listOf(entry), listOf(original.copy(userSerial = 99))).isEmpty())
        assertTrue(currentSelection(listOf(entry), listOf(original.copy(uid = original.uid + 1))).isEmpty())
        assertNull(NetworkSelection.decode("app|10|10001|11"))
        assertNull(NetworkSelection.decode("app|0|-1|null"))
        assertNull(NetworkSelection.decode("broken"))
    }

    @Test fun sheetsRemainReadableForUnresolvedIdentities() {
        val unknown = pkg().copy(uid = -1, userSerial = null)
        val encoded = NetworkSelection(unknown.id, unknown.identity).encode()
        assertNull(NetworkSelection.decode(encoded))
        assertEquals(unknown.identity, NetworkSelection.decodeSheet(encoded)?.identity)
        assertNotNull(selectionRefusal(unknown, null, BlockingContext()))
    }

    @Test fun batchSummaryUsesSavedRulesAndMarksPartialBlocksMixed() {
        val partial = pkg().copy(wifiBlocked = true)
        assertEquals("Mixed", networkBatchStatus(listOf(partial)))
        assertEquals("All allowed", networkBatchStatus(listOf(partial.copy(savedRule = UidRuleAggregate()))))
        assertEquals("All blocked", networkBatchStatus(listOf(partial.copy(mobileBlocked = true))))
    }

    @Test fun failedStopIsStuckAndDownIsNotOff() {
        assertEquals("Stuck", firewallStatus(false, FirewallHealth.StopFailed(null)))
        assertEquals("Down", firewallStatus(false, FirewallHealth.Down(FirewallHealth.Down.Reason.START_FAILED, null)))
        assertEquals("Off", firewallStatus(false, FirewallHealth.Healthy))
        assertEquals("Active", firewallStatus(true, FirewallHealth.Healthy))
    }

    @Test fun batchStatusKeepsMixedStateVisible() {
        assertEquals("All allowed", batchStatus(listOf(false, false)))
        assertEquals("All blocked", batchStatus(listOf(true, true)))
        assertEquals("Mixed", batchStatus(listOf(true, false)))
        assertEquals("No apps selected", batchStatus(emptyList()))
    }

    @Test fun firewallOperationsNeedATrustedSender() {
        listOf(NetworkDestination.START, NetworkDestination.VPN_FALLBACK, NetworkDestination.TOGGLE).forEach { destination ->
            val request = NetworkRequest(destination, userId = 0)
            assertEquals(request, request.gatedBy(trustedSender = true))
            assertEquals(NetworkRequest(NetworkDestination.OPEN), request.gatedBy(trustedSender = false))
        }
        val open = NetworkRequest(NetworkDestination.OPEN, "app.chrome", 10)
        assertEquals(open, open.gatedBy(trustedSender = false))
        assertEquals(NetworkRequest(NetworkDestination.SETTINGS), NetworkRequest(NetworkDestination.SETTINGS).gatedBy(trustedSender = false))
    }
}
