package dev.bgcheck.app.ui.network

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.bgcheck.app.data.AppEntry
import dev.bgcheck.app.ui.*
import dev.bgcheck.network.domain.firewall.BlockingContext
import dev.bgcheck.network.domain.firewall.FirewallBackendType
import dev.bgcheck.network.domain.model.NetworkPackage
import dev.bgcheck.network.domain.model.InternetRecommendation
import dev.bgcheck.network.domain.model.InternetRecommendations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NetworkAppSheet(pkg: NetworkPackage, mainApp: AppEntry?, profileName: String,
    backend: FirewallBackendType?, blockingContext: BlockingContext, granular: Boolean, limited: Boolean,
    hasCellular: Boolean, onDismiss: () -> Unit, onSettings: () -> Unit, onOpenAppSettings: () -> Unit,
    onAppDetails: (AppEntry) -> Unit, onChange: (NetworkControl, Boolean) -> Unit) {
    val context = LocalContext.current
    val recommendation = rememberInternetRecommendation(pkg)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Ink,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), modifier = Modifier.testTag("network-sheet")) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(pkg.packageName, userId = pkg.userId, userSerial = pkg.userSerial)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(pkg.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text("$profileName · User ${pkg.userId}", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onOpenAppSettings, modifier = Modifier.testTag("network-app-settings")) { Icon(Icons.Rounded.Settings, "Android app settings") }
            }
            Text(pkg.packageName, color = Cyan, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = "Copy package name") {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Package name", pkg.packageName))
                    Toast.makeText(context, "Package name copied", Toast.LENGTH_SHORT).show()
                }.padding(vertical = 12.dp))
            if (mainApp != null) OutlinedButton(onClick = { onDismiss(); onAppDetails(mainApp) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("network-app-details")) { Text("App details") }
            InternetAdviceCard(recommendation)
            NetworkAccessControls(pkg, backend, blockingContext, granular, limited, hasCellular, onChange, onSettings)
        }
    }
}

@Composable
internal fun InternetAdviceCard(recommendation: InternetRecommendation) {
    val uriHandler = LocalUriHandler.current
    Disclosure("Internet advice", "${recommendation.advice.label}. ${recommendation.reason}", modifier = Modifier.testTag("internet-advice")) {
        Text(recommendation.consequence, style = MaterialTheme.typography.bodyMedium)
        recommendation.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
        recommendation.afterInstall?.let {
            Text("If reinstalled: ${it.advice.label}. ${it.reason} ${it.consequence}", style = MaterialTheme.typography.bodySmall)
        }
        Text(InternetRecommendations.GUIDANCE_NOTE, style = MaterialTheme.typography.bodySmall, color = Orange)
        recommendation.sourceUrl?.let { source ->
            TextButton(onClick = { runCatching { uriHandler.openUri(source) } }) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Source")
            }
        }
    }
}

@Composable
internal fun rememberInternetRecommendation(pkg: NetworkPackage): dev.bgcheck.network.domain.model.InternetRecommendation {
    val context = LocalContext.current
    val sharedUid = remember(pkg.uid) {
        pkg.uid >= 0 && (runCatching { context.packageManager.getPackagesForUid(pkg.uid)?.size }.getOrNull() ?: 0) > 1
    }
    return remember(pkg, sharedUid) { pkg.internetRecommendation(sharedUid) }
}
