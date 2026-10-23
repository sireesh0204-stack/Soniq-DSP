package com.audiophile.buds2prooptimizer.ui

import kotlin.math.sin

object VisualizerMath {
    val TWO_PI: Float = (2.0 * Math.PI).toFloat()
    val FOUR_PI: Float = (4.0 * Math.PI).toFloat()

    /**
     * Subtle edge envelope taper for an organic acoustic ribbon look.
     * Tapers down to 0.85f at the edges and peaks at 1.0f in the middle.
     */
    fun calculateEnvelope(normX: Float): Float {
        val clampedX = normX.coerceIn(0f, 1f)
        return sin(clampedX * Math.PI).toFloat() * 0.15f + 0.85f
    }

    /**
     * Dual-harmonic wave equation with integer phase multiples to eliminate stutter on 120Hz displays.
     */
    fun sampleWave(normX: Float, phase: Float): Float {
        val clampedX = normX.coerceIn(0f, 1f)
        val primary = sin(clampedX * TWO_PI + phase).toFloat() * 0.72f
        val harmonic = sin(clampedX * FOUR_PI - phase * 0.85f).toFloat() * 0.28f
        return primary + harmonic
    }

    /**
     * Evaluates final Y-coordinate on canvas for given horizontal position.
     */
    fun calculateWaveY(centerY: Float, currentAmplitude: Float, normX: Float, phase: Float): Float {
        val envelope = calculateEnvelope(normX)
        val wave = sampleWave(normX, phase)
        return centerY + wave * currentAmplitude * envelope
    }

    /**
     * Converts 4 Catmull-Rom spline control coordinates into cubic Bezier control points.
     * Returns Pair(cp1, cp2).
     */
    fun catmullRomToBezier(p0: Float, p1: Float, p2: Float, p3: Float): Pair<Float, Float> {
        val cp1 = p1 + (p2 - p0) / 6f
        val cp2 = p2 - (p3 - p1) / 6f
        return Pair(cp1, cp2)
    }
}
