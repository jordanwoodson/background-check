package dev.bgcheck.app.ui

import android.graphics.Bitmap
import android.os.UserHandle
import dev.bgcheck.network.utils.PackageUtils
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.bgcheck.app.data.AppEntry
import dev.bgcheck.app.data.AppSort
import dev.bgcheck.app.data.ProcessState
import dev.bgcheck.app.data.bucketLabel
import dev.bgcheck.app.data.debloatRecommendation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private val appIconCache = LruCache<String, Bitmap>(140)

@Composable
internal fun AppIcon(packageName: String, size: Dp = 46.dp, userId: Int = android.os.Process.myUid() / 100000, userSerial: Long? = null) {
    val context = LocalContext.current
    val iconKey = "$userId:$userSerial:$packageName"
    val bitmap by produceState<Bitmap?>(appIconCache[iconKey], iconKey) {
        value = withContext(Dispatchers.IO) {
            appIconCache[iconKey] ?: runCatching {
                PackageUtils.getPackageIcon(context, packageName, userId)?.let { icon ->
                    context.packageManager.getUserBadgedIcon(icon, UserHandle.getUserHandleForUid(userId * 100000)).toBitmap(128, 128)
                }
            }.getOrNull()?.also { appIconCache.put(iconKey, it) }
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
    Text(text, modifier, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
internal fun StatusPill(text: String, active: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(if (active) Green.copy(alpha = 0.10f) else PanelRaised)
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
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Panel)
            .border(1.dp, Outline, RoundedCornerShape(24.dp)).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(message, style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
    }
}

@Composable
internal fun AppRow(app: AppEntry, modifier: Modifier = Modifier, sort: AppSort = AppSort.NAME,
    onLongClick: (() -> Unit)? = null, selectionMode: Boolean = false, selected: Boolean = false,
    compact: Boolean = false, protectionLabel: String? = null,
    selectionRefusal: String? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Surface(modifier = modifier.testTag("app-row-${app.key}").alpha(if (selectionMode && selectionRefusal != null && !selected) 0.6f else 1f).clip(shape)
        .combinedClickable(role = if (selectionMode) Role.Checkbox else Role.Button, onClick = onClick,
            onLongClick = onLongClick, onLongClickLabel = if (onLongClick != null) "Select app" else null)
        .semantics {
            if (selectionMode) {
                toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
                selectionRefusal?.let { stateDescription = it }
            }
        },
        color = Panel, shape = shape, border = if (selected) BorderStroke(1.dp, Purple) else null) {
        Row(Modifier.fillMaxWidth().padding(if (compact) 10.dp else 14.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selectionMode) Icon(if (selected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                null, tint = if (selected) Purple else Muted, modifier = Modifier.size(24.dp))
            AppIcon(app.packageName, size = if (compact) 36.dp else 46.dp, userId = app.userId, userSerial = app.profile.serialNumber)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                ProfileLabel(app.profile)
                if (!compact) Text(app.description.text, style = MaterialTheme.typography.bodySmall, color = Foreground,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text("Cana: ${app.debloatRecommendation.rating.label}",
                    style = MaterialTheme.typography.bodySmall, color = Cyan)
                Text(
                    buildString {
                        append(if (app.system) "System" else "User")
                        protectionLabel?.let { append(" · $it") }
                        if (!app.installed) append(" · Uninstalled") else {
                            append(" · ${app.processState.label}")
                            if (!app.enabled) append(" · Disabled")
                        }
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
                sortValue?.takeUnless { compact && sort == AppSort.PACKAGE }?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = ReadingFont), color = Muted) }
            }
            if (!selectionMode) Icon(Icons.Rounded.ChevronRight, null, tint = Muted, modifier = Modifier.size(20.dp))
        }
    }
}

/** Secondary detail stays available without competing with the main task. */
@Composable
internal fun Disclosure(
    title: String,
    summary: String? = null,
    initiallyExpanded: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(180), label = "Disclosure arrow")
    Column(modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = !expanded }, color = Panel, shape = RoundedCornerShape(24.dp),
            border = null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
                }
                Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(rotation), tint = Purple)
            }
        }
        AnimatedVisibility(expanded, enter = expandVertically(tween(220)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(120))) {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

internal fun timeLabel(timestamp: Long?): String = timestamp?.let {
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))
} ?: "Not refreshed"

internal fun dateTimeLabel(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
