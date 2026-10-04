package dev.bgcheck.app.network

import dev.bgcheck.network.domain.model.PackageListCodec
import dev.bgcheck.network.domain.model.PackageListEntry
import org.junit.Assert.*
import org.junit.Test

class PackageListCodecTest {
    private val personal = PackageListEntry("dev.fixture.shared", 0, 0)
    private val work = PackageListEntry("dev.fixture.shared", 10, 41)
    private val serials = mapOf(0 to 0L, 10 to 41L)

    @Test fun workExportImportsIntoWorkEvenWhenPersonalHasTheSamePackage() {
        val file = PackageListCodec.encode(listOf(work))
        val targets = PackageListCodec.resolve(PackageListCodec.decode(file), serials)
        assertEquals(listOf(work), targets)
        assertEquals(10, targets.single().userId)
    }

    @Test fun mixedProfileRoundTripKeepsBothCopiesAndOnlyDeduplicatesTheSameIdentity() {
        val entries = listOf(personal, work, work)
        assertEquals(listOf(personal, work), PackageListCodec.resolve(PackageListCodec.decode(PackageListCodec.encode(entries)), serials))
    }

    @Test fun recreatedOrUnavailableProfileRejectsTheEntireImport() {
        val file = PackageListCodec.decode(PackageListCodec.encode(listOf(personal, work)))
        rejects { PackageListCodec.resolve(file, mapOf(0 to 0L, 10 to 42L)) }
        rejects { PackageListCodec.resolve(file, mapOf(0 to 0L)) }
    }

    @Test fun legacyNamesRequireExplicitProfileSelectionAndBindItsSerial() {
        val file = PackageListCodec.decode("# Old package list\ndev.fixture.shared\ndev.fixture.other\n")
        assertTrue(file.requiresProfileSelection)
        rejects { PackageListCodec.resolve(file, serials) }
        assertEquals(listOf(work, PackageListEntry("dev.fixture.other", 10, 41)),
            PackageListCodec.resolve(file, serials, legacyTargetUserId = 10))
        rejects { PackageListCodec.resolve(file, serials, legacyTargetUserId = 11) }
    }

    @Test fun savedProfileIdentityCannotBeOverriddenByLegacySelection() {
        val file = PackageListCodec.decode(PackageListCodec.encode(listOf(work)))
        assertFalse(file.requiresProfileSelection)
        assertEquals(listOf(work), PackageListCodec.resolve(file, serials, legacyTargetUserId = 0))
    }

    @Test fun malformedVersionedRowsCannotFallBackToNameOnlyImport() {
        val header = PackageListCodec.HEADER
        listOf("$header\n10\tdev.fixture.shared", "$header\n-1\t41\tdev.fixture.shared",
            "$header\n10\t-1\tdev.fixture.shared", "$header\n10\t41\tdev.fixture;injected",
            "# Background Check package list v99\ndev.fixture.shared").forEach { content -> rejects { PackageListCodec.decode(content) } }
    }

    @Test fun conflictingSerialsForOneUserCannotBeExportedOrImported() {
        rejects { PackageListCodec.encode(listOf(work, work.copy(userSerial = 42))) }
        rejects { PackageListCodec.decode("${PackageListCodec.HEADER}\n10\t41\tdev.fixture.shared\n10\t42\tdev.fixture.other") }
    }

    private fun rejects(block: () -> Unit) {
        try { block(); fail("Unverified package-list identity accepted") }
        catch (_: IllegalArgumentException) { }
    }
}
