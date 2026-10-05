package dev.bgcheck.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.bgcheck.app.BackgroundViewModel
import dev.bgcheck.network.domain.model.Package
import dev.bgcheck.network.domain.model.PackageId
import dev.bgcheck.network.domain.model.PackageIdentity
import dev.bgcheck.network.presentation.viewmodel.PackagesViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class PendingPackageAction(val pkg: Package, val action: PackageAction)
internal data class PackageActionResult(val id: PackageId, val identity: PackageIdentity, val message: String)
internal data class PackageActionsState(
    val pending: PendingPackageAction? = null,
    val result: PackageActionResult? = null,
    val revision: Int = 0,
)

/** Observing a manager job belongs to the Activity, so closing the sheet still refreshes the app list. */
internal class PackageActionsViewModel(
    private val packagesModel: PackagesViewModel,
    private val onCompleted: (String) -> Unit,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PackageActionsState())
    val state = mutableState.asStateFlow()

    fun run(pkg: Package, action: PackageAction) {
        if (state.value.pending != null) return
        packagesModel.clearError()
        packagesModel.clearUninstallSuccess()
        packagesModel.clearReinstallSuccess()
        mutableState.value = state.value.copy(pending = PendingPackageAction(pkg, action), result = null)
        viewModelScope.launch {
            val message = try {
                val job = when (action) {
                    PackageAction.ENABLE, PackageAction.DISABLE -> packagesModel.setPackageEnabled(
                        pkg.packageName, pkg.userId, action == PackageAction.ENABLE, expected = pkg.identity)
                    PackageAction.UNINSTALL -> packagesModel.uninstallPackage(pkg.packageName, pkg.userId, pkg.name, expected = pkg.identity)
                    PackageAction.REINSTALL -> packagesModel.reinstallPackage(pkg.packageName, pkg.userId, pkg.name, expected = pkg.identity)
                    PackageAction.FORCE_STOP -> packagesModel.forceStopPackage(pkg.packageName, pkg.userId, expected = pkg.identity)
                }
                job.join()
                val events = packagesModel.uiState.value
                val success = when (action) {
                    PackageAction.UNINSTALL -> events.uninstallSuccess
                    PackageAction.REINSTALL -> events.reinstallSuccess
                    else -> null
                }
                packageActionResultMessage(action, pkg.name, job.isCancelled, events.error, success)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                error.message ?: "Couldn't complete the package action. Refresh to check ${pkg.name}’s current state."
            }
            mutableState.value = state.value.copy(pending = null,
                result = PackageActionResult(pkg.id, pkg.identity, message), revision = state.value.revision + 1)
            // The result is retained above before consuming the shared ViewModel's one-shot events.
            packagesModel.clearError()
            packagesModel.clearUninstallSuccess()
            packagesModel.clearReinstallSuccess()
            onCompleted(message)
            packagesModel.loadPackages(forceRefresh = true)
        }
    }

    fun clearResult(result: PackageActionResult) {
        if (state.value.result == result) mutableState.value = state.value.copy(result = null)
    }

    class Factory(private val packagesModel: PackagesViewModel, private val onCompleted: (String) -> Unit) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PackageActionsViewModel::class.java))
            return PackageActionsViewModel(packagesModel, onCompleted) as T
        }
    }
}

@Composable
internal fun appDetailPackageActionsViewModel(packagesModel: PackagesViewModel): PackageActionsViewModel {
    val mainModel: BackgroundViewModel = viewModel()
    return viewModel(factory = remember(packagesModel, mainModel) {
        PackageActionsViewModel.Factory(packagesModel, mainModel::onPackageActionCompleted)
    })
}
