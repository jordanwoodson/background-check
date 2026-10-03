package dev.bgcheck.app

import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bgcheck.app.data.*
import dev.bgcheck.app.shell.ShizukuBridge
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses only the disposable fixture. It never mutates another installed app. */
@RunWith(AndroidJUnit4::class)
class DevicePolicyTest {
    @Test fun realShizukuSnapshotAndReversibleControls() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixture = context.packageManager.getApplicationInfo("dev.bgcheck.fixture", 0)
        val app = AppEntry(fixture.packageName, "Background Check test app", fixture.uid, false, targetSdk = fixture.targetSdkVersion)
        val bridge = ShizukuBridge(context)
        try {
            bridge.check()
            withTimeout(30_000) { bridge.access.first { it == AccessState.READY } }
            val engine = PolicyEngine(bridge::execute, Process.myUid() / 100000)
            val snapshot = engine.snapshot()
            assertTrue("Process snapshot must be recognized: ${snapshot.warnings}", snapshot.processesKnown)
            assertTrue("Motorola must report observed processes", snapshot.processes.isNotEmpty())
            assertTrue("Doze exemptions must be readable", snapshot.exemptionsKnown)
            assertTrue("Standby buckets must be readable", snapshot.buckets.isNotEmpty())
            val original = engine.read(app)
            assertNotNull(original.backgroundMode)
            assertNotNull(original.dozeExempt)
            var current = original
            try {
                current = engine.apply(app, current, ControlAction.RESTRICT_BACKGROUND)
                assertEquals("ignore", current.backgroundMode)
                current = engine.restore(app, original, current)
                assertEquals(original.backgroundMode, current.backgroundMode)
                current = engine.apply(app, current, ControlAction.EXEMPT_DOZE)
                assertEquals(true, current.dozeExempt)
                current = engine.restore(app, original, current)
                assertEquals(original.userExempt, current.userExempt)
                assertEquals(original.dozeExempt, current.dozeExempt)
                current = engine.apply(app, current, ControlAction.ALLOW_BACKGROUND)
                assertEquals("allow", current.backgroundMode)
            } finally {
                val live = engine.read(app)
                engine.restore(app, original, live)
            }
            val restored = engine.read(app)
            assertEquals(original.backgroundMode, restored.backgroundMode)
            assertEquals(original.userExempt, restored.userExempt)
            assertEquals(original.dozeExempt, restored.dozeExempt)
        } finally { bridge.close() }
    }
}
