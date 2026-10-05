package dev.bgcheck.app.ui

import dev.bgcheck.app.data.AppEntry
import dev.bgcheck.app.data.PackageType
import dev.bgcheck.network.domain.model.Package
import dev.bgcheck.network.domain.model.PackageCriticality
import dev.bgcheck.network.domain.model.PackageId
import dev.bgcheck.network.domain.model.PackageIdentity
import dev.bgcheck.network.utils.Constants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal enum class AppsStateFilter(val label: String) {
    ANY("Any state"), ENABLED("Enabled"), DISABLED("Disabled")
}

internal enum class AppsProtectionFilter(val label: String, val level: PackageCriticality? = null) {
    ANY("Any"), BLOATWARE("Bloatware", PackageCriticality.BLOATWARE),
    OPTIONAL("Optional", PackageCriticality.OPTIONAL), IMPORTANT("Important", PackageCriticality.IMPORTANT),
    ESSENTIAL("Essential", PackageCriticality.ESSENTIAL)
}

internal fun matchesAppsState(app: AppEntry, type: PackageType, filter: AppsStateFilter): Boolean =
    type == PackageType.UNINSTALLED || when (filter) {
        AppsStateFilter.ANY -> true
        AppsStateFilter.ENABLED -> app.installed && app.enabled
        AppsStateFilter.DISABLED -> app.installed && !app.enabled
    }

internal fun matchesAppsProtection(level: PackageCriticality?, filter: AppsProtectionFilter): Boolean =
    filter.level == null || filter.level == level

internal fun appsBatchAction(type: PackageType): String =
    if (type == PackageType.UNINSTALLED) "Reinstall" else "Uninstall"

internal fun appsSelectionRefusal(app: AppEntry, level: PackageCriticality?, allowCritical: Boolean): String? = when {
    Constants.App.isOwnApp(app.packageName) -> "Background Check cannot manage its own installation."
    !app.profile.available -> app.profile.unavailableReason ?: "This profile is paused, locked or unavailable."
    app.uid < 0 || app.profile.userId != app.userId || app.profile.serialNumber == null || app.profile.serialNumber < 0 ->
        "Refresh the app list to verify this installation and profile."
    !app.system -> null
    level == PackageCriticality.BLOATWARE || level == PackageCriticality.OPTIONAL -> null
    level == PackageCriticality.ESSENTIAL || level == PackageCriticality.IMPORTANT ->
        if (allowCritical) null else "${level.name.lowercase().replaceFirstChar { it.titlecase() }} packages are protected. Enable critical package uninstall in Firewall settings to select them."
    else -> "This system package has no verified protection level. Only Optional or Bloatware system packages can be selected."
}

internal data class AppsSelection(val id: PackageId, val identity: PackageIdentity) {
    fun encode(): String = "${id.packageName}|${id.userId}|${identity.uid}|${identity.userSerial}"

    companion object {
        fun from(app: AppEntry) = AppsSelection(PackageId(app.packageName, app.userId), PackageIdentity(app.uid, app.profile.serialNumber))
        fun decode(raw: String): AppsSelection? {
            val parts = raw.split('|')
            if (parts.size != 4 || parts[0].isBlank()) return null
            val user = parts[1].toIntOrNull() ?: return null
            val uid = parts[2].toIntOrNull() ?: return null
            val serial = parts[3].toLongOrNull() ?: return null
            if (uid < 0 || uid / 100000 != user || serial < 0) return null
            return AppsSelection(PackageId(parts[0], user), PackageIdentity(uid, serial))
        }
    }
}

internal fun toggleAppsSelection(selected: List<AppsSelection>, app: AppEntry): List<AppsSelection> {
    val entry = AppsSelection.from(app)
    return when {
        selected.any { it.id == entry.id } -> selected.filterNot { it.id == entry.id }
        selected.size >= Constants.Packages.MultiSelect.MAX_SELECTION_COUNT -> selected
        else -> selected + entry
    }
}

/** Apps also includes this app and retained user apps, which the old Packages list omits. */
internal fun appsVisiblePackages(visible: List<AppEntry>, protection: Map<String, PackageCriticality>): List<Package> =
    visible.filter { it.profile.available && it.uid >= 0 && it.profile.userId == it.userId &&
        it.profile.serialNumber != null && it.profile.serialNumber >= 0 }.map { app ->
        Package(packageName = app.packageName, userId = app.userId, uid = app.uid, name = app.label, icon = "",
            isEnabled = app.enabled, type = if (app.system) dev.bgcheck.network.domain.model.PackageType.SYSTEM else dev.bgcheck.network.domain.model.PackageType.USER,
            criticality = protection[app.packageName], isWorkProfile = app.profile.isWork,
            isInstalled = app.installed, userSerial = app.profile.serialNumber)
    }

internal fun appsBatchPackages(visible: List<AppEntry>, selection: List<AppsSelection>, type: PackageType,
    protection: Map<String, PackageCriticality>, allowCritical: Boolean): List<Package> {
    val eligible = visible.filter { app ->
        val current = AppsSelection.from(app)
        selection.any { it == current } && app.installed == (type != PackageType.UNINSTALLED) &&
            appsSelectionRefusal(app, protection[app.packageName], allowCritical) == null
    }
    return appsVisiblePackages(eligible, protection)
}

internal fun appsBatchPreview(packages: List<Package>, reinstall: Boolean): String = buildString {
    append(if (reinstall) "Restore these packages for their selected profiles?" else "Uninstall these packages for their selected profiles? This cannot be undone here.")
    packages.groupBy { pkg ->
        if (pkg.type == dev.bgcheck.network.domain.model.PackageType.USER) "User apps"
        else when (pkg.criticality) {
            PackageCriticality.BLOATWARE -> "Bloatware"
            PackageCriticality.OPTIONAL -> "Optional"
            PackageCriticality.IMPORTANT -> "Important"
            PackageCriticality.ESSENTIAL -> "Essential"
            else -> "System apps"
        }
    }.forEach { (category, rows) ->
        append("\n\n$category (${rows.size})")
        rows.take(3).forEach { append("\n• ${it.name} (user ${it.userId})") }
        if (rows.size > 3) append("\nAnd ${rows.size - 3} more")
    }
    if (!reinstall && packages.any { it.type == dev.bgcheck.network.domain.model.PackageType.SYSTEM &&
        it.criticality in setOf(PackageCriticality.ESSENTIAL, PackageCriticality.IMPORTANT) }) {
        append("\n\nRemoving protected system packages can stop device features from working.")
    }
}

internal fun appsExportFileName(date: Date = Date(), zone: TimeZone = TimeZone.getDefault()): String =
    "packages-${SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = zone }.format(date)}.txt"
