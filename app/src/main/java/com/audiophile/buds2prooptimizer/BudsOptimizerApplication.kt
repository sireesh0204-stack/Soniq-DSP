package com.audiophile.buds2prooptimizer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class BudsOptimizerApplication : Application() {

    companion object {
        const val CHANNEL_ID = "buds2pro_dsp_service_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Galaxy Buds2 Pro Audiophile DSP",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the 64-bit audiophile DSP active for Galaxy Buds2 Pro on HyperOS"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}

