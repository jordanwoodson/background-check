package dev.bgcheck.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.bgcheck.app.data.AppEntry
import dev.bgcheck.app.data.PackageDescriptions
import dev.bgcheck.app.data.UiState
import dev.bgcheck.app.data.internetRecommendation
import dev.bgcheck.network.domain.model.InternetRecommendations

@Composable
internal fun InternetRecommendationCard(app: AppEntry, onOpenNetwork: () -> Unit) {
    val recommendation = app.internetRecommendation
    val uriHandler = LocalUriHandler.current
    Surface(color = Panel, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()
        .testTag("internet-recommendation")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Internet firewall recommendation", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            Text(recommendation.advice.label, style = MaterialTheme.typography.titleSmall, color = Cyan)
            Text(recommendation.reason, style = MaterialTheme.typography.bodySmall)
            Text(recommendation.consequence, style = MaterialTheme.typography.bodySmall, color = Muted)
            recommendation.afterInstall?.let { restored ->
                Text("If reinstalled: ${restored.advice.label}", style = MaterialTheme.typography.titleSmall, color = Purple)
                Text("${restored.reason} ${restored.consequence}", style = MaterialTheme.typography.bodySmall)
            }
            Disclosure("Recommendation details", "Permissions, scope and source") {
                recommendation.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
                Text(InternetRecommendations.GUIDANCE_NOTE, style = MaterialTheme.typography.bodySmall, color = Muted)
                recommendation.sourceUrl?.let { url ->
                    TextButton(onClick = { uriHandler.openUri(url) }) { Text("Recommendation source") }
                }
            }
            if (app.installed) TextButton(onClick = onOpenNetwork) { Text("Network and internet access") }
        }
    }
}

@Composable
internal fun PackageSources(app: AppEntry) {
    app.description.note?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
    if (app.description.citations.isEmpty()) return
    var expanded by remember(app.packageName) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    Column {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Hide description sources" else "Description sources (${app.description.citations.size})")
        }
        if (expanded) app.description.citations.forEach { citation ->
            TextButton(onClick = { uriHandler.openUri(citation.url) }) {
                Text(citation.title, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun AssociatedPackages(state: UiState, app: AppEntry, onSelect: (AppEntry) -> Unit) {
    val entries = remember(state.apps, state.uninstalledApps) {
        (state.apps + state.uninstalledApps).associateBy { it.packageName }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Associated packages", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        if (app.associations.isEmpty()) {
            Text("No package associations have been identified. Android does not expose every connection between apps.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        app.associations.groupBy { it.kind }.forEach { (kind, associations) ->
            var expanded by remember(app.packageName, kind) { mutableStateOf(false) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${kind.label} (${associations.size})", style = MaterialTheme.typography.titleMedium)
                Text(kind.explanation, style = MaterialTheme.typography.bodySmall, color = Muted)
                val sorted = associations.sortedWith(compareBy({ entries[it.packageName] == null }, { entries[it.packageName]?.label ?: it.packageName }))
                (if (expanded) sorted else sorted.take(5)).forEach { association ->
                    val entry = entries[association.packageName]
                    Surface(color = Panel, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()
                        .testTag("association-${kind.name}-${association.packageName}")
                        .clickable(enabled = entry != null && !state.busy, role = Role.Button) { entry?.let(onSelect) }) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry?.label ?: PackageDescriptions.label(association.packageName, association.packageName),
                                style = MaterialTheme.typography.titleSmall, color = Purple)
                            Text(association.packageName, style = MaterialTheme.typography.bodySmall, color = Muted)
                            Text(when {
                                entry == null -> "Not in this user's app inventory"
                                !entry.installed -> "Uninstalled for this user · view details"
                                !entry.enabled -> "Installed · disabled · view details"
                                else -> "Installed · view details"
                            }, style = MaterialTheme.typography.labelSmall, color = Muted)
                        }
                    }
                }
                if (associations.size > 5) TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Show fewer ${kind.label.lowercase()}" else "Show all ${associations.size} ${kind.label.lowercase()}")
                }
            }
        }
    }
}
