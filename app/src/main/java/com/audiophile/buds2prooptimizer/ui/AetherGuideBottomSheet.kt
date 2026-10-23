package com.audiophile.buds2prooptimizer.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.audiophile.buds2prooptimizer.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AetherGuideBottomSheet : BottomSheetDialogFragment() {

    data class GuideModule(
        val key: String,
        val codeTitle: String,
        val badgeText: String,
        val description: String
    )

    private val modules = listOf(
        GuideModule(
            key = KEY_TELEMETRY,
            codeTitle = "01 / Hardware Telemetry",
            badgeText = "RFCOMM BYTE 6•7•8",
            description = "Live battery readouts directly from the earbud microchip. Shows \"IN CASE\" when docked."
        ),
        GuideModule(
            key = KEY_ANC,
            codeTitle = "02 / Active Noise Cancellation (ANC)",
            badgeText = "PHASE INVERSION • 0x01",
            description = "Generates inverted acoustic soundwaves via outer microphones to cancel out low-frequency rumble, ambient traffic, and background noise."
        ),
        GuideModule(
            key = KEY_AMBIENT,
            codeTitle = "03 / Ambient Mode",
            badgeText = "TRANSPARENCY • 0x02",
            description = "Feeds external sound into your ears via microphones so you can talk and hear traffic without taking off your earbuds."
        ),
        GuideModule(
            key = KEY_OFF,
            codeTitle = "04 / Normal (Off)",
            badgeText = "PASSIVE ISOLATION • 0x00",
            description = "Passive silicon isolation only; maximizes earbud battery life up to 8 hours."
        ),
        GuideModule(
            key = KEY_MATRIX,
            codeTitle = "05 / 64-Bit Studio Matrix",
            badgeText = "+3.0 dB @ 16 kHz AIR",
            description = "Reverses Android's AAC treble roll-off by injecting +3.0 dB at 16 kHz (\"air\") and calibrating the Harman In-Ear Target curve for studio-grade instrument separation."
        ),
        GuideModule(
            key = KEY_PREAMP,
            codeTitle = "06 / Digital Pre-Amp (-2.5 dB)",
            badgeText = "ANTI-CLIPPING HEADROOM",
            description = "Eliminates inter-sample digital clipping on high-energy lossless FLAC music."
        ),
        GuideModule(
            key = KEY_SKINS,
            codeTitle = "07 / Widget Skins",
            badgeText = "REAL-TIME CHASSIS ENGINE",
            description = "Themes home screen widgets into Liquid Glass, Nothing UI, or CRED 3D Obsidian in real-time."
        )
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_aether_guide, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        val highlightKey = arguments?.getString(ARG_HIGHLIGHT_KEY)
        val container = view.findViewById<LinearLayout>(R.id.guideItemsContainer)
        val scrollView = view.findViewById<NestedScrollView>(R.id.guideScrollView)
        val btnClose = view.findViewById<TextView>(R.id.btnCloseGuide)

        btnClose.setOnClickListener { dismiss() }

        val density = resources.displayMetrics.density
        var highlightedView: View? = null

        modules.forEach { module ->
            val isHighlighted = module.key == highlightKey
            val cardView = buildModuleCard(module, isHighlighted, density)
            container.addView(cardView)
            if (isHighlighted) {
                highlightedView = cardView
            }
        }

        highlightedView?.let { target ->
            scrollView.post {
                scrollView.smoothScrollTo(0, target.top)
            }
        }
    }

    private fun buildModuleCard(
        module: GuideModule,
        isHighlighted: Boolean,
        density: Float
    ): View {
        val ctx = requireContext()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * density).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (12 * density).toInt()
            }
            background = GradientDrawable().apply {
                cornerRadius = 14f * density
                setColor(Color.parseColor(if (isHighlighted) "#141A24" else "#0E1117"))
                setStroke(
                    (if (isHighlighted) (1.5f * density) else (1f * density)).toInt(),
                    Color.parseColor(if (isHighlighted) "#00F0FF" else "#22FFFFFF")
                )
            }
        }

        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val titleView = TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            text = module.codeTitle
            textSize = 13f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setTextColor(
                if (isHighlighted) ContextCompat.getColor(ctx, R.color.accent_cyan)
                else ContextCompat.getColor(ctx, R.color.text_white)
            )
        }

        val badgeView = TextView(ctx).apply {
            text = module.badgeText
            textSize = 9f
            letterSpacing = 0.08f
            typeface = Typeface.MONOSPACE
            setTextColor(ContextCompat.getColor(ctx, R.color.accent_gold))
        }

        headerRow.addView(titleView)
        headerRow.addView(badgeView)

        val descView = TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * density).toInt()
            }
            text = module.description
            textSize = 12f
            setLineSpacing(4f * density, 1f)
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
        }

        card.addView(headerRow)
        card.addView(descView)
        return card
    }

    companion object {
        const val TAG = "AetherGuideBottomSheet"
        private const val ARG_HIGHLIGHT_KEY = "arg_highlight_key"

        const val KEY_TELEMETRY = "telemetry"
        const val KEY_ANC = "anc"
        const val KEY_AMBIENT = "ambient"
        const val KEY_OFF = "off"
        const val KEY_MATRIX = "matrix"
        const val KEY_PREAMP = "preamp"
        const val KEY_SKINS = "skins"

        fun newInstance(highlightKey: String? = null): AetherGuideBottomSheet {
            return AetherGuideBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_HIGHLIGHT_KEY, highlightKey)
                }
            }
        }
    }
}

