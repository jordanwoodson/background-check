package dev.bgcheck.app.ui.network

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.bgcheck.network.De1984Application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class VpnFallbackState(val requestBatteryOptimization: Boolean = false, val error: String? = null)

internal class VpnFallbackViewModel(
    private val applicationScope: CoroutineScope,
    private val startFallback: suspend () -> Unit,
    private val isActive: () -> Boolean,
    private val isBatteryOptimizationDisabled: () -> Boolean,
) : ViewModel() {
    private val mutableState = MutableStateFlow(VpnFallbackState())
    val state = mutableState.asStateFlow()
    private var startJob: Job? = null

    fun start() {
        if (startJob?.isActive == true) return
        mutableState.value = VpnFallbackState()
        // The manager's startup and the follow-up decision must outlive Activity recreation.
        startJob = applicationScope.launch {
            try {
                startFallback()
                mutableState.value = VpnFallbackState(requestBatteryOptimization = isActive() && !isBatteryOptimizationDisabled())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = VpnFallbackState(error = error.message ?: "Couldn't start VPN")
            }
        }
    }

    fun clearResult() { mutableState.value = VpnFallbackState() }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(VpnFallbackViewModel::class.java))
            val deps = (application as De1984Application).dependencies
            return VpnFallbackViewModel(deps.applicationScope, { deps.firewallManager.startVpnFallbackManually() },
                deps.firewallManager::isActive, deps.permissionManager::isBatteryOptimizationDisabled) as T
        }
    }
}
