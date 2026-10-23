# System Architecture & Shared State Map

> **Project**: Buds2Pro-HyperOS-Optimizer (`Soniq DSP`)  
> **Platform**: Android 16 (API 36) / Xiaomi HyperOS  
> **Architecture Style**: Hybrid Service-Driven Audio DSP & Framework Reflection Engine  
> **Target Devices**: Samsung Galaxy Buds 2 Pro (SM-R510) + Xiaomi Redmi Turbo 5 (`2606FRT34I`)

---

## 1. Module & Directory Structure

```
Buds2Pro-HyperOS-Optimizer/
├── app/
│   ├── build.gradle.kts           # Application plugins, dependencies, and build config
│   └── src/main/
│       ├── AndroidManifest.xml    # Permissions, Service, Tile & Activity declarations
│       ├── java/com/audiophile/buds2prooptimizer/
│       │   ├── BudsOptimizerApplication.kt   # Application entrypoint & notification channels
│       │   ├── dsp/
│       │   │   ├── AudioDspService.kt        # Session 0 Equalizer service & lifecycle
│       │   │   └── Buds2ProAudioProfile.kt   # Sound profiles enum (ORIGINAL, DOLBY, XIAOMI)
│       │   ├── feedback/
│       │   │   ├── AetherHapticFeedback.kt   # System vibration & haptic tick feedback
│       │   │   └── AetherSoundEngine.kt      # SoundPool mechanical click effects
│       │   ├── tiles/
│       │   │   ├── AetherDspTileService.kt   # Quick Settings tile for Master DSP Toggle
│       │   │   └── AetherProfileTileService.kt # Quick Settings tile for Sound Profile cycle
│       │   ├── ui/
│       │   │   ├── MainActivity.kt           # Main UI, telemetry engine, profile dispatcher
│       │   │   ├── AetherVisualizerView.kt   # 120Hz VSYNC Catmull-Rom Bezier wave renderer
│       │   │   └── AetherGuideBottomSheet.kt # Interactive guide & instruction bottom sheet
│       │   └── util/
│       │       └── HyperOSHelper.kt          # System Settings & framework reflection bridge
│       └── res/                              # Drawables, layouts, values, and vector assets
├── docs/                                     # Architecture, bugs, workflow specifications
├── build.gradle.kts                          # Root build script (AGP 8.2.2, Kotlin 1.9.22)
└── gradle/wrapper/                           # Locked to Gradle 8.2.1-bin
```

---

## 2. Key Classes & Component Responsibilities

| Class / Component | Type | Responsibility |
| :--- | :--- | :--- |
| **`BudsOptimizerApplication`** | `Application` | Initializes high-priority notification channels (`DSP_SERVICE_CHANNEL`) on app cold start. |
| **`MainActivity`** | `AppCompatActivity` | Binds UI controls, queries A2DP / playback telemetry, responds to device connections, and orchestrates profile changes. |
| **`AudioDspService`** | Foreground `Service` | Owns Android Global Audio Session 0 `android.media.audiofx.Equalizer`. Applies millibel band gains for Harman target. Listens to `ACTION_ACL_DISCONNECTED` to auto-stop. |
| **`HyperOSHelper`** | Singleton `object` | Communicates with Xiaomi HyperOS audio system: updates `Settings.Global`/`Settings.System`, broadcasts to `com.miui.misound`, and interfaces with `AudioEffectCenter`. |
| **`AetherVisualizerView`** | Custom `View` | Hardware-accelerated dynamic waveform rendering. Uses Catmull-Rom to Cubic Bézier spline with VSYNC-driven `postInvalidateOnAnimation()` locked to 120Hz. |
| **`Buds2ProAudioProfile`** | `enum class` | Presets (`ORIGINAL_SOUND`, `DOLBY_ATMOS`, `XIAOMI_SOUND`) with display names, EQ millibel band levels, and target HyperOS effect keys. |
| **`AetherProfileTileService`** | `TileService` | Android Quick Settings tile to cycle audio profiles without opening the main activity. |
| **`AetherDspTileService`** | `TileService` | Android Quick Settings tile to toggle master DSP on/off. |
| **`AetherHapticFeedback`** | Utility | Tactile click, heavy click, and double click patterns using `Vibrator` / `VibrationEffect`. |
| **`AetherSoundEngine`** | Utility | Low-latency click audio feedback using `SoundPool`. |

---

## 3. Lifecycle & Callback Flow

```
                      ┌──────────────────────────────────────────────┐
                      │              App Launch / Resume             │
                      └──────────────────────┬───────────────────────┘
                                             │
                       ┌─────────────────────┴─────────────────────┐
                       ▼                                           ▼
       AudioManager.registerAudioPlaybackCallback()   BluetoothAdapter.getProfileProxy()
       - Evaluates active track sample rate / bit     - Binds BluetoothA2dp service listener
       - Detects if music is actively streaming       - Queries negotiated A2DP codec config
                       │                                           │
                       └─────────────────────┬─────────────────────┘
                                             ▼
                                  updateCodecTelemetry()
                                  - Renders "PLAYING: 24-BIT / 96kHz // AAC"
                                  - Renders "CODEC: AAC // BUDS2 PRO (READY)"
                                  - Renders "DISCONNECTED // PHONE SPEAKER"
                                             │
      ┌──────────────────────────────────────┼──────────────────────────────────────┐
      │ AudioDeviceCallback (Connect)        │ User Taps Profile Button             │ AudioDeviceCallback (Disconnect)
      ▼                                      ▼                                      ▼
loadState()                            setProfile(targetProfile)              forceDisableDsp()
- Checks isHeadsetConnected()          - Updates SharedPreferences            - isDspActive = false
- If connected: permits DSP toggle     - Dispatches HyperOSHelper             - DSP bypassed on speaker
- If disconnected: forces bypass       - Starts/notifies AudioDspService      - Updates UI to flat line
```

---

## 4. Shared State Map (Critical for Regression Prevention)

The following state objects, singletons, and storage keys are shared across multiple components. **Any change touching one will affect the others:**

### 4.1 Storage & Preferences: `SharedPreferences("AetherPrefs")`
| Key | Type | Written By | Read By | Invariant / Side-Effect |
| :--- | :---: | :--- | :--- | :--- |
| **`"DSP_ACTIVE"`** | `Boolean` | `MainActivity.toggleDsp`, `MainActivity.forceDisableDsp`, `AudioDspService.onReceive(ACL_DISCONNECT)` | `MainActivity.loadState`, `AudioDspService`, `AetherDspTileService`, `AetherProfileTileService` | Must be `false` when `isHeadsetConnected() == false`. |
| **`"ACTIVE_PROFILE"`**| `String` | `MainActivity.setProfile`, `AetherProfileTileService.onClick` | `MainActivity.loadState`, `AudioDspService.onStartCommand`, `AetherProfileTileService.onStartListening` | Defaults to `ORIGINAL_SOUND` if null. |

### 4.2 System Settings & ContentProviders (`Settings.Global` & `Settings.System`)
| Key | Table | Written By | Read / Observed By | Invariant / Side-Effect |
| :--- | :---: | :--- | :--- | :--- |
| **`effect_implementer`** | Global & System | `HyperOSHelper.updateSystemSettings` | `com.miui.misound`, `HeadsetSettingsActivity`, HyperOS System Settings UI | Values: `"none"`, `"dolby"`, `"misound"`. |
| **`last_select_effect_none`** | Global & System | `HyperOSHelper.updateSystemSettings` | HyperOS Sound Effects framework | `1` when Original Sound; `0` when Dolby or Xiaomi. |
| **`dolby_effect_switch`** | Global & System | `HyperOSHelper.updateSystemSettings` | Dolby Audio Processing engine & HyperOS speaker master toggle | `1` when Dolby; `0` when Original or Xiaomi. Master speaker switch on phone. |
| **`misound_state`** | Global & System | `HyperOSHelper.updateSystemSettings` | Xiaomi MiSound service | `1` when Xiaomi; `0` when Original or Dolby. |

### 4.3 Hardware & Session Overrides: Audio Session 0
| Resource | Priority | Controller Component | Competing Component | Invariant / Risk |
| :--- | :---: | :--- | :--- | :--- |
| **Android Session 0 Equalizer** | Normal (0) | `AudioDspService.masterEqualizer` | System Equalizers | Only enabled when `isDspActive == true` AND `isHeadsetConnected() == true` AND `currentProfile == ORIGINAL_SOUND`. |
| **AudioEffect Hidden Handles** | High (10000) | `HyperOSHelper.retainedEffects` | `com.miui.misound` (priority 0) | Retained effect handles must be released cleanly when exiting "none" to avoid stale Session 0 audio pipeline deadlocks. |

### 4.4 Framework Singletons & Reflection: `AudioEffectCenter`
| Resource | Scope | Accessor | Risk / Constraint |
| :--- | :--- | :--- | :--- |
| **`android.media.audiofx.AudioEffectCenter`** | In-process singleton | `HyperOSHelper.invokeAudioEffectCenter` | Instantiates `BaseEffectPresenter` on caller's Looper. Calling invalid names (e.g. `"none"`) triggers `Spatializer.setEnabled()` and crashes with `SecurityException` (`MODIFY_DEFAULT_AUDIO_EFFECTS`). Only `"dolby"` and `"misound"` may be activated. |
