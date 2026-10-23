package com.audiophile.buds2prooptimizer.tiles

import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.audiophile.buds2prooptimizer.dsp.AudioDspService
import com.audiophile.buds2prooptimizer.dsp.Buds2ProAudioProfile
import com.audiophile.buds2prooptimizer.util.HyperOSHelper

class AetherProfileTileService : TileService() {

    override fun onClick() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val currentName = prefs.getString("ACTIVE_PROFILE", Buds2ProAudioProfile.ORIGINAL_SOUND.name)
        val currentProfile = try {
            Buds2ProAudioProfile.valueOf(currentName ?: Buds2ProAudioProfile.ORIGINAL_SOUND.name)
        } catch (e: Exception) {
            Buds2ProAudioProfile.ORIGINAL_SOUND
        }

        val profiles = Buds2ProAudioProfile.values()
        val nextIndex = (currentProfile.ordinal + 1) % profiles.size
        val nextProfile = profiles[nextIndex]

        prefs.edit().putString("ACTIVE_PROFILE", nextProfile.name).apply()

        // Directly set native HyperOS sound effect
        HyperOSHelper.setHyperOsEffect(this, nextProfile.hyperOsEffect)

        // Notify DSP Engine
        val intent = Intent(this, AudioDspService::class.java).apply {
            action = AudioDspService.ACTION_SET_PROFILE
            putExtra("PROFILE", nextProfile.name)
        }
        val isDspActive = prefs.getBoolean("DSP_ACTIVE", false)
        if (isDspActive) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }

        updateTile(nextProfile)
    }

    override fun onStartListening() {
        super.onStartListening()
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val currentName = prefs.getString("ACTIVE_PROFILE", Buds2ProAudioProfile.ORIGINAL_SOUND.name)
        val currentProfile = try {
            Buds2ProAudioProfile.valueOf(currentName ?: Buds2ProAudioProfile.ORIGINAL_SOUND.name)
        } catch (e: Exception) {
            Buds2ProAudioProfile.ORIGINAL_SOUND
        }
        updateTile(currentProfile)
    }

    private fun updateTile(profile: Buds2ProAudioProfile) {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.label = profile.displayName
        tile.subtitle = "SOUND PROFILE"
        tile.icon = Icon.createWithResource(this, profile.iconRes)
        tile.updateTile()
    }

    companion object {
        private const val PREFS_NAME = "AetherPrefs"

        fun requestTileUpdate(context: android.content.Context) {
            try {
                requestListeningState(context, ComponentName(context, AetherProfileTileService::class.java))
            } catch (_: Exception) {}
        }
    }
}
