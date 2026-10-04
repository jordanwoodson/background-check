package dev.bgcheck.app

import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bgcheck.app.data.AccessState
import dev.bgcheck.app.data.AppEntry
import dev.bgcheck.app.data.AppInventory
import dev.bgcheck.app.data.ChangeHistory
import dev.bgcheck.app.data.ChangeRecord
import dev.bgcheck.app.data.ControlAction
import dev.bgcheck.app.data.PolicyEngine
import dev.bgcheck.app.data.ProcessManager
import dev.bgcheck.app.data.ProfileSupport
import dev.bgcheck.app.data.ResourceMonitor
import dev.bgcheck.app.data.ResourceSnapshot
import dev.bgcheck.app.shell.ShizukuBridge
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run only on the disposable emulator with the fixture installed in users 0 and 10. */
@RunWith(AndroidJUnit4::class)
class WorkProfileRuntimeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun inventorySeparatesRealPersonalAndManagedFixtureCopies() = runBlocking {
        withFixtures { _, personal, work ->
            assertEquals("Background Check test app", personal.label)
            assertEquals("Background Check test app", work.label)
            assertEquals("Personal", personal.profile.name)
            assertEquals("Work", work.profile.name)
            assertFalse(personal.profile.isWork)
            assertTrue(work.profile.isWork)
            assertEquals(0, personal.uid / 100000)
            assertEquals(10, work.uid / 100000)
            assertEquals(personal.uid + 1000000, work.uid)
            assertNotEquals(personal.key, work.key)
            assertEquals("package:$FIXTURE uid:${personal.uid}", shell("pm list packages -U --user 0 $FIXTURE").trim())
            assertEquals("package:$FIXTURE uid:${work.uid}", shell("pm list packages -U --user 10 $FIXTURE").trim())
            assertNotNull(personal.profile.serialNumber)
            assertNotNull(work.profile.serialNumber)
            assertNotEquals(personal.profile.serialNumber, work.profile.serialNumber)
            Log.i(TAG, "Inventory: ${personal.key} UID=${personal.uid} serial=${personal.profile.serialNumber}; " +
                "${work.key} UID=${work.uid} serial=${work.profile.serialNumber}")
        }
    }

    @Test fun workRestrictionAndPersistedUndoLeavePersonalPolicyUnchanged() = runBlocking {
        withFixtures { bridge, personal, work ->
            val personalEngine = PolicyEngine(bridge::execute, 0)
            val workEngine = PolicyEngine(bridge::execute, 10)
            val personalOriginal = personalEngine.read(personal)
            val workOriginal = workEngine.read(work)
            assertNull(personalOriginal.error)
            assertNull(workOriginal.error)
            try {
                val allowed = workEngine.apply(work, workOriginal, ControlAction.ALLOW_BACKGROUND)
                val restricted = workEngine.apply(work, allowed, ControlAction.RESTRICT_BACKGROUND)
                assertEquals("ignore", restricted.backgroundMode)
                assertEquals(10, restricted.userId)
                assertEquals(work.uid, restricted.uid)
                assertEquals(work.profile.serialNumber, restricted.userSerial)
                assertEquals(personalOriginal.backgroundMode, personalEngine.read(personal).backgroundMode)
                val record = ChangeRecord("work-runtime", FIXTURE, work.label, "Restricted background", 123,
                    allowed, restricted, profileName = work.profile.name)
                val saved = ChangeHistory.decode(ChangeHistory.encode(listOf(record)), legacyUserId = 0).single()
                assertEquals(record, saved)
                rejected { personalEngine.restore(personal, saved.before, saved.after) }
                assertEquals("ignore", workEngine.read(work).backgroundMode)
                assertEquals(personalOriginal.backgroundMode, personalEngine.read(personal).backgroundMode)
                val restored = workEngine.restore(work, saved.before, saved.after)
                assertEquals("allow", restored.backgroundMode)
                assertEquals(personalOriginal.backgroundMode, personalEngine.read(personal).backgroundMode)
                Log.i(TAG, "Restricted and restored ${work.key}; ${personal.key} stayed ${personalOriginal.backgroundMode}; " +
                    "persisted Undo rejected the personal installation")
            } finally {
                withContext(NonCancellable) {
                    workEngine.restore(work, workOriginal, workEngine.read(work))
                }
            }
        }
    }

    @Test fun stoppingWorkFixturePreservesPersonalWorkerAndUidAttribution() = runBlocking {
        withFixtures { bridge, personal, work ->
            val monitor = ResourceMonitor(bridge::execute)
            try {
                launchFixture(0)
                launchFixture(10)
                val before = awaitSample(monitor) { sample ->
                    listOf(personal.uid, work.uid).all { uid ->
                        sample.processes.any { it.uid == uid && it.command == FIXTURE } &&
                            sample.processes.any { it.uid == uid && it.command == "$FIXTURE:worker" }
                    }
                }
                val personalWorker = before.processes.single { it.uid == personal.uid && it.command == "$FIXTURE:worker" }
                val workWorker = before.processes.single { it.uid == work.uid && it.command == "$FIXTURE:worker" }
                assertNotEquals(personalWorker.pid, workWorker.pid)
                val workDiagnostics = monitor.diagnostics(work)
                assertEquals(10, workDiagnostics.userId)
                assertNotNull("Work UID memory must be available: ${workDiagnostics.warnings}", workDiagnostics.pssKb)
                assertTrue("Work PSS includes the two fixture allocations", requireNotNull(workDiagnostics.pssKb) >= 12288)
                assertTrue("Work services must be recognized: ${workDiagnostics.warnings}", workDiagnostics.servicesKnown)
                assertEquals("The personal copy must not duplicate the work service", 1, workDiagnostics.services.size)
                assertEquals("$FIXTURE/.ProbeService", workDiagnostics.services.single().component)
                assertEquals("$FIXTURE:worker", workDiagnostics.services.single().process)
                val result = ProcessManager(bridge::execute, 10).forceStop(work)
                assertTrue(result.message, result.stopped)
                val after = awaitSample(monitor) { sample ->
                    sample.processes.none { it.uid == work.uid } &&
                        sample.processes.any { it.uid == personal.uid && it.pid == personalWorker.pid }
                }
                assertTrue(after.processes.none { it.uid == work.uid })
                assertTrue(after.processes.any { it.uid == personal.uid && it.pid == personalWorker.pid && it.command == "$FIXTURE:worker" })
                val stoppedDiagnostics = monitor.diagnostics(work)
                assertEquals(10, stoppedDiagnostics.userId)
                assertTrue("Stopped work services must be recognized: ${stoppedDiagnostics.warnings}", stoppedDiagnostics.servicesKnown)
                assertTrue("The surviving personal service must not appear for work", stoppedDiagnostics.services.isEmpty())
                Log.i(TAG, "Work UID ${work.uid} stopped; personal worker PID ${personalWorker.pid}, UID ${personal.uid} survived")
            } finally {
                // These shell cleanup commands target only the dedicated disposable fixture.
                shell("am force-stop --user 10 $FIXTURE")
                shell("am force-stop --user 0 $FIXTURE")
            }
        }
    }

    @Test fun stoppedManagedProfileRejectsThePreviouslySelectedApp() = runBlocking {
        withFixtures { bridge, _, work ->
            val inventory = AppInventory(context)
            try {
                // Android requires -f for a managed profile related to the foreground owner.
                val stopOutput = shell("am stop-user -w -f 10")
                assertTrue("Disposable work profile could not be stopped: $stopOutput", stopOutput.isBlank())
                val stoppedProfiles = withTimeout(30_000) {
                    var profiles = inventory.profiles()
                    while (profiles.any { it.userId == 10 && it.available }) {
                        delay(200)
                        profiles = inventory.profiles()
                    }
                    profiles
                }
                rejected { ProfileSupport.requireAvailable(work, stoppedProfiles) }
                val stopped = stoppedProfiles.singleOrNull { it.userId == 10 }
                if (stopped != null) {
                    val unavailable = work.copy(profile = stopped)
                    rejected { PolicyEngine(bridge::execute, 10).read(unavailable) }
                    assertFalse(ProcessManager(bridge::execute, 10).forceStop(unavailable).stopped)
                }
                Log.i(TAG, "Stopped profile invalidated selected ${work.key}")
            } finally {
                shell("am start-user -w 10")
                withContext(NonCancellable) {
                    withTimeout(30_000) {
                        while (inventory.profiles().none { it.userId == 10 && it.available }) delay(200)
                    }
                }
            }
        }
    }

    private suspend fun withFixtures(block: suspend (ShizukuBridge, AppEntry, AppEntry) -> Unit) {
        assertTrue("Use a disposable emulator", Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        assertEquals("Instrumentation must run in the personal profile", 0, Process.myUid() / 100000)
        val bridge = ShizukuBridge(context)
        try {
            bridge.check()
            try {
                withTimeout(30_000) { bridge.access.first { it == AccessState.READY } }
            } catch (timeout: TimeoutCancellationException) {
                throw AssertionError("Shizuku was not ready: ${bridge.access.value}: ${bridge.message.value}", timeout)
            }
            val inventory = AppInventory(context).read()
            val copies = inventory.apps.filter { it.packageName == FIXTURE && it.installed }
            assertEquals("Both real fixture copies must be inventoried: ${inventory.warnings}", setOf(0, 10), copies.map { it.userId }.toSet())
            val personal = copies.single { it.userId == 0 }
            val work = copies.single { it.userId == 10 }
            assertTrue("Personal profile unavailable: ${personal.profile.unavailableReason}", personal.profile.available)
            assertTrue("Work profile unavailable: ${work.profile.unavailableReason}", work.profile.available)
            assertTrue("User 10 must be a genuine managed profile", work.profile.isWork)
            assertNull("Personal fixture must be mutable", personal.protectedReason)
            assertNull("Work fixture must be mutable", work.protectedReason)
            block(bridge, personal, work)
        } finally {
            bridge.close()
        }
    }

    private fun launchFixture(userId: Int) {
        val output = shell("am start -W --user $userId -n $FIXTURE/.ProbeActivity")
        assertTrue("Fixture launch failed for user $userId: $output", output.contains("Status: ok"))
    }

    private suspend fun awaitSample(monitor: ResourceMonitor, matches: (ResourceSnapshot) -> Boolean): ResourceSnapshot =
        withTimeout(30_000) {
            var sample = monitor.sample()
            while (!sample.processesKnown || !matches(sample)) {
                delay(200)
                sample = monitor.sample()
            }
            sample
        }

    private suspend fun rejected(block: suspend () -> Unit) {
        try { block(); fail("Wrong or unavailable profile was accepted") } catch (_: IllegalStateException) { }
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }

    private companion object {
        const val FIXTURE = "dev.bgcheck.fixture"
        const val TAG = "WorkProfileRuntimeTest"
    }
}
