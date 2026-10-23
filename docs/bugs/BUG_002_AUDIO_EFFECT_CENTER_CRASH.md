# Bug 002: In-Process AudioEffectCenter Spatializer Crash

## 1. Metadata
- **Bug ID**: `BUG_002`
- **Severity**: Critical (Fatal App Crash)
- **Components**: [`HyperOSHelper.kt`](file:///d:/Mobile%20Projects/Buds2Pro-HyperOS-Optimizer/app/src/main/java/com/audiophile/buds2prooptimizer/util/HyperOSHelper.kt)
- **Coupled With**: `BUG_001` (Settings sync), `BUG_003` (Speaker baseline)

---

## 2. Symptoms
- The app immediately crashes with a `FATAL EXCEPTION: main` when clicking any profile button (Original, Dolby, or Xiaomi).
- Logcat reports:
  ```
  java.lang.SecurityException: Access denied, requires: android.permission.MODIFY_DEFAULT_AUDIO_EFFECTS
      at android.media.Spatializer.setEnabled(Spatializer.java:394)
      at android.media.audiofx.presenter.BaseEffectPresenter.setSpatialAudioActiveLocked(...)
      at android.media.audiofx.presenter.BaseEffectPresenter$H.handleMessage(BaseEffectPresenter.java:647)
  ```

---

## 3. Reproduction Steps
1. In `HyperOSHelper.kt`, invoke `AudioEffectCenter.setEffectActive(instance, "none", true)` or pass unregistered effect identifiers.
2. Tap **ORIGINAL** inside `Soniq DSP`.
3. Process crashes on the main Looper.

---

## 4. Root Cause & Coupled Architecture
- `android.media.audiofx.AudioEffectCenter` is an internal Xiaomi framework singleton loaded from `/system_ext/framework/miui-framework.jar`.
- When an untrusted app (`com.audiophile.buds2prooptimizer`) calls `getInstance(context)`, `AudioEffectCenter` instantiates `BaseEffectPresenter` on the caller's main thread Looper.
- When `setEffectActive()` is called with an invalid effect name like `"none"` or when spatial audio transitions, `BaseEffectPresenter$H` receives a message and calls `Spatializer.setEnabled()`.
- `Spatializer.setEnabled()` calls into `AudioService`, which strictly enforces `android.permission.MODIFY_DEFAULT_AUDIO_EFFECTS` (a platform signature-only permission).
- Because this runs asynchronously on the main thread message queue, standard `try/catch` or `runCatching` blocks surrounding reflection CANNOT catch it.

---

## 5. Invariants (What Must NEVER Break)
- [ ] `setEffectActive()` MUST NEVER be called with `"none"`. In `AudioEffectCenter`, only `"dolby"` and `"misound"` are registered. To achieve "none", explicitly deactivate both (`setEffectActive("dolby", false)` and `setEffectActive("misound", false)`).
- [ ] All reflection invocations on `AudioEffectCenter` MUST be guarded and fail-safe.
- [ ] Any crash in `HyperOSHelper` MUST NOT prevent `updateUi()` in `MainActivity.kt` from rendering the button state.

---

## 6. Regression Test Script
```bash
# Monitor logcat for Spatializer permission errors on tap
adb logcat -c
# Tap buttons in app, then check:
adb logcat -d -s AndroidRuntime:E | grep "MODIFY_DEFAULT_AUDIO_EFFECTS"
# (Must return 0 lines / empty)
```
