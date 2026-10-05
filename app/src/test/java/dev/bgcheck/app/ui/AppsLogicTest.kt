package dev.bgcheck.app.ui

import dev.bgcheck.app.data.AndroidProfile
import dev.bgcheck.app.data.AppEntry
import dev.bgcheck.app.data.PackageType
import dev.bgcheck.network.domain.model.Package
import dev.bgcheck.network.domain.model.PackageCriticality
import dev.bgcheck.network.utils.Constants
import org.junit.Assert.*
import org.junit.Test
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class AppsLogicTest {
    private fun app(index: Int = 0, user: Int = 0, system: Boolean = false) = AppEntry(
        "app.$index", "App $index", user * 100000 + 10000 + index, system,
        profile = AndroidProfile(user, serialNumber = user.toLong() + 1))

    private fun pkg(app: AppEntry, level: PackageCriticality? = null) = Package(app.packageName, app.userId,
        app.uid, app.label, "", app.enabled,
        if (app.system) dev.bgcheck.network.domain.model.PackageType.SYSTEM else dev.bgcheck.network.domain.model.PackageType.USER,
        criticality = level, isInstalled = app.installed, userSerial = app.profile.serialNumber)

    @Test fun stateFiltersUseEnabledStateAndIgnoreItForUninstalledType() {
        val enabled = app()
        val disabled = enabled.copy(enabled = false)
        val retained = enabled.copy(installed = false)
        assertTrue(matchesAppsState(disabled, PackageType.USER, AppsStateFilter.ANY))
        assertTrue(matchesAppsState(enabled, PackageType.ALL, AppsStateFilter.ENABLED))
        assertFalse(matchesAppsState(disabled, PackageType.ALL, AppsStateFilter.ENABLED))
        assertTrue(matchesAppsState(disabled, PackageType.SYSTEM, AppsStateFilter.DISABLED))
        assertFalse(matchesAppsState(enabled, PackageType.SYSTEM, AppsStateFilter.DISABLED))
        assertFalse(matchesAppsState(retained, PackageType.ALL, AppsStateFilter.ENABLED))
        assertFalse(matchesAppsState(retained.copy(enabled = false), PackageType.ALL, AppsStateFilter.DISABLED))
        AppsStateFilter.entries.forEach { assertTrue(matchesAppsState(retained, PackageType.UNINSTALLED, it)) }
    }

    @Test fun protectionFiltersUseSafetyDataIncludingUserBloatwareAndUnknownLevels() {
        assertTrue(matchesAppsProtection(null, AppsProtectionFilter.ANY))
        assertTrue(matchesAppsProtection(PackageCriticality.UNKNOWN, AppsProtectionFilter.ANY))
        AppsProtectionFilter.entries.filter { it != AppsProtectionFilter.ANY }.forEach { filter ->
            PackageCriticality.entries.forEach { level -> assertEquals(level == filter.level, matchesAppsProtection(level, filter)) }
            assertFalse(matchesAppsProtection(null, filter))
        }
        assertTrue(matchesAppsProtection(pkg(app(), PackageCriticality.BLOATWARE).criticality, AppsProtectionFilter.BLOATWARE))
    }

    @Test fun userAppsAreSelectableRegardlessOfSafetyIncludingDisabledAndRetained() {
        listOf(null, *PackageCriticality.entries.toTypedArray()).forEach { level ->
            assertNull(appsSelectionRefusal(app(), level, false))
            assertNull(appsSelectionRefusal(app().copy(enabled = false), level, false))
            assertNull(appsSelectionRefusal(app().copy(installed = false), level, false))
        }
    }

    @Test fun systemPackagesRequireKnownSafeLevelRegardlessOfCriticalOverride() {
        val system = app(system = true)
        listOf(PackageCriticality.OPTIONAL, PackageCriticality.BLOATWARE).forEach {
            assertNull(appsSelectionRefusal(system, it, false))
            assertNull(appsSelectionRefusal(system.copy(installed = false), it, false))
        }
        listOf(PackageCriticality.ESSENTIAL, PackageCriticality.IMPORTANT).forEach {
            listOf(false, true).forEach { override ->
                listOf(system, system.copy(installed = false), system.copy(enabled = false)).forEach { app ->
                    val refusal = appsSelectionRefusal(app, it, override)!!
                    assertTrue(refusal.contains("protected from batch actions"))
                    assertTrue(refusal.contains("app's details"))
                    assertTrue(refusal.contains("confirmation"))
                    if (it == PackageCriticality.ESSENTIAL) assertTrue(refusal.contains("typed confirmation"))
                }
            }
        }
        assertNotNull(appsSelectionRefusal(system, null, true))
        assertNotNull(appsSelectionRefusal(system, PackageCriticality.UNKNOWN, true))
    }

    @Test fun unavailableOrUnverifiableInstallationsCannotBeSelected() {
        assertNotNull(appsSelectionRefusal(app().copy(packageName = "dev.bgcheck.app"), null, true))
        assertNotNull(appsSelectionRefusal(app().copy(profile = AndroidProfile(0, available = false)), null, true))
        assertNotNull(appsSelectionRefusal(app().copy(profile = AndroidProfile(0)), null, true))
        assertNotNull(appsSelectionRefusal(app().copy(uid = -1), null, true))
        assertNotNull(appsSelectionRefusal(app().copy(profile = AndroidProfile(10, serialNumber = 1)), null, true))
    }

    @Test fun batchActionChangesOnlyForUninstalledType() {
        PackageType.entries.forEach { assertEquals(if (it == PackageType.UNINSTALLED) "Reinstall" else "Uninstall", appsBatchAction(it)) }
    }

    @Test fun selectionHonoursLimitAndCanDeselectAtLimit() {
        val selected = (0 until Constants.Packages.MultiSelect.MAX_SELECTION_COUNT).map { AppsSelection.from(app(it)) }
        assertEquals(selected, toggleAppsSelection(selected, app(51)))
        assertEquals(49, toggleAppsSelection(selected, app(0)).size)
        assertEquals(listOf(AppsSelection.from(app())), toggleAppsSelection(emptyList(), app()))
    }

    @Test fun selectionRestorationRequiresUidAndProfileSerial() {
        val selected = AppsSelection.from(app(user = 10))
        assertEquals(selected, AppsSelection.decode(selected.encode()))
        listOf("broken", "app|10|10000|11", "app|0|-1|1", "app|0|10000|null", "app|0|10000|-1").forEach {
            assertNull(AppsSelection.decode(it))
        }
    }

    @Test fun batchUsesOnlyVisibleSelectedInstallationsWithMatchingIdentity() {
        val personal = app()
        val work = app(user = 10)
        val selection = listOf(personal, work).map(AppsSelection::from)
        fun batch(rows: List<AppEntry>) = appsBatchPackages(rows, selection, PackageType.ALL, emptyMap(), false)
        assertEquals(listOf(pkg(work)), batch(listOf(work)))
        assertTrue(batch(listOf(work.copy(uid = work.uid + 1))).isEmpty())
        assertTrue(batch(listOf(work.copy(profile = work.profile.copy(serialNumber = 99)))).isEmpty())
        assertTrue(batch(listOf(work.copy(installed = false))).isEmpty())
        assertTrue(batch(listOf(work.copy(profile = work.profile.copy(available = false)))).isEmpty())
    }

    @Test fun visibleExportIncludesOwnAppAndRetainedUserAppsInTheirProfiles() {
        val own = app().copy(packageName = "dev.bgcheck.app")
        val retained = app(1, user = 10).copy(installed = false, enabled = false)
        val packages = appsVisiblePackages(listOf(own, retained), mapOf(retained.packageName to PackageCriticality.BLOATWARE))
        assertEquals(listOf(pkg(own), pkg(retained, PackageCriticality.BLOATWARE)), packages)
        assertEquals(listOf(pkg(retained, PackageCriticality.BLOATWARE)), appsBatchPackages(listOf(retained),
            listOf(AppsSelection.from(retained)), PackageType.UNINSTALLED,
            mapOf(retained.packageName to PackageCriticality.BLOATWARE), false))
    }

    @Test fun batchRejectsProtectedSystemPackagesEvenWithCriticalOverride() {
        val system = app(system = true)
        val selection = listOf(AppsSelection.from(system))
        listOf(PackageCriticality.ESSENTIAL, PackageCriticality.IMPORTANT).forEach { level ->
            listOf(false, true).forEach { override ->
                assertTrue(appsBatchPackages(listOf(system), selection, PackageType.SYSTEM,
                    mapOf(system.packageName to level), override).isEmpty())
            }
        }
    }

    @Test fun restoredBatchIsInterruptedOnlyWithoutLiveOperationOrOutcome() {
        assertTrue(appsBatchWasInterrupted(true, false, false, null))
        assertFalse(appsBatchWasInterrupted(false, false, false, null))
        assertFalse(appsBatchWasInterrupted(true, true, false, null))
        assertFalse(appsBatchWasInterrupted(true, false, true, null))
        assertFalse(appsBatchWasInterrupted(true, false, false, "Access lost"))
        assertFalse(dev.bgcheck.network.presentation.viewmodel.PackagesUiState().isBatchRunning)
        val restored = dev.bgcheck.network.presentation.viewmodel.PackagesUiState(error = "Package scan failed")
        assertTrue(appsBatchWasInterrupted(true, restored.isBatchRunning,
            restored.batchUninstallResult != null || restored.batchReinstallResult != null, restored.batchOperationError))
    }

    @Test fun uninstallPreviewGroupsNamesWithoutCountingUserBloatwareTwice() {
        val packages = (0..4).map { pkg(app(it), PackageCriticality.BLOATWARE) } +
            listOf(pkg(app(6, system = true), PackageCriticality.OPTIONAL),
                pkg(app(7, system = true), PackageCriticality.ESSENTIAL))
        val preview = appsBatchPreview(packages, false)
        assertTrue(preview.contains("User apps (5)"))
        assertTrue(preview.contains("App 0 (user 0)"))
        assertTrue(preview.contains("And 2 more"))
        assertFalse(preview.contains("App 4"))
        assertFalse(preview.contains("Bloatware (5)"))
        assertTrue(preview.contains("Optional (1)"))
        assertTrue(preview.contains("Essential (1)"))
        assertTrue(preview.contains("stop device features"))
        assertTrue(preview.contains("cannot be undone"))
    }

    @Test fun reinstallPreviewNamesProfilesAndDoesNotWarnAboutRemovingApps() {
        val preview = appsBatchPreview(listOf(pkg(app(user = 10, system = true).copy(installed = false), PackageCriticality.OPTIONAL)), true)
        assertTrue(preview.contains("Restore these packages"))
        assertTrue(preview.contains("App 0 (user 10)"))
        assertFalse(preview.contains("cannot be undone"))
    }

    @Test fun exportNameUsesCalendarDateAndPlainTextExtensionAcrossLocales() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar"))
            assertEquals("packages-2026-10-05.txt", appsExportFileName(Date(1791158400000L), TimeZone.getTimeZone("UTC")))
            assertEquals("packages-2026-10-04.txt", appsExportFileName(Date(1791158400000L), TimeZone.getTimeZone("America/Chicago")))
            assertEquals("packages-1970-01-01.txt", appsExportFileName(Date(0), TimeZone.getTimeZone("UTC")))
        } finally { Locale.setDefault(original) }
    }
}
