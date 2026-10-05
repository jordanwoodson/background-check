package dev.bgcheck.network.ui.common

import android.view.View
import dev.bgcheck.network.R
import kotlin.math.roundToInt

/** Applies both states on every bind so recycled rows never retain hidden text or old spacing. */
object CompactListRow {
    fun bind(root: View, compact: Boolean) {
        val density = root.resources.displayMetrics.density
        val padding = ((if (compact) 8 else 16) * density).roundToInt()
        root.findViewById<View>(R.id.row_content).setPadding(padding, padding, padding, padding)
        root.findViewById<View>(R.id.package_name).visibility = if (compact) View.GONE else View.VISIBLE
        val icon = root.findViewById<View>(R.id.app_icon)
        val iconSize = ((if (compact) 40 else 48) * density).roundToInt()
        if (icon.layoutParams.width != iconSize || icon.layoutParams.height != iconSize) {
            icon.layoutParams = icon.layoutParams.apply { width = iconSize; height = iconSize }
        }
    }
}
