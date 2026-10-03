package dev.bgcheck.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.bgcheck.app.data.BatteryInfo
import dev.bgcheck.app.data.ResourceHistoryPoint
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ResourceCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Panel), border = androidx.compose.foundation.BorderStroke(1.dp, Outline)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Purple)
            content()
        }
    }
}

@Composable
internal fun ResourceValue(label: String, value: String, accent: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Muted)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
            color = if (accent) Cyan else Foreground, textAlign = TextAlign.End)
    }
}

@Composable
internal fun SourceNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Muted)
}

@Composable
internal fun BatteryFlowCard(battery: BatteryInfo) {
    ResourceCard("Battery flow now") {
        ResourceValue("Battery level", battery.percent?.let { "$it%" } ?: "Unavailable")
        ResourceValue("State", battery.powerState.label)
        ResourceValue("Current", battery.currentMa?.let { signedDecimal(it) + " mA" } ?: "Unavailable")
        ResourceValue("Estimated power", battery.powerMw?.let { signedDecimal(it / 1000.0, 2) + " W" } ?: "Unavailable")
        ResourceValue("Voltage", battery.voltageMv?.let { decimal(it / 1000.0, 2) + " V" } ?: "Unavailable")
        ResourceValue("Temperature", battery.temperatureC?.let { decimal(it.toDouble()) + " °C" } ?: "Unavailable")
        battery.capturedAt?.let { SourceNote("Battery reading ${resourceTimeLabel(it)}") }
        SourceNote("Positive values flow into the battery; negative values flow out. Power is current × voltage for the whole battery, not an app’s power draw. Current may be unavailable on this device.")
        SourceNote("Zero battery flow while plugged in does not mean the phone uses no power; the adapter may supply it directly.")
    }
}

@Composable
internal fun ResourceTrends(history: List<ResourceHistoryPoint>, totalMemoryKb: Long?) {
    val points = history.takeLast(60)
    ResourceCard("Session trend") {
        if (points.isEmpty()) {
            SourceNote("Live samples appear here while the Monitor is open. No background monitoring.")
        } else {
            Sparkline("Device CPU", points.map { it.cpuPercent }, Cyan, { percent(it) })
            Sparkline("RAM used", points.map { point ->
                if (totalMemoryKb != null && totalMemoryKb > 0) point.usedMemoryKb?.let { it * 100.0 / totalMemoryKb } else null
            }, Pink, { percent(it) })
            SourceNote("${points.size} samples · ${timeLabel(points.first().capturedAt)}–${timeLabel(points.last().capturedAt)}. CPU is normalized across all cores. Gaps mean unavailable data. History stays in this foreground session.")
        }
    }
}

@Composable
private fun Sparkline(label: String, values: List<Double?>, color: Color, format: (Double?) -> String) {
    val known = values.filterNotNull()
    val summary = if (known.isEmpty()) "$label unavailable" else
        "$label: latest ${format(values.lastOrNull())}, minimum ${format(known.minOrNull())}, maximum ${format(known.maxOrNull())}"
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ResourceValue(label, format(values.lastOrNull()))
        Canvas(Modifier.fillMaxWidth().height(46.dp).semantics { contentDescription = summary }) {
            drawLine(Outline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            values.forEachIndexed { index, value ->
                if (value != null) {
                    val x = if (values.size == 1) size.width / 2f else index * size.width / (values.size - 1)
                    val y = size.height * (1f - (value / 100.0).coerceIn(0.0, 1.0).toFloat())
                    val previous = values.getOrNull(index - 1)
                    if (previous != null) {
                        val previousX = (index - 1) * size.width / (values.size - 1)
                        val previousY = size.height * (1f - (previous / 100.0).coerceIn(0.0, 1.0).toFloat())
                        drawLine(color, Offset(previousX, previousY), Offset(x, y), strokeWidth = 2.dp.toPx())
                    } else drawCircle(color, 2.dp.toPx(), Offset(x, y))
                }
            }
        }
    }
}

internal fun decimal(value: Double, places: Int = 1): String = String.format(Locale.getDefault(), "%.${places}f", value)
internal fun signedDecimal(value: Double, places: Int = 1): String = String.format(Locale.getDefault(), "%+.${places}f", value)
internal fun percent(value: Double?): String = value?.let { "${decimal(it)}%" } ?: "Unavailable"
internal fun memoryLabel(kb: Long?): String = when {
    kb == null -> "Unavailable"
    kb >= 1024L * 1024 -> "${decimal(kb / (1024.0 * 1024), 2)} GiB"
    kb >= 1024 -> "${decimal(kb / 1024.0)} MiB"
    else -> "$kb KiB"
}
internal fun bytesLabel(bytes: Long?): String = when {
    bytes == null -> "Unavailable"
    bytes >= 1024L * 1024 * 1024 -> "${decimal(bytes / (1024.0 * 1024 * 1024), 2)} GiB"
    bytes >= 1024L * 1024 -> "${decimal(bytes / (1024.0 * 1024), 2)} MiB"
    bytes >= 1024 -> "${decimal(bytes / 1024.0)} KiB"
    else -> "$bytes B"
}
internal fun durationLabel(ms: Long?): String = when {
    ms == null -> "Unavailable"
    ms >= 86_400_000L -> "${ms / 86_400_000}d ${(ms / 3_600_000) % 24}h"
    ms >= 3_600_000L -> "${ms / 3_600_000}h ${(ms / 60_000) % 60}m"
    ms >= 60_000L -> "${ms / 60_000}m ${(ms / 1000) % 60}s"
    else -> "${decimal(ms / 1000.0)}s"
}
internal fun mahLabel(mah: Double?): String = mah?.let { "${decimal(it, 2)} mAh" } ?: "Unavailable"

internal fun resourceTimeLabel(timestamp: Long): String = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(timestamp))
