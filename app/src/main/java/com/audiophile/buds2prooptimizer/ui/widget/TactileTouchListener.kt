package com.audiophile.buds2prooptimizer.ui.widget

import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import com.audiophile.buds2prooptimizer.util.HapticEngine

/**
 * Attaches CRED-style 3D physical depression and haptic feedback to any view or button.
 */
class TactileTouchListener(
    private val hapticEngine: HapticEngine,
    private val isHeavy: Boolean = false,
    private val onClick: (() -> Unit)? = null
) : View.OnTouchListener {

    private val overshoot = OvershootInterpolator(2.0f)

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Compress 3D button inward
                v.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .translationY(4f)
                    .setDuration(70)
                    .start()

                if (isHeavy) {
                    hapticEngine.heavyThud(v)
                } else {
                    hapticEngine.click(v)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                // Spring release
                v.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .translationY(0f)
                    .setInterpolator(overshoot)
                    .setDuration(180)
                    .start()

                onClick?.invoke()
                v.performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                v.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .translationY(0f)
                    .setDuration(100)
                    .start()
                return true
            }
        }
        return false
    }

    companion object {
        fun attach(view: View, hapticEngine: HapticEngine, isHeavy: Boolean = false, onClick: (() -> Unit)? = null) {
            view.setOnTouchListener(TactileTouchListener(hapticEngine, isHeavy, onClick))
        }
    }
}

