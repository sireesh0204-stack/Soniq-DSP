# UI Aesthetic Architecture & Design System (`UI.md`)

> **Project**: Buds2Pro-HyperOS-Optimizer (`Soniq DSP`)  
> **Genre**: Professional Audiophile Utility & Hardware DSP Engine  
> **Dominant Aesthetic**: Matte Obsidian / Dark Cyberpunk + Skeuomorphic-Modern Hybrid  
> **Target Display**: 120Hz Variable Refresh Rate AMOLED (20:9 Aspect Ratio)

---

## 1. Genre-to-Aesthetic Taxonomy & Visual Archetypes

Modern digital design is governed by cognitive load parameters, ergonomic reachability on 20:9 screens, and perceptual contrast algorithms. Software genres must align with their natural neuro-cognitive archetypes:

| Genre | Dominant Aesthetic Archetype | Primary Luminance Strategy | Z-Axis Depth Mechanism | Core Cognitive Trigger |
| :--- | :--- | :--- | :--- | :--- |
| **Professional Audio DSP & Hardware** | **Matte Obsidian / Skeuomorphic-Modern** | `< 18%` (OLED True Black `#030508`) | 1px Specular Border Strokes | Precision, Physical Affordance & Luxury |
| **FinTech & High-Trust (CRED)** | **Matte Obsidian / Dark Cyberpunk** | `< 15%` (OLED Pure Void) | Chamfered Specular Edges | Exclusivity, Security & High Authority |
| **Productivity & Knowledge** | **Clean Bento Grid / Spatial Material** | Adaptive / Contextual | Glassmorphic Material Blur | Spatial Organization & Visual Clarity |
| **Developer Tools & Telemetry** | **Neo-Brutalism & High Contrast** | `> 85%` (Stark Monochromatic) | Hard 2px X/Y Offset Shadows | Raw Utility, Density & Truth |
| **Lifestyle & Media** | **Spatial Material / Dynamic Blur** | Immersive / Dynamic Backdrop | Translucent Layer Stacking | Immersion & Emotional Resonance |

### 1.1 The Matte Obsidian & Dark Cyberpunk Archetype
- **Cognitive Mechanism**: OLED absolute black (`#000000` to `#08090C`) prevents retinal fatigue and slightly dilates the user's pupil, heightening physiological focus on illuminated high-chroma data points.
- **Lighting Model**: Rejects muddy drop shadows (which increase GPU overdraw without visual gain on OLED). All depth is rendered via **1px specular border highlights** mimicking light catching the chamfered edge of machined aluminum.
- **Typography**: Heavily geometric, monospaced or uppercase-tracked sans-serifs evoking machine-readable accuracy.

### 1.2 The Skeuomorphic-Modern Hybrid
- **Cognitive Mechanism**: Leverages muscle memory and analog hardware mental models. Rotary dials, vacuum-tube meters, and tactile sliders feature sub-pixel specular highlights and physical detents.
- **Affordance**: Users interact using intuitive physical affordance rather than translating abstract numerical values.

---

## 2. Comprehensive Audiophile Design Tokens

### 2.1 Advanced Color Systems: OKLCH & Display P3 Gamut
Legacy sRGB and HSL models suffer from perceptual non-uniformity (shifting lightness drifts hue). The architecture enforces **OKLCH** within the **Display P3** wide color gamut:

```css
/* Core Audiophile Tokens in OKLCH */
:root {
  /* Surfaces (OLED Dark Mode) */
  --surface-000: oklch(0% 0 0);          /* #000000 OLED True Black */
  --surface-100: oklch(18% 0.01 260);    /* #0B0F17 Base Modules */
  --surface-200: oklch(25% 0.015 260);   /* Elevated Controls */

  /* Functional Neon Accents (sRGB Safe) */
  --neon-cyan:    oklch(70% 0.15 210);   /* Active DSP Profile */
  --neon-emerald: oklch(75% 0.18 150);   /* Safe dB / Connected */
  --neon-amber:   oklch(75% 0.20 60);    /* Peak Clipping Warning */
  --satin-gold:   oklch(80% 0.12 85);    /* Hi-Res / Master Toggle */

  /* Specular Highlights */
  --specular-border: oklch(100% 0 0 / 0.12); /* 1px Top Highlight */
}

@media (color-gamut: p3) {
  :root {
    /* Unlocks 50% more chromatic volume on modern AMOLED */
    --neon-cyan:    oklch(70% 0.30 210);
    --neon-emerald: oklch(75% 0.28 150);
    --neon-amber:   oklch(75% 0.32 60);
    --satin-gold:   oklch(80% 0.20 85);
  }
}
```

### 2.2 Telemetry Typography & Tabular Figures
- **Rule**: Rapidly fluctuating decibel numbers, sample rates, and bit depths must strictly enforce `font-variant-numeric: tabular-nums` (or Android `fontFeatureSettings = "tnum"`).
- **Justification**: Proportional digits cause horizontal layout jitter, triggering continuous CPU `requestLayout()` thrashing. Tabular numbers lock digit bounding boxes to identical widths.

### 2.3 G2 Squircles & Curvature Continuity
- **Mathematical Form**: Lamé superellipse $|x/a|^n + |y/b|^n = 1$ with exponent $n = 4$:
  $$\boxed{x^4 + y^4 = r^4}$$
- **Benefit**: Eliminates G1 circular arc kinks, creating seamless G2 curvature matching aerospace CNC machining. Approximate in CSS/Figma via `cornerSmoothing = 0.6`.

---

## 3. Ergonomics & Fitts's Law on 20:9 Viewports

On modern $20:9$ tall smartphone displays, physical screen width narrows while vertical distance expands. Interactive targets located in the top half cause severe rotational thumb counter-torque and increase selection latency by up to $340\%$.

```
┌──────────────────────────────────────────────────────────┐
│ TOP 20%: READ-ONLY PASSIVE TELEMETRY ZONE                │
│ • Live Codec Telemetry HUD                               │
│ • Real-Time FFT Spectrum Analyzer                        │
│ • ISO 226:2023 Phon Contour Displays                     │
├──────────────────────────────────────────────────────────┤
│ MIDDLE 40%: SECONDARY INTERPOLATION CANVAS               │
│ • Aether Visualizer Wave Spline Canvas                   │
│ • Parametric Equalizer Node Graph                        │
├──────────────────────────────────────────────────────────┤
│ BOTTOM 40%: PRIMARY ERGONOMIC THUMB ZONE                 │
│ • Profile Selector Segmented Buttons                     │
│ • Master DSP Bypass Switch (btnMasterToggle)             │
│ • HyperOS Sound Engine CTA (btnHyperOs)                  │
│ • Tactile Switches (Haptics: ON / Sound: ON)             │
│ (All targets strictly ≥ 48 x 48 dp)                      │
└──────────────────────────────────────────────────────────┘
```

---

## 4. Acoustic Math: Catmull-Rom Splines & ISO 226:2023

### 4.1 Centripetal Catmull-Rom Spline
- Parametric EQ curves must interpolate **through** user gain/frequency nodes rather than approximating them like cubic Béziers.
- Centripetal parameterization ($\alpha = 0.5$) guarantees $C^1$ continuity and prevents self-intersection loops or overshoot cusps.

### 4.2 ISO 226:2023 Normal Equal-Loudness Contours
- Human hearing sensitivity is non-linear; bass and treble drop off significantly faster at lower listening volumes.
- Loudness level in phons ($L_N$):
  $$A_f = 4.47 \times 10^{-3} \left(10^{0.025 L_p} - 1.15\right) + \left(0.4 \cdot 10^{(T_f + L_U)/10 - 9}\right)^{a_f}$$
  $$L_N = \left(\frac{10}{a_f}\right) \log_{10}(A_f) - L_U + 94$$
- Soniq DSP visually layers the ISO 226 compensation curve as a translucent guide over the manual target curve.

---

## 5. Master Catalog of UI Elements

### 5.1 Soniq DSP Component Terminology
1. **Segmented Button Group / Profile Selector** (`ORIGINAL`, `DOLBY`, `XIAOMI`):
   - UI/UX: `Segmented Control`, `Tab Switcher`, or `Toggle Button Group`.
   - Android: `MaterialButtonToggleGroup` or weighted linear button strip.
   - States: Active segment (Cyan `#00F0FF` fill, high-contrast text `#0C0E12`), Inactive segment (Carbon `#161922` fill, text `#FFFFFF`).
2. **Status Pill / Badge** (`DISCONNECTED // PHONE SPEAKER`):
   - UI/UX: `Status Chip`, `HUD Pill Indicator`, or `Context Badge`.
   - Android: Rounded container `TextView` with monospace letter tracking.
3. **Audio Waveform Visualizer** (`AetherVisualizerView`):
   - UI/UX: `Oscilloscope Ribbon`, `120Hz Liquid Spline`, `Dynamic Waveform`.
   - Android: Canvas-accelerated hardware view with Catmull-Rom Bezier spline.
4. **Master DSP Bypass Button** (`[ BYPASSED ]` / `[ ACTIVE ]`):
   - UI/UX: `Primary Mode Switch`, `DSP Bypass Toggle`, `Master State Button`.
   - Android: `MaterialButton` with gold specular stroke `#E5B869` ($1.2\text{dp}$).
5. **Primary CTA Button** (`ENGAGE HYPEROS SOUND ENGINE`):
   - UI/UX: `Outlined Call-to-Action (CTA)`, `High-Priority Action Button`.
   - Android: Outlined `MaterialButton` with cyan stroke `#00F0FF` ($0.8\text{dp}$).
6. **Toggle Badges / Binary Switches** (`HAPTICS: ON` | `SOUND: ON`):
   - UI/UX: `Toggle Chips`, `Checkbox Badges`, `Compact Binary Switches`.
   - Android: Styled `CheckBox` with custom color tints (`#E5B869` / `#00F0FF`).
7. **Brand Certification Watermark** (`HARMAN for Xiaomi` & `ASZSD Monogram`):
   - UI/UX: `Brand Emblem`, `Target Curve Certification Watermark`.
   - Android: Sub-pixel canvas draw or vector drawable (`ImageView`).

### 5.2 FinTech / CRED Component Terminology
1. **Stacked Card Deck / Bill Reminders**:
   - UI/UX: `Layered Bill Cards`, `Stacked Card Deck`.
   - Purpose: Visual depth representing outstanding obligations with card overlap.
2. **Expandable Pill Accordion** (`+4 more bills ∨`):
   - UI/UX: `Accordion Pill`, `Dropdown Stack Trigger`.
3. **Circular Action Icons** (`pay contacts`, `rewards`, `bank accounts`):
   - UI/UX: `Quick Actions`, `App Shortcuts`, `Circular Icon Buttons`.
4. **Pill Action Chips** (`bills & recharges`, `gift cards`, `shop`):
   - UI/UX: `Pill Buttons`, `Action Chips`, `Tag Grid`.
5. **Snippet / Info Bar** (UPI ID strip):
   - UI/UX: `Copy Bar`, `Snippet Bar`, `Metadata Strip`.
6. **Bottom Dock with Hero Center FAB** (`UPI` button):
   - UI/UX: `Bottom Navigation Bar` with `Hero Center Floating Action Button (FAB)`.

