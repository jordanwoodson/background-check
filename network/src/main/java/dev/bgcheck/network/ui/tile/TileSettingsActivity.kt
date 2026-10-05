package dev.bgcheck.network.ui.tile

import android.app.Activity
import android.os.Bundle
import dev.bgcheck.network.ui.NetworkEntryIntents

/** Public tile preferences entry point forwards navigation only, never external operational extras. */
class TileSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(NetworkEntryIntents.create(this))
        finish()
    }
}
