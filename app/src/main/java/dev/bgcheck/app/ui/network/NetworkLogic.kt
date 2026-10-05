package dev.bgcheck.app.ui.network

import dev.bgcheck.network.domain.firewall.BlockingContext
import dev.bgcheck.network.domain.firewall.FirewallBackendType
import dev.bgcheck.network.domain.firewall.FirewallHealth
import dev.bgcheck.network.domain.firewall.unblockableReason
import dev.bgcheck.network.domain.model.FirewallFilterState
import dev.bgcheck.network.domain.model.NetworkPackage
import dev.bgcheck.network.domain.model.PackageId
import dev.bgcheck.network.domain.model.PackageIdentity
import dev.bgcheck.network.domain.model.PackageType
import dev.bgcheck.network.domain.model.InternetRecommendations
import dev.bgcheck.network.utils.Constants

enum class NetworkDestination { OPEN, START, VPN_FALLBACK, TOGGLE }

data class NetworkRequest(val destination: NetworkDestination, val packageName: String? = null, val userId: Int = 0)

/** Android Intent extraction stays in the activity; routing itself is testable without Android. */
internal fun parseNetworkRequest(action: String?, legacyPackage: String?, legacyUser: Int?,
    actionPackage: String?, actionUser: Int?, ownUser: Int): NetworkRequest? {
    val destination = when (action) {
        Constants.Notifications.ACTION_OPEN_FIREWALL -> NetworkDestination.OPEN
        Constants.Notifications.ACTION_ENABLE_VPN_FALLBACK -> NetworkDestination.VPN_FALLBACK
        Constants.Firewall.ACTION_REQUEST_VPN_PERMISSION -> NetworkDestination.START
        Constants.Firewall.ACTION_TOGGLE_FIREWALL -> NetworkDestination.TOGGLE
        else -> null
    }
    if (destination != null) return NetworkRequest(destination,
        if (destination == NetworkDestination.OPEN) actionPackage?.takeIf { it.isNotBlank() } else null,
        actionUser ?: ownUser)
    return legacyPackage?.takeIf { it.isNotBlank() }?.let { NetworkRequest(NetworkDestination.OPEN, it, legacyUser ?: ownUser) }
}

internal fun firewallStatus(enabled: Boolean, health: FirewallHealth): String = when (health) {
    is FirewallHealth.StopFailed -> "Stuck"
    is FirewallHealth.Down -> "Down"
    else -> if (enabled) "Active" else "Off"
}

internal data class ProfileChoice(val value: String, val label: String)

internal fun networkProfileChoices(discovered: List<ProfileChoice>, saved: String): List<ProfileChoice> = buildList {
    add(ProfileChoice("All", "All profiles"))
    addAll(discovered)
    if (saved != "All" && none { it.value == saved }) {
        if (size == 1) add(ProfileChoice("Personal", "Personal"))
        if (none { it.value == saved }) add(ProfileChoice(saved,
            if (saved.startsWith("user:")) "Unavailable profile (${saved.substringAfter("user:").substringBefore(':')})" else saved))
    }
}

internal fun networkFilterSummary(filter: FirewallFilterState, profiles: List<ProfileChoice>, compact: Boolean): String =
    (listOf(filter.packageType, filter.networkState ?: "Any access") +
        listOfNotNull("Internet-capable only".takeIf { filter.internetOnly },
            profiles.firstOrNull { it.value == filter.profileFilter }?.label) +
        if (compact) listOf("Compact") else emptyList()).joinToString(" · ")

internal enum class NetworkControl(val label: String) {
    WIFI("Wi-Fi"), MOBILE("Mobile data"), ROAMING("Roaming"), LAN("Local network"),
    BACKGROUND("Screen-off access"), ALL("Internet access")
}

internal fun NetworkPackage.blockedOn(control: NetworkControl): Boolean = when (control) {
    NetworkControl.WIFI -> wifiBlocked
    NetworkControl.MOBILE -> mobileBlocked
    NetworkControl.ROAMING -> roamingBlocked
    NetworkControl.LAN -> lanBlocked
    NetworkControl.BACKGROUND -> backgroundBlocked
    NetworkControl.ALL -> wifiBlocked || mobileBlocked || roamingBlocked
}

internal data class TogglePresentation(val allowed: Boolean, val enabled: Boolean, val description: String)

internal fun togglePresentation(pkg: NetworkPackage, control: NetworkControl, backend: FirewallBackendType?, context: BlockingContext): TogglePresentation {
    val allowed = !pkg.blockedOn(control)
    val enabled = selectionRefusal(pkg, backend, context) == null
    return TogglePresentation(allowed, enabled,
        "${control.label} ${if (allowed) "allowed" else "blocked"} for ${pkg.name}. " +
            if (enabled) "Double-tap to ${if (allowed) "block" else "allow"}" else "Controls unavailable")
}

internal fun selectionRefusal(pkg: NetworkPackage, backend: FirewallBackendType?, context: BlockingContext): String? = when {
    pkg.uid < 0 || pkg.userSerial == null -> "This app's installation could not be verified. Refresh the list before changing its rules."
    (pkg.isSystemCritical || pkg.isVpnApp) && !pkg.paintedAllowCritical -> "This app is protected. Change critical-package protection in Firewall settings to edit its rules."
    backend.unblockableReason(pkg, context)?.fixableHere == false -> "The current backend cannot block this app's identity. Open the app's network sheet for details."
    else -> null
}

internal data class NetworkSelection(val id: PackageId, val identity: PackageIdentity) {
    fun encode(): String = "${id.packageName}|${id.userId}|${identity.uid}|${identity.userSerial}"
    companion object {
        fun decode(value: String): NetworkSelection? = decodeSheet(value)?.takeIf {
            it.identity.uid >= 0 && it.id.userId >= 0 && it.identity.uid / 100000 == it.id.userId &&
                (it.identity.userSerial ?: -1) >= 0
        }
        fun decodeSheet(value: String): NetworkSelection? {
            val parts = value.split('|')
            if (parts.size != 4 || parts[0].isBlank()) return null
            val user = parts[1].toIntOrNull() ?: return null
            val uid = parts[2].toIntOrNull() ?: return null
            val serial = parts[3].toLongOrNull()
            if (user < 0 || (parts[3] != "null" && serial == null)) return null
            return NetworkSelection(PackageId(parts[0], user), PackageIdentity(uid, serial))
        }
    }
}

internal fun toggleSelection(selected: List<NetworkSelection>, pkg: NetworkPackage): List<NetworkSelection> = when {
    selected.any { it.id == pkg.id } -> selected.filterNot { it.id == pkg.id }
    selected.size >= Constants.Packages.MultiSelect.MAX_SELECTION_COUNT -> selected
    else -> selected + NetworkSelection(pkg.id, pkg.identity)
}

internal fun currentSelection(selected: List<NetworkSelection>, packages: List<NetworkPackage>): List<NetworkSelection> =
    selected.filter { selection -> packages.any { it.id == selection.id && it.identity == selection.identity } }

internal fun networkBatchStatus(packages: List<NetworkPackage>): String = when {
    packages.isEmpty() -> "No apps selected"
    packages.all { it.asSaved().isFullyAllowed } -> "All allowed"
    packages.all { it.asSaved().isFullyBlocked } -> "All blocked"
    else -> "Mixed"
}

internal fun batchStatus(blocked: List<Boolean>): String = when {
    blocked.isEmpty() -> "No apps selected"
    blocked.all { it } -> "All blocked"
    blocked.none { it } -> "All allowed"
    else -> "Mixed"
}

internal fun NetworkPackage.internetRecommendation(sharedUid: Boolean = false) = InternetRecommendations.forPackage(
    packageName, type == PackageType.SYSTEM, uid = uid,
    hasInternetPermission = hasInternetPermission.takeIf { permissionsKnown },
    sharedUid = sharedUid, critical = isSystemCritical || isVpnApp, enabled = isEnabled,
)
