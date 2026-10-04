package dev.bgcheck.network.domain.firewall

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Unproven ROM support is limited protection. Calibration may upgrade it after a real write. */
object NetworkPolicySupport {
    private val supported = MutableStateFlow(false)
    val fullBlocking = supported.asStateFlow()
    fun reportFullBlocking(value: Boolean) { supported.value = value }
    fun isLimited(backend: FirewallBackendType?) = backend == FirewallBackendType.NETWORK_POLICY_MANAGER && !supported.value
}
