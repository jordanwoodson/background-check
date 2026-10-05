package dev.bgcheck.app.ui.network

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bgcheck.app.ui.Muted
import dev.bgcheck.network.BuildConfig
import dev.bgcheck.network.utils.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun NetworkLogsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val count by AppLogger.logCount.collectAsStateWithLifecycle()
    val bytes by AppLogger.logFileSize.collectAsStateWithLifecycle()
    var enabled by remember { mutableStateOf(AppLogger.isEnabledInPrefs(context)) }
    var preview by remember { mutableStateOf("") }
    var fileSize by remember { mutableStateOf("0 B") }
    var revision by remember { mutableIntStateOf(0) }
    var clear by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val previewScroll = rememberScrollState()

    LifecycleResumeEffect(Unit) {
        enabled = AppLogger.isEnabledInPrefs(context)
        revision++
        onPauseOrDispose {}
    }
    LaunchedEffect(count, bytes, revision) {
        val snapshot = withContext(Dispatchers.IO) {
            AppLogger.refreshStats()
            AppLogger.getLastLines(100) to AppLogger.getFormattedFileSize()
        }
        preview = snapshot.first
        fileSize = snapshot.second
    }
    LaunchedEffect(preview, previewScroll.maxValue) { previewScroll.scrollTo(previewScroll.maxValue) }

    Box(Modifier.fillMaxSize().testTag("network-logs")) {
        LazyColumn(Modifier.fillMaxSize().testTag("network-logs-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item { NetworkSettingsHeader("App logs", "Back to Firewall settings", onBack) }
            item {
                NetworkSettingsCard("Logging") {
                    NetworkSettingsToggle("Enable logging", "Save firewall diagnostics on this device.", enabled, "network-logs-enabled") { value ->
                        scope.launch {
                            withContext(Dispatchers.IO) { AppLogger.setLoggingEnabled(context.applicationContext, value) }
                            enabled = AppLogger.isEnabledInPrefs(context)
                            if (!value) message = if (BuildConfig.DEBUG) "Logging stays enabled in debug builds." else "Logging disabled. Existing logs are kept until you clear them."
                            revision++
                        }
                    }
                    if (BuildConfig.DEBUG) Text("Debug build: logging is always enabled.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    Text("$count lines · $fileSize", style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.testTag("network-logs-stats"))
                    NetworkSettingsAction("Clear logs", tag = "network-logs-clear") { clear = true }
                    NetworkSettingsAction("Share logs", tag = "network-logs-share") {
                        scope.launch {
                            try {
                                val file = withContext(Dispatchers.IO) {
                                    if (AppLogger.getLastLines(1).isBlank()) null else AppLogger.createExportFile(context.applicationContext)
                                }
                                if (file == null) {
                                    message = "No logs are available to share."
                                } else {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, "Background Check Debug Logs")
                                        putExtra(Intent.EXTRA_TEXT, "Background Check debug logs attached. Please include these when reporting issues.")
                                        clipData = ClipData.newRawUri("App logs", uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share app logs"))
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                message = "Could not share logs: ${e.message}"
                            }
                        }
                    }
                }
            }
            item {
                NetworkSettingsCard("Last 100 lines") {
                    SelectionContainer {
                        Text(preview.ifBlank { "No logs yet." }, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 400.dp).verticalScroll(previewScroll).testTag("network-logs-preview"))
                    }
                }
            }
        }
    }
    if (clear) NetworkSettingsDialog("Clear app logs?", "All saved app logs will be deleted.", { clear = false }, confirm = "Clear", cancel = "Cancel", onConfirm = {
        clear = false
        scope.launch {
            withContext(Dispatchers.IO) { AppLogger.clearLogs() }
            revision++
            message = "Logs cleared."
        }
    })
    message?.let { NetworkSettingsDialog("App logs", it, { message = null }) }
}
