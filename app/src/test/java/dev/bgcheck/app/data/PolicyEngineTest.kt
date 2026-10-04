package dev.bgcheck.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PolicyEngineTest {
    private val app = AppEntry("com.example.app", "Example", 10101, false)

    @Test fun uninstalledPackageCannotReadOrChangePolicy() = runBlocking {
        var commands = 0
        val engine = PolicyEngine({ commands++; "" }, 0)
        val removed = app.copy(installed = false)
        expectFailure { engine.read(removed) }
        expectFailure { engine.apply(removed, PolicyState(app.packageName, "allow", null, false), ControlAction.RESTRICT_BACKGROUND) }
        assertEquals(0, commands)
    }

    @Test fun restrictionIsVerifiedAndDoesNotAlterIndependentDozeChoice() = runBlocking {
        val device = Device(userExempt = true)
        val engine = PolicyEngine(device::exec, 0)
        val after = engine.apply(app, engine.read(app), ControlAction.RESTRICT_BACKGROUND)
        assertEquals("ignore", after.backgroundMode)
        assertEquals(true, after.userExempt)
        assertTrue(device.writes.none { it.contains("deviceidle") })
    }

    @Test fun stalePolicyIsRejectedBeforeWriting() = runBlocking {
        val device = Device()
        val engine = PolicyEngine(device::exec, 0)
        val before = engine.read(app)
        device.background = "ignore"
        var checkpointed = false
        expectFailure { engine.apply(app, before, ControlAction.ALLOW_BACKGROUND) { _, _ -> checkpointed = true } }
        assertFalse(checkpointed)
        assertTrue(device.writes.isEmpty())
    }

    @Test fun systemExemptionCannotBeRemoved() = runBlocking {
        val device = Device(systemExempt = true)
        val engine = PolicyEngine(device::exec, 0)
        expectFailure { engine.apply(app, engine.read(app), ControlAction.OPTIMIZE_DOZE) }
        assertTrue(device.writes.isEmpty())
    }

    @Test fun uidOverrideAndProtectedAppsCannotBeChanged() = runBlocking {
        val device = Device(uidOverride = true)
        val engine = PolicyEngine(device::exec, 0)
        expectFailure { engine.apply(app, engine.read(app), ControlAction.ALLOW_BACKGROUND) }
        device.uidOverride = false
        val protected = app.copy(protectedReason = "Active keyboard")
        expectFailure { engine.apply(protected, engine.read(protected), ControlAction.RESTRICT_BACKGROUND) }
        assertTrue(device.writes.isEmpty())
    }

    @Test fun failedLegacyUpdateRollsBackCompletedBackgroundUpdate() = runBlocking {
        val device = Device(failLegacyOnce = true)
        val old = app.copy(targetSdk = 25)
        val engine = PolicyEngine(device::exec, 0)
        expectFailure { engine.apply(old, engine.read(old), ControlAction.RESTRICT_BACKGROUND) }
        assertEquals("allow", device.background)
        assertEquals("allow", device.legacy)
    }

    @Test fun successfulExitWithoutChangedStateIsNotSuccess() = runBlocking {
        val device = Device(ignoreWrites = true)
        val engine = PolicyEngine(device::exec, 0)
        expectFailure { engine.apply(app, engine.read(app), ControlAction.RESTRICT_BACKGROUND) }
        assertEquals("allow", device.background)
    }

    @Test fun undoRestoresUserMembershipAndRejectsLaterExternalChanges() = runBlocking {
        val device = Device()
        val engine = PolicyEngine(device::exec, 0)
        val before = engine.read(app)
        val after = engine.apply(app, before, ControlAction.EXEMPT_DOZE)
        assertEquals(true, after.userExempt)
        assertEquals(false, engine.restore(app, before, after).userExempt)
        val changed = engine.apply(app, engine.read(app), ControlAction.RESTRICT_BACKGROUND)
        device.userExempt = true
        var checkpointed = false
        expectFailure { engine.restore(app, before, changed) { checkpointed = true } }
        assertFalse(checkpointed)
        assertEquals("ignore", device.background)
        assertTrue(device.userExempt)
    }

    @Test fun unavailableReadNeverAllowsMutation() = runBlocking {
        val device = Device(malformedAppOp = true)
        val engine = PolicyEngine(device::exec, 0)
        val before = engine.read(app)
        assertNotNull(before.error)
        assertNull(before.backgroundMode)
        expectFailure { engine.apply(app, before, ControlAction.RESTRICT_BACKGROUND) }
        assertTrue(device.writes.isEmpty())
    }

    @Test fun lostConnectionAfterWriteCanRecoverAfterReconnect() = runBlocking {
        val device = Device(disconnectOnWriteOnce = true)
        val engine = PolicyEngine(device::exec, 0)
        val before = engine.read(app)
        val intended = engine.desiredPolicy(app, before, ControlAction.RESTRICT_BACKGROUND)
        expectFailure { engine.apply(app, before, ControlAction.RESTRICT_BACKGROUND) }
        assertEquals("ignore", device.background)
        device.disconnected = false
        val recovered = engine.recover(app, before, intended)
        assertEquals("allow", recovered.backgroundMode)
        assertEquals(false, recovered.userExempt)
    }

    @Test fun recoveryDoesNotOverwriteUnrelatedPolicyDrift() = runBlocking {
        val device = Device()
        val engine = PolicyEngine(device::exec, 0)
        val before = engine.read(app)
        val intended = engine.desiredPolicy(app, before, ControlAction.EXEMPT_DOZE)
        device.userExempt = true
        device.background = "deny"
        expectFailure { engine.recover(app, before, intended) }
        assertEquals("deny", device.background)
        assertTrue(device.userExempt)
        assertTrue(device.writes.isEmpty())
    }

    @Test fun validatedCheckpointRunsBeforeWriteAndAlsoForNoOp() = runBlocking {
        val device = Device()
        val engine = PolicyEngine(device::exec, 0)
        val oldSnapshot = engine.read(app).copy(capturedAt = 0)
        var checkpoints = 0
        engine.apply(app, oldSnapshot, ControlAction.ALLOW_BACKGROUND) { current, desired ->
            checkpoints++
            assertTrue(current.capturedAt > 0)
            assertEquals("allow", current.backgroundMode)
            assertEquals("allow", desired.backgroundMode)
            assertTrue(device.writes.isEmpty())
        }
        engine.apply(app, oldSnapshot, ControlAction.RESTRICT_BACKGROUND) { current, desired ->
            checkpoints++
            assertEquals("allow", current.backgroundMode)
            assertEquals("ignore", desired.backgroundMode)
            assertTrue(device.writes.isEmpty())
        }
        assertEquals(2, checkpoints)
        assertEquals("ignore", device.background)
    }

    @Test fun failedCheckpointPreventsAllWrites() = runBlocking {
        val device = Device()
        val engine = PolicyEngine(device::exec, 0)
        expectFailure {
            engine.apply(app, engine.read(app), ControlAction.RESTRICT_BACKGROUND) { _, _ -> error("Journal unavailable") }
        }
        assertTrue(device.writes.isEmpty())
    }

    @Test fun interruptedLegacyUndoRecoversToOriginalBeforeState() = runBlocking {
        val device = Device()
        val old = app.copy(targetSdk = 25)
        val engine = PolicyEngine(device::exec, 0)
        val before = engine.read(old)
        val intendedAfter = engine.apply(old, before, ControlAction.RESTRICT_BACKGROUND)
        device.failLegacyOnce = true
        device.disconnectOnLegacyFailure = true
        var pendingRecovery = false
        expectFailure {
            engine.restore(old, before, intendedAfter) { pendingRecovery = true }
        }
        assertTrue(pendingRecovery)
        assertEquals("allow", device.background)
        assertEquals("ignore", device.legacy)
        device.disconnected = false
        val recovered = engine.recover(old, before, intendedAfter)
        assertEquals("allow", recovered.backgroundMode)
        assertEquals("allow", recovered.legacyMode)
    }

    private suspend fun expectFailure(block: suspend () -> Unit) {
        try { block(); fail("Expected operation to be rejected") } catch (_: IllegalStateException) { }
    }

    private class Device(
        var background: String = "allow", var legacy: String = "allow",
        var userExempt: Boolean = false, var systemExempt: Boolean = false,
        var uidOverride: Boolean = false, var failLegacyOnce: Boolean = false,
        var ignoreWrites: Boolean = false, var malformedAppOp: Boolean = false,
        var disconnectOnWriteOnce: Boolean = false, var disconnected: Boolean = false,
        var disconnectOnLegacyFailure: Boolean = false,
    ) {
        val writes = mutableListOf<List<String>>()
        suspend fun exec(args: List<String>): String {
            check(!disconnected) { "Simulated connection loss" }
            return when {
            args.take(6) == listOf("pm", "list", "packages", "-U", "--user", "0") -> {
                check(args.size == 6 || args == listOf("pm", "list", "packages", "-U", "--user", "0", "com.example.app"))
                "package:com.example.app uid:10101"
            }
            args.take(3) == listOf("cmd", "appops", "get") -> {
                check(args.subList(3, 6) == listOf("--user", "0", "com.example.app"))
                if (malformedAppOp) "Unsupported operation" else {
                    val op = args.last()
                    val mode = if (op == "RUN_ANY_IN_BACKGROUND") background else legacy
                    if (uidOverride) "Uid mode: $op: ignore\n$op: $mode" else "$op: $mode"
                }
            }
            args.take(3) == listOf("cmd", "appops", "set") -> {
                check(args.subList(3, 6) == listOf("--user", "0", "com.example.app"))
                writes += args
                if (args[6] == "RUN_IN_BACKGROUND" && failLegacyOnce) {
                    failLegacyOnce = false
                    if (disconnectOnLegacyFailure) disconnected = true
                    error("Simulated legacy failure")
                }
                if (!ignoreWrites) {
                    if (args[6] == "RUN_ANY_IN_BACKGROUND") background = args[7] else legacy = args[7]
                }
                if (disconnectOnWriteOnce) {
                    disconnectOnWriteOnce = false
                    disconnected = true
                    error("Simulated connection loss after write")
                }
                ""
            }
            args == listOf("cmd", "deviceidle", "whitelist") -> buildString {
                append("system-excidle,android,1000\n")
                if (systemExempt) append("system,com.example.app,10101\n")
                if (userExempt) append("user,com.example.app,10101\n")
            }
            args.take(3) == listOf("cmd", "deviceidle", "whitelist") -> {
                check(args.last() in listOf("+com.example.app", "-com.example.app"))
                writes += args
                if (!ignoreWrites) userExempt = args.last().startsWith("+")
                ""
            }
            args == listOf("am", "get-standby-bucket", "--user", "0", "com.example.app") -> "10"
            else -> error("Unexpected command $args")
        }
        }
    }
}
