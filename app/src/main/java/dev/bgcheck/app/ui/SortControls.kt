package dev.bgcheck.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.bgcheck.app.data.SortDirection
import dev.bgcheck.app.data.SortKey

/** A wrapping control shared by lists; choices belong to the caller's saveable state. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T : SortKey> SortControls(
    id: String,
    options: List<T>,
    selected: T,
    direction: SortDirection,
    onSort: (T) -> Unit,
    onDirection: (SortDirection) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.heightIn(min = 48.dp).testTag("$id-sort")
                    .semantics { stateDescription = "${selected.label}, ${direction.label}" },
            ) {
                Text("Sort by: ${selected.label}", Modifier.weight(1f, fill = false))
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        trailingIcon = { if (option == selected) Icon(Icons.Rounded.Check, contentDescription = null) },
                        modifier = Modifier.semantics { this.selected = option == selected },
                        onClick = {
                            expanded = false
                            if (option != selected) {
                                onSort(option)
                                onDirection(option.defaultDirection)
                            }
                        },
                    )
                }
            }
        }
        OutlinedButton(
            onClick = { onDirection(direction.reversed()) },
            modifier = Modifier.heightIn(min = 48.dp).testTag("$id-direction")
                .semantics { stateDescription = "${direction.label}; activate to reverse order" },
        ) {
            Icon(if (direction == SortDirection.ASCENDING) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, contentDescription = null)
            Text(direction.label)
        }
    }
}
