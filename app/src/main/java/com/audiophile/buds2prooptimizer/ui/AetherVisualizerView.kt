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
        lastFrameTimeNanos = System.nanoTime()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val now = System.nanoTime()
                val deltaSec = ((now - lastFrameTimeNanos).coerceAtLeast(0L) / 1_000_000_000.0).toFloat()
                lastFrameTimeNanos = now

                // Clamp deltaSec to prevent jumps after pause or drop frame
                val safeDelta = deltaSec.coerceIn(0.001f, 0.05f)

                // Smooth 3.2 rad/s progression independent of 60Hz or 120Hz display refresh rate
                phase = (phase + safeDelta * 3.2f) % (Math.PI.toFloat() * 2f)

                val target = if (isDspActive) targetAmplitude else 0f
                val smoothSpeed = safeDelta * 8f
                currentAmplitude += (target - currentAmplitude) * smoothSpeed.coerceIn(0.05f, 0.35f)

                invalidate()

                if (!isDspActive && currentAmplitude < 0.2f) {
                    currentAmplitude = 0f
                    invalidate()
                    cancel()
                }
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerY = height / 2f
        val widthF = width.toFloat()
        if (widthF <= 0) return

        wavePath.reset()
        val step = 6f
        var x = 0f
        var isFirst = true

        while (x <= widthF) {
            val normalizedX = x / widthF
            val yOffset = sin(normalizedX * Math.PI * 4 + phase) * currentAmplitude +
                          sin(normalizedX * Math.PI * 8 - phase * 1.4) * (currentAmplitude * 0.45f)
            val currentY = centerY + yOffset.toFloat()

            if (isFirst) {
                wavePath.moveTo(x, currentY)
                isFirst = false
            } else {
                wavePath.lineTo(x, currentY)
            }
            x += step
        }
        canvas.drawPath(wavePath, wavePaint)

        drawHarmanLogoBottomRight(canvas, widthF, height.toFloat())
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
