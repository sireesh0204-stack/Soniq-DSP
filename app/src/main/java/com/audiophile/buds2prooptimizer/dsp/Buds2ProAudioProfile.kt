package com.audiophile.buds2prooptimizer.dsp

import androidx.annotation.DrawableRes
import com.audiophile.buds2prooptimizer.R

enum class Buds2ProAudioProfile(
    val displayName: String,
    val description: String,
    val hyperOsEffect: String,
    @DrawableRes val iconRes: Int,
    val bands: ShortArray // EQ band levels in millibels (mB)
) {
    ORIGINAL_SOUND(
        "ORIGINAL",
        "Harman 2019v2 Target Bit-Perfect Restore | -2.5dB Headroom",
        "none",
        R.drawable.ic_tile_original,
        shortArrayOf(100, 0, -50, 150, 300) 
    ),
    DOLBY_ATMOS(
        "DOLBY",
        "Cinematic Soundstage | Spatial Air Enhancement",
        "dolby",
        R.drawable.ic_tile_dolby,
        shortArrayOf(200, -100, 100, 250, 400)
    ),
    XIAOMI_SOUND(
        "XIAOMI",
        "Fast Sub-Bass Transient | Crisp Upper Mids",
        "misound",
        R.drawable.ic_tile_xiaomi,
        shortArrayOf(350, -50, 50, 200, 250)
    )
}
