package dev.bgcheck.app

import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bgcheck.network.De1984Application
import dev.bgcheck.network.domain.firewall.FirewallMode
import dev.bgcheck.network.domain.model.FirewallRule
import dev.bgcheck.network.utils.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Two explicit phases; traffic is tested externally after reboot, before reopening the host. */
@RunWith(AndroidJUnit4::class)
class NetworkBootRuntimeTest {
    @Test fun bootRestoreFixture() = runBlocking {
        val phase = InstrumentationRegistry.getArguments().getString("boot_phase")
        assumeTrue(phase == "prepare" || phase == "cleanup")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val deps = (context as De1984Application).dependencies
        val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
        if (phase == "cleanup") {
            prefs.edit().putBoolean(Constants.Settings.KEY_FIREWALL_ENABLED, false).commit()
            deps.firewallManager.stopFirewall().getOrThrow()
            deps.firewallRepository.deleteRule("dev.bgcheck.fixture", 0)
            return@runBlocking
        }
        prefs.edit().putString(Constants.Settings.KEY_FIREWALL_MODE, "vpn")
            .putBoolean(Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT, false).commit()
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "appops set dev.bgcheck.app ACTIVATE_VPN allow"
        )).use { it.readBytes() }
        val uid = context.packageManager.getApplicationInfo("dev.bgcheck.fixture", 0).uid
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
            delay(2000)
            deps.firewallManager.stopFirewall().getOrThrow()
            deps.firewallRepository.insertRule(FirewallRule("dev.bgcheck.fixture", uid = uid, appName = "Boot probe", wifiBlocked = true, mobileBlocked = true, hasInternetPermission = true))
            deps.firewallManager.startFirewall(FirewallMode.VPN).getOrThrow()
            prefs.edit().putBoolean(Constants.Settings.KEY_FIREWALL_ENABLED, true).commit()
            assertTrue(prefs.getBoolean(Constants.Settings.KEY_FIREWALL_ENABLED, false))
        }
    }
}
