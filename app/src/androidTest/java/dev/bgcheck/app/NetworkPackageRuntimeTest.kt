package dev.bgcheck.app

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bgcheck.network.De1984Application
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkPackageRuntimeTest {
    @Test fun shizukuCanDisableEnableAndForceStopTheDisposableFixture() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val deps = (context as De1984Application).dependencies
        val pkg = "dev.bgcheck.fixture"
        repeat(30) {
            if (deps.shizukuManager.hasShizukuPermission) return@repeat
            deps.shizukuManager.checkShizukuStatus()
            delay(100)
        }
        assertTrue("Start and authorize Shizuku for this test", deps.shizukuManager.hasShizukuPermission)
        try {
            deps.packageRepository.setPackageEnabled(pkg, 0, false).getOrThrow()
            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, context.packageManager.getApplicationEnabledSetting(pkg))
            deps.packageRepository.setPackageEnabled(pkg, 0, true).getOrThrow()
            assertTrue(context.packageManager.getApplicationInfo(pkg, 0).enabled)
            deps.packageRepository.forceStopPackage(pkg, 0).getOrThrow()
            assertTrue("Android must report stopped", deps.shizukuManager.executeShellCommand("dumpsys package $pkg").second.contains("stopped=true"))
            assertTrue("The host must protect itself", deps.packageRepository.setPackageEnabled(context.packageName, 0, false).isFailure)
        } finally {
            deps.packageRepository.setPackageEnabled(pkg, 0, true)
        }
    }
}
