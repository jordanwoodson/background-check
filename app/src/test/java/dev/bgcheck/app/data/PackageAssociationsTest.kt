package dev.bgcheck.app.data

import org.junit.Assert.*
import org.junit.Test

class PackageAssociationsTest {
    @Test fun samePackageCopiesKeepTheirOwnReverseLinks() {
        val result = PackageAssociations.enrich(listOf(
            app("dev.target", uid = 10001), app("dev.target", uid = 1010001),
            app("dev.personal", uid = 10002, links = arrayOf(PackageAssociation("dev.target", AssociationKind.PACKAGE_QUERY))),
            app("dev.work", uid = 1010002, links = arrayOf(PackageAssociation("dev.target", AssociationKind.PACKAGE_QUERY))),
        ))
        assertEquals(listOf(PackageAssociation("dev.personal", AssociationKind.QUERIED_BY)), result[0].associations)
        assertEquals(listOf(PackageAssociation("dev.work", AssociationKind.QUERIED_BY)), result[1].associations)
    }

    @Test fun equalPackageSortsHaveStableProfileTieBreaker() {
        val personal = app("dev.example", uid = 10001)
        val work = app("dev.example", uid = 1010001)
        assertEquals(listOf(10001, 1010001), ListSorting.apps(listOf(work, personal), AppSort.NAME, SortDirection.ASCENDING).map { it.uid })
    }

    private fun app(id: String, uid: Int = 12345, installed: Boolean = true, vararg links: PackageAssociation) =
        AppEntry(id, id, uid, false, installed = installed, associations = links.toList())

    @Test fun sharedIdentityOnlyIncludesCurrentlyInstalledPeers() {
        val entries = PackageAssociations.enrich(listOf(
            app("dev.one"), app("dev.two"), app("dev.removed", installed = false), app("dev.other", uid = 22222)))
        assertEquals(listOf(PackageAssociation("dev.two", AssociationKind.SHARED_UID)), entries[0].associations)
        assertEquals(listOf(PackageAssociation("dev.one", AssociationKind.SHARED_UID)), entries[1].associations)
        assertTrue(entries[2].associations.isEmpty())
        assertTrue(entries[3].associations.isEmpty())
    }

    @Test fun reverseOverlayRelationshipsIncludeRetainedRecordsWithoutCreatingUidPeers() {
        val target = app("dev.target", uid = 10001)
        val overlay = app("dev.overlay", uid = 10002, installed = false,
            links = arrayOf(PackageAssociation("dev.target", AssociationKind.OVERLAY_TARGET)))
        val result = PackageAssociations.enrich(listOf(target, overlay))
        assertEquals(listOf(PackageAssociation("dev.overlay", AssociationKind.OVERLAY_SOURCE)), result[0].associations)
        assertEquals(AssociationKind.OVERLAY_TARGET, result[1].associations.single().kind)
    }

    @Test fun queriesAndStaticLibrariesHaveDifferentReverseRelationshipTypes() {
        val reader = app("dev.reader", uid = 11111, links = arrayOf(
            PackageAssociation("dev.library", AssociationKind.STATIC_LIBRARY),
            PackageAssociation("dev.visible", AssociationKind.PACKAGE_QUERY),
            PackageAssociation("dev.absent", AssociationKind.PACKAGE_QUERY)))
        val entries = PackageAssociations.enrich(listOf(reader, app("dev.library", uid = 11112), app("dev.visible", uid = 11113)))
        assertEquals(listOf(PackageAssociation("dev.reader", AssociationKind.LIBRARY_CLIENT)), entries[1].associations)
        assertEquals(listOf(PackageAssociation("dev.reader", AssociationKind.QUERIED_BY)), entries[2].associations)
        assertTrue(entries[0].associations.any { it.packageName == "dev.absent" })
        assertFalse(entries[0].associations.any { it.kind == AssociationKind.SHARED_UID })
    }

    @Test fun curatedOverlayHasItsExactTargetLink() {
        assertTrue(PackageDescriptions.associations("com.android.cellbroadcast.overlay")
            .contains(PackageAssociation("com.google.android.cellbroadcastreceiver", AssociationKind.OVERLAY_TARGET)))
        assertTrue(PackageDescriptions.associations("com.android.cellbroadcast.overlay.fake").isEmpty())
    }

    @Test fun retainedAddonKeepsItsDocumentedHostsWithoutLiveUidPeers() {
        val addon = app("dev.addon", uid = 11111, installed = false, links = arrayOf(
            PackageAssociation("dev.host", AssociationKind.COMPANION),
            PackageAssociation("dev.other", AssociationKind.COMPANION)))
        val hosts = listOf(app("dev.host", uid = 11111), app("dev.other", uid = 22222))
        val result = PackageAssociations.enrich(hosts + addon)
        val removed = result.last()
        assertTrue(removed.associations.contains(PackageAssociation("dev.host", AssociationKind.COMPANION)))
        assertTrue(removed.associations.contains(PackageAssociation("dev.other", AssociationKind.COMPANION)))
        assertFalse(removed.associations.any { it.kind == AssociationKind.SHARED_UID })
        assertTrue(result[1].associations.contains(PackageAssociation("dev.addon", AssociationKind.COMPANION)))
    }
}
