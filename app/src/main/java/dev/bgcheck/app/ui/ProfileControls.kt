package dev.bgcheck.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.bgcheck.app.data.AndroidProfile
import dev.bgcheck.app.data.UiState

internal const val ALL_PROFILES = -1

/** Keep known and unavailable profiles visible, including UIDs with no package match. */
internal fun UiState.visibleProfiles(): List<AndroidProfile> {
    val profilesById = linkedMapOf<Int, AndroidProfile>()
    profiles.forEach { profilesById[it.userId] = it }
    (apps + uninstalledApps).forEach { profilesById.putIfAbsent(it.userId, it.profile) }
    changes.forEach { record ->
        profilesById.putIfAbsent(record.userId, AndroidProfile(record.userId, record.profileName,
            serialNumber = record.userSerial, available = profiles.isEmpty(),
            unavailableReason = if (profiles.isEmpty()) null else "Profile no longer available"))
    }
    (resources?.processes.orEmpty().map { it.uid } + energy?.uids.orEmpty().map { it.uid }).forEach { uid ->
        if (uid >= 0) profilesById.putIfAbsent(uid / 100000, AndroidProfile(uid / 100000))
    }
    return profilesById.values.sortedBy { it.userId }
}

internal fun AndroidProfile.displayLabel(): String = "$name · User $userId"

@Composable
internal fun ProfileLabel(profile: AndroidProfile, modifier: Modifier = Modifier) {
    Text(profile.displayLabel() + if (profile.available) "" else " · Unavailable",
        modifier = modifier, style = MaterialTheme.typography.labelSmall,
        color = if (profile.available) Cyan else Orange)
}

@Composable
internal fun ProfileFilters(id: String, profiles: List<AndroidProfile>, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selected == ALL_PROFILES, onClick = { onSelect(ALL_PROFILES) },
                label = { Text("All profiles") }, modifier = Modifier.heightIn(min = 48.dp).testTag("$id-profile-all"))
            profiles.forEach { profile ->
                FilterChip(selected = selected == profile.userId, onClick = { onSelect(profile.userId) },
                    label = { Text(profile.name + if (profile.available) "" else " · Unavailable") },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("$id-profile-${profile.userId}"))
            }
        }
        profiles.filter { !it.available && (selected == ALL_PROFILES || selected == it.userId) }.forEach { profile ->
            Text("${profile.name}: ${profile.unavailableReason ?: "Profile unavailable"}. Resume or unlock the profile to use app controls.",
                style = MaterialTheme.typography.bodySmall, color = Orange)
        }
    }
}
