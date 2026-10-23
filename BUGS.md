# Master Bug Index & Regression Harness Specification

This document tracks all known coupled bugs, their shared root causes, reproduction steps, and non-negotiable invariants. Every prompt sent to any model and every commit must verify against this index.

---

## 1. Active Bug Registry

| Bug ID | Title | Primary Components | Coupled With | Status |
| :--- | :--- | :--- | :--- | :---: |
| **`BUG_001`** | [HyperOS Settings Profile Desync](./docs/bugs/BUG_001_HYPEROS_SETTINGS_SYNC.md) | `HyperOSHelper.kt`, `MainActivity.kt` | `BUG_002`, `BUG_003` | 🟡 Active |
| **`BUG_002`** | [AudioEffectCenter Spatializer Crash](./docs/bugs/BUG_002_AUDIO_EFFECT_CENTER_CRASH.md) | `HyperOSHelper.kt` | `BUG_001`, `BUG_003` | 🟢 Resolved |
| **`BUG_003`** | [Speaker Original Profile Baseline Sync](./docs/bugs/BUG_003_SPEAKER_ORIGINAL_BASELINE.md) | `HyperOSHelper.kt`, `MainActivity.kt` | `BUG_001`, `BUG_002` | 🟡 Active |
| **`BUG_004`** | [120Hz Visualizer Wave Fluidity](./docs/bugs/BUG_004_VISUALIZER_HARMONIC_STUTTER.md) | `AetherVisualizerView.kt` | Independent | 🟢 Resolved |

---

## 2. The Coupled State Machine (The Regression Loop)

```
                       ┌───────────────────────────────────────────┐
                       │           MainActivity.kt                 │
                       │ - isHeadsetConnected()                    │
                       │ - setProfile() / loadState()              │
                       │ - SharedPreferences "ACTIVE_PROFILE"      │
                       └─────────────────────┬─────────────────────┘
                                             │
                        Calls HyperOSHelper  │ Calls AudioDspService
                                             ▼
     ┌───────────────────────────────────────┴───────────────────────────────────────┐
     │                                                                               │
     ▼                                                                               ▼
┌──────────────────────────────────────────┐                   ┌──────────────────────────────────────────┐
│             HyperOSHelper.kt             │                   │           AudioDspService.kt             │
│ - Settings.Global (effect_implementer)   │                   │ - Session 0 Equalizer (Harman target)    │
│ - Settings.System (dolby_effect_switch)  │                   │ - Bluetooth ACL Disconnect Receiver      │
│ - AudioEffectCenter (miui-framework)     │                   │ - Foreground Service Lifecycle           │
│ - Session 0 Retained AudioEffects (10000)│                   └──────────────────────────────────────────┘
│ - Broadcasts to com.miui.misound         │
└──────────────────────────────────────────┘
```

### The Loop Trap:
1. **Fixing Bug 001** (forcing UI settings sync) led to invoking `AudioEffectCenter.setEffectActive()`.
2. **Invoking `AudioEffectCenter`** caused `BUG_002` (fatal `SecurityException` at `Spatializer.setEnabled()` on main Looper).
3. **Fixing Bug 002** (disabling `AudioEffectCenter` completely) caused `BUG_001` and `BUG_003` to recur (settings stopped syncing, stuck on Dolby on speakers).
4. **Conclusion**: Single-function snippet updates cannot solve this because the state machine spans `Settings.Global`, `Settings.System`, `AudioEffectCenter` reflection, and Session 0 AudioFlinger priority locks simultaneously.

---

## 3. Invariants Matrix (What Must Never Break)

| Invariant Rule | Applies To | Verification Check |
| :--- | :--- | :--- |
| **INV-1: No Spatializer Crash** | `HyperOSHelper.kt` | Zero `MODIFY_DEFAULT_AUDIO_EFFECTS` in `adb logcat -s AndroidRuntime:E`. |
| **INV-2: Inactive DSP on Speaker** | `MainActivity.kt` | When `isHeadsetConnected() == false`, master button shows `[ BYPASSED ]` and wave is flat. |
| **INV-3: Settings Tables Coherence** | `HyperOSHelper.kt` | `Settings.Global` and `Settings.System` must agree on `effect_implementer` and `dolby_effect_switch`. |
| **INV-4: UI Responsiveness** | `MainActivity.kt` | Button clicks must update the UI highlight immediately, even if a system sound setting write fails. |
| **INV-5: 120Hz Fluid Rendering** | `AetherVisualizerView.kt` | Uses VSYNC `postInvalidateOnAnimation()`, zero `ValueAnimator` repeat jumps. |

---

## 4. Mandatory Regression Harness (Run Before Every Commit)

```bash
# Step 1: Check ADB Device
adb devices

# Step 2: Build and Install Clean
./gradlew.bat assembleDebug --no-configuration-cache
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Step 3: Clear and Monitor Logcat
adb logcat -c

# Step 4: Run Profile Switch Tests via ADB
# Test ORIGINAL
adb shell am start -n com.audiophile.buds2prooptimizer/.ui.MainActivity --es PROFILE ORIGINAL_SOUND
adb shell settings get global effect_implementer  # Expect: none
adb shell settings get global dolby_effect_switch  # Expect: 0

# Test DOLBY
adb shell am start -n com.audiophile.buds2prooptimizer/.ui.MainActivity --es PROFILE DOLBY_ATMOS
adb shell settings get global effect_implementer  # Expect: dolby
adb shell settings get global dolby_effect_switch  # Expect: 1

# Test XIAOMI
adb shell am start -n com.audiophile.buds2prooptimizer/.ui.MainActivity --es PROFILE XIAOMI_SOUND
adb shell settings get global effect_implementer  # Expect: misound

# Step 5: Verify Zero Crash Exceptions
adb logcat -d -s AndroidRuntime:E
```
