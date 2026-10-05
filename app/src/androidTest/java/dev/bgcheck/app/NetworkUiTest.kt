package dev.bgcheck.app

import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bgcheck.network.presentation.viewmodel.FirewallViewModel
import dev.bgcheck.network.De1984Application
import dev.bgcheck.network.ui.NetworkEntryIntents
import dev.bgcheck.network.utils.Constants
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkUiTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(NetworkTestPreferences(mapOf(
        Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT to true,
    ))).around(compose)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)

    @Test fun settingsSelectionsGrowToFitTheirText() {
        compose.openFirewallSettings()
        listOf("Auto (recommended)", "iptables", "ConnectivityManager", "NetworkPolicyManager", "VPN").forEach { label ->
            compose.onNodeWithTag("network-settings-list").performScrollToNode(hasText(label))
            compose.assertCompleteText(label)
        }
    }

    @Test fun startupCardShowsItsFullTitleAndAction() {
        assertFalse("Stop firewall before this test", prefs.getBoolean(Constants.Settings.KEY_FIREWALL_ENABLED, false))
        compose.openNetwork()
        compose.onNodeWithTag("network-list").performScrollToNode(hasTestTag("network-start"))
        compose.assertCompleteText("Turn on the firewall?")
        compose.assertCompleteText("Start")
        compose.onNodeWithTag("network-dismiss-start").performClick()
        compose.onNodeWithTag("network-start").assertDoesNotExist()
    }

    @Test fun declinedStartupPromptStaysDismissedAcrossRecreation() {
        assertFalse("Stop firewall before this test", prefs.getBoolean(Constants.Settings.KEY_FIREWALL_ENABLED, false))
        compose.openNetwork()
        compose.onNodeWithTag("network-list").performScrollToNode(hasTestTag("network-dismiss-start"))
        compose.onNodeWithTag("network-dismiss-start").performClick()
        compose.openFirewallSettings()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("network-settings").assertExists()
        compose.onNodeWithTag("network-page-back").performClick()
        compose.onNodeWithTag("network-dismiss-start").assertDoesNotExist()
        // Closing the activity discards the inline card's visit state; its preference remains on.
        compose.activityRule.scenario.onActivity { it.finish() }
        androidx.test.core.app.ActivityScenario.launch<MainActivity>(NetworkEntryIntents.create(context)).use {
            compose.awaitNetworkNode("network-screen")
            compose.onNodeWithTag("network-list").performScrollToNode(hasTestTag("network-dismiss-start"))
            compose.onNodeWithTag("network-dismiss-start").performClick()
        }
        assertFalse(prefs.getBoolean(Constants.Settings.KEY_FIREWALL_ENABLED, false))
    }

    @Test fun appShortcutClearsConflictingFiltersAndAllScreensOpen() {
        compose.activityRule.scenario.onActivity {
            ViewModelProvider(it)[FirewallViewModel::class.java].apply {
                setPackageTypeFilter("system")
                setNetworkStateFilter("Blocked")
                setProfileFilter("Work")
                setInternetOnlyFilter(true)
            }
        }
        compose.activityRule.scenario.onActivity { it.deliverNetworkIntent(Intent(context, MainActivity::class.java)
            .putExtra("dev.bgcheck.network.PACKAGE", "dev.bgcheck.fixture")) }
        compose.awaitNetworkNode("network-sheet")
        compose.onNode(hasText("dev.bgcheck.fixture") and hasAnyAncestor(hasTestTag("network-sheet"))).assertExists()
        compose.closeNetworkAppSheet()
        compose.onNodeWithTag("network-list").performScrollToNode(hasTestTag("network-search"))
        compose.onNodeWithTag("network-search").assertTextContains("dev.bgcheck.fixture")
        assertEquals("all", prefs.getString(Constants.Settings.KEY_FIREWALL_FILTER_TYPE, null))
        assertNull(prefs.getString(Constants.Settings.KEY_FIREWALL_FILTER_STATE, null))
        assertEquals("All", prefs.getString(Constants.Settings.KEY_FIREWALL_FILTER_PROFILE, null))
        assertFalse(prefs.getBoolean(Constants.Settings.KEY_FIREWALL_FILTER_INTERNET_ONLY, true))
        val row = "network-row-${android.os.Process.myUid() / 100000}:dev.bgcheck.fixture"
        compose.onNodeWithTag("network-list").performScrollToNode(hasTestTag(row))
        compose.onNodeWithTag(row).assertIsDisplayed()
        compose.onNodeWithTag("nav-apps").performClick()
        compose.onNodeWithTag("apps-search").assertExists()
        compose.openFirewallSettings()
        compose.onNodeWithTag("network-settings-list").performScrollToNode(hasTestTag("network-settings-backup"))
        compose.onNodeWithTag("network-settings-backup").assertTextContains("Back up firewall rules")
    }

    @Test fun launcherIntentKeepsOnlyAnExplicitNavigationTarget() {
        val intent = NetworkEntryIntents.create(context)
        assertEquals(context.packageManager.getLaunchIntentForPackage(context.packageName)?.component, intent.component)
        assertNull(intent.action)
        assertEquals(0, intent.flags)
        assertTrue(intent.getBooleanExtra(NetworkEntryIntents.EXTRA_OPEN_NETWORK, false))
        compose.activityRule.scenario.onActivity { it.deliverNetworkIntent(intent) }
        compose.awaitNetworkNode("network-screen")
        compose.activityRule.scenario.onActivity { it.deliverNetworkIntent(NetworkEntryIntents.create(context).apply {
            action = NetworkEntryIntents.ACTION_OPEN_SETTINGS
        }) }
        compose.awaitNetworkNode("network-settings")
    }

    @Test fun externalTilePreferencesCannotStartFirewallWithInjectedAction() {
        assertFalse("Stop firewall before this test", prefs.getBoolean(Constants.Settings.KEY_FIREWALL_ENABLED, false))
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(
            "am start -W -n dev.bgcheck.app/dev.bgcheck.network.ui.tile.TileSettingsActivity -a dev.bgcheck.network.REQUEST_VPN_PERMISSION --es package_name dev.bgcheck.fixture --ei user_id 10 --ez dev.bgcheck.network.extra.OPEN_NETWORK false"
        )).use { it.readBytes() }
        compose.awaitNetworkNode("network-screen")
        compose.onNodeWithTag("network-sheet").assertDoesNotExist()
        assertFalse(prefs.getBoolean(Constants.Settings.KEY_FIREWALL_ENABLED, false))
        assertNull((context as De1984Application).dependencies.firewallManager.activeBackendType.value)
        androidx.test.espresso.Espresso.pressBack()
    }
}
