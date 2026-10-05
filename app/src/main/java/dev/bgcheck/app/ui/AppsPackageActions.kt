package dev.bgcheck.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.bgcheck.app.data.AppEntry
import dev.bgcheck.app.data.PackageType
import dev.bgcheck.app.data.UiState
import dev.bgcheck.app.ui.network.settingsViewModelFactory
import dev.bgcheck.network.De1984Application
import dev.bgcheck.network.data.common.RootStatus
import dev.bgcheck.network.data.common.ShizukuStatus
import dev.bgcheck.network.data.common.hasPrivilegedAccess
import dev.bgcheck.network.domain.model.PackageCriticality
import dev.bgcheck.network.presentation.viewmodel.PackagesViewModel
import dev.bgcheck.network.presentation.viewmodel.SettingsViewModel
import dev.bgcheck.network.utils.Constants
import kotlinx.coroutines.launch

internal data class AppsPackageActions(
    val selectionMode: Boolean,
    val selection: List<AppsSelection>,
    val select: (AppEntry) -> Unit,
    val selectionRefusal: (AppEntry) -> String?,
    val exitSelection: () -> Unit,
    val startBatch: () -> Unit,
    val export: () -> Unit,
    val privilegeMessage: String?,
    val profileWarnings: List<String>,
    val scanFailed: Boolean,
    val retry: () -> Unit,
    val showIcons: Boolean,
)

@Composable
internal fun rememberAppsPackageActions(mainState: UiState, visible: List<AppEntry>, type: PackageType,
    protection: Map<String, PackageCriticality>, protectionLoaded: Boolean, snackbar: SnackbarHostState, onRefresh: () -> Unit): AppsPackageActions {
    val context = LocalContext.current
    val application = context.applicationContext as De1984Application
    val deps = application.dependencies
    val model: PackagesViewModel = viewModel(factory = remember(application) {
        PackagesViewModel.Factory(application, deps.provideGetPackagesUseCase(), deps.provideManagePackageUseCase(),
            deps.superuserBannerState, deps.rootManager, deps.shizukuManager, deps.packageDataChanged)
    })
    val settingsModel: SettingsViewModel = viewModel(factory = remember(application) { settingsViewModelFactory(application) })
    val state by model.uiState.collectAsStateWithLifecycle()
    val settings by settingsModel.uiState.collectAsStateWithLifecycle()
    val root by model.rootManager.rootStatus.collectAsStateWithLifecycle()
    val shizuku by model.shizukuManager.shizukuStatus.collectAsStateWithLifecycle()
    val rootBanner by model.showRootBanner.collectAsStateWithLifecycle()
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var entries by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var confirmation by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var preview by rememberSaveable { mutableStateOf("") }
    var reinstall by rememberSaveable { mutableStateOf(false) }
    var running by rememberSaveable { mutableStateOf(false) }
    var batchError by rememberSaveable { mutableStateOf<String?>(null) }
    var batchNames by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var needsAccess by rememberSaveable { mutableStateOf(false) }
    val selection = entries.mapNotNull(AppsSelection::decode)
    val scope = rememberCoroutineScope()
    val available = hasPrivilegedAccess(root, shizuku)
    val visiblePackages = appsVisiblePackages(visible, protection)
    fun message(text: String) { scope.launch { snackbar.showSnackbar(text, withDismissAction = true) } }
    fun exitSelection() { entries = emptyList(); selectionMode = false; confirmation = emptyList(); needsAccess = false }
    fun refusal(app: AppEntry): String? = when {
        !app.installed && type != PackageType.UNINSTALLED -> "Choose the Uninstalled type to select this package for reinstall."
        app.system && !protectionLoaded -> "Protection levels are still loading. Try selecting this package again in a moment."
        else -> appsSelectionRefusal(app, protection[app.packageName], settings.allowCriticalPackageUninstall)
    }
    fun selectedVisible() = appsBatchPackages(visible, selection, type, protection, settings.allowCriticalPackageUninstall)
    fun finishBatch() {
        model.clearBatchUninstallResult()
        model.clearBatchReinstallResult()
        batchError = null
        running = false
        exitSelection()
        onRefresh()
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            if (visiblePackages.size != visible.size) message("The visible list changed or could not be verified. Refresh before exporting.")
            else model.exportVisiblePackages(uri, visiblePackages)
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scope.launch {
                model.shizukuManager.checkShizukuStatus()
                model.rootManager.checkRootStatus()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(mainState.apps, mainState.uninstalledApps, mainState.loading, mainState.refreshing, protection, protectionLoaded, settings.allowCriticalPackageUninstall) {
        if (!mainState.loading && !mainState.refreshing && !running && protectionLoaded) {
            val current = (mainState.apps + mainState.uninstalledApps).associateBy { AppsSelection.from(it).id }
            entries = selection.filter { entry -> current[entry.id]?.let {
                AppsSelection.from(it).identity == entry.identity &&
                    appsSelectionRefusal(it, protection[it.packageName], settings.allowCriticalPackageUninstall) == null
            } == true }.map(AppsSelection::encode)
        }
    }
    LaunchedEffect(state.isRenderingUI) { if (state.isRenderingUI) model.setUIReady() }
    LaunchedEffect(state.exportSuccess) {
        state.exportSuccess?.let { model.clearExportSuccess(); snackbar.showSnackbar(it, withDismissAction = true) }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            model.clearError()
            if (running) { running = false; batchError = it }
            else snackbar.showSnackbar(it, withDismissAction = true)
        }
    }
    LaunchedEffect(rootBanner) {
        if (rootBanner) { needsAccess = true; model.dismissRootBanner() }
    }
    LaunchedEffect(state.batchUninstallResult, state.batchReinstallResult) {
        if (state.batchUninstallResult != null || state.batchReinstallResult != null) running = false
    }
    BackHandler(selectionMode && confirmation.isEmpty() && !running && state.batchUninstallResult == null && state.batchReinstallResult == null && batchError == null) { exitSelection() }

    if (confirmation.isNotEmpty()) {
        AlertDialog(onDismissRequest = { confirmation = emptyList() }, containerColor = Panel,
            modifier = Modifier.testTag("apps-batch-confirmation"),
            title = { Text("${if (reinstall) "Reinstall" else "Uninstall"} ${confirmation.size} apps?") },
            text = { Text(preview, Modifier.verticalScroll(rememberScrollState())) },
            dismissButton = { TextButton(onClick = { confirmation = emptyList() }) { Text("Cancel") } },
            confirmButton = { TextButton(onClick = {
                val expected = confirmation.mapNotNull(AppsSelection::decode)
                val packages = selectedVisible().filter { pkg -> expected.any { it.id == pkg.id && it.identity == pkg.identity } }
                confirmation = emptyList()
                if (!hasPrivilegedAccess(model.rootManager.rootStatus.value, model.shizukuManager.shizukuStatus.value)) {
                    needsAccess = true
                } else if (packages.size != expected.size || reinstall != (type == PackageType.UNINSTALLED)) {
                    message("The selection changed or is unavailable. Review the visible apps before trying again.")
                } else {
                    batchNames = packages.map { "${it.packageName}|${it.name}" }
                    running = true
                    val identities = packages.associate { it.id to it.identity }
                    val targets = packages.map { it.packageName to it.userId }
                    if (reinstall) model.reinstallMultiplePackages(targets, identities)
                    else model.uninstallMultiplePackages(targets, identities)
                }
            }, modifier = Modifier.testTag("apps-confirm-batch")) { Text(if (reinstall) "Reinstall" else "Uninstall") } })
    }
    if (running) {
        AlertDialog(onDismissRequest = {}, containerColor = Panel,
            modifier = Modifier.testTag("apps-batch-progress"),
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(if (reinstall) "Reinstalling apps" else "Uninstalling apps") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = Purple)
                Text("Applying changes to ${batchNames.size} apps. Please wait.")
            } }, confirmButton = {})
    }
    val succeeded = state.batchReinstallResult?.succeeded ?: state.batchUninstallResult?.succeeded
    val failed = state.batchReinstallResult?.failed ?: state.batchUninstallResult?.failed
    if (succeeded != null || batchError != null) {
        fun label(packageName: String): String = batchNames.filter { it.substringBefore('|') == packageName }
            .map { it.substringAfter('|') }.distinct().joinToString().ifEmpty { packageName }
        val resultText = buildString {
            batchError?.let { append(it) }
            if (!succeeded.isNullOrEmpty()) {
                append("${succeeded.size} succeeded")
                succeeded.forEach { append("\n• ${label(it)}") }
            }
            if (!failed.isNullOrEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("${failed.size} failed")
                failed.forEach { (name, error) -> append("\n• ${label(name)}\n$error") }
            }
        }
        AlertDialog(onDismissRequest = { finishBatch() }, containerColor = Panel,
            modifier = Modifier.testTag("apps-batch-results"), title = { Text("Batch results") },
            text = { Text(resultText, Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { finishBatch() }) { Text("Done") } })
    }
    val privilegeMessage = if (needsAccess && !available) when {
        root == RootStatus.CHECKING || shizuku == ShizukuStatus.CHECKING -> "Checking access. Batch package actions require Shizuku or root."
        shizuku == ShizukuStatus.RUNNING_NO_PERMISSION || root == RootStatus.ROOTED_NO_PERMISSION ->
            "Permission required. Grant Shizuku or root access in the Access tab to manage packages."
        shizuku == ShizukuStatus.INSTALLED_NOT_RUNNING -> "Shizuku is not running. Start it from the Access tab to manage packages."
        else -> "No package management access. Set up Shizuku or root in the Access tab."
    } else null
    return AppsPackageActions(selectionMode, selection, select = { app ->
        val existing = selection.any { it.id == AppsSelection.from(app).id }
        val refusal = if (existing) null else refusal(app)
        when {
            refusal != null -> message(refusal)
            !existing && selection.size >= Constants.Packages.MultiSelect.MAX_SELECTION_COUNT ->
                message("Maximum ${Constants.Packages.MultiSelect.MAX_SELECTION_COUNT} apps can be selected at once.")
            else -> { selectionMode = true; entries = toggleAppsSelection(selection, app).map(AppsSelection::encode) }
        }
    }, selectionRefusal = { refusal(it) }, exitSelection = { exitSelection() }, startBatch = {
        if (!available) needsAccess = true else {
            val packages = selectedVisible()
            if (packages.isEmpty()) message("No selected apps match the visible, verified installations. Refresh and select them again.")
            else {
                reinstall = type == PackageType.UNINSTALLED
                preview = appsBatchPreview(packages, reinstall)
                confirmation = packages.map { AppsSelection(it.id, it.identity).encode() }
            }
        }
    }, export = {
        if (visiblePackages.size != visible.size) message("The visible list could not be verified. Refresh before exporting.")
        else exportLauncher.launch(appsExportFileName())
    }, privilegeMessage = privilegeMessage,
        profileWarnings = state.profileWarnings, scanFailed = state.scanFailed,
        retry = { model.loadPackages(forceRefresh = true); onRefresh() }, showIcons = settings.showAppIcons)
}

@Composable
internal fun AppsPrivilegeCard(message: String, onAccess: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("apps-privilege-explanation")) {
        InfoCard("Package management access", message, Icons.Rounded.Key, accent = Orange)
        TextButton(onClick = onAccess, modifier = Modifier.heightIn(min = 48.dp).testTag("apps-open-access")) { Text("Open Access") }
    }
}
