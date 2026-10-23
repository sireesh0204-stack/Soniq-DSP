package com.audiophile.buds2prooptimizer.feedback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.exp
import kotlin.math.sin

/**
 * Precision mechanical keyboard keypress sound synthesizer.
 * Replaces loud synthetic clicks with a discreet, tactile mechanical switch bottom-out:
 * - Subtle click-leaf tick (2600Hz, rapid 2ms damping).
 * - Warm housing "thock" resonance (460Hz body tone with smooth exponential decay).
 * - Gentle volume level (-15 dBFS) calibrated for tactile paired UI feedback.
 */
class AetherSoundEngine {
    private var isEnabled = true
    private val sampleRate = 44100
    private var staticTrack: AudioTrack? = null

    init {
        try {
            val durationMs = 22
            val numSamples = (sampleRate * durationMs) / 1000
            val clickBuffer = ShortArray(numSamples)

            // Calibrated amplitude: 18% of digital maximum (-15 dBFS) for a subtle, premium tactile click
            val maxAmp = Short.MAX_VALUE * 0.18

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val progress = i.toDouble() / numSamples

                // 1. Tactile click-leaf transient (first 2.5ms)
                val clickTransient = if (progress < 0.15) {
                    val clickEnv = 1.0 - (progress / 0.15)
                    sin(2.0 * Math.PI * 2600.0 * t) * clickEnv * 0.35
                } else {
                    0.0
                }

                // 2. Switch housing & keycap acoustic resonance (460Hz body with smooth exponential decay)
                val bodyFreq = 460.0 - (progress * 100.0)
                val bodyEnv = exp(-progress * 5.5)
                val bodyTone = (sin(2.0 * Math.PI * bodyFreq * t) + 0.25 * sin(2.0 * Math.PI * (bodyFreq * 2.0) * t)) * bodyEnv * 0.65

                // 3. Combined mechanical keypress impulse
                val sampleValue = (clickTransient + bodyTone) * maxAmp
                clickBuffer[i] = sampleValue.toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(clickBuffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(clickBuffer, 0, clickBuffer.size)
            staticTrack = track
        } catch (e: Exception) {
            Log.w("AetherSoundEngine", "Failed to init mechanical keyboard AudioTrack: ${e.message}")
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    fun playMechanicalClick() {
        if (!isEnabled) return
        try {
            val track = staticTrack ?: return
            track.stop()
            track.reloadStaticData()
            track.play()
        } catch (e: Exception) {
            Log.w("AetherSoundEngine", "Playback failed: ${e.message}")
        }
    }

    fun release() {
        try {
            staticTrack?.stop()
            staticTrack?.release()
        } catch (_: Exception) {}
        staticTrack = null
    }
}
