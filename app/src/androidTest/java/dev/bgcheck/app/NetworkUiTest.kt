package dev.bgcheck.app

import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bgcheck.network.De1984Application
import dev.bgcheck.network.R as NetworkR
import dev.bgcheck.network.utils.Constants
import org.junit.Assert.*
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkUiTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun settingsSelectionsGrowToFitTheirText() {
        val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
        val original = prefs.getBoolean(Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT, true)
        prefs.edit().putBoolean(Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT, false).commit()
        try {
            ActivityScenario.launch<android.app.Activity>(Intent(context, dev.bgcheck.network.ui.MainActivity::class.java)).use {
                onView(withId(NetworkR.id.settingsFragment)).perform(click())
                listOf(NetworkR.id.backend_selection_dropdown, NetworkR.id.language_selection_dropdown).forEach { id ->
                    onView(withId(id)).perform(scrollTo()).check { view, failure ->
                        if (failure != null) throw failure
                        val text = view as android.widget.TextView
                        val layout = requireNotNull(text.layout)
                        val available = text.height - text.compoundPaddingTop - text.compoundPaddingBottom
                        assertTrue("Selected setting text must fit vertically", layout.height <= available)
                        assertEquals("The whole selected setting must be laid out", text.text.length, layout.getLineEnd(layout.lineCount - 1))
                        for (line in 0 until layout.lineCount) {
                            assertEquals("Selected setting must not be ellipsized", 0, layout.getEllipsisCount(line))
                        }
                    }
                }
            }
        } finally {
            prefs.edit().putBoolean(Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT, original).commit()
        }
    }

    @Test fun startupDialogShowsItsFullTitleAndAction() {
        val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
        assertFalse(prefs.getBoolean(Constants.Settings.KEY_FIREWALL_ENABLED, false))
        val original = prefs.getBoolean(Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT, true)
        prefs.edit().putBoolean(Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT, true).commit()
        try {
            ActivityScenario.launch<android.app.Activity>(Intent(context, dev.bgcheck.network.ui.MainActivity::class.java)).use {
                listOf("Start Background Check Firewall?", "Start Firewall").forEach { label ->
                    onView(withText(label)).check { view, failure ->
                        if (failure != null) throw failure
                        val text = view as android.widget.TextView
                        val layout = requireNotNull(text.layout)
                        val available = text.width - text.compoundPaddingLeft - text.compoundPaddingRight
                        assertEquals("The entire label must be laid out", text.text.length, layout.getLineEnd(layout.lineCount - 1))
                        for (line in 0 until layout.lineCount) {
                            assertEquals("No ellipsis in $label", 0, layout.getEllipsisCount(line))
                            assertTrue("Every line of $label must fit", layout.getLineWidth(line) <= available + 1)
                        }
                    }
                }
                onView(withText("Later")).perform(click())
            }
        } finally {
            prefs.edit().putBoolean(Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT, original).commit()
        }
    }

    @Test fun declinedStartupPromptStaysDismissedAcrossRecreation() {
        val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
        assertFalse("Stop firewall before this test", prefs.getBoolean(Constants.Settings.KEY_FIREWALL_ENABLED, false))
        val original = prefs.getBoolean(Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT, true)
        prefs.edit().putBoolean(Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT, true).commit()
        val intent = Intent(context, dev.bgcheck.network.ui.MainActivity::class.java)
        try {
            ActivityScenario.launch<android.app.Activity>(intent).use { scenario ->
                onView(withText("Later")).perform(click())
                onView(withId(NetworkR.id.settingsFragment)).perform(click())
                scenario.recreate()
                onView(withText("Later")).check(doesNotExist())
                onView(withId(NetworkR.id.toolbar_section_name)).check(matches(withText(NetworkR.string.nav_settings)))
            }
            // A new visit still respects the user's startup-prompt preference.
            ActivityScenario.launch<android.app.Activity>(intent).use {
                onView(withText("Later")).check(matches(isDisplayed())).perform(click())
            }
            assertFalse(prefs.getBoolean(Constants.Settings.KEY_FIREWALL_ENABLED, false))
        } finally {
            prefs.edit().putBoolean(Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT, original).commit()
        }
    }
    @Test fun appShortcutClearsConflictingFiltersAndAllScreensOpen() {
        val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT, false)
            .putString(Constants.Settings.KEY_FIREWALL_FILTER_TYPE, "system")
            .putString(Constants.Settings.KEY_FIREWALL_FILTER_STATE, "blocked")
            .putString(Constants.Settings.KEY_FIREWALL_FILTER_PROFILE, "work").commit()
        ActivityScenario.launch<android.app.Activity>(
            Intent(context, dev.bgcheck.network.ui.MainActivity::class.java).putExtra("dev.bgcheck.network.PACKAGE", "dev.bgcheck.fixture")
        ).use { scenario ->
            // The shortcut opens the target's control sheet after loading its identity.
            // Verify that destination before dismissing it to inspect the underlying inventory.
            val sheetDeadline = android.os.SystemClock.uptimeMillis() + 15_000
            while (true) {
                try {
                    onView(allOf(withId(NetworkR.id.action_sheet_package_name), withText(containsString("dev.bgcheck.fixture")), isDisplayed()))
                        .check(matches(isDisplayed()))
                    break
                } catch (failure: androidx.test.espresso.NoMatchingViewException) {
                    if (android.os.SystemClock.uptimeMillis() >= sheetDeadline) throw failure
                    Thread.sleep(100)
                }
            }
            scenario.closeNetworkAppSheet()
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
