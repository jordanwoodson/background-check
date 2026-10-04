package dev.bgcheck.app.data

import org.junit.Assert.*
import org.junit.Test

class ChangeHistoryTest {
    @Test fun oldHistoryBindsToStorageOwnerWithoutLosingUndoOrRecovery() {
        val legacy = """[
          {"id":"old","package":"dev.example","label":"Example","description":"Restrict background",
           "timestamp":456,"undone":false,"recovery":true,
           "before":{"package":"dev.example","background":"allow","legacy":null,"exempt":false,
             "systemExempt":false,"userExempt":false,"uidOverride":false,"bucket":10,"time":123,"error":null},
           "after":{"package":"dev.example","background":"ignore","legacy":null,"exempt":false,
             "systemExempt":false,"userExempt":false,"uidOverride":false,"bucket":10,"time":123,"error":null}}
        ]"""
        val record = ChangeHistory.decode(legacy, legacyUserId = 10).single()
        assertEquals(10, record.userId)
        assertEquals(10, record.before.userId)
        assertEquals(10, record.after.userId)
        assertEquals("User 10", record.profileName)
        assertNull(record.before.uid)
        assertNull(record.userSerial)
        assertEquals("allow", record.before.backgroundMode)
        assertEquals("ignore", record.after.backgroundMode)
        assertTrue(record.recovery)
        assertFalse(record.undone)
        assertEquals(0, ChangeHistory.decode(legacy).single().userId)
        assertEquals(listOf(record), ChangeHistory.decode(ChangeHistory.encode(listOf(record)), legacyUserId = 0))
    }

    @Test fun historyRetainsSeparateProfileIdentityAcrossRestart() {
        val before = PolicyState("dev.example", "allow", null, false, userExempt = false,
            userId = 10, uid = 1010101, userSerial = 42)
        val work = ChangeRecord("work", "dev.example", "Example", "Device-wide Doze exemption", 456,
            before, before.copy(dozeExempt = true, userExempt = true), profileName = "Company work")
        val personalBefore = before.copy(userId = 0, uid = 10101, userSerial = 0)
        val personal = ChangeRecord("personal", "dev.example", "Example", "Restrict background", 123,
            personalBefore, personalBefore.copy(backgroundMode = "ignore"))
        assertEquals(listOf(work, personal), ChangeHistory.decode(ChangeHistory.encode(listOf(work, personal))))
    }

    @Test fun contradictoryHistoryIdentityCannotBeRestored() {
        val before = PolicyState("dev.example", "allow", null, false, userExempt = false,
            userId = 10, uid = 1010101, userSerial = 42)
        val invalid = listOf(
            ChangeRecord("profile", "dev.example", "Example", "Change", 1, before, before.copy(userId = 0)),
            ChangeRecord("uid", "dev.example", "Example", "Change", 1, before, before.copy(uid = 1010102)),
            ChangeRecord("serial", "dev.example", "Example", "Change", 1, before, before.copy(userSerial = 43)),
            ChangeRecord("package", "dev.other", "Example", "Change", 1, before, before),
        )
        assertTrue(ChangeHistory.decode(ChangeHistory.encode(invalid)).isEmpty())
    }
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
