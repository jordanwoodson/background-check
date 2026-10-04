package dev.bgcheck.app.network

import dev.bgcheck.network.domain.firewall.KernelRuleCheck
import org.junit.Assert.*
import org.junit.Test

class KernelRuleCheckTest {
    @Test fun bothAddressFamiliesMustMatchRequestedVerdict() {
        assertTrue(check(mapOf(10123 to 1), mapOf(10123 to 1)))
        assertFalse(check(emptyMap(), mapOf(10123 to 1)))
        assertFalse(check(mapOf(10123 to 1), emptyMap()))
        assertFalse(check(mapOf(10123 to 2), mapOf(10123 to 1)))
    }
    @Test fun unblockRequiresRemovingIpv6AndIpv4Rules() {
        assertTrue(KernelRuleCheck.matches(0, emptySet(), setOf(10123), emptyMap(), emptyMap()))
        assertFalse(KernelRuleCheck.matches(0, emptySet(), setOf(10123), emptyMap(), mapOf(10123 to 1)))
    }
    @Test fun lanRequiresEveryRangeAndSuccessfulShellExit() {
        assertFalse(KernelRuleCheck.matches(0, setOf(10123), emptySet(), mapOf(10123 to 2), mapOf(10123 to 2), 3, 2))
        assertTrue(KernelRuleCheck.matches(0, setOf(10123), emptySet(), mapOf(10123 to 3), mapOf(10123 to 2), 3, 2))
        assertFalse(KernelRuleCheck.matches(1, setOf(10123), emptySet(), mapOf(10123 to 1), mapOf(10123 to 1)))
    }
    private fun check(v4: Map<Int,Int>, v6: Map<Int,Int>) =
        KernelRuleCheck.matches(0, setOf(10123), emptySet(), v4, v6)
}
