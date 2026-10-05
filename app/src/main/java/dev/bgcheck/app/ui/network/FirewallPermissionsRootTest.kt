package dev.bgcheck.app.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.bgcheck.network.De1984Dependencies
import dev.bgcheck.network.utils.ShellResult
import dev.bgcheck.network.utils.ShellRunner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class FirewallPermissionsRootOutcome { GRANTED, TIMEOUT, NO_ACCESS, DENIED }

internal fun firewallPermissionsRootOutcome(result: ShellResult): FirewallPermissionsRootOutcome = when {
    result.timedOut -> FirewallPermissionsRootOutcome.TIMEOUT
    result.exitCode == 0 -> FirewallPermissionsRootOutcome.GRANTED
    result.exitCode == -1 -> FirewallPermissionsRootOutcome.NO_ACCESS
    else -> FirewallPermissionsRootOutcome.DENIED
}

internal data class FirewallPermissionsRootTestState(val testing: Boolean = false, val result: ShellResult? = null)

internal class FirewallPermissionsRootTest(private val deps: De1984Dependencies) : ViewModel() {
    private val mutableState = MutableStateFlow<FirewallPermissionsRootTestState?>(null)
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    fun start() {
        if (mutableState.value?.testing == true) return
        job?.cancel()
        deps.rootManager.markRootPermissionRequested()
        mutableState.value = FirewallPermissionsRootTestState(testing = true)
        job = viewModelScope.launch {
            val result = ShellRunner.run("root test: su -c id") { Runtime.getRuntime().exec(arrayOf("su", "-c", "id")) }
            mutableState.value = FirewallPermissionsRootTestState(result = result)
            deps.rootManager.forceRecheckRootStatus(retry = false)
        }
    }

    fun dismiss() {
        job?.cancel()
        mutableState.value = null
    }

    class Factory(private val deps: De1984Dependencies) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(FirewallPermissionsRootTest::class.java))
            return FirewallPermissionsRootTest(deps) as T
        }
    }
}
