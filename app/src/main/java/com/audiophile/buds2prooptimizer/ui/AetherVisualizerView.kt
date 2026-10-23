package com.audiophile.buds2prooptimizer.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

class AetherVisualizerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val harmanTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D9FFFFFF")
        textSize = 24f
        letterSpacing = 0.05f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private val harmanSubTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCDFB464")
        textSize = 15.5f
        letterSpacing = 0.04f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }

    private val harmanSwooshPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D9FFFFFF")
        style = Paint.Style.FILL
    }

    private val upperSwooshPath = Path()
    private val lowerSwooshPath = Path()

    private var animator: ValueAnimator? = null
    private var phase = 0f
    private var isDspActive = false
    private var currentAmplitude = 0f
    private val targetAmplitude = 46f

    private val wavePath = Path()
    private var lastFrameTimeNanos = 0L

    init {
        setBackgroundColor(Color.parseColor("#0C0E12"))
    }

    fun setDspActive(active: Boolean) {
        isDspActive = active
        if (animator?.isRunning != true) {
            startAnimation()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0) {
            wavePaint.shader = LinearGradient(
                0f, 0f, w.toFloat(), 0f,
                intArrayOf(Color.parseColor("#E5B869"), Color.parseColor("#00F0FF")),
                null,
                Shader.TileMode.CLAMP
            )
        }
    }

    private fun startAnimation() {
        animator?.cancel()
        animator = null
        lastFrameTimeNanos = System.nanoTime()
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerY = height / 2f
        val widthF = width.toFloat()
        if (widthF <= 0f) return

        // Hardware VSYNC-aligned delta computation for locked 120Hz fluidity
        val now = System.nanoTime()
        val rawDeltaSec = if (lastFrameTimeNanos == 0L) {
            1f / 120f
        } else {
            ((now - lastFrameTimeNanos).coerceAtLeast(0L) / 1_000_000_000.0).toFloat()
        }
        lastFrameTimeNanos = now

        val safeDelta = rawDeltaSec.coerceIn(0.001f, 0.033f)

        val target = if (isDspActive) targetAmplitude else 0f
        val smoothing = (1.0 - Math.exp(-safeDelta * 6.5)).toFloat().coerceIn(0.005f, 0.35f)
        currentAmplitude += (target - currentAmplitude) * smoothing

        if (isDspActive || currentAmplitude > 0.08f) {
            phase += safeDelta * 2.8f
            if (phase > 62831.85f) { // 20,000 * PI seamless wrap
                phase -= 62831.85f
            }
        } else {
            currentAmplitude = 0f
        }

        // High-density Catmull-Rom to Cubic Bezier liquid ribbon spline
        wavePath.reset()
        val segments = 96
        val dx = widthF / segments

        fun sampleWaveY(xPos: Float): Float {
            val normX = (xPos / widthF).coerceIn(0f, 1f)
            return VisualizerMath.calculateWaveY(centerY, currentAmplitude, normX, phase)
        }

        var x0 = 0f
        var y0 = sampleWaveY(0f)
        var x1 = 0f
        var y1 = y0
        var x2 = dx
        var y2 = sampleWaveY(x2)

        wavePath.moveTo(x1, y1)

        for (i in 1..segments) {
            val x3 = if (i < segments) (i + 1) * dx else widthF
            val y3 = sampleWaveY(x3)

            // Catmull-Rom tangents converted to cubic Bezier control points
            val cp1x = x1 + (x2 - x0) / 6f
            val cp1y = y1 + (y2 - y0) / 6f
            val cp2x = x2 - (x3 - x1) / 6f
            val cp2y = y2 - (y3 - y1) / 6f

            wavePath.cubicTo(cp1x, cp1y, cp2x, cp2y, x2, y2)

            x0 = x1
            y0 = y1
            x1 = x2
            y1 = y2
            x2 = x3
            y2 = y3
        }

        canvas.drawPath(wavePath, wavePaint)
        drawHarmanLogoBottomRight(canvas, widthF, height.toFloat())

        if (isDspActive || currentAmplitude > 0.08f) {
            postInvalidateOnAnimation()
        }
    }

    /**
     * Draws the HARMAN emblem with upper/lower tapered crescent swooshes and
     * a non-overlapping "for Xiaomi" subtitle cleanly stacked below.
     */
    private fun drawHarmanLogoBottomRight(canvas: Canvas, canvasWidth: Float, canvasHeight: Float) {
        val brandText = "HARMAN"
        val subText = "for Xiaomi"

        val textWidth = harmanTextPaint.measureText(brandText)
        val subWidth = harmanSubTextPaint.measureText(subText)

        val fm = harmanTextPaint.fontMetrics
        val capHeight = -fm.ascent * 0.74f

        // Anchor "for Xiaomi" cleanly near the bottom-right corner first, then stack HARMAN + swooshes above
        val rightMargin = 28f
        val bottomMargin = 12f
        val subBaselineY = canvasHeight - bottomMargin

        // Leave generous vertical clearance (34px) between HARMAN baseline and "for Xiaomi" baseline
        // so the lower crescent swoosh sits purely between the two lines without colliding with either.
        val textBaselineY = subBaselineY - 34f
        val textTopY = textBaselineY - capHeight
        val textLeft = canvasWidth - rightMargin - textWidth

        // 1. Upper curved swoosh arc sweeping cleanly above 'A R M A N'
        upperSwooshPath.reset()
        upperSwooshPath.moveTo(textLeft + textWidth * 0.18f, textTopY - 4f)
        upperSwooshPath.cubicTo(
            textLeft + textWidth * 0.56f, textTopY - 21f,
            textLeft + textWidth * 1.12f, textTopY - 16f,
            textLeft + textWidth + 14f, textBaselineY - 4f
        )
        upperSwooshPath.cubicTo(
            textLeft + textWidth * 1.04f, textTopY - 9f,
            textLeft + textWidth * 0.56f, textTopY - 12f,
            textLeft + textWidth * 0.18f, textTopY - 4f
        )
        upperSwooshPath.close()
        canvas.drawPath(upperSwooshPath, harmanSwooshPaint)

        // 2. Bold "HARMAN" wordmark
        canvas.drawText(brandText, textLeft, textBaselineY, harmanTextPaint)

        // 3. Lower curved swoosh arc sweeping cleanly BELOW the baseline of 'H A R M'
        val swooshStartLeftX = textLeft - 12f
        val swooshStartY = textTopY + capHeight * 0.45f
        val swooshDipBottomY = textBaselineY + 15f
        val swooshDipInnerY = textBaselineY + 9f
        val swooshEndRightX = textLeft + textWidth * 0.70f
        val swooshEndRightY = textBaselineY + 4.5f

        lowerSwooshPath.reset()
        lowerSwooshPath.moveTo(swooshStartLeftX, swooshStartY)
        lowerSwooshPath.cubicTo(
            textLeft - 6f, swooshDipBottomY,
            textLeft + textWidth * 0.35f, swooshDipBottomY + 2f,
            swooshEndRightX, swooshEndRightY
        )
        lowerSwooshPath.cubicTo(
            textLeft + textWidth * 0.35f, swooshDipInnerY + 1f,
            textLeft - 1f, swooshDipInnerY,
            swooshStartLeftX, swooshStartY
        )
        lowerSwooshPath.close()
        canvas.drawPath(lowerSwooshPath, harmanSwooshPaint)

        // 4. "for Xiaomi" subtitle centered horizontally beneath HARMAN and below the lower swoosh
        val subX = textLeft + (textWidth - subWidth) / 2f
        canvas.drawText(subText, subX, subBaselineY, harmanSubTextPaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isDspActive) startAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
