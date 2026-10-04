package dev.bgcheck.app.data

import org.junit.Assert.*
import org.junit.Test

class PackageAssociationsTest {
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

    @Test fun actualMotoOverlayAndPatchedAppCompanionsHaveExactPackageLinks() {
        assertTrue(PackageDescriptions.associations("com.motorola.setup.overlay.tmo")
            .contains(PackageAssociation("com.motorola.setup", AssociationKind.OVERLAY_TARGET)))
        assertTrue(PackageDescriptions.associations("app.morphe.android.youtube")
            .contains(PackageAssociation("app.revanced.android.gms", AssociationKind.COMPANION)))
        assertTrue(PackageDescriptions.associations("com.motorola.setup.overlay.tmo.fake").isEmpty())
    }

    @Test fun retainedTaskerAddonKeepsItsDocumentedHostsWithoutLiveUidPeers() {
        val addon = AppEntry("com.termux.tasker", "Termux:Tasker", 11111, false, installed = false)
        val hosts = listOf(AppEntry("com.termux", "Termux", 11111, false),
            AppEntry("net.dinglisch.android.taskerm", "Tasker", 22222, false))
        val result = PackageAssociations.enrich(hosts + addon)
        val removed = result.last()
        assertTrue(removed.associations.contains(PackageAssociation("com.termux", AssociationKind.COMPANION)))
        assertTrue(removed.associations.contains(PackageAssociation("net.dinglisch.android.taskerm", AssociationKind.COMPANION)))
        assertFalse(removed.associations.any { it.kind == AssociationKind.SHARED_UID })
        assertTrue(result[1].associations.contains(PackageAssociation("com.termux.tasker", AssociationKind.COMPANION)))
    }
}
