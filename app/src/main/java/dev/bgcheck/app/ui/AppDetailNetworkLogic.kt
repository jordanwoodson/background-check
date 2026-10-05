package dev.bgcheck.app.ui

import dev.bgcheck.network.domain.model.NetworkPackage

internal fun appDetailNetworkStatus(pkg: NetworkPackage?, installed: Boolean, limited: Boolean = false): String = when {
    !installed -> "Uninstalled for this profile"
    pkg == null -> "Network status unavailable"
    pkg.permissionsKnown && !pkg.hasInternetPermission -> "No internet permission"
    !pkg.permissionsKnown -> "Internet permission not verified"
    limited -> if (pkg.wifiBlocked || pkg.mobileBlocked) "Background data restricted" else "Background data allowed"
    else -> {
        val blocked = buildList {
            if (pkg.wifiBlocked) add("Wi-Fi")
            if (pkg.mobileBlocked) add("mobile data")
            else if (pkg.roamingBlocked) add("roaming")
        }
        when {
            pkg.wifiBlocked && pkg.mobileBlocked -> "Blocked on all networks"
            blocked.isNotEmpty() -> "Blocked on ${blocked.joinToString(" and ")}"
            pkg.lanBlocked && pkg.backgroundBlocked -> "Local network and screen-off access blocked"
            pkg.lanBlocked -> "Local network blocked"
            pkg.backgroundBlocked -> "Screen-off access blocked"
            else -> "Allowed on all networks"
        }
    }
}
