package dev.bgcheck.app.network

import dev.bgcheck.network.domain.firewall.UidFirewallPort
import dev.bgcheck.network.domain.firewall.UidFirewallSession
import org.junit.Assert.*
import org.junit.Test

class UidFirewallSessionTest {
    private class Firewall : UidFirewallPort {
        val rules = mutableMapOf<Int, Int>()
        override fun getRule(uid: Int) = rules[uid] ?: 1
        override fun setRule(uid: Int, rule: Int) { rules[uid] = rule }
    }

    @Test fun workBlockLeavesPersonalUidAloneAndStopRestoresWork() {
        val port = Firewall()
        var journal = emptyMap<Int, Int>()
        val session = UidFirewallSession(port, { journal }, { journal = it })
        session.apply(setOf(1010123))
        assertEquals(1, port.getRule(10123))
        assertEquals(2, port.getRule(1010123))
        assertEquals(mapOf(1010123 to 1), journal)
        session.restore()
        assertEquals(1, port.getRule(1010123))
        assertTrue(journal.isEmpty())
    }

    @Test fun existingAndroidDenialIsNeverClaimedOrRemoved() {
        val port = Firewall().apply { rules[1010123] = 2 }
        var journal = emptyMap<Int, Int>()
        val session = UidFirewallSession(port, { journal }, { journal = it })
        session.apply(setOf(1010123))
        session.apply(emptySet())
        session.restore()
        assertEquals(2, port.getRule(1010123))
        assertTrue(journal.isEmpty())
    }

    @Test fun restartedSessionRestoresDurableRecord() {
        val port = Firewall().apply { rules[1010123] = 2 }
        var journal = mapOf(1010123 to 1)
        UidFirewallSession(port, { journal }, { journal = it }).restore()
        assertEquals(1, port.getRule(1010123))
        assertTrue(journal.isEmpty())
    }
}
