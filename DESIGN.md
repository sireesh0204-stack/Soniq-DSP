# Design System Specification (`DESIGN.md`)

> **Project**: Buds2Pro-HyperOS-Optimizer (`Soniq DSP`)  
> **Aesthetic Direction**: Cyberpunk Audiophile / Matte Obsidian (Inspired by CRED, Nothing OS, and Sennheiser HE-1)  
> **Form Factor**: Android Smartphone Portrait (Primary Hardware: Xiaomi Redmi Turbo 5, 6.67" AMOLED, 1220x2712 px, 120Hz)  
> **Master Specifications**:
> - Detailed Aesthetic & Element Catalog: [`UI.md`](UI.md)
> - 5-Stage Construction Pipeline & Quality Gates: [`UI_HARNESS.md`](UI_HARNESS.md)
> - Motion Dynamics, Spring Physics & 120Hz Pipeline: [`ANIMATIONS.md`](ANIMATIONS.md)

---

## 1. UI Framework & Screen Constraints

- **Framework**: Traditional Android View System (XML layouts with `ViewBinding`).
- **Custom Hardware Views**: Canvas-accelerated views (`AetherVisualizerView`) running on hardware VSYNC (`postInvalidateOnAnimation()`).
- **Platform Boundaries**:
  - `minSdk`: `28` (Android 9.0 Pie)
  - `compileSdk`: `34` (Android 14)
  - `targetSdk`: `34` (Android 14) / Tested on Android 16 (API 36, HyperOS)
- **Target Dimensions**: Optimized for high-density modern AMOLED displays ($1080 \times 2400$ to $1220 \times 2712$ px, 20:9 aspect ratio).

---

## 2. Theme Tokens & Styling

### 2.1 Color Palette
```xml
<!-- Obsidian Backgrounds & Surfaces -->
<color name="bg_dark">#08090C</color>            <!-- Deepest OLED black -->
<color name="surface_carbon">#101318</color>      <!-- Card & container surface -->
<color name="surface_raised">#181C24</color>      <!-- Raised components & dialogs -->
<color name="surface_button">#161922</color>      <!-- Inactive button fill -->

<!-- Precision Specular Borders -->
<color name="border_subtle">#262C38</color>       <!-- Neutral card border -->
<color name="border_specular">#444E63</color>     <!-- High-contrast highlight -->
<color name="border_cyan">#00F0FF</color>         <!-- Active cyan stroke -->
<color name="border_gold">#E5B869</color>         <!-- Accent gold stroke -->

<!-- Accents & Functional States -->
<color name="accent_cyan">#00F0FF</color>         <!-- Primary cyber cyan / Active profile -->
<color name="accent_gold">#E5B869</color>         <!-- Satin gold / Master toggle border / Flac -->
<color name="accent_emerald">#10B981</color>      <!-- Connection active green -->
<color name="accent_ruby">#FF3B56</color>         <!-- Error / Bypass indicator -->
<color name="stealth_grey">#3A4354</color>        <!-- Discreet watermark & muted borders -->

<!-- Typography -->
<color name="text_white">#F7F8FA</color>          <!-- Primary header text -->
<color name="text_secondary">#94A3B8</color>      <!-- Subtitle & telemetry labels -->
<color name="text_muted">#475569</color>          <!-- Disabled & footer copy -->
<color name="text_dark_contrast">#0C0E12</color>  <!-- Dark text on cyan active button fill -->
<color name="text_disconnected">#757C8A</color>   <!-- Disconnected speaker state -->
```

### 2.2 Typography & Text Treatments
- **Font Family**: Android system `sans-serif` (system-native font stack).
  > *Flagged for User Input*: If a custom brand font (e.g. *Inter*, *Space Grotesk*, or *Roboto Mono*) is planned, place `.ttf` files in `app/src/main/res/font/`.
- **Text Sizing & Tracking Hierarchy**:
  - **Screen Titles / App Brand**: `16sp` - `18sp`, bold, `letterSpacing="0.12"` (Uppercase tracked: `S O N I Q  //  S T U D I O  D S P`).
  - **Live Telemetry HUD**: `11.5sp` - `12sp`, bold monospace/sans, `letterSpacing="0.08"`.
  - **Master Action Buttons**: `15sp`, bold, `letterSpacing="0.10"`.
  - **Profile Selector Tabs**: `13sp`, bold, `letterSpacing="0.06"`.
  - **Subtitles & Footnotes**: `10sp` - `11sp`, regular, `letterSpacing="0.04"`.

### 2.3 Spacing, Geometry & Corner Radii
- **Base Grid**: 4dp / 8dp incremental spacing.
- **Screen Margins**: `24dp` horizontal padding (`layout_marginStart="24dp"`, `layout_marginEnd="24dp"`).
- **Component Spacing**:
  - `24dp` vertical gap between major action blocks.
  - `14dp` bottom margin for the footer watermark badge.
- **Corner Radii**:
  - Interactive Action Buttons: **`10dp`** (`app:cornerRadius="10dp"`).
  - Cards & Bottom Sheets: **`16dp`** to **`20dp`**.
- **Elevation**: Flat design (`elevation="0dp"`). All visual depth is achieved via specular border strokes (`0.8dp` to `1.2dp`) rather than drop shadows.

### 2.4 Iconography & Vector Assets
- **Format**: Android Vector Drawables (`<vector>`) with AAPT gradients.
- **Viewports**:
  - Status & Tile Icons: `24 x 24` or `48 x 48` (`ic_tile_*.xml`).
  - ASZSD Monogram Emblem: `400 x 120` viewport, rendered at `84dp x 25dp`, single continuous wireframe trace.
- **Stroke Treatment**: Rounded caps (`strokeLineCap="round"`), rounded joins (`strokeLineJoin="round"`), width `6.5` to `7.0`.

---

## 3. Naming Conventions

| Asset Type | File / ID Convention | Examples |
| :--- | :--- | :--- |
| **Activity Layouts** | `activity_<screen_name>.xml` | `activity_main.xml` |
| **Bottom Sheet Layouts** | `bottom_sheet_<name>.xml` | `bottom_sheet_aether_guide.xml` |
| **App Widget Layouts** | `widget_<col>x<row>_layout.xml` | `widget_2x1_layout.xml`, `widget_4x2_layout.xml` |
| **Vector Drawables** | `ic_<descriptor>.xml` | `ic_tile_dolby.xml`, `ic_tile_original.xml`, `ic_aszsd_monogram.xml` |
| **Buttons** | `btn<Descriptor>` | `btnMasterToggle`, `btnProfileOriginal`, `btnHyperOs` |
| **Text Views** | `tv<Descriptor>` | `tvAppTitle`, `tvCodecStatus`, `tvTelemetry` |
| **Images & Icons** | `iv<Descriptor>` | `ivAszsdWatermark` |
| **Toggles & Checkboxes** | `cb<Descriptor>` | `cbHaptics`, `cbSound` |

---

## 4. Existing Screens & Components

1. **`MainActivity` ([`activity_main.xml`](file:///d:/Mobile%20Projects/Buds2Pro-HyperOS-Optimizer/app/src/main/res/layout/activity_main.xml))**:
   - Primary studio dashboard featuring the tracked title header, dynamic live audio format telemetry HUD, 3-way profile selector (`ORIGINAL`, `DOLBY`, `XIAOMI`), active 120Hz liquid wave visualizer, master DSP bypass button, HyperOS shortcut, tactile switches, and the stealth ASZSD watermark.
2. **`AetherGuideBottomSheet` ([`bottom_sheet_aether_guide.xml`](file:///d:/Mobile%20Projects/Buds2Pro-HyperOS-Optimizer/app/src/main/res/layout/bottom_sheet_aether_guide.xml))**:
   - Slide-up modal educating the user on Harman 2019v2 target benefits, pre-amp digital headroom (-2.5dB), spatial audio compatibility, and Xiaomi HyperOS system permission setup.
3. **Home Screen Widgets** ([`app/src/main/res/layout/widget_*_layout.xml`](file:///d:/Mobile%20Projects/Buds2Pro-HyperOS-Optimizer/app/src/main/res/layout/)):
   - Compact homescreen widgets (2x1, 2x2, 2x4, 4x1, 4x2) providing glanceable DSP status and one-tap profile toggling.

---

## 5. Motion Design & Interaction System

> **Comprehensive Motion Specification**: See [`ANIMATIONS.md`](ANIMATIONS.md) for full taxonomy, state diagrams, GitHub research repositories, and planned physics implementations.

The motion system is built on **ColorOS Aquamorphic** and **Material Motion** principles, optimized for high-refresh-rate AMOLED displays (120Hz locked). Every motion conveys physical weight, surface tension, and organic fluid damping rather than artificial mechanical jerking.

### 5.1 Global Motion Tokens

| Token | Value | Target & Usage |
| :--- | :--- | :--- |
| `DURATION_TACTILE_PRESS` | `90ms` | Immediate haptic-linked compression on touch down. |
| `DURATION_TACTILE_RELEASE` | `220ms` | Relaxing spring rebound upon touch release. |
| `DURATION_FADE_TRANSITION` | `320ms` | Soft alpha / scale dissolution of secondary controls. |
| `DURATION_FLUID_GLIDE` | `520ms` | Primary layout spatial continuity (vertical translation). |
| `INTERPOLATOR_TACTILE_DOWN` | `PathInterpolator(0.20f, 0.00f, 0.00f, 1.00f)` | Sharp, responsive initial finger compression. |
| `INTERPOLATOR_TACTILE_UP` | `PathInterpolator(0.20f, 1.00f, 0.30f, 1.00f)` | Gentle settle without overshoot or visual wobble. |
| `INTERPOLATOR_AQUAMORPHIC` | `PathInterpolator(0.22f, 1.00f, 0.36f, 1.00f)` | ColorOS fluid deceleration curve (~65 frames at 120Hz). |

### 5.2 UI Situation to Pattern Matrix

| UI Situation | Motion Pattern | Implementation Strategy |
| :--- | :--- | :--- |
| **Earbuds Connect / Disconnect** | **Shared Axis Y + Fade Through** | Secondary controls (`visualizer`, `btnMasterToggle`) fade (`alpha` $0 \leftrightarrow 1$, `scale` $0.96 \leftrightarrow 1.0$) while primary controls (`btnHyperOs`, `llToggles`) glide along the Y-axis via GPU RenderThread `translationY`. |
| **Interactive Button Press** | **Tactile Elastic Micro-interaction** | Touch down contracts to `0.965f` scale in 90ms. Touch release gently rebounds to `1.0f` in 220ms. If the finger slides outside view bounds, it cleanly cancels back to `1.0f`. |
| **Audio Visualizer Waveform** | **Continuous Liquid Spline** | Catmull-Rom to Cubic Bezier 96-segment ribbon spline driven by hardware VSYNC (`postInvalidateOnAnimation`) and framed by exponential smoothing (`damping = 6.5`). |
| **Profile Selection** | **Stateful Specular Crossfade** | Active button swaps to cyan (`#00F0FF`) with high-contrast text (`#0C0E12`), while siblings transition to carbon (`#161922`) with smooth ColorStateList tinting. |
| **Modal Sheets / Guidelines** | **Container Transform / Shared Axis Z** | Expands from origin bounds or slides up smoothly on the Z-plane with scrim dimming. |

### 5.3 Architectural Motion Invariants

1. **Zero Layout Reflow (`requestLayout` Invariant)**:
   - **NEVER** use `ConstraintSet.applyTo()` with `TransitionManager.beginDelayedTransition()` for dynamic runtime animations where views collapse or expand. This forces full CPU layout remeasures on every frame, resulting in stutter and multi-stage snapping.
   - **ALWAYS** use direct RenderThread GPU matrix transforms: `translationY`, `alpha`, and `scaleX`/`scaleY` on `View.INVISIBLE` backgrounds.
2. **Deterministic Startup Geometry**:
   - Initial layout state must wait for layout resolution (`rootLayout.doOnLayout`) before applying spatial transforms (`travelY`).
   - When setting initial states (`animate = false`), apply properties directly and synchronously without starting zero-duration animators.
3. **No Overshoot Wobble**:
   - Audio and engineering UI must feel stable and high-end. Never use `OvershootInterpolator` on buttons; use critically damped cubic beziers (`0.2, 1.0, 0.3, 1.0`).

### 5.4 Reference Design Knowledge Base

- **Material Motion System**: Container Transform, Shared Axis (X/Y/Z), Fade Through, and Elevation Morphing ([serbelga/Material-Motion-Samples](https://github.com/serbelga/Material-Motion-Samples)).
- **Android Animation Set**: Custom interpolators, property animation, hardware layers ([OCNYang/Android-Animation-Set](https://github.com/OCNYang/Android-Animation-Set)).
- **Compose & Physics Systems**: Damping ratios, stiffness, and mass modeling ([Jetpack Compose Animations](https://github.com/topics/compose-animations)).

