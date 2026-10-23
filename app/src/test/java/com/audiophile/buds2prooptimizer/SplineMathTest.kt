package com.audiophile.buds2prooptimizer

import com.audiophile.buds2prooptimizer.ui.VisualizerMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SplineMathTest {

    @Test
    fun testEnvelopeBoundaryAndPeak() {
        // At edges (normX = 0 and normX = 1), sin(0) = 0 and sin(PI) = 0, so envelope = 0.85
        val leftEdge = VisualizerMath.calculateEnvelope(0.0f)
        val rightEdge = VisualizerMath.calculateEnvelope(1.0f)
        assertEquals(0.85f, leftEdge, 0.001f)
        assertEquals(0.85f, rightEdge, 0.001f)

        // At center (normX = 0.5), sin(PI/2) = 1, so envelope = 0.15 + 0.85 = 1.00
        val center = VisualizerMath.calculateEnvelope(0.5f)
        assertEquals(1.00f, center, 0.001f)

        // Monotonic rise from left edge to center
        val quarter = VisualizerMath.calculateEnvelope(0.25f)
        assertTrue(quarter > leftEdge)
        assertTrue(quarter < center)
    }

    @Test
    fun testSampleWaveBoundedOutput() {
        // Wave sample must be bounded within [-1.0, 1.0] across various phases and positions
        val phases = listOf(0f, 0.5f, 1.0f, 3.14159f, 6.28318f, 100f)
        val step = 0.01f

        for (phase in phases) {
            var x = 0.0f
            while (x <= 1.0f) {
                val wave = VisualizerMath.sampleWave(x, phase)
                assertTrue("Wave sample $wave at x=$x phase=$phase exceeded bounds", abs(wave) <= 1.0001f)
                x += step
            }
        }
    }

    @Test
    fun testCalculateWaveY_flatWhenAmplitudeZero() {
        val centerY = 200f
        val zeroAmplitude = 0f
        for (phase in listOf(0f, 1.57f, 3.14f)) {
            val yAt0 = VisualizerMath.calculateWaveY(centerY, zeroAmplitude, 0f, phase)
            val yAtHalf = VisualizerMath.calculateWaveY(centerY, zeroAmplitude, 0.5f, phase)
            val yAt1 = VisualizerMath.calculateWaveY(centerY, zeroAmplitude, 1.0f, phase)
            assertEquals(centerY, yAt0, 0.0001f)
            assertEquals(centerY, yAtHalf, 0.0001f)
            assertEquals(centerY, yAt1, 0.0001f)
        }
    }

    @Test
    fun testCatmullRomToBezierContinuity() {
        // Test straight collinear line points (0, 10, 20, 30)
        val (cp1, cp2) = VisualizerMath.catmullRomToBezier(0f, 10f, 20f, 30f)
        // cp1 = 10 + (20 - 0)/6 = 10 + 3.3333 = 13.3333
        // cp2 = 20 - (30 - 10)/6 = 20 - 3.3333 = 16.6667
        assertEquals(13.3333f, cp1, 0.001f)
        assertEquals(16.6667f, cp2, 0.001f)
    }
}

