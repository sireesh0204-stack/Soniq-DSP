package com.audiophile.buds2prooptimizer

import com.audiophile.buds2prooptimizer.dsp.Buds2ProAudioProfile
import com.audiophile.buds2prooptimizer.util.ProfileStateLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileStateLogicTest {

    @Test
    fun testNormalizeEffect() {
        assertEquals("dolby", ProfileStateLogic.normalizeEffect("dolby"))
        assertEquals("dolby", ProfileStateLogic.normalizeEffect("DOLBY"))
        assertEquals("misound", ProfileStateLogic.normalizeEffect("misound"))
        assertEquals("misound", ProfileStateLogic.normalizeEffect("MISOUND"))
        assertEquals("none", ProfileStateLogic.normalizeEffect("none"))
        assertEquals("none", ProfileStateLogic.normalizeEffect("ORIGINAL"))
        assertEquals("none", ProfileStateLogic.normalizeEffect("invalid_profile"))
    }

    @Test
    fun testExpectedSettings_Dolby() {
        val settings = ProfileStateLogic.getExpectedSettings("dolby")
        assertEquals("dolby", settings.effectImplementer)
        assertEquals(1, settings.dolbyEffectSwitch)
        assertEquals(0, settings.misoundState)
        assertEquals(0, settings.lastSelectEffectNone)
    }

    @Test
    fun testExpectedSettings_MiSound() {
        val settings = ProfileStateLogic.getExpectedSettings("misound")
        assertEquals("misound", settings.effectImplementer)
        assertEquals(0, settings.dolbyEffectSwitch)
        assertEquals(1, settings.misoundState)
        assertEquals(0, settings.lastSelectEffectNone)
    }

    @Test
    fun testExpectedSettings_OriginalSound() {
        val settings = ProfileStateLogic.getExpectedSettings("none")
        assertEquals("none", settings.effectImplementer)
        assertEquals(0, settings.dolbyEffectSwitch)
        assertEquals(0, settings.misoundState)
        assertEquals(1, settings.lastSelectEffectNone)
    }

    @Test
    fun testBug002_AudioEffectCenterActivationSafety() {
        // BUG-002 invariant: setEffectActive("none", true) crashes via Spatializer.setEnabled
        assertFalse(ProfileStateLogic.isAudioEffectCenterActivationAllowed("none"))
        assertTrue(ProfileStateLogic.isAudioEffectCenterActivationAllowed("dolby"))
        assertTrue(ProfileStateLogic.isAudioEffectCenterActivationAllowed("misound"))
    }

    @Test
    fun testBug003_SpeakerDspBypassedInvariant() {
        // When headset is disconnected (phone speaker active):
        // DSP must ALWAYS be bypassed regardless of requested state or profile
        assertFalse(ProfileStateLogic.computeEffectiveDspActive(
            isDspRequested = true,
            isHeadsetConnected = false,
            profile = Buds2ProAudioProfile.ORIGINAL_SOUND
        ))

        // When headset is connected, DSP can only be active if profile is ORIGINAL_SOUND
        assertTrue(ProfileStateLogic.computeEffectiveDspActive(
            isDspRequested = true,
            isHeadsetConnected = true,
            profile = Buds2ProAudioProfile.ORIGINAL_SOUND
        ))
        assertFalse(ProfileStateLogic.computeEffectiveDspActive(
            isDspRequested = true,
            isHeadsetConnected = true,
            profile = Buds2ProAudioProfile.DOLBY_ATMOS
        ))
        assertFalse(ProfileStateLogic.computeEffectiveDspActive(
            isDspRequested = true,
            isHeadsetConnected = true,
            profile = Buds2ProAudioProfile.XIAOMI_SOUND
        ))
    }
}
