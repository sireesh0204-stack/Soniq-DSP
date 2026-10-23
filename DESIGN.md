# Design System Specification (`DESIGN.md`)

> **Project**: Buds2Pro-HyperOS-Optimizer (`Soniq DSP`)  
> **Aesthetic Direction**: Cyberpunk Audiophile / Matte Obsidian (Inspired by CRED, Nothing OS, and Sennheiser HE-1)  
> **Form Factor**: Android Smartphone Portrait (Primary Hardware: Xiaomi Redmi Turbo 5, 6.67" AMOLED, 1220x2712 px, 120Hz)

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
