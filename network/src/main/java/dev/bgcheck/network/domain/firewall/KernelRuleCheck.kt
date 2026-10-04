package dev.bgcheck.network.domain.firewall

/** A successful shell exit alone does not establish that either address family is protected. */
object KernelRuleCheck {
    fun matches(
        exitCode: Int,
        added: Set<Int>,
        removed: Set<Int>,
        ipv4: Map<Int, Int>,
        ipv6: Map<Int, Int>,
        ipv4RulesPerUid: Int = 1,
        ipv6RulesPerUid: Int = 1,
    ): Boolean = exitCode == 0 &&
        added.all { ipv4[it] == ipv4RulesPerUid && ipv6[it] == ipv6RulesPerUid } &&
        removed.none { it in ipv4 || it in ipv6 } &&
        ipv4.values.all { it == ipv4RulesPerUid } && ipv6.values.all { it == ipv6RulesPerUid }
}
