package com.audiophile.buds2prooptimizer.util

import com.audiophile.buds2prooptimizer.dsp.Buds2ProAudioProfile

data class ExpectedSystemSettings(
    val effectImplementer: String,
    val lastSelectEffectNone: Int,
    val dolbyEffectSwitch: Int,
    val misoundState: Int
)

object ProfileStateLogic {

    fun normalizeEffect(effect: String): String = when (effect.lowercase()) {
        "dolby" -> "dolby"
        "misound" -> "misound"
        else -> "none"
    }

    fun getExpectedSettings(normalizedEffect: String): ExpectedSystemSettings {
        return when (normalizeEffect(normalizedEffect)) {
            "dolby" -> ExpectedSystemSettings(
                effectImplementer = "dolby",
                lastSelectEffectNone = 0,
                dolbyEffectSwitch = 1,
                misoundState = 0
            )
            "misound" -> ExpectedSystemSettings(
                effectImplementer = "misound",
                lastSelectEffectNone = 0,
                dolbyEffectSwitch = 0,
                misoundState = 1
            )
            else -> ExpectedSystemSettings(
                effectImplementer = "none",
                lastSelectEffectNone = 1,
                dolbyEffectSwitch = 0,
                misoundState = 0
            )
        }
    }

    /**
     * Harman DSP can ONLY be active when a headset is connected AND the selected profile
     * is ORIGINAL_SOUND (Bit-perfect mode). On phone speakers or when Dolby/Xiaomi Sound is active,
     * DSP must be bypassed to protect speakers and avoid DSP collisions.
     */
    fun computeEffectiveDspActive(
        isDspRequested: Boolean,
        isHeadsetConnected: Boolean,
        profile: Buds2ProAudioProfile
    ): Boolean {
        return isDspRequested && isHeadsetConnected && (profile == Buds2ProAudioProfile.ORIGINAL_SOUND)
    }

    /**
     * BUG-002 invariant: "none" is NOT a registered effect string in AudioEffectCenter.
     * Calling setEffectActive("none", true) triggers BaseEffectPresenter -> Spatializer.setEnabled(),
     * throwing java.lang.SecurityException: requires android.permission.MODIFY_DEFAULT_AUDIO_EFFECTS.
     */
    fun isAudioEffectCenterActivationAllowed(effectToActivate: String): Boolean {
        return effectToActivate != "none"
    }
}
