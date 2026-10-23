# Master Bug Registry (`BUGS.md`)

This registry tracks every historical and currently active bug in the Soniq DSP ecosystem. Each entry defines exact reproduction steps, coupled dependencies, non-negotiable invariants, and the regression check that guards it.

---

## Summary of Bugs & Automated Coverage

| Bug ID | Title | Status | Coupled With | Guard Type |
| :--- | :--- | :---: | :--- | :--- |
| **`BUG-001`** | HyperOS Sound Effects Profile Desync | **OPEN** | `BUG-002`, `BUG-003` | Automated (`scripts/device_smoke.sh`) |
| **`BUG-002`** | `AudioEffectCenter` `Spatializer.setEnabled` Crash | **FIXED** | `BUG-001`, `BUG-003` | Automated (`scripts/device_smoke.sh` + Logcat assert) |
| **`BUG-003`** | Phone Speaker Original Profile Reversion on Disconnect | **OPEN** | `BUG-001`, `BUG-002` | Automated (`scripts/device_smoke.sh`) + Manual |
| **`BUG-004`** | Visualizer Wave Stutter & Animator Repeat on 120Hz | **FIXED** | Independent | Automated (`scripts/verify.sh` Unit/Spline test) + Manual |
| **`BUG-005`** | Gradle Wrapper Download Timeout & AGP Incompatibility | **FIXED** | Independent | Automated (`scripts/verify.sh` Gradle assemble test) |
| **`BUG-006`** | Live Bluetooth Audio Format Telemetry Inaccuracy | **FIXED** | Independent | Automated (Unit Test `TelemetryLogicTest`) |

---

## Detailed Bug Registry

### `BUG-001`: HyperOS Sound Effects Profile Desynchronization
- **Status**: **OPEN**
- **Coupled With**: `BUG-002` (In-process reflection crash), `BUG-003` (Speaker baseline)
- **Symptom**: User taps **DOLBY**, **XIAOMI**, or **ORIGINAL** in Soniq DSP. The app buttons update, but opening HyperOS **Settings > Sound & vibration > Sound effects** reveals that the system profile slider has not moved or remains on the previous preset.
- **Repro Steps**:
  1. Launch `Soniq DSP` with earbuds connected.
  2. Tap **XIAOMI**.
  3. Pull down notification shade or open **Settings > Sound & vibration > Sound effects**.
  4. Observe whether "Xiaomi Sound" is active in system settings.
- **Root Cause**: Confirmed. Xiaomi HyperOS framework (`com.miui.misound`) maintains independent state across `Settings.Global`/`Settings.System` and its internal `AudioEffectCenter` service. Setting keys alone without driving the active implementer or dispatching `ACTION_SYSTEM_UI_DOLBY_EFFECT_SWITCH` leaves the system UI stale.
- **Fix**: Commit `3385f37` in [`HyperOSHelper.kt`](file:///d:/Mobile%20Projects/Buds2Pro-HyperOS-Optimizer/app/src/main/java/com/audiophile/buds2prooptimizer/util/HyperOSHelper.kt) in functions `setHyperOsEffect()` and `invokeAudioEffectCenter()`.
- **Must-Not-Break**: Must NOT invoke unregistered effect strings (e.g. `"none"`) in `AudioEffectCenter` (triggers `BUG-002` fatal crash). Must update both `Settings.Global` and `Settings.System` coherently.
- **Regression Check**: `scripts/device_smoke.sh` Step 2 (Asserts `Settings.Global` and `Settings.System` match active profile via ADB).

---

### `BUG-002`: `AudioEffectCenter` `Spatializer.setEnabled` Fatal Crash
- **Status**: **FIXED** (in commit `3385f37`, previously regressed in `8740a86`)
- **Coupled With**: `BUG-001`, `BUG-003`
- **Symptom**: Tapping any profile button (or specifically **ORIGINAL**) causes the app to abruptly crash with `FATAL EXCEPTION: main`.
- **Repro Steps**:
  1. Launch `Soniq DSP`.
  2. Tap **ORIGINAL** (or any profile if `setEffectActive("none", ...)` is called).
  3. App closes immediately; logcat prints:
     `java.lang.SecurityException: Access denied, requires: android.permission.MODIFY_DEFAULT_AUDIO_EFFECTS at android.media.Spatializer.setEnabled()`.
- **Root Cause**: Confirmed. Invoking `AudioEffectCenter.getInstance(context).setEffectActive(centerInstance, "none", true)` forces the internal `BaseEffectPresenter` to post to the calling app's main Looper. The presenter invokes `Spatializer.setEnabled()`, which requires platform signature permission `MODIFY_DEFAULT_AUDIO_EFFECTS`. Because it runs on the main thread message loop, standard try/catch blocks cannot intercept it.
- **Fix**: Commit `3385f37` in [`HyperOSHelper.kt`](file:///d:/Mobile%20Projects/Buds2Pro-HyperOS-Optimizer/app/src/main/java/com/audiophile/buds2prooptimizer/util/HyperOSHelper.kt) in `invokeAudioEffectCenter()`: explicitly deactivates `"dolby"` and `"misound"` without ever calling `setEffectActive("none", true)`.
- **Must-Not-Break**: Must never remove the deactivation calls completely, otherwise `BUG-001` returns.
- **Regression Check**: `scripts/device_smoke.sh` Step 1 (Monitors logcat for `MODIFY_DEFAULT_AUDIO_EFFECTS` and `FATAL EXCEPTION`).

---

### `BUG-003`: Phone Speaker Original Profile Reversion on Disconnect
- **Status**: **OPEN**
- **Coupled With**: `BUG-001`, `BUG-002`
- **Symptom**: When Galaxy Buds are disconnected and audio routes to phone speakers, HyperOS sound settings show Dolby Atmos active. When the user taps **ORIGINAL** in Soniq DSP, HyperOS Settings does not show the unenhanced baseline (or reverts to Dolby Atmos).
- **Repro Steps**:
  1. Disconnect Galaxy Buds (put in case or turn off Bluetooth).
  2. Open `Soniq DSP` (telemetry displays `DISCONNECTED // PHONE SPEAKER`).
  3. Tap **ORIGINAL**.
  4. Open HyperOS **Settings > Sound & vibration > Sound effects**.
  5. Check if Dolby Atmos remains enabled for the built-in speaker.
- **Root Cause**: Confirmed. On Xiaomi HyperOS devices, built-in phone speakers do not have a dedicated 3-way profile selector like headphones do; instead, Dolby Atmos is the speaker master switch (`dolby_effect_switch`). Reverting to "Original Sound" on phone speakers requires setting `dolby_effect_switch = 0`, `effect_implementer = "none"`, and `last_select_effect_none = 1` across both `Settings.Global` and `Settings.System`, and releasing any Session 0 priority 10000 locks.
- **Fix**: Commit `3385f37` in [`MainActivity.kt`](file:///d:/Mobile%20Projects/Buds2Pro-HyperOS-Optimizer/app/src/main/java/com/audiophile/buds2prooptimizer/ui/MainActivity.kt) (`setProfile`) and [`HyperOSHelper.kt`](file:///d:/Mobile%20Projects/Buds2Pro-HyperOS-Optimizer/app/src/main/java/com/audiophile/buds2prooptimizer/util/HyperOSHelper.kt).
- **Must-Not-Break**: Harman EQ on Session 0 MUST remain bypassed (`[ BYPASSED ]`) when on phone speaker to protect speaker hardware and avoid battery drain.
- **Regression Check**: `scripts/device_smoke.sh` Step 3 (Asserts `dolby_effect_switch == 0` when in Original mode on speaker).

---

### `BUG-004`: Visualizer Wave Stutter & Animator Repeat on 120Hz Displays
- **Status**: **FIXED** (in commit `3385f37`)
- **Coupled With**: Independent
- **Symptom**: The cyan/gold acoustic wave in the center of the screen appears choppy or micro-stutters every second, especially on the Redmi Turbo 5's 120Hz AMOLED display.
- **Repro Steps**:
  1. Open `Soniq DSP`.
  2. Connect earbuds and tap **[ BYPASSED ]** to engage DSP (`[ ACTIVE ]`).
  3. Watch the fluid wave for 3 to 5 seconds. Look for a periodic hitch every 1 second.
- **Root Cause**: Confirmed. 
  1. The wave math used non-integer harmonic multipliers (`3.8 * PI` and `7.2 * PI`), causing phase collisions at boundary points.
  2. The animation loop was driven by a `ValueAnimator.ofFloat(0f, 1f)` with `duration = 1000` set to `INFINITE`. On repeat, the animator tick dropped frames against the 120Hz display refresh.
- **Fix**: Commit `3385f37` in [`AetherVisualizerView.kt`](file:///d:/Mobile%20Projects/Buds2Pro-HyperOS-Optimizer/app/src/main/java/com/audiophile/buds2prooptimizer/ui/AetherVisualizerView.kt):
  - Swapped `ValueAnimator` for hardware VSYNC-aligned **`postInvalidateOnAnimation()`**.
  - Implemented 96-segment **Catmull-Rom to Cubic Bézier spline** rendering with edge envelope tapering.
- **Must-Not-Break**: When DSP is bypassed, the wave must smoothly decay to a flat line and stop the `postInvalidateOnAnimation()` loop to consume 0% GPU/CPU.
- **Regression Check**: Automated Catmull-Rom math unit test (`SplineMathTest`) + Manual Check 4 in `CHECKLIST.md`.

---

### `BUG-005`: Gradle Wrapper Download Timeout & AGP Incompatibility
- **Status**: **FIXED** (in commit `3d99b40`, re-verified in `f792f61`)
- **Coupled With**: Independent
- **Symptom**: Android Studio or terminal `./gradlew assembleDebug` fails with `java.net.SocketTimeoutException: Connect timed out` or `Cannot mutate dependencies of configuration after resolution`.
- **Repro Steps**:
  1. Change `gradle-wrapper.properties` to `gradle-8.13-bin.zip` or `gradle-9.7.1-bin.zip`.
  2. Run `./gradlew assembleDebug`.
  3. Gradle fails trying to download missing distributions or throws configuration cache errors.
- **Root Cause**: Confirmed. Android Gradle Plugin (AGP) version `8.2.2` in `build.gradle.kts` requires Gradle 8.2 or 8.5. Upgrading the wrapper to Gradle 9.7 or downloading 8.13 via Android Studio assistant caused network timeouts and API breakage.
- **Fix**: Commit `3d99b40` in [`gradle/wrapper/gradle-wrapper.properties`](file:///d:/Mobile%20Projects/Buds2Pro-HyperOS-Optimizer/gradle/wrapper/gradle-wrapper.properties): locked distribution to cached `gradle-8.2.1-bin.zip`.
- **Must-Not-Break**: Do not modify `distributionUrl` in `gradle-wrapper.properties` without verifying against local cache and AGP compatibility matrix.
- **Regression Check**: `scripts/verify.sh` Step 1 (`./gradlew assembleDebug --no-daemon --dry-run`).

---

### `BUG-006`: Live Bluetooth Audio Format Telemetry Inaccuracy
- **Status**: **FIXED** (in commit `e71c255`)
- **Coupled With**: Independent
- **Symptom**: When playing high-resolution audio (e.g. 24-bit 96kHz or 48kHz), the telemetry bar displays inaccurate base codec defaults (e.g. static 16-bit 44.1kHz) or does not show `PLAYING: [FORMAT]`.
- **Repro Steps**:
  1. Connect Galaxy Buds 2 Pro.
  2. Play a 24-bit / 96kHz or 48kHz track in Apple Music, Spotify, or Tidal.
  3. Check the telemetry line above the visualizer.
- **Root Cause**: Confirmed. Android Bluetooth stack returns static base A2DP profile handshake (`BluetoothCodecConfig`), masking active media stream sample rates.
- **Fix**: Commit `e71c255` in [`MainActivity.kt`](file:///d:/Mobile%20Projects/Buds2Pro-HyperOS-Optimizer/app/src/main/java/com/audiophile/buds2prooptimizer/ui/MainActivity.kt) in `evaluatePlaybackConfigs()` and `updateCodecTelemetry()`: actively queries `AudioPlaybackConfiguration` stream attributes and maps `BluetoothCodecConfig.sampleRate` and `bitsPerSample`.
- **Must-Not-Break**: When no media is playing, telemetry must show `CODEC: [CODEC] // [DEVICE] (READY)`. When disconnected, must show `DISCONNECTED // PHONE SPEAKER`.
- **Regression Check**: Unit test `TelemetryLogicTest` checking sample rate formatting and bit-depth mapping.
