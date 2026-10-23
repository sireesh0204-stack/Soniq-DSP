# Soniq DSP // Engineering Progress & Ecosystem Report 🎧

> **Project**: Buds2Pro-HyperOS-Optimizer (`Soniq DSP`)  
> **Target Device**: Xiaomi Redmi Turbo 5 (`2606FRT34I`), Xiaomi HyperOS, Android 16 (API 36)  
> **Hardware Pair**: Samsung Galaxy Buds 2 Pro (SM-R510)  
> **Repository**: [`sireesh0204-stack/Soniq-DSP`](https://github.com/sireesh0204-stack/Soniq-DSP)  
> **Profile Hub**: [`sireesh0204-stack/sireesh0204-stack`](https://github.com/sireesh0204-stack/sireesh0204-stack)  
> **Last Updated**: October 23, 2026

---

## 1. Executive Summary & What Was Built

**Soniq DSP** was conceived as an advanced, hardware-tailored audiophile bridge connecting Samsung Galaxy Buds 2 Pro with Xiaomi HyperOS devices. The goal was to solve acoustic deficiencies in standard Android Bluetooth playback (inter-sample clipping, harsh treble spikes, spatial sound misalignments) while providing a cyberpunk-inspired studio interface with real-time telemetry.

```
┌────────────────────────────────────────────────────────────────────────┐
│                              SONIQ DSP                                 │
│                                                                        │
│  [ORIGINAL]                [DOLBY]                 [XIAOMI]            │
│  Harman 2019v2 Target      Cinematic Soundstage    Fast Sub-Bass       │
│  -2.5dB Preamp Headroom    Dolby Atmos Profile     MiSound Curve       │
│                                                                        │
│           CODEC: AAC // GALAXY BUDS2 PRO (READY / PLAYING)             │
│        ~~~~~~~~~~~~~~~~~ Dynamic Fluid Wave ~~~~~~~~~~~~~~~~~~        │
│                                                     HARMAN for Xiaomi │
│                                                                        │
│                         [ ACTIVE / BYPASS ]                            │
│                    ENGAGE HYPEROS SOUND ENGINE                         │
│                    [✔] HAPTICS: ON    [✔] SOUND: ON                    │
│                                                                        │
│                              — a-S-Z-S-D —                             │
└────────────────────────────────────────────────────────────────────────┘
```

### Key Technical Systems Implemented:
1. **Audiophile DSP Engine**:
   - Target calibration restored to the **Harman 2019v2 In-Ear Target Curve**.
   - Built-in **`-2.5 dB` digital preamp headroom** attenuator to prevent inter-sample clipping on compressed Bluetooth streams (AAC/SBC).
   - Global Audio Session 0 routing running in a lightweight foreground audio service (`AudioDspService`).
2. **HyperOS Sound Engine Synchronization (`HyperOSHelper.kt`)**:
   - Direct integration with Xiaomi HyperOS's proprietary sound framework (`com.miui.misound`).
   - Presets mapped dynamically:
     - `ORIGINAL`: Bit-perfect unenhanced monitoring baseline.
     - `DOLBY`: Engages HyperOS Dolby Atmos audio processing.
     - `XIAOMI`: Engages proprietary Xiaomi MiSound acoustic engine.
   - Dual-table synchronization across `Settings.System` and `Settings.Global`.
3. **Live Hardware Audio Telemetry**:
   - Queries `BluetoothCodecConfig` (sample rates from 44.1kHz up to 192kHz; bit depths from 16-bit to 32-bit Hi-Res).
   - Real-time `AudioPlaybackConfiguration` listener detecting active media streams vs idle states.
   - Telemetry HUD dynamically rendering `PLAYING: [BIT] / [SAMPLE_RATE] // [CODEC]`.
4. **Organic Visualizer (`AetherVisualizerView.kt`)**:
   - Custom hardware-accelerated Canvas wave renderer with harmonic Bézier curves.
   - Authentic Harman Kardon emblem featuring upper and lower crescent swooshes and stacked typography.
5. **ASZSD Monogram Brand Emblem**:
   - Faithfully vectorized from the creator's handwritten sketch into a single continuous wireframe trace.
   - Letters: **a - S - Z - S - D** (Anasuri, Sireesh, Zoro, Sweety, Dhanvika).
   - Styled with a subtle matte stealth grey (`#3A4354`) finish blended into the dark AMOLED chassis (`#0C0E12`).
6. **Quick Settings Tiles**:
   - `AetherProfileTileService`: Cycle profiles directly from the Android Control Center.
   - `AetherDspTileService`: Instant Master DSP toggle without opening the app.
7. **Two-Tier AI Engineering Architecture**:
   - Documented in [`WORKFLOW.md`](file:///d:/Mobile%20Projects/Buds2Pro-HyperOS-Optimizer/docs/WORKFLOW.md).
   - Establishes role separation: Antigravity as System Architect/Debugger, and Gemini Argon 4 / Pro for targeted code generation.

---

## 2. Key Milestones Reached

| Milestone | Status | Description |
| :--- | :---: | :--- |
| **Physical Hardware Deployment** | ✅ Complete | Successfully compiled and deployed directly to **Redmi Turbo 5** via ADB with zero runtime permissions hurdles. |
| **Harman Target DSP Core** | ✅ Complete | Android `Equalizer` engine mapped with millibel-level accuracy across 5 key acoustic bands with preamp headroom. |
| **HyperOS Framework Bridging** | ✅ Complete | Reverse-engineered `miui-framework.jar` and `com.miui.misound`, identifying system keys and intent broadcasts. |
| **Real-Time Stream Telemetry** | ✅ Complete | Dynamic detection of active Bluetooth playback configs, sample rates, and connected hardware sinks. |
| **GitHub Open Ecosystem** | ✅ Complete | Created and pushed [`sireesh0204-stack/Soniq-DSP`](https://github.com/sireesh0204-stack/Soniq-DSP) on `main`. |
| **Brand Profile Hub** | ✅ Complete | Created the special GitHub Profile repository [`sireesh0204-stack/sireesh0204-stack`](https://github.com/sireesh0204-stack/sireesh0204-stack) with ASZSD vector assets. |
| **Aesthetic Monogram Integration** | ✅ Complete | Transformed hand drawing into a sleek, matte continuous trace integrated into app and GitHub headers. |

---

## 3. Engineering Challenges & Solutions

### Challenge 1: The `AudioEffectCenter` Privilege Crash (Android 16 / HyperOS)
* **Problem**: Invoking `AudioEffectCenter.getInstance(context).setEffectActive(...)` directly inside our third-party app process caused HyperOS to instantiate `BaseEffectPresenter` on our app's main Looper. The presenter called `Spatializer.setEnabled()`, throwing an uncatchable fatal exception:
  `java.lang.SecurityException: Access denied, requires: android.permission.MODIFY_DEFAULT_AUDIO_EFFECTS`.
* **Root Cause**: `MODIFY_DEFAULT_AUDIO_EFFECTS` is a platform signature permission that cannot be granted to user apps.
* **Solution**: Bypassed in-process instantiation of `AudioEffectCenter`. State transitions are communicated via `Settings.Global`, `Settings.System`, and directed Intent broadcasts (`ACTION_SYSTEM_UI_DOLBY_EFFECT_SWITCH`, `ACTION_AUDIO_EFFECT_REFRESH`) to `com.miui.misound`, which possesses the system signature and executes the effect change in its own process.

### Challenge 2: Gradle & AGP Upgrade Incompatibilities
* **Problem**: Android Studio Upgrade Assistant attempted to upgrade Gradle to version 8.13/9.7.1, causing download timeouts (`SocketTimeoutException`) and build configuration errors against AGP 8.2.2.
* **Solution**: Reverted and locked `gradle-wrapper.properties` to the locally cached, verified **Gradle 8.2.1-bin**, reducing incremental build times to 2-4 seconds.

### Challenge 3: Static Bluetooth A2DP Codec Handshake vs Live Playback
* **Problem**: The stock Android Bluetooth stack reports the base negotiated profile (16-bit 44.1kHz), failing to reflect higher sample rates or bit depths when Hi-Res tracks are streaming.
* **Solution**: Combined `BluetoothCodecConfig` telemetry with active `AudioPlaybackConfiguration` inspection and `AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE` fallbacks to dynamically display live audio formats (`24-BIT / 96kHz`, `24-BIT / 48kHz`, etc.).

### Challenge 4: Visualizer Wave Jitter on 120Hz Displays
* **Problem**: The original secondary harmonic calculation used non-integer multiples of $\pi$ (`3.8 * PI` and `7.2 * PI`), causing phase collisions and jitter at the wave boundary edges on the 120Hz AMOLED panel.
* **Solution**: Migrated to pure integral harmonics ($\sin(2\pi \cdot x)$ and $\sin(4\pi \cdot x)$) with frame-rate-independent damping.

---

## 4. Current App Status & Next Steps

### Current Status: 🟢 STABLE / ACTIVE DEVELOPMENT
- **App Stability**: Zero crashes on launch, button interaction, or backgrounding.
- **Profile Switching**: Dolby Atmos and Xiaomi Sound switch reliably in HyperOS settings.
- **Hardware Connection**: Automatic detection of connected Galaxy Buds 2 Pro; DSP correctly bypasses (`[ BYPASSED ]`) when running on phone speakers to protect battery.
- **Visuals**: Dull stealth matte grey ASZSD emblem perfectly blended at the bottom footer.

### Active Items in Flight:
1. **Phone Speaker Baseline Sync**: Ensuring that selecting "ORIGINAL" while earbuds are disconnected visibly flips HyperOS phone speaker settings to the unenhanced baseline rather than defaulting to Dolby.
2. **Choreographer 120Hz Visualizer**: Upgrading the wave drawing loop to a VSYNC-aligned `postInvalidateOnAnimation()` cadence for butter-smooth fluidity on the Redmi Turbo 5's 120Hz display.

