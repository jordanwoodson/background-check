package dev.bgcheck.network.ui.tile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dev.bgcheck.network.ui.MainActivity

/** Public tile preferences entry point forwards navigation only, never external operational extras. */
class TileSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
