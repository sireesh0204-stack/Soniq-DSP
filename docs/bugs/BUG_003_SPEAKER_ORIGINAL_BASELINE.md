# Bug 003: Phone Speaker Original Profile Reversion & Locking

## 1. Metadata
- **Bug ID**: `BUG_003`
- **Severity**: High
- **Components**: [`HyperOSHelper.kt`](file:///d:/Mobile%20Projects/Buds2Pro-HyperOS-Optimizer/app/src/main/java/com/audiophile/buds2prooptimizer/util/HyperOSHelper.kt), [`MainActivity.kt`](file:///d:/Mobile%20Projects/Buds2Pro-HyperOS-Optimizer/app/src/main/java/com/audiophile/buds2prooptimizer/ui/MainActivity.kt), [`AudioDspService.kt`](file:///d:/Mobile%20Projects/Buds2Pro-HyperOS-Optimizer/app/src/main/java/com/audiophile/buds2prooptimizer/dsp/AudioDspService.kt)
- **Coupled With**: `BUG_001` (Settings sync), `BUG_002` (Crash)

---

## 2. Symptoms
- When Galaxy Buds 2 Pro are disconnected (audio playing on phone speaker), HyperOS defaults its speaker sound effects to Dolby Atmos.
- When tapping **ORIGINAL** in `Soniq DSP`, the phone speaker continues applying Dolby sound processing, and HyperOS Settings does not display the un-enhanced baseline.
- In some scenarios, profile switching becomes locked or unresponsive because `isHeadsetConnected()` forces DSP disablement or fails to dispatch the speaker effect transition.

---

## 3. Reproduction Steps
1. Disconnect Galaxy Buds (Bluetooth off or buds in case).
2. Open `Soniq DSP`.
3. Tap **ORIGINAL**.
4. Open HyperOS **Settings > Sound & vibration > Sound effects**.
5. Observe that speaker profile remains stuck on Dolby Atmos.

---

## 4. Root Cause & Coupled Architecture
- **Xiaomi HyperOS Speaker Asymmetry**:
  - For **headphones/earbuds**, HyperOS provides a 3-way segmented switch: `[Xiaomi Sound, Dolby Atmos, Original sound]`.
  - For **phone speakers**, HyperOS treats Dolby Atmos as the master toggle (`dolby_effect_switch`). There is often NO dedicated "Xiaomi Sound" for built-in speakers. To get "Original Sound" on phone speakers, `dolby_effect_switch` MUST be set to `0`, and `effect_implementer` set to `"none"`.
- **App Service Coupling**:
  - In `MainActivity.kt`: `setProfile()` checks `isHeadsetConnected()`. If disconnected, it forces `isDspActive = false` (which is correct to protect battery and phone speakers from Harman in-ear EQ), BUT it must still forward the system sound effect state (`"none"`) to `HyperOSHelper`.
  - In `HyperOSHelper.kt`: Priority 10000 Session 0 AudioEffect takeover was retaining disabled effect handles (`retainedEffects.add(fx)`), which prevented HyperOS AudioFlinger from clearing speaker effects.

---

## 5. Invariants (What Must NEVER Break)
- [ ] When disconnected, Harman DSP on Session 0 MUST remain bypassed (`[ BYPASSED ]` flat visualizer) to protect phone speakers.
- [ ] When disconnected, profile button taps in the app MUST still switch `currentProfile` and update the active button highlight.
- [ ] When selecting ORIGINAL on speaker, `dolby_effect_switch = 0` must be written to both `Settings.Global` and `Settings.System`.

---

## 6. Regression Test Script
```bash
# Verify speaker Dolby switch is disabled in Original mode
adb shell settings get global dolby_effect_switch
# Expected: 0
adb shell settings get system dolby_effect_switch
# Expected: 0
```
