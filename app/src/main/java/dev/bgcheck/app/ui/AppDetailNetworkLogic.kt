package dev.bgcheck.app.ui

import dev.bgcheck.network.domain.model.NetworkPackage
import dev.bgcheck.network.domain.firewall.FirewallHealth

internal fun appDetailNetworkProtectionWarning(health: FirewallHealth): String? = when (health) {
    is FirewallHealth.Down, is FirewallHealth.StopFailed, is FirewallHealth.ApplyFailed ->
        "Protection isn't working right now, so this rule may not apply."
    else -> null
}

internal fun appDetailNetworkStatus(pkg: NetworkPackage?, installed: Boolean, limited: Boolean = false,
    enabled: Boolean = true, health: FirewallHealth = FirewallHealth.Healthy): String {
    val saved = !enabled || appDetailNetworkProtectionWarning(health) != null
    val rule = if (saved) pkg?.asSaved() else pkg
    val status = appDetailNetworkRuleStatus(rule, installed, limited)
    return if (saved && installed && rule != null && rule.permissionsKnown && rule.hasInternetPermission)
        "Saved rule: ${status.replaceFirstChar { it.lowercase() }}" else status
}

private fun appDetailNetworkRuleStatus(pkg: NetworkPackage?, installed: Boolean, limited: Boolean): String = when {
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
