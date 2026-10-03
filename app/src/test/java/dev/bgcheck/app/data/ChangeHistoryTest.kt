package dev.bgcheck.app.data

import org.junit.Assert.*
import org.junit.Test

class ChangeHistoryTest {
    @Test fun policyRoundTripPreservesUnknownAndUserExemption() {
        val before = PolicyState("dev.example", "default", null, false, userExempt = false, capturedAt = 123)
        val after = before.copy(backgroundMode = "ignore", userExempt = true, dozeExempt = true)
        val record = ChangeRecord("id", "dev.example", "Example", "Background restricted", 456, before, after)
        assertEquals(listOf(record), ChangeHistory.decode(ChangeHistory.encode(listOf(record))))
    }
    @Test fun malformedHistoryDoesNotCrashStartup() {
        assertEquals(emptyList<ChangeRecord>(), ChangeHistory.decode("broken json"))
    }
    @Test fun interruptedChangeRetainsRecoveryIntentAcrossRestart() {
        val before = PolicyState("dev.example", "allow", null, false, userExempt = false)
        val pending = ChangeRecord("pending", "dev.example", "Example", "Restrict background", 500,
            before, before.copy(backgroundMode = "ignore"), recovery = true)
        assertEquals(listOf(pending), ChangeHistory.decode(ChangeHistory.encode(listOf(pending))))
    }
    @Test fun historyKeepsNewestFifty() {
        val policy = PolicyState("dev.example", null, null, null)
        val records = (1..60).map { ChangeRecord("$it", "dev.example", "App", "Change", it.toLong(), policy, policy) }
        val restored = ChangeHistory.decode(ChangeHistory.encode(records))
        assertEquals(50, restored.size)
        assertEquals("1", restored.first().id)
    }
}
