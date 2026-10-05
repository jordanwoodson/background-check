package dev.bgcheck.app.ui

import dev.bgcheck.network.data.common.RootStatus
import dev.bgcheck.network.data.common.ShizukuStatus
import dev.bgcheck.network.data.common.hasPrivilegedAccess
import dev.bgcheck.network.domain.model.PackageCriticality
import dev.bgcheck.network.domain.model.PackageType

internal enum class PackageAction { ENABLE, DISABLE, UNINSTALL, REINSTALL }

internal enum class PackageConfirmation {
    ENABLE, DISABLE_USER, DISABLE_SYSTEM, UNINSTALL_ESSENTIAL, UNINSTALL_IMPORTANT,
    UNINSTALL_OPTIONAL, UNINSTALL_BLOATWARE, UNINSTALL_UNKNOWN_SYSTEM, UNINSTALL_UNKNOWN_USER, REINSTALL
}

internal data class PackageActionPolicy(val actions: Set<PackageAction>, val uninstallConfirmation: PackageConfirmation?)

internal fun packageActionPolicy(installed: Boolean, enabled: Boolean, type: PackageType,
    criticality: PackageCriticality?, allowCriticalUninstall: Boolean): PackageActionPolicy {
    if (!installed) return PackageActionPolicy(setOf(PackageAction.REINSTALL), null)
    val confirmation = when (criticality) {
        PackageCriticality.ESSENTIAL -> PackageConfirmation.UNINSTALL_ESSENTIAL
        PackageCriticality.IMPORTANT -> PackageConfirmation.UNINSTALL_IMPORTANT
        PackageCriticality.OPTIONAL -> PackageConfirmation.UNINSTALL_OPTIONAL
        PackageCriticality.BLOATWARE -> PackageConfirmation.UNINSTALL_BLOATWARE
        else -> if (type == PackageType.SYSTEM) PackageConfirmation.UNINSTALL_UNKNOWN_SYSTEM else PackageConfirmation.UNINSTALL_UNKNOWN_USER
    }
    val protected = criticality in setOf(PackageCriticality.ESSENTIAL, PackageCriticality.IMPORTANT) && !allowCriticalUninstall
    return PackageActionPolicy(buildSet {
        add(if (enabled) PackageAction.DISABLE else PackageAction.ENABLE)
        if (!protected) add(PackageAction.UNINSTALL)
    }, confirmation)
}

internal fun packageConfirmation(action: PackageAction, policy: PackageActionPolicy, type: PackageType): PackageConfirmation = when (action) {
    PackageAction.ENABLE -> PackageConfirmation.ENABLE
    PackageAction.DISABLE -> if (type == PackageType.SYSTEM) PackageConfirmation.DISABLE_SYSTEM else PackageConfirmation.DISABLE_USER
    PackageAction.UNINSTALL -> requireNotNull(policy.uninstallConfirmation)
    PackageAction.REINSTALL -> PackageConfirmation.REINSTALL
}

internal fun protectionLevelLabel(criticality: PackageCriticality?): String = when (criticality) {
    PackageCriticality.ESSENTIAL -> "Essential"
    PackageCriticality.IMPORTANT -> "Important"
    PackageCriticality.OPTIONAL -> "Optional"
    PackageCriticality.BLOATWARE -> "Bloatware"
    else -> "Not classified"
}

internal fun packageActionProgress(action: PackageAction, name: String): String = when (action) {
    PackageAction.ENABLE -> "Enabling $name…"
    PackageAction.DISABLE -> "Disabling $name…"
    PackageAction.UNINSTALL -> "Uninstalling $name for this profile…"
    PackageAction.REINSTALL -> "Reinstalling $name for this profile…"
}

internal fun packageActionResultMessage(action: PackageAction, name: String, cancelled: Boolean,
    error: String?, successEvent: String?): String {
    if (cancelled) return "The package action was interrupted. Refresh to check $name’s current state."
    if (error != null) return if (successEvent != null) "$successEvent. $error" else error
    return when (action) {
        PackageAction.ENABLE -> "$name enabled for this profile."
        PackageAction.DISABLE -> "$name disabled for this profile."
        PackageAction.UNINSTALL, PackageAction.REINSTALL -> successEvent
            ?: "The package action could not be confirmed. Refresh to check $name’s current state."
    }
}

internal fun packageActionsDisabledReason(root: RootStatus, shizuku: ShizukuStatus): String? = when {
    hasPrivilegedAccess(root, shizuku) -> null
    root == RootStatus.CHECKING || shizuku == ShizukuStatus.CHECKING -> "Checking Shizuku and root access for package actions."
    shizuku == ShizukuStatus.RUNNING_NO_PERMISSION -> "Allow Shizuku permission or grant root access to manage packages."
    root == RootStatus.ROOTED_NO_PERMISSION -> "Grant root access or connect Shizuku to manage packages."
    shizuku == ShizukuStatus.INSTALLED_NOT_RUNNING -> "Start Shizuku or grant root access to manage packages."
    else -> "Package actions require Shizuku or root access."
}
