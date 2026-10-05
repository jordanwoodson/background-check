package dev.bgcheck.app.data

import dev.bgcheck.network.data.firewall.FirewallManager.FirewallState
import dev.bgcheck.network.domain.firewall.FirewallBackendType
import dev.bgcheck.network.domain.firewall.FirewallHealth

data class ProtectionSummary(
    val status: String = "Checking", val backend: String = "None", val profileScope: String = "Not verified",
    val detail: String = "Reading firewall status.", val active: Boolean = false,
) {
    companion object {
        fun from(state: FirewallState, health: FirewallHealth, profiles: List<AndroidProfile>, ownUserId: Int,
            npmFullBlocking: Boolean, activeBackend: FirewallBackendType? = (state as? FirewallState.Running)?.backend): ProtectionSummary {
            val type = activeBackend
            val backend = when (type) {
                FirewallBackendType.VPN -> "Local VPN"
                FirewallBackendType.IPTABLES -> "Root · iptables"
                FirewallBackendType.CONNECTIVITY_MANAGER -> "ConnectivityManager"
                FirewallBackendType.NETWORK_POLICY_MANAGER -> "NetworkPolicyManager"
                null -> "None"
            }
            if (health is FirewallHealth.StopFailed) return ProtectionSummary("Needs attention", backend, "Not verified", "Stopping failed. Some saved blocks may still apply. Open Network to recover.")
            if (health is FirewallHealth.Down) return ProtectionSummary("Protection down", backend, "None", "Firewall protection is unavailable. Open Network to recover.")
            if (state is FirewallState.Error) return ProtectionSummary("Error", backend, "Not verified", state.message)
            if (state is FirewallState.Starting) return ProtectionSummary("Starting", backend, "Not verified", "Waiting for the firewall backend to start.")
            if (state is FirewallState.Stopped) return ProtectionSummary("Off", backend, "None", "Saved firewall rules are not active.")
            if (type == null) return ProtectionSummary("Checking", backend, "Not verified", "Waiting for the active firewall backend.")
            val reachable = profiles.filter { it.available && type?.reachesUser(it.userId, ownUserId) == true }
            val excluded = profiles.filterNot { it in reachable }
            val scope = reachable.joinToString { it.name }.ifEmpty { "No available profiles verified" }
            val limitation = when {
                type == FirewallBackendType.VPN -> "VPN protects only the profile running Background Check."
                type == FirewallBackendType.NETWORK_POLICY_MANAGER && !npmFullBlocking -> "Only metered background data for application UIDs is restricted on this device."
                type == FirewallBackendType.NETWORK_POLICY_MANAGER -> "Application UID rules only; core system UIDs are outside this backend's scope."
                type == FirewallBackendType.CONNECTIVITY_MANAGER -> "Application UID rules; supported profiles depend on Android access."
                else -> "Saved rules apply within this backend's supported scope."
            }
            val detail = limitation + if (excluded.isEmpty()) "" else " Outside active scope: ${excluded.joinToString { it.name }}."
            if (health is FirewallHealth.ApplyFailed) return ProtectionSummary("Partial", backend, "Not fully verified", "Some rules could not be applied. $detail")
            return ProtectionSummary(if (type == FirewallBackendType.NETWORK_POLICY_MANAGER && !npmFullBlocking) "Limited" else "On",
                backend, scope, (if (health is FirewallHealth.SwitchedToVpn) "Switched to VPN after a backend failure. " else "") + detail, active = true)
        }
    }
}
