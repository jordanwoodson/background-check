package dev.bgcheck.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WorkProfilePolicyTest {
    private val personal = AppEntry("com.example.app", "Example", 10101, false)
    private val work = personal.copy(uid = 1010101, profile = AndroidProfile(10, "Work", true, 42))

    @Test fun readBindsPolicyToWorkInstallation() = runBlocking {
        val state = PolicyEngine(Device()::exec, 10).read(work)
        assertEquals(10, state.userId)
        assertEquals(1010101, state.uid)
        assertEquals(42L, state.userSerial)
    }

    @Test fun samePackagePoliciesCannotCrossProfiles() = runBlocking {
        val device = Device()
        val personalEngine = PolicyEngine(device::exec, 0)
        val workEngine = PolicyEngine(device::exec, 10)
        val otherProfile = personalEngine.read(personal)
        reject { workEngine.apply(work, otherProfile, ControlAction.RESTRICT_BACKGROUND) }
        reject { workEngine.restore(work, otherProfile, otherProfile) }
        reject { workEngine.recover(work, otherProfile, otherProfile) }
        assertTrue(device.writes.isEmpty())
    }

    @Test fun undoRejectsRecreatedProfileAndChangedInstallation() = runBlocking {
        val device = Device()
        val engine = PolicyEngine(device::exec, 10)
        val before = engine.read(work)
        val after = engine.apply(work, before, ControlAction.RESTRICT_BACKGROUND)
        device.writes.clear()
        reject { engine.restore(work.copy(profile = work.profile.copy(serialNumber = 43)), before, after) }
        reject { engine.recover(work.copy(uid = 1010102), before, after) }
        assertTrue(device.writes.isEmpty())
        assertEquals("ignore", device.background[10])
    }

    @Test fun unavailableProfileRejectsPolicyAndForceStopLocally() = runBlocking {
        var commands = 0
        val paused = work.copy(profile = work.profile.copy(available = false, unavailableReason = "Work profile is paused"))
        val engine = PolicyEngine({ commands++; "" }, 10)
        reject { engine.read(paused) }
        assertFalse(ProcessManager({ commands++; "" }, 10).forceStop(paused).stopped)
        assertEquals(0, commands)
    }

    @Test fun mismatchedProfileMetadataRejectsPolicyAndForceStopLocally() = runBlocking {
        var commands = 0
        val mismatch = work.copy(profile = personal.profile)
        val engine = PolicyEngine({ commands++; "" }, 10)
        reject { engine.read(mismatch) }
        assertFalse(ProcessManager({ commands++; "" }, 10).forceStop(mismatch).stopped)
        assertEquals(0, commands)
    }

    @Test fun workRestrictionAndUndoLeavePersonalPolicyIntact() = runBlocking {
        val device = Device()
        val engine = PolicyEngine(device::exec, 10)
        val before = engine.read(work)
        val after = engine.apply(work, before, ControlAction.RESTRICT_BACKGROUND)
        assertEquals("ignore", device.background[10])
        assertEquals("allow", device.background[0])
        assertEquals("allow", engine.restore(work, before, after).backgroundMode)
        assertEquals("allow", device.background[0])
        assertTrue(device.writes.all { it.subList(3, 5) == listOf("--user", "10") })
    }

    @Test fun dozeChangeIsGlobalAndRetainsOriginatingProfileIdentity() = runBlocking {
        val device = Device()
        val engine = PolicyEngine(device::exec, 10)
        val after = engine.apply(work, engine.read(work), ControlAction.EXEMPT_DOZE)
        assertEquals(10, after.userId)
        assertEquals(true, PolicyEngine(device::exec, 0).read(personal).dozeExempt)
        assertEquals(listOf(listOf("cmd", "deviceidle", "whitelist", "+com.example.app")), device.writes)
    }

    @Test fun legacyUndoBindsToOwningProfileAndReturnsFullIdentity() = runBlocking {
        val device = Device()
        device.background[10] = "ignore"
        val before = PolicyState("com.example.app", "allow", null, false, userExempt = false, userId = 10)
        val after = before.copy(backgroundMode = "ignore")
        val restored = PolicyEngine(device::exec, 10).restore(work, before, after)
        assertEquals("allow", restored.backgroundMode)
        assertEquals(10, restored.userId)
        assertEquals(1010101, restored.uid)
        assertEquals(42L, restored.userSerial)
        assertEquals("allow", device.background[0])
    }

    @Test fun legacyRecoveryDoesNotPermitNewUnboundMutation() = runBlocking {
        val device = Device()
        val engine = PolicyEngine(device::exec, 10)
        val before = PolicyState("com.example.app", "allow", null, false, userExempt = false, userId = 10)
        reject { engine.apply(work, before, ControlAction.RESTRICT_BACKGROUND) }
        device.background[10] = "ignore"
        assertEquals("allow", engine.recover(work, before, before.copy(backgroundMode = "ignore")).backgroundMode)
    }

    @Test fun policyMutationRejectsChangedPackageUid() = runBlocking {
        val device = Device(uid = 10102)
        val engine = PolicyEngine(device::exec, 0)
        reject { engine.apply(personal, engine.read(personal), ControlAction.RESTRICT_BACKGROUND) }
        assertEquals("allow", device.background[0])
        assertTrue(device.writes.isEmpty())
    }

    @Test fun policyMutationRejectsSharedUid() = runBlocking {
        val device = Device(extraPackages = "package:com.example.shared uid:10101")
        val engine = PolicyEngine(device::exec, 0)
        reject { engine.apply(personal, engine.read(personal), ControlAction.RESTRICT_BACKGROUND) }
        assertTrue(device.writes.isEmpty())
    }

    @Test fun checkpointCannotAuthorizeReplacementInstallation() = runBlocking {
        val device = Device()
        val engine = PolicyEngine(device::exec, 0)
        reject {
            engine.apply(personal, engine.read(personal), ControlAction.RESTRICT_BACKGROUND) { _, _ ->
                device.uid = 10102
            }
        }
        assertTrue(device.writes.isEmpty())
    }

    @Test fun noOpCannotReportVerifiedPolicyForReplacementInstallation() = runBlocking {
        val device = Device()
        val engine = PolicyEngine(device::exec, 0)
        reject {
            engine.apply(personal, engine.read(personal), ControlAction.ALLOW_BACKGROUND) { _, _ ->
                device.uid = 10102
            }
        }
        assertTrue(device.writes.isEmpty())
    }

    private suspend fun reject(block: suspend () -> Unit) {
        try { block(); fail("Unsafe mutation was accepted") } catch (_: IllegalStateException) { }
    }

    private class Device(var uid: Int = 10101, val extraPackages: String = "") {
        val background = mutableMapOf(0 to "allow", 10 to "allow")
        var userExempt = false
        val writes = mutableListOf<List<String>>()

        suspend fun exec(args: List<String>): String = when {
            args.take(4) == listOf("pm", "list", "packages", "-U") -> {
                check(args[4] == "--user")
                val userId = args[5].toInt()
                val targetUid = userId * 100000 + uid
                if (args.size == 7) {
                    check(args[6] == "com.example.app")
                    "package:com.example.app uid:$targetUid"
                } else "package:com.example.app uid:$targetUid\n$extraPackages"
            }
            args.take(3) == listOf("cmd", "appops", "get") -> {
                check(args[3] == "--user" && args[5] == "com.example.app")
                "${args.last()}: ${background.getValue(args[4].toInt())}"
            }
            args.take(3) == listOf("cmd", "appops", "set") -> {
                check(args[3] == "--user" && args[5] == "com.example.app")
                writes += args
                background[args[4].toInt()] = args.last()
                ""
            }
            args == listOf("cmd", "deviceidle", "whitelist") ->
                "system-excidle,android,1000\n" + if (userExempt) "user,com.example.app,10101\n" else ""
            args.take(3) == listOf("cmd", "deviceidle", "whitelist") -> {
                check(args.last() in listOf("+com.example.app", "-com.example.app"))
                writes += args
                userExempt = args.last().startsWith("+")
                ""
            }
            args.take(2) == listOf("am", "get-standby-bucket") -> {
                check(args[2] == "--user" && args[4] == "com.example.app")
                "10"
            }
            else -> error("Unexpected command $args")
        }
    }
}
