# Bug 001: HyperOS Settings Profile Desynchronization

## 1. Metadata
- **Bug ID**: `BUG_001`
- **Severity**: High
- **Components**: [`HyperOSHelper.kt`](file:///d:/Mobile%20Projects/Buds2Pro-HyperOS-Optimizer/app/src/main/java/com/audiophile/buds2prooptimizer/util/HyperOSHelper.kt), [`MainActivity.kt`](file:///d:/Mobile%20Projects/Buds2Pro-HyperOS-Optimizer/app/src/main/java/com/audiophile/buds2prooptimizer/ui/MainActivity.kt)
- **Coupled With**: `BUG_002` (In-process reflection crash), `BUG_003` (Speaker baseline)

---

## 2. Symptoms
- Tapping **ORIGINAL**, **DOLBY**, or **XIAOMI** in the Soniq DSP app updates internal app buttons, but does **not** reflect in Xiaomi HyperOS system settings (**Settings > Sound & vibration > Sound effects**).
- When returning to the system settings page, the profile selector remains stuck on the previous mode (typically defaulting to Dolby Atmos on phone speakers).

---

## 3. Reproduction Steps
1. Launch `Soniq DSP`.
2. Tap **DOLBY** or **XIAOMI**.
3. Open HyperOS **Settings > Sound & vibration > Sound effects**.
4. Observe whether the active radio button / slider matches the app's selected preset.

---

## 4. Root Cause & Coupled Architecture
- **Dual-State Desync**: Xiaomi HyperOS (`com.miui.misound`) maintains internal state in two places:
  1. `Settings.Global` & `Settings.System` keys (`effect_implementer`, `dolby_effect_switch`, `misound_state`, `last_select_effect_none`).
  2. Its own in-memory service (`HeadsetSettingsFW` / `AudioEffectCenter`) driven by broadcasts.
- If only settings keys are written without broadcasting `ACTION_SYSTEM_UI_DOLBY_EFFECT_SWITCH` and `ACTION_AUDIO_EFFECT_REFRESH`, the UI never re-evaluates.
- **The Coupling Trap (Bug 001 vs Bug 002)**: When we attempted to directly drive `AudioEffectCenter` via reflection to force the UI to sync, it triggered `BUG_002` (fatal crash on `Spatializer.setEnabled()`).

---

## 5. Invariants (What Must NEVER Break)
- [ ] Switching presets in the app MUST dispatch both `Settings.Global` and `Settings.System` writes.
- [ ] Broadcasts to `com.miui.misound` MUST be wrapped in `runCatching` so non-system UIDs don't crash.
- [ ] Setting "ORIGINAL" MUST set `effect_implementer = "none"`, `last_select_effect_none = 1`, `dolby_effect_switch = 0`, and `misound_state = 0`.
- [ ] Phone speaker settings MUST NOT be overridden by high-priority Session 0 locks that prevent the user from using system volume/sound controls.

---

## 6. Regression Test Script
```bash
# Verify system keys match active profile
adb shell settings get global effect_implementer
adb shell settings get system effect_implementer
adb shell settings get global dolby_effect_switch
adb shell settings get global misound_state
```
