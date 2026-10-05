package dev.bgcheck.app.ui

import dev.bgcheck.network.data.common.RootStatus
import dev.bgcheck.network.data.common.ShizukuStatus
import dev.bgcheck.network.domain.model.PackageCriticality
import dev.bgcheck.network.domain.model.PackageType
import dev.bgcheck.network.domain.model.Package
import dev.bgcheck.app.data.AndroidProfile
import dev.bgcheck.app.data.AppEntry
import org.junit.Assert.*
import org.junit.Test

class PackageActionsLogicTest {
    @Test fun essentialAndImportantRemovalRequireOverrideIncludingUserPackages() {
        listOf(PackageType.USER, PackageType.SYSTEM).forEach { type ->
            listOf(PackageCriticality.ESSENTIAL, PackageCriticality.IMPORTANT).forEach { level ->
                val protected = packageActionPolicy(true, true, type, level, false)
                assertEquals(setOf(PackageAction.DISABLE), protected.actions)
                val allowed = packageActionPolicy(true, true, type, level, true)
                assertTrue(PackageAction.UNINSTALL in allowed.actions)
                assertEquals(if (level == PackageCriticality.ESSENTIAL) PackageConfirmation.UNINSTALL_ESSENTIAL
                    else PackageConfirmation.UNINSTALL_IMPORTANT, allowed.uninstallConfirmation)
            }
        }
    }

    @Test fun optionalBloatwareAndUnclassifiedPackagesHaveTailoredConfirmations() {
        mapOf(PackageCriticality.OPTIONAL to PackageConfirmation.UNINSTALL_OPTIONAL,
            PackageCriticality.BLOATWARE to PackageConfirmation.UNINSTALL_BLOATWARE,
            PackageCriticality.UNKNOWN to PackageConfirmation.UNINSTALL_UNKNOWN_SYSTEM,
            null to PackageConfirmation.UNINSTALL_UNKNOWN_SYSTEM).forEach { (level, expected) ->
            val policy = packageActionPolicy(true, false, PackageType.SYSTEM, level, false)
            assertEquals(setOf(PackageAction.ENABLE, PackageAction.UNINSTALL), policy.actions)
            assertEquals(expected, policy.uninstallConfirmation)
        }
        assertEquals(PackageConfirmation.UNINSTALL_UNKNOWN_USER,
            packageActionPolicy(true, true, PackageType.USER, null, false).uninstallConfirmation)
    }

    @Test fun retainedPackagesOnlyOfferReinstallRegardlessOfProtection() {
        PackageCriticality.entries.forEach { level ->
            val policy = packageActionPolicy(false, false, PackageType.SYSTEM, level, false)
            assertEquals(setOf(PackageAction.REINSTALL), policy.actions)
            assertNull(policy.uninstallConfirmation)
            assertEquals(PackageConfirmation.REINSTALL, packageConfirmation(PackageAction.REINSTALL, policy, PackageType.SYSTEM))
        }
    }

    @Test fun systemAndUserDisableWarningsDifferAndEnableIsSeparate() {
        val policy = packageActionPolicy(true, true, PackageType.SYSTEM, null, false)
        assertEquals(PackageConfirmation.DISABLE_SYSTEM, packageConfirmation(PackageAction.DISABLE, policy, PackageType.SYSTEM))
        assertEquals(PackageConfirmation.DISABLE_USER, packageConfirmation(PackageAction.DISABLE, policy, PackageType.USER))
        assertEquals(PackageConfirmation.ENABLE, packageConfirmation(PackageAction.ENABLE, policy, PackageType.SYSTEM))
    }

    @Test fun eitherPrivilegedBackendIsEnoughEvenWhileTheOtherIsChecking() {
        RootStatus.entries.forEach { root -> assertNull(packageActionsDisabledReason(root, ShizukuStatus.RUNNING_WITH_PERMISSION)) }
        ShizukuStatus.entries.forEach { shizuku -> assertNull(packageActionsDisabledReason(RootStatus.ROOTED_WITH_PERMISSION, shizuku)) }
    }

    @Test fun disabledReasonsExplainHowToRestoreAccess() {
        assertEquals("Checking Shizuku and root access for package actions.", packageActionsDisabledReason(RootStatus.CHECKING, ShizukuStatus.NOT_INSTALLED))
        assertEquals("Allow Shizuku permission or grant root access to manage packages.", packageActionsDisabledReason(RootStatus.NOT_ROOTED, ShizukuStatus.RUNNING_NO_PERMISSION))
        assertEquals("Start Shizuku or grant root access to manage packages.", packageActionsDisabledReason(RootStatus.NOT_ROOTED, ShizukuStatus.INSTALLED_NOT_RUNNING))
        assertEquals("Grant root access or connect Shizuku to manage packages.", packageActionsDisabledReason(RootStatus.ROOTED_NO_PERMISSION, ShizukuStatus.NOT_INSTALLED))
        assertEquals("Package actions require Shizuku or root access.", packageActionsDisabledReason(RootStatus.NOT_ROOTED, ShizukuStatus.NOT_INSTALLED))
        assertEquals("Not classified", protectionLevelLabel(null))
        assertEquals("Not classified", protectionLevelLabel(PackageCriticality.UNKNOWN))
    }

    @Test fun removalAndReinstallNeedTheirSuccessEventBeforeReportingSuccess() {
        assertEquals("Test uninstalled", packageActionResultMessage(PackageAction.UNINSTALL, "Test", false, null, "Test uninstalled"))
        assertEquals("Test reinstalled", packageActionResultMessage(PackageAction.REINSTALL, "Test", false, null, "Test reinstalled"))
        listOf(PackageAction.UNINSTALL, PackageAction.REINSTALL).forEach { action ->
            assertTrue(packageActionResultMessage(action, "Test", false, null, null).contains("could not be confirmed"))
        }
        assertEquals("Test enabled for this profile.", packageActionResultMessage(PackageAction.ENABLE, "Test", false, null, null))
        assertEquals("Test disabled for this profile.", packageActionResultMessage(PackageAction.DISABLE, "Test", false, null, null))
    }

    @Test fun interruptedOrFailedActionsNeverGetASuccessMessage() {
        PackageAction.entries.forEach { action ->
            assertEquals("Access lost", packageActionResultMessage(action, "Test", false, "Access lost", null))
            assertTrue(packageActionResultMessage(action, "Test", true, null, "Test uninstalled").contains("interrupted"))
        }
        assertEquals("Test uninstalled. Inventory scan failed", packageActionResultMessage(
            PackageAction.UNINSTALL, "Test", false, "Inventory scan failed", "Test uninstalled"))
    }

    @Test fun progressNamesTheActionAndSelectedApp() {
        assertEquals("Disabling Test…", packageActionProgress(PackageAction.DISABLE, "Test"))
        assertEquals("Reinstalling Test for this profile…", packageActionProgress(PackageAction.REINSTALL, "Test"))
        assertEquals("Force-stopping Test for this profile…", packageActionProgress(PackageAction.FORCE_STOP, "Test"))
    }

    @Test fun rootOnlyUsersAndSystemAppsUsePackageForceStopWhileMainPathStaysAvailable() {
        assertFalse(usePackageForceStop(mainAccessReady = true, system = false))
        assertTrue(usePackageForceStop(mainAccessReady = false, system = false))
        assertTrue(usePackageForceStop(mainAccessReady = false, system = true))
        assertTrue(usePackageForceStop(mainAccessReady = true, system = true))
        val app = AppEntry("app.test", "Test", 1010001, true,
            protectedReason = "Main app controls unavailable", profile = AndroidProfile(10, serialNumber = 11))
        val pkg = Package(app.packageName, app.userId, app.uid, app.label, "", true, PackageType.SYSTEM, userSerial = 11)
        val rootAccess = packageActionsDisabledReason(RootStatus.ROOTED_WITH_PERMISSION, ShizukuStatus.NOT_INSTALLED)
        assertNull(packageForceStopRefusal(app, pkg, false, rootAccess))
        assertNull(packageForceStopRefusal(app.copy(system = false), pkg.copy(type = PackageType.USER), false, rootAccess))
        assertNotNull(packageForceStopRefusal(app, pkg, false,
            packageActionsDisabledReason(RootStatus.NOT_ROOTED, ShizukuStatus.NOT_INSTALLED)))
        assertEquals("Force stop sent for Test in this profile. Opening the app can start it again.",
            packageActionResultMessage(PackageAction.FORCE_STOP, "Test", false, null, null))
    }

    @Test fun packageForceStopRequiresTheExpectedInstallationAndAvailableProfile() {
        val app = AppEntry("app.test", "Test", 1010001, true,
            profile = AndroidProfile(10, serialNumber = 11), firstInstallTime = 123)
        val pkg = Package(app.packageName, app.userId, app.uid, app.label, "", true, PackageType.SYSTEM,
            installTime = 123, userSerial = 11)
        assertNull(packageForceStopRefusal(app, pkg, false, null))
        listOf(null, pkg.copy(uid = pkg.uid + 1), pkg.copy(userSerial = 12), pkg.copy(userSerial = null),
            pkg.copy(userId = 0), pkg.copy(packageName = "other.app"), pkg.copy(isInstalled = false),
            pkg.copy(installTime = 124)).forEach { assertNotNull(packageForceStopRefusal(app, it, false, null)) }
        assertNotNull(packageForceStopRefusal(app, pkg, true, null))
        assertNotNull(packageForceStopRefusal(app.copy(installed = false), pkg, false, null))
        assertNotNull(packageForceStopRefusal(app.copy(profile = app.profile.copy(available = false)), pkg, false, null))
        assertNotNull(packageForceStopRefusal(app.copy(profile = app.profile.copy(serialNumber = null)), pkg, false, null))
        assertNotNull(packageForceStopRefusal(app.copy(profile = app.profile.copy(serialNumber = -1)), pkg.copy(userSerial = -1), false, null))
        assertNotNull(packageForceStopRefusal(app.copy(packageName = "dev.bgcheck.app"), pkg.copy(packageName = "dev.bgcheck.app"), false, null))
    }
}
