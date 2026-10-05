package dev.bgcheck.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bgcheck.app.data.AppEntry
import dev.bgcheck.network.R
import dev.bgcheck.network.domain.model.Package
import dev.bgcheck.network.domain.model.PackageType
import dev.bgcheck.network.presentation.viewmodel.PackagesViewModel

@Composable
internal fun AppDetailPackage(app: AppEntry, profileAvailable: Boolean, unavailableReason: String?, busy: Boolean,
    onReviewAccess: () -> Unit, onSettings: () -> Unit, onAction: (Package, PackageAction) -> Unit,
    allowCriticalUninstall: Boolean, model: PackagesViewModel, revision: Int) {
    val data = rememberAppDetailPackage(app, model, revision)
    val pkg = data.pkg
    val root by model.rootManager.rootStatus.collectAsStateWithLifecycle()
    val shizuku by model.shizukuManager.shizukuStatus.collectAsStateWithLifecycle()
    val accessReason = packageActionsDisabledReason(root, shizuku)
    val reason = when {
        !profileAvailable -> unavailableReason ?: "Resume or unlock this profile before managing its packages."
        accessReason != null -> accessReason
        data.loading -> "Reading package state…"
        pkg == null -> "This app's installation could not be verified. Refresh the app list."
        else -> null
    }
    val type = if (app.system) PackageType.SYSTEM else PackageType.USER
    val policy = packageActionPolicy(app.installed, pkg?.isEnabled ?: app.enabled, type, data.criticality, allowCriticalUninstall)
    var confirmation by rememberSaveable(app.key, app.profile.serialNumber, app.firstInstallTime) { mutableStateOf<PackageAction?>(null) }
    var confirmationKind by rememberSaveable(app.key, app.profile.serialNumber, app.firstInstallTime) { mutableStateOf<PackageConfirmation?>(null) }
    var confirmationIdentity by rememberSaveable(app.key, app.profile.serialNumber, app.firstInstallTime) { mutableStateOf<String?>(null) }
    fun confirm(action: PackageAction) {
        confirmationIdentity = pkg?.let { "${it.uid}:${it.userSerial}:${it.installTime}:${it.isInstalled}:${it.isEnabled}" }
        confirmationKind = packageConfirmation(action, policy, type)
        confirmation = action
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) model.checkRootAccess() }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) model.checkRootAccess()
        onDispose { lifecycle.removeObserver(observer) }
    }
    Column(Modifier.testTag("app-detail-package"), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Text("Package", style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("app-detail-package-heading").semantics { heading() })
        DetailValue("Protection level", if (data.loading) "Reading…" else protectionLevelLabel(data.criticality))
        Text("Protection level controls whether uninstalling is allowed. The Cana debloat recommendation is separate advice.",
            style = MaterialTheme.typography.bodySmall, color = Muted)
        if (data.category != null || data.affects.isNotEmpty()) {
            Disclosure("Protection details", data.category?.replace('-', ' ')?.replaceFirstChar { it.uppercase() }) {
                data.category?.let { DetailValue("Category", it.replace('-', ' ').replaceFirstChar { char -> char.uppercase() }) }
                if (data.affects.isNotEmpty()) {
                    Text("Affects", style = MaterialTheme.typography.titleSmall)
                    data.affects.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        reason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Orange) }
        if (accessReason != null) TextButton(onClick = onReviewAccess) { Text("Review access") }
        val enabled = reason == null && !busy
        if (app.installed) {
            val toggle = if (pkg?.isEnabled ?: app.enabled) PackageAction.DISABLE else PackageAction.ENABLE
            OutlinedButton(onClick = { confirm(toggle) }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (toggle == PackageAction.ENABLE) "Enable" else "Disable")
            }
            if (PackageAction.UNINSTALL !in policy.actions) {
                Text("${protectionLevelLabel(data.criticality)} packages are protected from uninstalling. Change ‘Allow uninstalling critical packages’ in Firewall settings to proceed.",
                    style = MaterialTheme.typography.bodySmall, color = Orange)
                TextButton(onClick = onSettings) { Text("Firewall settings") }
            }
            OutlinedButton(onClick = { confirm(PackageAction.UNINSTALL) }, enabled = enabled && PackageAction.UNINSTALL in policy.actions,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Uninstall for this profile") }
        } else {
            OutlinedButton(onClick = { confirm(PackageAction.REINSTALL) }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Reinstall") }
        }
    }
    confirmation?.let { action ->
        val currentIdentity = pkg?.let { "${it.uid}:${it.userSerial}:${it.installTime}:${it.isInstalled}:${it.isEnabled}" }
        val kind = requireNotNull(confirmationKind)
        val allowed = reason == null && !busy && action in policy.actions && currentIdentity == confirmationIdentity &&
            kind == packageConfirmation(action, policy, type)
        AppDetailPackageConfirmation(app, data, kind, allowed,
            onDismiss = { confirmation = null }, onConfirm = {
                confirmation = null
                pkg?.let { onAction(it, action) }
            })
    }
}

@Composable
private fun AppDetailPackageConfirmation(app: AppEntry, data: AppDetailPackageData, kind: PackageConfirmation,
    allowed: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typed by rememberSaveable(app.key, kind) { mutableStateOf("") }
    val word = stringResource(R.string.uninstall_dialog_confirm_word)
    val effects = data.affects.joinToString("\n") { "• $it" }
    val affects = if (effects.isEmpty()) "" else stringResource(R.string.uninstall_dialog_affects_prefix, effects)
    val message = when (kind) {
        PackageConfirmation.ENABLE -> stringResource(R.string.dialog_enable_message, app.label)
        PackageConfirmation.DISABLE_USER -> stringResource(R.string.dialog_disable_message, app.label)
        PackageConfirmation.DISABLE_SYSTEM -> stringResource(R.string.dialog_disable_system_message)
        PackageConfirmation.UNINSTALL_ESSENTIAL -> stringResource(R.string.uninstall_dialog_message_essential, app.label, affects)
        PackageConfirmation.UNINSTALL_IMPORTANT -> stringResource(R.string.uninstall_dialog_message_important, app.label, affects)
        PackageConfirmation.UNINSTALL_OPTIONAL -> if (effects.isEmpty()) stringResource(R.string.uninstall_dialog_message_optional, app.label)
            else stringResource(R.string.uninstall_dialog_message_optional_with_affects, effects)
        PackageConfirmation.UNINSTALL_BLOATWARE -> stringResource(R.string.uninstall_dialog_message_bloatware)
        PackageConfirmation.UNINSTALL_UNKNOWN_SYSTEM -> stringResource(R.string.uninstall_dialog_message_unknown_system)
        PackageConfirmation.UNINSTALL_UNKNOWN_USER -> stringResource(R.string.uninstall_dialog_message_unknown_user, app.label)
        PackageConfirmation.REINSTALL -> stringResource(R.string.reinstall_dialog_message)
    }
    val verb = when (kind) {
        PackageConfirmation.ENABLE -> "Enable"
        PackageConfirmation.DISABLE_USER, PackageConfirmation.DISABLE_SYSTEM -> "Disable"
        PackageConfirmation.REINSTALL -> "Reinstall"
        else -> "Uninstall"
    }
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val compact = with(LocalDensity.current) { windowHeight.toDp() < 480.dp }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Panel,
        modifier = if (compact) Modifier.widthIn(max = 560.dp).fillMaxWidth() else Modifier,
        properties = DialogProperties(usePlatformDefaultWidth = !compact),
        title = { Text("$verb ${app.label}?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Profile: ${app.profile.displayLabel()}")
                Text(message)
                if (verb == "Uninstall" || verb == "Reinstall") Text("This changes only the selected Android profile.", style = MaterialTheme.typography.bodySmall, color = Muted)
                if (kind == PackageConfirmation.UNINSTALL_ESSENTIAL) {
                    OutlinedTextField(value = typed, onValueChange = { typed = it }, label = { Text("Type $word") },
                        singleLine = true, modifier = Modifier.fillMaxWidth().testTag("app-detail-uninstall-confirmation"))
                }
                if (!allowed) Text("App identity, protection or access changed. Close this dialog and refresh before continuing.", color = Orange)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = allowed && (kind != PackageConfirmation.UNINSTALL_ESSENTIAL || typed == word)) { Text(verb) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
