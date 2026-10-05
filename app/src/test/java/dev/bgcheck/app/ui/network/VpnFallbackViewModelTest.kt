package dev.bgcheck.app.ui.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VpnFallbackViewModelTest {
    @Test fun recreationDuringStartLeavesStartupAndBatteryDecisionRunning() = runBlocking {
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val completeStart = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        var starts = 0
        val model = VpnFallbackViewModel(applicationScope, {
            starts++
            completeStart.await()
        }, { true }, { false })
        try {
            activityScope.launch { model.state.collect {} }
            model.start()
            model.start()
            assertEquals(1, starts)
            activityScope.cancel()
            assertFalse(model.state.value.requestBatteryOptimization)
            applicationScope.launch {
                model.state.collect { if (it.requestBatteryOptimization) finished.complete(Unit) }
            }
            completeStart.complete(Unit)
            finished.await()
            assertTrue(model.state.value.requestBatteryOptimization)
            assertNull(model.state.value.error)
            model.clearResult()
            assertFalse(model.state.value.requestBatteryOptimization)
        } finally {
            activityScope.cancel()
            applicationScope.cancel()
        }
    }

    @Test fun inactiveOrAlreadyExemptFirewallDoesNotPromptForBatteryAccess() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            listOf(false to false, false to true, true to true).forEach { (active, exempt) ->
                val model = VpnFallbackViewModel(scope, {}, { active }, { exempt })
                model.start()
                assertFalse(model.state.value.requestBatteryOptimization)
                assertNull(model.state.value.error)
            }
        } finally { scope.cancel() }
    }

    @Test fun failedStartRetainsAnErrorForTheRecreatedActivity() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val model = VpnFallbackViewModel(scope, { error("VPN refused") }, { true }, { false })
            model.start()
            assertEquals("VPN refused", model.state.value.error)
            assertFalse(model.state.value.requestBatteryOptimization)
            model.clearResult()
            assertNull(model.state.value.error)
        } finally { scope.cancel() }
    }
}
