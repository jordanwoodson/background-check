package dev.bgcheck.app.data

import org.json.JSONObject

data class DescriptionCitation(val title: String, val url: String)

enum class AssociationKind(val label: String, val explanation: String) {
    SHARED_UID("Shared Android identity", "Android assigns these installed packages the same UID. App-level rules can affect the group."),
    OVERLAY_TARGET("Customizes this package", "The overlay manifest names this resource target. This describes configuration, not a running app."),
    OVERLAY_SOURCE("Resource customizations", "These overlays provide resource values for this package. Their enabled state is managed by Android."),
    STATIC_LIBRARY("Required shared library", "The app manifest declares this shared code library."),
    LIBRARY_CLIENT("Uses this shared library", "These app manifests declare this shared code library."),
    PACKAGE_QUERY("Declared package queries", "The manifest allows discovery of these packages. A query does not establish a dependency or current communication."),
    QUERIED_BY("Queried by these packages", "These manifests allow discovery of this package. A query does not establish a dependency or current communication."),
    COMPANION("Related features", "Publisher documentation identifies companion apps or a shared feature. This is separate from a shared UID."),
}

data class PackageAssociation(val packageName: String, val kind: AssociationKind)

internal data class AuditedPackage(
    val label: String,
    val description: String,
    val sources: List<DescriptionCitation>,
    val associations: List<PackageAssociation>,
    val note: String?,
)

internal object AuditedPackageGuide {
    // Java resources are available both in the APK and the JVM test classpath.
    val packages: Map<String, AuditedPackage> by lazy {
        val text = requireNotNull(javaClass.getResourceAsStream("/package-guide.json")) {
            "The bundled package guide is missing"
        }.bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        require(root.getInt("schemaVersion") == 1)
        val records = root.getJSONObject("packages")
        buildMap {
            for (id in records.keys()) {
                val record = records.getJSONObject(id)
                val sources = record.getJSONArray("sources")
                val associations = record.getJSONArray("associations")
                put(id, AuditedPackage(
                    label = record.getString("label"),
                    description = record.getString("description"),
                    sources = List(sources.length()) { index -> sources.getJSONObject(index).let {
                        DescriptionCitation(it.getString("title"), it.getString("url"))
                    } },
                    associations = List(associations.length()) { index -> associations.getJSONObject(index).let {
                        PackageAssociation(it.getString("packageName"), AssociationKind.valueOf(it.getString("kind")))
                    } },
                    note = record.optString("note").takeIf { it.isNotBlank() },
                ))
            }
        }
    }
}

object PackageAssociations {
    /** Reverse declared links, then add current installed UID peers. Removed UIDs are never live peers. */
    fun enrich(apps: List<AppEntry>): List<AppEntry> {
        val byPackage = apps.associateBy { it.key }
        val links = apps.associate { it.key to it.associations.toMutableSet() }
        apps.forEach { app ->
            app.associations.forEach { association ->
                val reverse = when (association.kind) {
                    AssociationKind.OVERLAY_TARGET -> AssociationKind.OVERLAY_SOURCE
                    AssociationKind.STATIC_LIBRARY -> AssociationKind.LIBRARY_CLIENT
                    AssociationKind.PACKAGE_QUERY -> AssociationKind.QUERIED_BY
                    AssociationKind.COMPANION -> AssociationKind.COMPANION
                    else -> null
                }
                if (reverse != null) links["${app.userId}:${association.packageName}"]?.add(PackageAssociation(app.packageName, reverse))
            }
        }
        apps.filter { it.installed && it.uid >= 0 }.groupBy { it.uid }.values.forEach { peers ->
            if (peers.size > 1) peers.forEach { app ->
                peers.filter { it.packageName != app.packageName }.forEach {
                    links.getValue(app.key).add(PackageAssociation(it.packageName, AssociationKind.SHARED_UID))
                }
            }
        }
        return apps.map { app ->
            app.copy(associations = links.getValue(app.key)
                .filter { it.packageName != app.packageName }
                .sortedWith(compareBy({ it.kind.ordinal }, { byPackage["${app.userId}:${it.packageName}"]?.label ?: it.packageName })))
        }
    }
}
