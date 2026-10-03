package dev.bgcheck.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bgcheck.app.ui.BackgroundCheckApp
import dev.bgcheck.app.ui.BackgroundCheckTheme

class MainActivity : ComponentActivity() {
    private val model: BackgroundViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            BackgroundCheckTheme {
                BackgroundCheckApp(
                    state = model.state.collectAsStateWithLifecycle().value,
                    onRefresh = model::refresh, onConnect = model::connect, onSelect = model::select,
                    onDismissDetail = model::dismissDetail, onAction = model::apply, onUndo = model::undo,
                    onOpenAppSettings = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$it"))) },
                    onOpenShizuku = { open(packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                        ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))) },
                    onOpenWirelessSettings = { open(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) },
                    onClearMessage = model::clearMessage,
                    onRefreshResources = model::refreshResources,
                    onRefreshEnergy = model::refreshEnergy,
                    onSetMonitorPaused = model::setMonitorPaused,
                    onSetMonitorInterval = model::setMonitorInterval,
                    onMonitorVisible = model::setMonitorVisible,
                    onForceStop = model::forceStop,
                )
            }
        }
    }
    override fun onResume() { super.onResume(); model.onResume() }
    override fun onPause() { model.onPause(); super.onPause() }
    private fun open(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, "Couldn't open this screen. You can open it from Android Settings.", Toast.LENGTH_LONG).show()
        }
    }
}
