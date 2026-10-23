package com.audiophile.buds2prooptimizer

import com.audiophile.buds2prooptimizer.util.AudioTelemetryUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryLogicTest {

    @Test
    fun testFormatSampleRateKHz_standardRates() {
        assertEquals("48kHz", AudioTelemetryUtils.formatSampleRateKHz(48000))
        assertEquals("96kHz", AudioTelemetryUtils.formatSampleRateKHz(96000))
        assertEquals("192kHz", AudioTelemetryUtils.formatSampleRateKHz(192000))
        assertEquals("44.1kHz", AudioTelemetryUtils.formatSampleRateKHz(44100))
        assertEquals("88.2kHz", AudioTelemetryUtils.formatSampleRateKHz(88200))
    }

    @Test
    fun testFormatSampleRateKHz_boundaryAndInvalid() {
        assertEquals("48kHz", AudioTelemetryUtils.formatSampleRateKHz(0))
        assertEquals("48kHz", AudioTelemetryUtils.formatSampleRateKHz(-1))
    }

    @Test
    fun testResolveBitDepth() {
        assertEquals("16-BIT", AudioTelemetryUtils.resolveBitDepth(AudioTelemetryUtils.ENCODING_PCM_16BIT))
        assertEquals("24-BIT", AudioTelemetryUtils.resolveBitDepth(AudioTelemetryUtils.ENCODING_PCM_24BIT_PACKED))
        assertEquals("32-BIT / HI-RES", AudioTelemetryUtils.resolveBitDepth(AudioTelemetryUtils.ENCODING_PCM_32BIT))
        assertEquals("32-BIT / HI-RES", AudioTelemetryUtils.resolveBitDepth(AudioTelemetryUtils.ENCODING_PCM_FLOAT))
        assertEquals("24-BIT", AudioTelemetryUtils.resolveBitDepth(null))
        assertEquals("24-BIT", AudioTelemetryUtils.resolveBitDepth(999))
    }

    @Test
    fun testResolveCodecName() {
        assertEquals("SBC", AudioTelemetryUtils.resolveCodecName(0, "Pixel Buds"))
        assertEquals("AAC", AudioTelemetryUtils.resolveCodecName(1, "Sony WH-1000XM4"))
        assertEquals("aptX", AudioTelemetryUtils.resolveCodecName(2, "Bose QC35"))
        assertEquals("aptX HD", AudioTelemetryUtils.resolveCodecName(3, "Bowers & Wilkins PX"))
        assertEquals("LDAC", AudioTelemetryUtils.resolveCodecName(4, "Sony WF-1000XM5"))
        assertEquals("LC3", AudioTelemetryUtils.resolveCodecName(5, "LinkBuds S"))
        assertEquals("aptX Adaptive", AudioTelemetryUtils.resolveCodecName(6, "Sennheiser MTW3"))

        // Samsung Buds SSC identification
        assertEquals("SSC", AudioTelemetryUtils.resolveCodecName(0x18, "Galaxy Buds2 Pro"))
        assertEquals("SSC", AudioTelemetryUtils.resolveCodecName(99, "Buds2 Pro"))
        assertEquals("HI-RES BT", AudioTelemetryUtils.resolveCodecName(99, "Generic Headset"))
    }
}

