package dev.bgcheck.app.ui.network

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bgcheck.app.ui.Muted
import dev.bgcheck.app.ui.Panel
import dev.bgcheck.network.presentation.viewmodel.ImportUninstalledPreview
import dev.bgcheck.network.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class NetworkSettingsBackupActions(
    val backup: () -> Unit,
    val restore: () -> Unit,
    val export: () -> Unit,
    val import: () -> Unit,
)

// Keep launchers and dialogs at the page level; a lazy item can leave composition while a picker is open.
@Composable
internal fun rememberNetworkSettingsBackupActions(model: SettingsViewModel): NetworkSettingsBackupActions {
    val state by model.uiState.collectAsStateWithLifecycle()
    var restoreUri by rememberSaveable { mutableStateOf<String?>(null) }
    var preview by rememberSaveable { mutableStateOf<String?>(null) }
    var replace by rememberSaveable { mutableStateOf(false) }
    var parseError by rememberSaveable { mutableStateOf<String?>(null) }
    var operation by rememberSaveable { mutableStateOf("Working…") }
    var importCount by rememberSaveable { mutableIntStateOf(0) }

    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { operation = "Backing up firewall rules…"; model.backupRules(it) }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { preview = null; replace = false; restoreUri = it.toString() }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { operation = "Exporting retained packages…"; model.exportUninstalledApps(it) }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { operation = "Reading the retained-package list…"; model.importUninstalledApps(it) }
    }

    LaunchedEffect(restoreUri) {
        val uri = restoreUri ?: return@LaunchedEffect
        if (preview != null) return@LaunchedEffect
        try {
            model.parseBackupFile(uri.toUri()).fold(
                onSuccess = { backupFile ->
                    val date = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(backupFile.exportDate))
                    preview = "Date: $date\nApp version: ${backupFile.appVersion}\nBackup version: ${backupFile.version}\nRules: ${backupFile.rulesCount}\n\nMerge adds these rules to your saved rules. Replace removes your saved rules first."
                },
                onFailure = { parseError = "Could not read this backup: ${it.message}"; restoreUri = null },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            parseError = "Could not load this backup: ${e.message}"
            restoreUri = null
        }
    }

    fun clearRestore() { restoreUri = null; preview = null; replace = false }
    val uri = restoreUri
    if (uri != null && preview == null) NetworkSettingsProgress("Loading backup", "Reading and validating the selected file…")
    if (uri != null && preview != null) {
        if (replace) {
            NetworkSettingsDialog("Replace all firewall rules?", "All current saved rules will be removed and replaced by the rules in this backup.", ::clearRestore, confirm = "Replace", cancel = "Cancel", onConfirm = {
                operation = "Replacing firewall rules…"
                model.restoreRules(uri.toUri(), replaceExisting = true)
                clearRestore()
            })
        } else {
            NetworkSettingsDialog("Restore firewall rules", requireNotNull(preview), ::clearRestore, confirm = "Merge", cancel = "Cancel", onConfirm = {
                operation = "Merging firewall rules…"
                model.restoreRules(uri.toUri(), replaceExisting = false)
                clearRestore()
            }, extra = {
                TextButton({ replace = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Replace…") }
            })
        }
    }
    parseError?.let { NetworkSettingsDialog("Could not restore rules", it, { parseError = null }) }

    state.importProfileSelection?.let { selection ->
        AlertDialog(
            onDismissRequest = model::clearImportProfileSelection, containerColor = Panel,
            title = { Text("Name-only list: choose target profile") },
            text = {
                Column(Modifier.heightIn(max = networkSettingsDialogMaxHeight()).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This legacy list contains ${selection.document.count} package names. Choose the only profile it may remove apps from.", color = Muted)
                    selection.profiles.forEach { profile ->
                        TextButton({ operation = "Preparing import preview…"; model.selectImportProfile(profile.userId) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(profile.label) }
                    }
                }
            },
            confirmButton = { TextButton(model::clearImportProfileSelection) { Text("Cancel") } },
        )
    }
    state.importUninstalledPreview?.let { importPreview ->
        NetworkSettingsDialog("Import retained-package list", networkSettingsImportPreview(importPreview), model::clearImportPreview,
            confirm = "Uninstall ${importPreview.packagesToUninstall.size} apps", cancel = "Cancel", onConfirm = {
                importCount = importPreview.packagesToUninstall.size
                operation = "Removing $importCount apps from the selected profiles…"
                model.confirmImportUninstall()
            })
    }
    state.batchUninstallResult?.let { result ->
        val text = buildString {
            appendLine("Removed: ${result.succeeded.size}")
            appendLine(networkSettingsPackageSummary(result.succeeded))
            appendLine("\nFailed: ${result.failed.size}")
            append(networkSettingsPackageSummary(result.failed.map { (name, error) -> "$name: $error" }))
        }
        NetworkSettingsDialog("Import results", text, model::clearBatchUninstallResult)
    }
    if (state.isLoading && state.error == null) NetworkSettingsProgress("Backup and restore", operation)

    return NetworkSettingsBackupActions(
        backup = { backup.launch(networkSettingsBackupFileName(LocalDate.now())) },
        restore = { restore.launch(arrayOf("application/json")) },
        export = { export.launch(networkSettingsPackageFileName(LocalDate.now())) },
        import = { import.launch(arrayOf("text/plain", "text/*")) },
    )
}

internal fun networkSettingsPackageSummary(names: List<String>): String = buildString {
    append(names.take(10).joinToString("\n") { "• $it" })
    if (names.size > 10) append("\n…and ${names.size - 10} more")
}

internal fun networkSettingsImportPreview(preview: ImportUninstalledPreview): String = buildString {
    appendLine(preview.legacyProfile?.let { "This name-only list will remove apps only from $it." }
        ?: "Apps will be removed only from the profiles recorded in this file.")
    appendLine("\nRemovable: ${preview.packagesToUninstall.size} of ${preview.totalPackages}")
    appendLine(networkSettingsPackageSummary(preview.packagesToUninstall.map { (name, userId) -> preview.targetLabel(name, userId) }))
    appendLine("\nMissing: ${preview.packagesNotFound.size}")
    appendLine(networkSettingsPackageSummary(preview.packagesNotFound))
    appendLine("\nProtected and skipped: ${preview.packagesProtected.size}")
    appendLine(networkSettingsPackageSummary(preview.packagesProtected))
    append("\nImporting uninstalls the eligible apps. Their retained packages can be reinstalled later.")
}
