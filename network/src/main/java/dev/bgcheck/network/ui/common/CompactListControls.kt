package dev.bgcheck.network.ui.common

import android.view.View
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import dev.bgcheck.network.R
import dev.bgcheck.network.utils.CompactListPreferences
import dev.bgcheck.network.utils.NetworkList

object CompactListControls {
    fun bind(root: View, list: NetworkList, onChanged: (Boolean) -> Unit) {
        val group = root.findViewById<MaterialButtonToggleGroup>(R.id.list_density_group)
        // ToggleGroup applies a single-line limit when adding children. Restore wrapping after
        // inflation so larger fonts and translated labels can grow rather than disappear.
        listOf(R.id.layout_comfortable, R.id.layout_compact).forEach { id ->
            group.findViewById<MaterialButton>(id).apply {
                setSingleLine(false)
                maxLines = Int.MAX_VALUE
                ellipsize = null
            }
        }
        val compact = CompactListPreferences.isCompact(root.context, list)
        group.check(if (compact) R.id.layout_compact else R.id.layout_comfortable)
        onChanged(compact)
        group.addOnButtonCheckedListener { _, checkedId, checked ->
            if (checked) {
                val selectedCompact = checkedId == R.id.layout_compact
                CompactListPreferences.save(root.context, list, selectedCompact)
                onChanged(selectedCompact)
            }
        }
    }
}
