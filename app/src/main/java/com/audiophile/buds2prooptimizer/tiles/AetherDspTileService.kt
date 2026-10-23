package com.audiophile.buds2prooptimizer.tiles

import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.audiophile.buds2prooptimizer.R
import com.audiophile.buds2prooptimizer.dsp.AudioDspService

class AetherDspTileService : TileService() {

    override fun onClick() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val activeProfile = prefs.getString("ACTIVE_PROFILE", "ORIGINAL_SOUND")

        if (activeProfile != "ORIGINAL_SOUND") {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(
                    this,
                    "Turn on ORIGINAL profile to access Harman DSP",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            updateTile(false)
            return
        }

        val isActive = !prefs.getBoolean("DSP_ACTIVE", false)
        prefs.edit().putBoolean("DSP_ACTIVE", isActive).apply()

        val intent = Intent(this, AudioDspService::class.java).apply {
            action = AudioDspService.ACTION_TOGGLE_DSP
            putExtra("STATE", isActive)
        }
        if (isActive) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }

        updateTile(isActive)
    }

    override fun onStartListening() {
        super.onStartListening()
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        updateTile(prefs.getBoolean("DSP_ACTIVE", false))
    }

    private fun updateTile(isActive: Boolean) {
        val tile = qsTile ?: return
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val activeProfile = prefs.getString("ACTIVE_PROFILE", "ORIGINAL_SOUND")
        val isOriginal = activeProfile == "ORIGINAL_SOUND"

        if (!isOriginal) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Soniq DSP"
            tile.subtitle = "REQUIRES ORIGINAL"
        } else {
            tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = "Soniq DSP"
            tile.subtitle = if (isActive) "ACTIVE" else "BYPASSED"
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_harman)
        tile.updateTile()
    }

    companion object {
        private const val PREFS_NAME = "AetherPrefs"

        fun requestTileUpdate(context: android.content.Context) {
            try {
                requestListeningState(context, ComponentName(context, AetherDspTileService::class.java))
            } catch (_: Exception) {}
        }
    }
}
