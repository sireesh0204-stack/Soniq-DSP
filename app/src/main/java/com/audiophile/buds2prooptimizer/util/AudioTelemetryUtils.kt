package com.audiophile.buds2prooptimizer.util

object AudioTelemetryUtils {

    const val ENCODING_PCM_16BIT = 2
    const val ENCODING_PCM_24BIT_PACKED = 21
    const val ENCODING_PCM_32BIT = 22
    const val ENCODING_PCM_FLOAT = 4

    /**
     * Formats integer Hz sample rates into audiophile notation (e.g. 48kHz, 96kHz, 44.1kHz).
     */
    fun formatSampleRateKHz(sampleRateHz: Int): String {
        if (sampleRateHz <= 0) return "48kHz"
        return if (sampleRateHz % 1000 == 0) {
            "${sampleRateHz / 1000}kHz"
        } else {
            String.format(java.util.Locale.US, "%.1fkHz", sampleRateHz / 1000.0)
        }
    }

    /**
     * Maps Android AudioFormat PCM encodings to audiophile telemetry strings.
     */
    fun resolveBitDepth(encoding: Int?): String {
        return when (encoding) {
            ENCODING_PCM_16BIT -> "16-BIT"
            ENCODING_PCM_24BIT_PACKED -> "24-BIT"
            ENCODING_PCM_32BIT,
            ENCODING_PCM_FLOAT -> "32-BIT / HI-RES"
            else -> "24-BIT"
        }
    }

    /**
     * Resolves standard and vendor Bluetooth codec types to UI badge names.
     */
    fun resolveCodecName(codecType: Int, deviceName: String?): String {
        val isBuds = deviceName?.contains("Buds", ignoreCase = true) == true
        return when (codecType) {
            0 -> "SBC"
            1 -> "AAC"
            2 -> "aptX"
            3 -> "aptX HD"
            4 -> "LDAC"
            5 -> "LC3"
            6 -> "aptX Adaptive"
            0x18 -> if (isBuds) "SSC" else "SSC / UHQ"
            else -> if (isBuds) "SSC" else "HI-RES BT"
        }
    }
}
