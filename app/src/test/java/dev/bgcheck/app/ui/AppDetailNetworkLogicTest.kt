package dev.bgcheck.app.ui

import dev.bgcheck.network.domain.model.NetworkPackage
import dev.bgcheck.network.domain.model.PackageType
import dev.bgcheck.network.domain.model.UidRuleAggregate
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDetailNetworkLogicTest {
    private val pkg = NetworkPackage("app.test", uid = 10001, name = "Test", icon = "", isEnabled = true,
        type = PackageType.USER, networkPermissions = listOf("android.permission.INTERNET"), permissionsKnown = true)

    @Test fun statusReportsEnforcementRatherThanTheSavedRule() {
        assertEquals("Allowed on all networks", appDetailNetworkStatus(pkg, true))
        assertEquals("Blocked on mobile data", appDetailNetworkStatus(pkg.copy(mobileBlocked = true, roamingBlocked = true), true))
        assertEquals("Blocked on Wi-Fi", appDetailNetworkStatus(pkg.copy(wifiBlocked = true, savedRule = UidRuleAggregate()), true))
        assertEquals("Blocked on all networks", appDetailNetworkStatus(pkg.copy(wifiBlocked = true, mobileBlocked = true), true))
        assertEquals("Blocked on roaming", appDetailNetworkStatus(pkg.copy(roamingBlocked = true), true))
        assertEquals("Blocked on Wi-Fi and roaming", appDetailNetworkStatus(pkg.copy(wifiBlocked = true, roamingBlocked = true), true))
    }

    @Test fun permissionMetadataAndRetainedStateTakePrecedenceOverRules() {
        assertEquals("No internet permission", appDetailNetworkStatus(pkg.copy(networkPermissions = emptyList(), wifiBlocked = true), true))
        assertEquals("Internet permission not verified", appDetailNetworkStatus(pkg.copy(permissionsKnown = false), true))
        assertEquals("Uninstalled for this profile", appDetailNetworkStatus(pkg, false))
        assertEquals("Uninstalled for this profile", appDetailNetworkStatus(null, false))
        assertEquals("Network status unavailable", appDetailNetworkStatus(null, true))
    }

    @Test fun limitedBackendKeepsBackgroundDataWording() {
        assertEquals("Background data allowed", appDetailNetworkStatus(pkg, true, limited = true))
        assertEquals("Background data restricted", appDetailNetworkStatus(pkg.copy(mobileBlocked = true), true, limited = true))
    }

    @Test fun localAndScreenOffBlocksDoNotReadAsFullyAllowed() {
        assertEquals("Local network blocked", appDetailNetworkStatus(pkg.copy(lanBlocked = true), true))
        assertEquals("Screen-off access blocked", appDetailNetworkStatus(pkg.copy(backgroundBlocked = true), true))
        assertEquals("Local network and screen-off access blocked", appDetailNetworkStatus(pkg.copy(lanBlocked = true, backgroundBlocked = true), true))
    }
}
