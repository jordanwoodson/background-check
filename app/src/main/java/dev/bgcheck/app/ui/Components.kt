package dev.bgcheck.app.ui

import android.graphics.Bitmap
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.bgcheck.app.data.AppEntry
import dev.bgcheck.app.data.AppSort
import dev.bgcheck.app.data.ProcessState
import dev.bgcheck.app.data.bucketLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private val appIconCache = LruCache<String, Bitmap>(140)

@Composable
internal fun AppIcon(packageName: String, size: Dp = 46.dp) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(appIconCache[packageName], packageName) {
        value = withContext(Dispatchers.IO) {
            appIconCache[packageName] ?: runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(128, 128)
            }.getOrNull()?.also { appIconCache.put(packageName, it) }
        }
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.26f)).background(PanelRaised),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size)) }
            ?: Icon(Icons.Rounded.Apps, contentDescription = null, tint = Muted, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
internal fun Eyebrow(text: String, color: Color = Cyan, modifier: Modifier = Modifier) {
    Text("// ${text.lowercase()}", modifier, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
internal fun StatusPill(text: String, active: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(4.dp)).background(Panel)
            .border(1.dp, if (active) Green.copy(alpha = 0.4f) else Outline, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(if (active) Green else Muted))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (active) Green else Muted)
    }
}

@Composable
internal fun InfoCard(title: String, message: String, icon: ImageVector, modifier: Modifier = Modifier, accent: Color = Muted) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Panel)
            .border(1.dp, Outline, RoundedCornerShape(8.dp)).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
    }
}

@Composable
internal fun AppRow(app: AppEntry, sort: AppSort = AppSort.NAME, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Panel, shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Outline)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(app.packageName)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append(app.processState.label)
                        if (app.system) append(" · System")
                        if (!app.enabled) append(" · Disabled")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (app.processState == ProcessState.FOREGROUND || app.processState == ProcessState.SERVICE) Green else Muted,
                )
                val sortValue = when (sort) {
                    AppSort.PACKAGE -> app.packageName
                    AppSort.UID -> "UID ${app.uid}"
                    AppSort.STANDBY -> "Standby: ${bucketLabel(app.standbyBucket)}"
                    else -> null
                }
                sortValue?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Muted, modifier = Modifier.size(20.dp))
        }
    }
}

internal fun timeLabel(timestamp: Long?): String = timestamp?.let {
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))
} ?: "Not refreshed"

internal fun dateTimeLabel(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
