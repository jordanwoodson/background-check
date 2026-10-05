package dev.bgcheck.app.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.bgcheck.app.data.AppEntry
import dev.bgcheck.network.De1984Application
import dev.bgcheck.network.domain.model.Package
import dev.bgcheck.network.domain.model.PackageCriticality
import dev.bgcheck.network.presentation.viewmodel.PackagesViewModel
import dev.bgcheck.network.utils.PackageSafetyLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun packagesViewModelFactory(application: Application): PackagesViewModel.Factory {
    val deps = (application as De1984Application).dependencies
    return PackagesViewModel.Factory(application, deps.provideGetPackagesUseCase(), deps.provideManagePackageUseCase(),
        deps.superuserBannerState, deps.rootManager, deps.shizukuManager, deps.packageDataChanged)
}

internal data class AppDetailPackageData(
    val pkg: Package? = null,
    val criticality: PackageCriticality? = null,
    val category: String? = null,
    val affects: List<String> = emptyList(),
    val loading: Boolean = true,
)

@Composable
internal fun rememberAppDetailPackage(app: AppEntry, model: PackagesViewModel, revision: Int): AppDetailPackageData {
    val context = LocalContext.current
    val deps = (context.applicationContext as De1984Application).dependencies
    val state by model.uiState.collectAsStateWithLifecycle()
    val root by model.rootManager.rootStatus.collectAsStateWithLifecycle()
    val shizuku by model.shizukuManager.shizukuStatus.collectAsStateWithLifecycle()
    val data by produceState(AppDetailPackageData(), app.key, app.uid, app.profile.serialNumber, app.firstInstallTime,
        app.installed, app.enabled, state.packages, state.isLoadingData, root, shizuku, revision) {
        value = AppDetailPackageData()
        value = withContext(Dispatchers.IO) {
            // Repository lookup is independent of the Packages tab's search and filters.
            val pkg = if (app.installed) deps.packageRepository.getPackage(app.packageName, app.userId).getOrNull()
            else deps.packageRepository.getUninstalledSystemPackages().getOrNull()?.firstOrNull {
                it.packageName == app.packageName && it.userId == app.userId
            }
            AppDetailPackageData(pkg?.takeIf {
                it.uid >= 0 && it.userSerial != null && it.uid == app.uid && it.userSerial == app.profile.serialNumber && it.isInstalled == app.installed &&
                    (app.firstInstallTime == null || it.installTime == app.firstInstallTime)
            }, PackageSafetyLoader.getCriticality(context, app.packageName),
                PackageSafetyLoader.getCategory(context, app.packageName), PackageSafetyLoader.getAffects(context, app.packageName), loading = false)
        }
    }
    return data
}

@Composable
internal fun appDetailPackagesViewModel(): PackagesViewModel {
    val application = LocalContext.current.applicationContext as Application
    return viewModel(factory = remember(application) { packagesViewModelFactory(application) })
}
