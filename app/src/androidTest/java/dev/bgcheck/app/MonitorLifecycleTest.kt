package dev.bgcheck.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies real foreground sampling, manual refresh while paused, and activity lifecycle. */
@RunWith(AndroidJUnit4::class)
class MonitorLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun backgroundingDuringRefreshDoesNotStartNewAppDiagnostics() {
        lateinit var model: BackgroundViewModel
        compose.runOnUiThread { model = ViewModelProvider(compose.activity)[BackgroundViewModel::class.java] }
        compose.waitUntil(30_000) { model.state.value.resources?.processesKnown == true && !model.state.value.refreshing }
        compose.runOnUiThread { model.select(model.state.value.apps.first { it.packageName == "dev.bgcheck.app" }) }
        compose.waitUntil(20_000) { model.state.value.diagnostics != null && !model.state.value.diagnosticsLoading }
        val before = model.state.value.diagnostics?.capturedAt
        compose.runOnUiThread { model.refresh(); model.onPause() }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        val deadline = System.nanoTime() + 20_000_000_000L
        while ((model.state.value.refreshing || model.state.value.diagnosticsLoading) && System.nanoTime() < deadline) Thread.sleep(100)
        Thread.sleep(1500)
        val after = model.state.value.diagnostics?.capturedAt
        val loadingInBackground = model.state.value.diagnosticsLoading
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertEquals("Refresh finishing in background must not launch new diagnostics", before, after)
        assertFalse(loadingInBackground)
    }

    @Test fun pauseManualRefreshAndBackgroundLifecycleControlSampling() {
        lateinit var model: BackgroundViewModel
        compose.runOnUiThread { model = ViewModelProvider(compose.activity)[BackgroundViewModel::class.java] }
        compose.waitUntil(30_000) { model.state.value.resources?.processesKnown == true }
        compose.onNodeWithText("Monitor").performClick()
        compose.waitUntil(20_000) { model.state.value.resourceHistory.size >= 2 }
        compose.onNodeWithText("Pause").performClick()
        compose.waitUntil(15_000) { !model.state.value.resourceLoading }
        val pausedAt = model.state.value.resources?.capturedAt
        Thread.sleep(5_500)
        assertEquals("Paused monitor must keep its captured snapshot", pausedAt, model.state.value.resources?.capturedAt)
        assertFalse(model.state.value.monitoring)

        compose.onNodeWithText("Refresh now").performClick()
        compose.waitUntil(15_000) { model.state.value.resources?.capturedAt != pausedAt }
        assertTrue("Manual refresh must not resume automatic sampling", model.state.value.monitorPaused)
        compose.onNodeWithText("Resume").performClick()
        compose.waitUntil(15_000) { model.state.value.monitoring && !model.state.value.resourceLoading }

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        Thread.sleep(1_500)
        val backgroundAt = model.state.value.resources?.capturedAt
        Thread.sleep(5_500)
        assertEquals("No samples while activity is in the background", backgroundAt, model.state.value.resources?.capturedAt)
        assertFalse(model.state.value.monitoring)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(20_000) { model.state.value.monitoring && model.state.value.resources?.capturedAt != backgroundAt }
    }
}
