package com.audiophile.buds2prooptimizer.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * CRED-style precision haptic feedback engine.
 * Delivers sharp mechanical ticks, heavy thuds, and subtle tactile bumps.
 */
class HapticEngine(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * Sharp mechanical micro-click (like a mechanical keyboard or luxury watch bezel).
     */
    fun click(view: View? = null) {
        view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(12)
        }
    }

    /**
     * Heavy tactile thud (used when engaging ANC or toggling master DSP).
     */
    fun heavyThud(view: View? = null) {
        view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(35)
        }
    }

    /**
     * Subtle tactile tick (for slider adjustments or preset scrolling).
     */
    fun tick(view: View? = null) {
        view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(6)
        }
    }

    /**
     * Double pulse confirmation (when Galaxy Buds2 Pro connects or handshake completes).
     */
    fun doubleSuccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 15, 60, 20)
            val amplitudes = intArrayOf(0, 180, 0, 255)
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 20, 60, 30), -1)
        }
    }
}

