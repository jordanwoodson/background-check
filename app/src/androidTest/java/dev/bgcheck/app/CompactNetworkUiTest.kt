package dev.bgcheck.app

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.Checkable
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bgcheck.network.utils.Constants
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import dev.bgcheck.network.R as NetworkR

@RunWith(AndroidJUnit4::class)
class CompactNetworkUiTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun bothListLayoutsExposeDensityChoices() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            listOf(1f, 2f).forEach { fontScale ->
                val configuration = android.content.res.Configuration(context.resources.configuration).apply {
                    this.fontScale = fontScale
                }
                val scaled = context.createConfigurationContext(configuration)
                val inflater = LayoutInflater.from(ContextThemeWrapper(scaled, NetworkR.style.Theme_De1984_Views))
                listOf(NetworkR.layout.fragment_firewall, NetworkR.layout.fragment_packages).forEach { layout ->
                    val root = inflater.inflate(layout, null)
                    // Apply the same binding as the real fragment, including restoring wrapping
                    // after ToggleGroup applies its single-line defaults during inflation.
                    dev.bgcheck.network.ui.common.CompactListControls.bind(root,
                        if (layout == NetworkR.layout.fragment_firewall) dev.bgcheck.network.utils.NetworkList.FIREWALL
                        else dev.bgcheck.network.utils.NetworkList.PACKAGES) { }
                    val labels = mutableListOf<TextView>()
                    fun visit(view: View) {
                        if (view is TextView && view is Checkable) labels.add(view)
                        if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
                    }
                    visit(root)
                    val choices = labels.filter { it.text.toString() in listOf("Comfortable", "Compact") }
                    assertEquals("Each Network list must offer Comfortable and Compact choices", 2, choices.size)
                    val density = root.resources.displayMetrics.density
                    root.measure(View.MeasureSpec.makeMeasureSpec((360 * density).toInt(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec((800 * density).toInt(), View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                    choices.forEach { button ->
                        assertTrue("Density choice retains a 48dp target at font scale $fontScale",
                            button.height >= 48 * density && button.width >= 48 * density)
                        val textLayout = requireNotNull(button.layout)
                        assertEquals("Density choice text remains complete at font scale $fontScale", button.text.length,
                            textLayout.getLineEnd(textLayout.lineCount - 1))
                        assertTrue(textLayout.height <= button.height - button.compoundPaddingTop - button.compoundPaddingBottom)
                        for (line in 0 until textLayout.lineCount) assertEquals(0, textLayout.getEllipsisCount(line))
                    }
                }
            }
        }
    }

    // Catches either missing controls, a preference that is not saved, or the two lists sharing a key.
    @Test fun listDensityChoicesAreIndependentAndRestoredOnFreshActivityLaunch() {
        val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
        val keys = listOf(Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT,
            "firewall_compact_layout", "packages_compact_layout")
        val original = keys.associateWith { prefs.all[it] }
        prefs.edit().putBoolean(keys[0], false).remove(keys[1]).remove(keys[2]).commit()
        val intent = Intent(context, dev.bgcheck.network.ui.MainActivity::class.java)
        try {
            ActivityScenario.launch<android.app.Activity>(intent).use {
                onView(withId(NetworkR.id.firewallFragment)).perform(click())
                assertChoice("Comfortable", true)
                onView(allOf(withText("Compact"), isDisplayed())).perform(click())
                assertChoice("Compact", true)
                onView(withId(NetworkR.id.packagesFragment)).perform(click())
                assertChoice("Comfortable", true)
            }
            ActivityScenario.launch<android.app.Activity>(intent).use {
                onView(withId(NetworkR.id.firewallFragment)).perform(click())
                assertChoice("Compact", true)
                onView(withId(NetworkR.id.packagesFragment)).perform(click())
                assertChoice("Comfortable", true)
                onView(allOf(withText("Compact"), isDisplayed())).perform(click())
            }
            ActivityScenario.launch<android.app.Activity>(intent).use {
                onView(withId(NetworkR.id.packagesFragment)).perform(click())
                assertChoice("Compact", true)
                onView(allOf(withText("Comfortable"), isDisplayed())).perform(click())
                onView(withId(NetworkR.id.firewallFragment)).perform(click())
                assertChoice("Compact", true)
            }
        } finally {
            prefs.edit().apply {
                original.forEach { (key, value) ->
                    if (value is Boolean) putBoolean(key, value) else remove(key)
                }
            }.commit()
        }
    }

    private fun assertChoice(label: String, checked: Boolean) {
        onView(allOf(withText(label), isDisplayed())).check(matches(isDisplayed())).check { view, failure ->
            if (failure != null) throw failure
            assertEquals("Selected list density", checked, (view as Checkable).isChecked)
            val minimum = 48 * view.resources.displayMetrics.density
            assertTrue("Density control must have a 48dp touch target", view.height >= minimum && view.width >= minimum)
            val text = view as TextView
            val layout = requireNotNull(text.layout)
            assertEquals("Density label must remain complete at large font sizes", text.text.length,
                layout.getLineEnd(layout.lineCount - 1))
            assertTrue("Density label must fit vertically",
                layout.height <= text.height - text.compoundPaddingTop - text.compoundPaddingBottom)
            for (line in 0 until layout.lineCount) {
                assertEquals("Density label must not be ellipsized", 0, layout.getEllipsisCount(line))
            }
        }
    }

    // Catches stale hidden children after rebinding, lost selection, lost row details and smaller controls.
    @Test fun compactRowsRetainDetailsControlsAndSelectionWhenChangingDensity() {
        val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
        val baseline = mapOf<String, Any>(
            Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT to false,
            "firewall_compact_layout" to false,
            "packages_compact_layout" to false,
            Constants.Settings.KEY_FIREWALL_FILTER_TYPE to "all",
            Constants.Settings.KEY_FIREWALL_FILTER_PROFILE to "all",
            Constants.Settings.KEY_FIREWALL_FILTER_INTERNET_ONLY to false,
            Constants.Settings.KEY_PACKAGES_FILTER_TYPE to "all",
            Constants.Settings.KEY_PACKAGES_FILTER_PROFILE to "all",
        )
        val keys = baseline.keys + listOf(Constants.Settings.KEY_FIREWALL_FILTER_STATE,
            Constants.Settings.KEY_PACKAGES_FILTER_STATE)
        val original = keys.associateWith { prefs.all[it] }
        prefs.edit().apply {
            keys.forEach { remove(it) }
            baseline.forEach { (key, value) ->
                if (value is Boolean) putBoolean(key, value) else putString(key, value as String)
            }
        }.commit()
        try {
            ActivityScenario.launch<android.app.Activity>(Intent(context, dev.bgcheck.network.ui.MainActivity::class.java)
                .putExtra("dev.bgcheck.network.PACKAGE", "dev.bgcheck.fixture")).use { scenario ->
                awaitFixtureSheet()
                scenario.closeNetworkAppSheet()
                awaitFixtureRow()
                verifyRowDensityAndSelection(firewall = true)
                inspectFirstRow { it.performClick() }
                awaitFixtureSheet()
                onView(withId(NetworkR.id.internet_recommendation)).perform(click()).check { view, failure ->
                    if (failure != null) throw failure
                    assertTrue((view as TextView).text.contains("not the current firewall status"))
                }
                scenario.closeNetworkAppSheet()

                onView(withId(NetworkR.id.packagesFragment)).perform(click())
                onView(allOf(withId(NetworkR.id.search_input), isDisplayed()))
                    .perform(replaceText("Background Check test app"), closeSoftKeyboard())
                awaitFixtureRow()
                verifyRowDensityAndSelection(firewall = false)
                inspectFirstRow { it.performClick() }
                awaitFixtureSheet()
                onView(withId(NetworkR.id.internet_recommendation)).check { view, failure ->
                    if (failure != null) throw failure
                    assertTrue((view as TextView).text.isNotBlank())
                }
            }
        } finally {
            prefs.edit().apply {
                original.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> putBoolean(key, value)
                        is String -> putString(key, value)
                        else -> remove(key)
                    }
                }
            }.commit()
        }
    }

    private fun verifyRowDensityAndSelection(firewall: Boolean) {
        var comfortableHeight = 0
        inspectFirstRow { row ->
            comfortableHeight = row.height
            assertEquals(View.VISIBLE, row.findViewById<View>(NetworkR.id.package_name).visibility)
        }
        onView(allOf(withText("Compact"), isDisplayed())).perform(click())
        inspectFirstRow { row ->
            assertTrue("Compact rows must use less vertical space", row.height < comfortableHeight)
            assertEquals(View.GONE, row.findViewById<View>(NetworkR.id.package_name).visibility)
            if (firewall) {
                assertEquals(View.GONE, row.findViewById<View>(NetworkR.id.internet_recommendation_summary).visibility)
                listOf(NetworkR.id.wifi_container, NetworkR.id.mobile_container, NetworkR.id.roaming_container)
                    .map { row.findViewById<View>(it) }.filter { it.visibility == View.VISIBLE }.forEach { control ->
                        val minimum = 48 * control.resources.displayMetrics.density
                        assertTrue("Quick controls must retain 48dp touch targets", control.height >= minimum && control.width >= minimum)
                    }
            } else {
                assertEquals(View.VISIBLE, row.findViewById<View>(NetworkR.id.enabled_badge).visibility)
            }
            val title = row.findViewById<TextView>(NetworkR.id.app_name)
            val layout = requireNotNull(title.layout)
            assertEquals("App title stays complete at large fonts", title.text.length, layout.getLineEnd(layout.lineCount - 1))
            for (line in 0 until layout.lineCount) assertEquals(0, layout.getEllipsisCount(line))
            row.performLongClick()
        }
        onView(allOf(withText("Comfortable"), isDisplayed())).perform(click())
        inspectFirstRow { row ->
            assertEquals(View.VISIBLE, row.findViewById<View>(NetworkR.id.package_name).visibility)
            if (firewall) assertEquals(View.VISIBLE, row.findViewById<View>(NetworkR.id.internet_recommendation_summary).visibility)
            assertTrue("Changing density keeps selected apps", (row.findViewById<View>(NetworkR.id.selection_checkbox) as Checkable).isChecked)
        }
        onView(allOf(withText("Compact"), isDisplayed())).perform(click())
        inspectFirstRow { row ->
            assertTrue("Selection survives repeated density changes", (row.findViewById<View>(NetworkR.id.selection_checkbox) as Checkable).isChecked)
        }
        androidx.test.espresso.Espresso.pressBack()
    }

    private fun inspectFirstRow(block: (View) -> Unit) {
        onView(allOf(withId(NetworkR.id.packages_recycler_view), isDisplayed())).check { view, failure ->
            if (failure != null) throw failure
            val list = view as ViewGroup
            assertTrue("Fixture list must be populated", list.childCount > 0)
            block(list.getChildAt(0))
        }
    }

    private fun awaitFixtureRow() = awaitUi {
        inspectFirstRow { row ->
            assertEquals("dev.bgcheck.fixture", row.findViewById<TextView>(NetworkR.id.package_name).text.toString())
        }
    }

    private fun awaitFixtureSheet() = awaitUi {
        onView(withId(NetworkR.id.action_sheet_package_name)).check { view, failure ->
            if (failure != null) throw failure
            assertTrue((view as TextView).text.contains("dev.bgcheck.fixture"))
        }
    }

    private fun awaitUi(block: () -> Unit) {
        val deadline = android.os.SystemClock.uptimeMillis() + 20_000
        while (true) {
            try {
                block()
                return
            } catch (failure: androidx.test.espresso.NoMatchingViewException) {
                if (android.os.SystemClock.uptimeMillis() >= deadline) throw failure
            } catch (failure: AssertionError) {
                if (android.os.SystemClock.uptimeMillis() >= deadline) throw failure
            }
            Thread.sleep(100)
        }
    }
}
