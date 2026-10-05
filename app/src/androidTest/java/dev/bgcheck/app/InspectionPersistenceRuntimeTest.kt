package dev.bgcheck.app

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bgcheck.app.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InspectionPersistenceRuntimeTest {
    @Test fun newActivityAndViewModelRestorePinsIntervalAndComparison() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("inspection_preferences", 0)
        val oldPins = prefs.getString("pins", null)
        val oldInterval = prefs.getLong("monitor.interval", 5_000)
        val store = BatteryComparisonStore(context)
        val oldComparison = store.load()
        var pinned: AppEntry? = null
        var saved: BatteryComparisonSession? = null
        try {
            check(prefs.edit().remove("pins").remove("monitor.interval").commit())
            store.clear()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: BackgroundViewModel
                scenario.onActivity { model = ViewModelProvider(it)[BackgroundViewModel::class.java] }
                await { !model.state.value.loading && !model.state.value.refreshing }
                val app = model.state.value.apps.single { it.packageName == "dev.bgcheck.fixture" && it.userId == 0 }
                assertNotNull(PinnedApp.from(app))
                pinned = app
                scenario.onActivity { model.setMonitorInterval(10_000); model.togglePin(app) }
                await { model.state.value.pins.any { it.matches(app) } }
                saved = BatteryComparisonSession(BatteryComparisonSample.capture(
                    EnergySnapshot(uids = listOf(UidEnergy(app.uid, cpuTimeMs = 12)), startClockTimeMs = 1, capturedAt = 2),
                    listOf(app), listOf(app.profile)))
                store.save(saved!!)
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: BackgroundViewModel
                scenario.onActivity { model = ViewModelProvider(it)[BackgroundViewModel::class.java] }
                assertEquals(10_000L, model.state.value.monitorIntervalMs)
                assertTrue(model.state.value.pins.any { it.matches(pinned!!) })
                assertEquals(saved, model.state.value.batteryComparison)
            }
        } finally {
            val preferencesRestored = prefs.edit().putString("pins", oldPins).putLong("monitor.interval", oldInterval).commit()
            try {
                if (oldComparison == null) store.clear() else store.save(oldComparison)
            } finally {
                check(preferencesRestored) { "Could not restore the original inspection preferences" }
            }
        }
    }

    private fun await(condition: () -> Boolean) {
        val end = System.nanoTime() + 90_000_000_000L
        while (!condition() && System.nanoTime() < end) Thread.sleep(100)
        assertTrue("App state did not settle", condition())
    }
}
