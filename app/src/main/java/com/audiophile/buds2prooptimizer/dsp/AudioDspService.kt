package com.audiophile.buds2prooptimizer.dsp

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.audiophile.buds2prooptimizer.BudsOptimizerApplication
import com.audiophile.buds2prooptimizer.ui.MainActivity
import com.audiophile.buds2prooptimizer.util.HyperOSHelper

class AudioDspService : Service() {

    private var masterEqualizer: Equalizer? = null
    private var isDspActive = false
    private var currentProfile = Buds2ProAudioProfile.ORIGINAL_SOUND

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                Log.d(TAG, "Bluetooth disconnected. Releasing DSP to ensure 0.0% battery drain.")
                val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                prefs.edit().putBoolean("DSP_ACTIVE", false).apply()
                releaseDsp()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        ContextCompat.registerReceiver(
            this,
            bluetoothReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_TOGGLE_DSP -> {
                val state = intent.getBooleanExtra("STATE", false)
                toggleDsp(state)
            }
            ACTION_SET_PROFILE -> {
                val profileName = intent.getStringExtra("PROFILE") ?: Buds2ProAudioProfile.ORIGINAL_SOUND.name
                currentProfile = try {
                    Buds2ProAudioProfile.valueOf(profileName)
                } catch (e: Exception) {
                    Buds2ProAudioProfile.ORIGINAL_SOUND
                }
                applyProfileIfActive()
                HyperOSHelper.setHyperOsEffect(this, currentProfile.hyperOsEffect)
            }
        }
        return START_STICKY
    }

    private fun toggleDsp(active: Boolean) {
        isDspActive = active
        if (active) {
            initializeDsp()
            startForegroundNotification()
            HyperOSHelper.setHyperOsEffect(this, currentProfile.hyperOsEffect)
        } else {
            releaseDsp()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun initializeDsp() {
        if (masterEqualizer == null) {
            try {
                // Session 0 applies to global Android audio mix output
                masterEqualizer = Equalizer(0, 0)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to init Equalizer on Session 0", e)
                return
            }
        }
        masterEqualizer?.enabled = true
        applyProfileIfActive()
    }

    private fun applyProfileIfActive() {
        if (!isDspActive || masterEqualizer == null) return
        val eq = masterEqualizer ?: return

        try {
            val numBands = eq.numberOfBands
            for (i in 0 until numBands) {
                val targetLevel = currentProfile.bands.getOrElse(i.toInt()) { 0 }
                eq.setBandLevel(i.toShort(), targetLevel)
            }
            Log.d(TAG, "Applied DSP profile: ${currentProfile.displayName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply bands", e)
        }
    }

    private fun releaseDsp() {
        try {
            masterEqualizer?.enabled = false
            masterEqualizer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing Equalizer: ${e.message}")
        }
        masterEqualizer = null
        isDspActive = false
    }

    private fun startForegroundNotification() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, BudsOptimizerApplication.CHANNEL_ID)
            .setContentTitle("AETHER // Studio Engine Active")
            .setContentText("Profile: ${currentProfile.displayName}")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Receiver already unregistered")
        }
        releaseDsp()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AudioDspService"
        private const val NOTIFICATION_ID = 2001
        private const val PREFS_NAME = "AetherPrefs"

        const val ACTION_TOGGLE_DSP = "ACTION_TOGGLE_DSP"
        const val ACTION_SET_PROFILE = "ACTION_SET_PROFILE"
    }
}
