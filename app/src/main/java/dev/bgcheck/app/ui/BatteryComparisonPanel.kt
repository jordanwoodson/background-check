package dev.bgcheck.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.bgcheck.app.data.BatteryComparison
import dev.bgcheck.app.data.BatteryComparisonRow
import dev.bgcheck.app.data.BatteryComparisonSession
import java.text.DateFormat
import java.util.Date

@Composable
internal fun BatteryComparisonPanel(
    session: BatteryComparisonSession?,
    loading: Boolean,
    canSample: Boolean,
    profileId: Int = ALL_PROFILES,
    onStart: () -> Unit,
    onCompare: () -> Unit,
    onClear: () -> Unit,
) {
    val result = remember(session) { session?.let(BatteryComparison::calculate) }
    var query by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable(session?.baseline?.energy?.capturedAt, query, profileId) { mutableIntStateOf(10) }
    val rows = remember(result, profileId, query) {
        val needle = query.trim()
        result?.rows.orEmpty().filter { row ->
            (profileId == ALL_PROFILES || row.uid / 100000 == profileId) &&
                (needle.isEmpty() || row.uid.toString().contains(needle) ||
                    row.identity?.profileName?.contains(needle, ignoreCase = true) == true ||
                    row.identity?.packages.orEmpty().any {
                        it.label.contains(needle, ignoreCase = true) || it.packageName.contains(needle, ignoreCase = true)
                    })
        }.sortedWith(compareByDescending<BatteryComparisonRow> { it.delta?.estimatedMah ?: Double.NEGATIVE_INFINITY }.thenBy { it.uid })
    }
    ResourceCard("Battery comparison") {
        SourceNote("Save a baseline, use your phone, then compare a fresh reading. Both samples stay on this device across app restarts until you restart or clear the comparison.")
        if (session != null) {
            ResourceValue("Baseline", comparisonTime(session.baseline.energy.capturedAt))
            session.later?.let { later ->
                ResourceValue("Later sample", comparisonTime(later.energy.capturedAt))
                ResourceValue("Elapsed interval", durationLabel(result?.intervalMs))
                if (result?.intervalMs == null && result?.wallClockSpanMs != null) {
                    ResourceValue("Recorded clock span", durationLabel(result.wallClockSpanMs))
                }
                result?.intervalNote?.let { SourceNote(it) }
            } ?: SourceNote("Baseline saved. Compare when you are ready; this session takes no background samples.")
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Sampling battery comparison" })
        if (!canSample) SourceNote("Connect Shizuku to take a fresh comparison sample.")
        if (session != null) {
            OutlinedButton(onClick = onCompare, enabled = canSample && !loading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Compare now") }
        }
        OutlinedButton(onClick = onStart, enabled = canSample && !loading,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (session == null) "Start comparison" else "Restart comparison")
        }
        if (session != null) {
            TextButton(onClick = onClear, enabled = !loading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Clear comparison") }
        }
        result?.unavailableReason?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Orange)
        }
        if (session?.later != null && result?.unavailableReason == null) {
            Disclosure("Interval changes", "${rows.size} UIDs · estimated mAh, CPU, wakelocks and traffic") {
                OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search comparison") }, supportingText = { Text("App, package, profile or UID") }, singleLine = true)
                if (rows.isEmpty()) SourceNote("No UID readings match this profile and search.")
                rows.take(limit).forEach { row ->
                    key(row.uid) {
                        ComparisonUid(row)
                        HorizontalDivider(color = Outline)
                    }
                }
                if (rows.size > limit) TextButton(onClick = { limit += 20 },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Show more (${rows.size - limit} remaining)")
                }
            }
        }
        if (session != null) {
            val warnings = (session.baseline.energy.warnings + session.later?.energy?.warnings.orEmpty()).distinct()
            if (warnings.isNotEmpty()) Disclosure("Sample limitations", "${warnings.size} source warnings") {
                warnings.forEach { SourceNote(it) }
            }
        }
        Disclosure("How to read this comparison", "UID estimates and identity safeguards") {
            SourceNote("Changes are differences between cumulative Android battery statistics. Estimated mAh is not a direct energy measurement and does not prove that a setting caused a change. Charging and usage can affect the interval.")
            SourceNote("Each UID is counted once. Apps sharing a UID share its measurements; the comparison cannot split usage between them. Personal and work profiles remain separate, and the selected profile filter applies to these results.")
            SourceNote("Missing counters stay unavailable. A history reset, backwards counter, unavailable profile, changed package membership, or unverified installation identity prevents comparison. Restart after a reset or reinstall. Elapsed interval uses device uptime, including sleep, when both samples share a verified boot. A recorded clock span is shown separately when elapsed duration cannot be verified.")
            SourceNote("Partial wakelocks can keep the CPU awake. Their duration is not energy use; CPU time can exceed elapsed time on multiple cores.")
        }
    }
}

@Composable
private fun ComparisonUid(row: BatteryComparisonRow) {
    val identity = row.identity
    val packages = identity?.packages.orEmpty()
    val title = packages.firstOrNull()?.label?.let {
        if (packages.size > 1) "$it + ${packages.size - 1} apps" else it
    } ?: "Unattributed UID"
    Disclosure("$title · UID ${row.uid}", row.unavailableReason ?: mahLabel(row.delta?.estimatedMah)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceNote("${identity?.profileName ?: "User ${row.uid / 100000}"} · user ${row.uid / 100000} · UID ${row.uid}")
            identity?.packages.orEmpty().forEach { SourceNote(it.packageName, technical = true) }
            if (identity != null && identity.packages.size > 1) SourceNote("Shared UID: these packages share every measurement below.")
            row.unavailableReason?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Orange) }
            row.delta?.let { delta ->
                ResourceValue("Estimated battery use change", mahLabel(delta.estimatedMah), accent = true)
                ResourceValue("CPU time change", durationLabel(delta.cpuTimeMs))
                ResourceValue("Partial wakelock change", durationLabel(delta.wakelockMs))
                ResourceValue("Received bytes change", bytesLabel(delta.networkRxBytes))
                ResourceValue("Sent bytes change", bytesLabel(delta.networkTxBytes))
            }
        }
    }
}

private fun comparisonTime(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
