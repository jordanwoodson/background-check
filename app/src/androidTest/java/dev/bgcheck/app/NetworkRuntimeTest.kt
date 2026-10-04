package dev.bgcheck.app

import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bgcheck.network.De1984Application
import dev.bgcheck.network.domain.firewall.FirewallBackendType
import dev.bgcheck.network.domain.firewall.FirewallMode
import dev.bgcheck.network.domain.model.FirewallRule
import dev.bgcheck.network.utils.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Only run on the disposable emulator with the fixture and host HTTP server at port 8765. */
@RunWith(AndroidJUnit4::class)
class NetworkRuntimeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val deps get() = (context as De1984Application).dependencies
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }

    private suspend fun probe(): String {
        val token = System.nanoTime().toString()
        shell("am start -S -n dev.bgcheck.fixture/.NetworkProbeActivity --es token $token")
        repeat(50) {
            val value = shell("run-as dev.bgcheck.fixture cat files/probe-$token").trim()
            if (value in setOf("ALLOWED", "BLOCKED", "INVALID_RESPONSE")) return value
            delay(100)
        }
        error("Fixture produced no traffic result")
    }

    private suspend fun expectTraffic(expected: String) {
        var result = ""
        repeat(20) {
            result = probe()
            if (result == expected) return
            delay(750)
        }
        assertEquals(expected, result)
    }

    @Test fun selectedBackendActuallyBlocksUnblocksPersistsAndStops() = runBlocking {
        assertTrue("Use the test emulator", android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk"))
        val backendName = InstrumentationRegistry.getArguments().getString("backend") ?: "VPN"
        val mode = FirewallMode.valueOf(backendName)
        val expectedBackend = FirewallBackendType.valueOf(backendName)
        val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT, false)
            .putString(Constants.Settings.KEY_FIREWALL_MODE, backendName.lowercase()).commit()
        shell("appops set dev.bgcheck.app ACTIVATE_VPN allow")
        shell("pm grant dev.bgcheck.app android.permission.POST_NOTIFICATIONS")
        val fixture = context.packageManager.getApplicationInfo("dev.bgcheck.fixture", 0)
        val rule = FirewallRule("dev.bgcheck.fixture", uid = fixture.uid, appName = "Traffic probe", hasInternetPermission = true)
        val original = deps.firewallRepository.getRuleByPackageSync(rule.packageName, 0)
        val activity = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            delay(2000)
            deps.firewallManager.stopFirewall().getOrThrow()
            expectTraffic("ALLOWED")
            deps.firewallRepository.insertRule(rule.copy(wifiBlocked = true, mobileBlocked = true))
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            delay(500)
            assertEquals(expectedBackend, deps.firewallManager.startFirewall(mode).getOrThrow())
            delay(1500)
            val limited = dev.bgcheck.network.domain.firewall.NetworkPolicySupport.isLimited(expectedBackend)
            if (limited) {
                assertTrue(shell("cmd netpolicy list restrict-background-blacklist").contains(fixture.uid.toString()))
                expectTraffic("ALLOWED") // This ROM does not implement full blocking; the UI must say so.
            } else {
                expectTraffic("BLOCKED")
            }
            if (mode == FirewallMode.IPTABLES) {
                assertEquals(0, deps.shizukuManager.executeShellCommand("iptables -D bgcheck_output -m owner --uid-owner ${fixture.uid} -j DROP").first)
                assertTrue("Kernel loss must be detected", deps.iptablesBackend.verifyEnforcement().isFailure)
                expectTraffic("BLOCKED") // The service health check repairs the missing rule.
                assertTrue(deps.iptablesBackend.verifyEnforcement().isSuccess)
            }
            assertTrue(deps.firewallRepository.getRuleByPackageSync(rule.packageName, 0)!!.wifiBlocked)
            deps.firewallRepository.updateAllNetworkBlocking(rule.packageName, 0, false)
            expectTraffic("ALLOWED")
            deps.firewallRepository.updateAllNetworkBlocking(rule.packageName, 0, true)
            expectTraffic(if (limited) "ALLOWED" else "BLOCKED")
            if (mode == FirewallMode.VPN || mode == FirewallMode.IPTABLES) {
                deps.firewallRepository.updateAllNetworkBlocking(rule.packageName, 0, false)
                deps.firewallRepository.updateBackgroundBlocking(rule.packageName, 0, true)
                shell("input keyevent 223")
                delay(500)
                assertFalse(context.getSystemService(android.os.PowerManager::class.java).isInteractive)
                expectTraffic("BLOCKED")
                shell("input keyevent 224")
                shell("wm dismiss-keyguard")
                deps.firewallRepository.updateBackgroundBlocking(rule.packageName, 0, false)
                expectTraffic("ALLOWED")
            }
            if (mode == FirewallMode.IPTABLES) {
                deps.firewallRepository.updateLanBlocking(rule.packageName, 0, true)
                expectTraffic("BLOCKED")
                deps.firewallRepository.updateLanBlocking(rule.packageName, 0, false)
                expectTraffic("ALLOWED")
            }
            deps.firewallManager.stopFirewall().getOrThrow()
            expectTraffic("ALLOWED")
        } finally {
            shell("input keyevent 224")
            shell("wm dismiss-keyguard")
            deps.firewallManager.stopFirewall()
            if (original == null) deps.firewallRepository.deleteRule(rule.packageName, 0)
            else deps.firewallRepository.insertRule(original)
            activity.close()
        }
    }
}
