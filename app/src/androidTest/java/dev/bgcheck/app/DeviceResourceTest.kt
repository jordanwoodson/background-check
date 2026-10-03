package dev.bgcheck.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Process
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bgcheck.app.data.AccessState
import dev.bgcheck.app.data.AppEntry
import dev.bgcheck.app.data.ProcessManager
import dev.bgcheck.app.data.ResourceMonitor
import dev.bgcheck.app.data.ResourceSnapshot
import dev.bgcheck.app.shell.ShizukuBridge
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** All mutations target the dedicated disposable fixture; other installed apps are read-only. */
@RunWith(AndroidJUnit4::class)
class DeviceResourceTest {
    @Test fun liveResourcesAndVerifiedForceStopOfDedicatedFixture(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val installed = context.packageManager.getApplicationInfo(FIXTURE, 0)
        val fixture = AppEntry(FIXTURE, "Background Check test app", installed.uid, false, targetSdk = installed.targetSdkVersion)
        val userId = Process.myUid() / 100000
        assertEquals("Fixture must belong to the instrumentation user", userId, installed.uid / 100000)
        val bridge = ShizukuBridge(context)
        val manager = ProcessManager(bridge::execute, userId)
        var launched = false
        var stopped = false
        try {
            bridge.check()
            withTimeout(30_000) { bridge.access.first { it == AccessState.READY } }
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                context.startActivity(Intent().apply {
                    component = ComponentName(FIXTURE, "$FIXTURE.ProbeActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            launched = true

            val monitor = ResourceMonitor(bridge::execute)
            val sample = withTimeout(30_000) {
                var current: ResourceSnapshot
                do {
                    current = monitor.sample()
                    assertTrue("Live process snapshot must be recognized: ${current.warnings}", current.processesKnown)
                    val observed = current.processes.any {
                        it.uid == fixture.uid && it.command == FIXTURE && (it.cpuPercent ?: 0.0) > 0.0 && (it.rssKb ?: 0) >= 8192
                    } && current.processes.any {
                        it.uid == fixture.uid && it.command == "$FIXTURE:worker" && (it.rssKb ?: 0) >= 4096
                    }
                    if (!observed) delay(250)
                } while (!observed)
                current
            }
            val fixtureProcesses = sample.processes.filter { it.uid == fixture.uid }
            assertEquals("Fixture must expose its activity and worker as separate processes", 2, fixtureProcesses.size)
            val process = fixtureProcesses.single { it.command == FIXTURE }
            val worker = fixtureProcesses.single { it.command == "$FIXTURE:worker" }
            assertNotEquals("Worker service must have its own PID", process.pid, worker.pid)
            assertTrue("Worker RSS includes its resident allocation", requireNotNull(worker.rssKb) >= 4096)
            assertTrue("Fixture must have an actual PID", process.pid > 0)
            assertTrue("Command identifies the fixture", process.command.contains(FIXTURE))
            assertTrue("Activity workload must have observed CPU use", requireNotNull(process.cpuPercent) > 0.0)
            assertTrue("RSS includes the fixture's resident allocation", requireNotNull(process.rssKb) >= 8192)
            assertTrue("Virtual memory must cover resident memory", requireNotNull(process.virtualKb) >= requireNotNull(process.rssKb))
            assertTrue("CPU sample needs a known core count", requireNotNull(sample.cpuCoreCount) > 0)
            assertTrue("Per-process CPU is bounded by available cores", requireNotNull(process.cpuPercent) <= requireNotNull(sample.cpuCoreCount) * 100.0)
            assertTrue("Device CPU is normalized to all cores", requireNotNull(sample.cpuPercent) in 0.0..100.0)
            val memory = requireNotNull(sample.memory) { "System memory unavailable: ${sample.warnings}" }
            assertTrue(memory.totalKb > 0)
            assertTrue(memory.availableKb in 0..memory.totalKb)
            assertTrue(memory.swapFreeKb in 0..memory.swapTotalKb)

            val diagnostics = monitor.diagnostics(fixture)
            assertEquals(FIXTURE, diagnostics.packageName)
            assertTrue("Package PSS includes the activity and service allocations: ${diagnostics.warnings}", requireNotNull(diagnostics.pssKb) >= 12288)
            assertTrue(requireNotNull(diagnostics.privateDirtyKb) >= 0)
            assertTrue(requireNotNull(diagnostics.swapPssKb) >= 0)
            assertTrue("Running-service query must be recognized: ${diagnostics.warnings}", diagnostics.servicesKnown)
            assertEquals("Exactly the fixture worker service must be reported", 1, diagnostics.services.size)
            val service = diagnostics.services.single()
            assertEquals("$FIXTURE/.ProbeService", service.component)
            assertEquals("$FIXTURE:worker", service.process)
            assertFalse("ProbeService is a regular started service", service.foreground)

            val energy = monitor.energy()
            assertTrue("Android energy estimate must be readable: ${energy.warnings}", requireNotNull(energy.estimatedTotalMah) >= 0.0)
            assertTrue("Battery measurement duration must be readable", requireNotNull(energy.batteryRealtimeMs) >= 0)
            assertTrue("Battery estimate needs at least one UID or component", energy.uids.isNotEmpty() || energy.components.isNotEmpty())
            Log.i(TAG, "Fixture PIDs=${process.pid},${worker.pid}, CPU=${process.cpuPercent}%, RSS=${process.rssKb}+${worker.rssKb}KiB, package PSS=${diagnostics.pssKb}KiB, service=${service.component} in ${service.process}; ${sample.processes.size} processes; energy=${energy.estimatedTotalMah}mAh")

            val result = manager.forceStop(fixture)
            assertTrue(result.message, result.stopped)
            stopped = true
            withTimeout(10_000) {
                while (context.packageManager.getApplicationInfo(FIXTURE, 0).flags and ApplicationInfo.FLAG_STOPPED == 0) delay(100)
            }
            val after = monitor.sample()
            assertTrue("Post-stop snapshot must be recognized: ${after.warnings}", after.processesKnown)
            assertTrue("Fixture processes must disappear after force-stop", after.processes.none { it.uid == fixture.uid })
            val afterDiagnostics = monitor.diagnostics(fixture)
            assertTrue("Post-stop service query must be recognized: ${afterDiagnostics.warnings}", afterDiagnostics.servicesKnown)
            assertTrue("Fixture service must disappear after force-stop", afterDiagnostics.services.isEmpty())
            Log.i(TAG, "Fixture force-stop verified by manager, PackageManager FLAG_STOPPED, and later resource sample")
        } finally {
            if (launched && !stopped) {
                withContext(NonCancellable) {
                    runCatching { manager.forceStop(fixture) }
                        .onSuccess { if (!it.stopped) Log.w(TAG, "Fixture cleanup could not be verified: ${it.message}") }
                        .onFailure { Log.w(TAG, "Fixture cleanup could not be verified", it) }
                }
            }
            bridge.close()
        }
    }

    private companion object {
        const val FIXTURE = "dev.bgcheck.fixture"
        const val TAG = "DeviceResourceTest"
    }
}
