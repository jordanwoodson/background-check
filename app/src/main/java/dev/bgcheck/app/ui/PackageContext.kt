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
import dev.bgcheck.app.data.debloatRecommendation
import dev.bgcheck.app.data.CanaDebloatGuide
import dev.bgcheck.app.data.DebloatRating
import androidx.compose.runtime.key

@Composable
internal fun CanaDebloatRecommendationCard(app: AppEntry) {
    val recommendation = app.debloatRecommendation
    val uriHandler = LocalUriHandler.current
    Surface(color = Panel, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()
        .testTag("cana-debloat-recommendation")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Cana debloat recommendation", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            Text(recommendation.rating.label, style = MaterialTheme.typography.titleSmall,
                color = if (recommendation.rating == DebloatRating.UNSAFE || recommendation.rating == DebloatRating.EXPERT) Orange else Cyan)
            Text(recommendation.rating.explanation, style = MaterialTheme.typography.bodySmall)
            if (!app.installed) Text(
                "Already uninstalled for this user. Cana's rating describes removal risk if this package is restored; review the notes before restoring it.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
            recommendation.warning?.let {
                Text("Cana list warning", style = MaterialTheme.typography.titleSmall, color = Orange)
                Text(it, style = MaterialTheme.typography.bodySmall, color = Orange)
            }
            Text("Source: Android Debloat List bundled with Cana", style = MaterialTheme.typography.labelSmall, color = Muted)
            key(app.key) {
                Disclosure("Cana list details", "Package notes, dependencies and replacements") {
                    recommendation.description?.takeIf { it.isNotBlank() }?.let {
                        Text("Upstream package notes", style = MaterialTheme.typography.titleSmall)
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    if (recommendation.dependencies.isNotEmpty()) Text(
                        "List-declared dependencies: ${recommendation.dependencies.joinToString()}",
                        style = MaterialTheme.typography.bodySmall)
                    if (recommendation.neededBy.isNotEmpty()) Text(
                        "Listed as needed by: ${recommendation.neededBy.joinToString()}",
                        style = MaterialTheme.typography.bodySmall)
                    if (recommendation.dependencies.isNotEmpty() || recommendation.neededBy.isNotEmpty()) Text(
                        "These relationships come from Cana's list; they do not establish what is installed or required on this device.",
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                    if (recommendation.alternatives.isNotEmpty()) {
                        Text("Suggested replacements", style = MaterialTheme.typography.titleSmall)
                        recommendation.alternatives.forEach { alternative ->
                            TextButton(onClick = { uriHandler.openUri(alternative.url) }) {
                                Text("${alternative.label} · ${alternative.packageName}", style = MaterialTheme.typography.bodySmall)
                            }
                            alternative.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Orange) }
                        }
                    }
                    recommendation.references.forEachIndexed { index, url ->
                        if (url.startsWith("https://") || url.startsWith("http://")) TextButton(onClick = { uriHandler.openUri(url) }) {
                            Text("List reference ${index + 1}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text("Bundled source revision ${CanaDebloatGuide.REVISION.take(7)}. Cana may have a newer or custom list. Ratings are guidance, not a guarantee that removal is safe on this phone.",
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                    TextButton(onClick = { uriHandler.openUri(CanaDebloatGuide.SOURCE_URL) }) { Text("View Cana's bundled source") }
                }
            }
        }
    }
}

@Composable
internal fun PackageSources(app: AppEntry) {
    app.description.note?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
    if (app.description.citations.isEmpty()) return
    var expanded by remember(app.key) { mutableStateOf(false) }
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
    val entries = remember(state.apps, state.uninstalledApps, app.userId) {
        (state.apps + state.uninstalledApps).filter { it.userId == app.userId }.associateBy { it.packageName }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Associated packages", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        if (app.associations.isEmpty()) {
            Text("No package associations have been identified. Android does not expose every connection between apps.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        app.associations.groupBy { it.kind }.forEach { (kind, associations) ->
            var expanded by remember(app.key, kind) { mutableStateOf(false) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${kind.label} (${associations.size})", style = MaterialTheme.typography.titleMedium)
                Text(kind.explanation, style = MaterialTheme.typography.bodySmall, color = Muted)
                val sorted = associations.sortedWith(compareBy({ entries[it.packageName] == null }, { entries[it.packageName]?.label ?: it.packageName }))
                (if (expanded) sorted else sorted.take(5)).forEach { association ->
                    val entry = entries[association.packageName]
                    Surface(color = Panel, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()
                        .testTag("association-${kind.name}-${association.packageName}")
                        .clickable(enabled = entry != null && !state.busy, role = Role.Button) { entry?.let(onSelect) }) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry?.label ?: PackageDescriptions.label(association.packageName, association.packageName),
                                style = MaterialTheme.typography.titleSmall, color = Purple)
                            entry?.let { ProfileLabel(it.profile) }
                            Text(association.packageName, style = MaterialTheme.typography.bodySmall.copy(fontFamily = ReadingFont), color = Muted)
                            Text(when {
                                entry == null -> "Not in ${app.profile.name} app inventory"
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
