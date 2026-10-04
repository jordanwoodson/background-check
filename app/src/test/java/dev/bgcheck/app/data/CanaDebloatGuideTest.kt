package dev.bgcheck.app.data

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CanaDebloatGuideTest {
    @Test fun matchesCanasFourRemovalLevelsUsingExactPackageIds() {
        mapOf("com.motorola.help" to DebloatRating.RECOMMENDED,
            "com.android.chrome" to DebloatRating.ADVANCED,
            "com.google.android.gms" to DebloatRating.EXPERT,
            "com.android.systemui" to DebloatRating.UNSAFE).forEach { (id, rating) ->
            assertEquals(id, rating, CanaDebloatGuide.lookup(id).rating)
            assertEquals(DebloatRating.NOT_RATED, CanaDebloatGuide.lookup("$id.fake").rating)
        }
    }

    @Test fun preservesWarningsAndActualReplacementRestrictions() {
        val chrome = CanaDebloatGuide.lookup("com.android.chrome")
        assertTrue(chrome.warning.orEmpty().contains("passkeys"))
        assertEquals(listOf("Vanadium", "Cromite"), chrome.alternatives.map { it.label })
        assertEquals("GrapheneOS only", chrome.alternatives.first().note)
        assertTrue(chrome.references.contains("https://fidoalliance.org/passkeys/"))
        assertTrue(CanaDebloatGuide.lookup("com.google.android.gms").warning.orEmpty().contains("bootloops"))
    }

    @Test fun sourceRatingDoesNotChangeWithInstallationStateOrAppCategory() {
        val app = AppEntry("com.android.chrome", "Chrome", 10200, true)
        listOf(app, app.copy(system = false), app.copy(installed = false), app.copy(enabled = false)).forEach {
            assertEquals(DebloatRating.ADVANCED, it.debloatRecommendation.rating)
        }
        assertEquals(DebloatRating.NOT_RATED, AppEntry("com.openai.chatgpt", "ChatGPT", 10210, false).debloatRecommendation.rating)
    }

    @Test fun keepsListDeclaredDependenciesSeparateFromLiveAssociations() {
        val overlay = CanaDebloatGuide.lookup("com.android.cellbroadcastreceiver.basiccolorblack.overlay")
        assertEquals(listOf("com.android.cellbroadcastreceiver"), overlay.dependencies)
    }

    @Test fun everyAuditedPhonePackageGetsEitherItsSourceRatingOrAnExplicitUnratedEntry() {
        val ids = requireNotNull(javaClass.getResourceAsStream("/usb-package-ids.txt"))
            .bufferedReader().use { it.readLines() }
        assertEquals(676, ids.size)
        val ratings = ids.map { CanaDebloatGuide.lookup(it).rating }
        assertEquals(392, ratings.count { it != DebloatRating.NOT_RATED })
        assertEquals(284, ratings.count { it == DebloatRating.NOT_RATED })
    }

    @Test fun bundledArchiveContainsAll4801SourceRecords() {
        val entries = requireNotNull(javaClass.getResourceAsStream("/cana-debloat-list.zip")).use(::parseCanaDebloatArchive)
        assertEquals(4801, entries.size)
        assertEquals(mapOf(DebloatRating.RECOMMENDED to 2732, DebloatRating.ADVANCED to 913,
            DebloatRating.EXPERT to 888, DebloatRating.UNSAFE to 268), entries.values.groupingBy { it.rating }.eachCount())
        assertTrue(entries.values.all { it.description != null && it.category != null })
    }

    @Test fun malformedOrIncompleteSourceIsRejectedInsteadOfInventingRatings() {
        fun archive(files: Map<String, String>): ByteArray = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip -> files.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry("source/$name")); zip.write(text.toByteArray()); zip.closeEntry()
            } }
        }.toByteArray()
        val categories = listOf("aosp", "carrier", "google", "misc", "oem").associate { "$it.json" to "[]" }
        listOf(mapOf("aosp.json" to "[]"),
            categories + ("aosp.json" to """[{"id":"example.app","description":"test","removal":"maybe"}]"""),
            categories + ("aosp.json" to """[{"id":"example.app","description":"test","removal":"replace","suggestions":"missing"}]"""),
        ).forEach { files ->
            assertThrows(Exception::class.java) { parseCanaDebloatArchive(archive(files).inputStream()) }
        }
    }
}
