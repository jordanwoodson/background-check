package dev.bgcheck.app.ui

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dev.bgcheck.network.utils.Constants

@Composable
internal fun rememberAppsStateFilter(): MutableState<AppsStateFilter> {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE) }
    val state = rememberSaveable {
        val raw = preferences.getString(Constants.Settings.KEY_PACKAGES_FILTER_STATE, null)
        mutableStateOf(AppsStateFilter.entries.firstOrNull { it != AppsStateFilter.ANY && it.label.equals(raw, true) } ?: AppsStateFilter.ANY)
    }
    LaunchedEffect(state.value) {
        preferences.edit {
            putString(Constants.Settings.KEY_PACKAGES_FILTER_STATE,
                state.value.takeUnless { it == AppsStateFilter.ANY }?.label)
        }
    }
    return state
}

@Composable
internal fun <T> AppsFilterChips(tag: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag(tag), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { item ->
            FilterChip(selected = item == selected, onClick = { onSelect(item) }, label = { Text(label(item)) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("$tag-${label(item)}"), colors = appFilterColors())
        }
    }
}
