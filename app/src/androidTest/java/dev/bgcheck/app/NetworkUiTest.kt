package dev.bgcheck.app

import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bgcheck.network.De1984Application
import dev.bgcheck.network.R as NetworkR
import dev.bgcheck.network.utils.Constants
import org.junit.Assert.*
import org.hamcrest.Matchers.allOf
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkUiTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun appShortcutClearsConflictingFiltersAndAllScreensOpen() {
        val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT, false)
            .putString(Constants.Settings.KEY_FIREWALL_FILTER_TYPE, "system")
            .putString(Constants.Settings.KEY_FIREWALL_FILTER_STATE, "blocked")
            .putString(Constants.Settings.KEY_FIREWALL_FILTER_PROFILE, "work").commit()
        ActivityScenario.launch<android.app.Activity>(
            Intent(context, dev.bgcheck.network.ui.MainActivity::class.java).putExtra("dev.bgcheck.network.PACKAGE", "dev.bgcheck.fixture")
        ).use {
            onView(allOf(withId(NetworkR.id.search_input), isDisplayed())).check(matches(withText("dev.bgcheck.fixture")))
            assertEquals("all", prefs.getString(Constants.Settings.KEY_FIREWALL_FILTER_TYPE, null))
            assertNull(prefs.getString(Constants.Settings.KEY_FIREWALL_FILTER_STATE, null))
            assertEquals("all", prefs.getString(Constants.Settings.KEY_FIREWALL_FILTER_PROFILE, null))
            // Inventory loads asynchronously; the shortcut must find the actual package row,
            // not merely populate a search box that only matches application labels.
            val deadline = android.os.SystemClock.uptimeMillis() + 15_000
            while (true) {
                try {
                    onView(allOf(withId(NetworkR.id.package_name), withText("dev.bgcheck.fixture"), isDisplayed()))
                        .check(matches(isDisplayed()))
                    break
                } catch (failure: androidx.test.espresso.NoMatchingViewException) {
                    if (android.os.SystemClock.uptimeMillis() >= deadline) throw failure
                    Thread.sleep(100)
                }
            }
            onView(withId(NetworkR.id.packagesFragment)).perform(click())
            onView(allOf(withId(NetworkR.id.search_input), isDisplayed())).check(matches(isDisplayed()))
            onView(withId(NetworkR.id.settingsFragment)).perform(click())
            onView(withId(NetworkR.id.backup_rules_button)).check(matches(withText(NetworkR.string.settings_backup_button)))
        }
    }

    @Test fun externalTilePreferencesCannotStartFirewallWithInjectedAction() {
        val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
        assertFalse("Stop firewall before this test", prefs.getBoolean(Constants.Settings.KEY_FIREWALL_ENABLED, false))
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(
            "am start -W -n dev.bgcheck.app/dev.bgcheck.network.ui.tile.TileSettingsActivity -a dev.bgcheck.network.REQUEST_VPN_PERMISSION"
        )).use { it.readBytes() }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertFalse(prefs.getBoolean(Constants.Settings.KEY_FIREWALL_ENABLED, false))
        assertNull((context as De1984Application).dependencies.firewallManager.activeBackendType.value)
    }
}
