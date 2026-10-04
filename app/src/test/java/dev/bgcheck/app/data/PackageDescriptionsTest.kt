package dev.bgcheck.app.data

import org.junit.Assert.*
import org.junit.Test

class PackageDescriptionsTest {
    @Test fun exactPackageGuideWinsOverBroadMetadata() {
        val description = PackageDescriptions.describe("com.android.systemui", "A system app", 7)
        assertEquals(DescriptionSource.GUIDE, description.source)
        assertTrue(description.text.contains("notification", ignoreCase = true))
    }

    @Test fun unfamiliarAppsUseTheirOwnDescriptionWithNormalizedWhitespace() {
        val description = PackageDescriptions.describe("dev.example.notes", "  Write\n notes\tand   lists.  ", 7)
        assertEquals("Write notes and lists.", description.text)
        assertEquals(DescriptionSource.APP, description.source)
    }

    @Test fun blankDescriptionsFallBackToCategoryWithoutGuessingExactFeatures() {
        val description = PackageDescriptions.describe("dev.example.player", " \n ", 1)
        assertEquals(DescriptionSource.CATEGORY, description.source)
        assertTrue(description.text.contains("audio", ignoreCase = true))
    }

    @Test fun similarPackageNamesDoNotInheritAnUnrelatedDescription() {
        listOf("com.android.systemui.fake", "com.google.android.gmsclone", "com.motorola.unknown").forEach {
            assertEquals(DescriptionSource.UNKNOWN, PackageDescriptions.describe(it).source)
        }
        assertEquals(DescriptionSource.UNKNOWN, PackageDescriptions.describe("dev.example", category = 999).source)
    }

    @Test fun descriptionsRemainAvailableForRemovedPackagesAndCanBeSearched() {
        val removed = AppEntry("com.android.systemui", "System UI", 1000, true, installed = false)
        assertTrue(matchesPackage(removed, PackageType.UNINSTALLED, " Notifications "))
        assertTrue(matchesPackage(removed, PackageType.ALL, "systemui"))
        assertFalse(matchesPackage(removed, PackageType.SYSTEM, ""))
        assertFalse(matchesPackage(removed, PackageType.USER, ""))
        assertFalse(matchesPackage(removed, PackageType.UNINSTALLED, "camera"))
    }

    @Test fun disabledInstalledAppsStayInTheirInstalledCategory() {
        val system = AppEntry("com.android.settings", "Settings", 1000, true, enabled = false)
        val user = AppEntry("com.example", "Example", 10123, false)
        assertTrue(matchesPackage(system, PackageType.SYSTEM, "settings"))
        assertFalse(matchesPackage(system, PackageType.UNINSTALLED, ""))
        assertFalse(matchesPackage(user, PackageType.SYSTEM, ""))
        assertTrue(matchesPackage(user, PackageType.USER, "example"))
    }
}
