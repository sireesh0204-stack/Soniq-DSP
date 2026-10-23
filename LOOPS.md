# Regression & Loop Analysis (`LOOPS.md`)

This document traces the historical commits in the repository that attempted bug fixes and highlights where later commits re-modified or reverted the same code paths (the "loop signals").

---

## 1. Commit History Trace (Chronological Order)

| Commit Hash | Commit Message / Intent | Key Files Modified | Loop Signal / Subsequent Regression |
| :--- | :--- | :--- | :--- |
| **`248e8fa`** | *Initial commit of Soniq DSP for HyperOS* | All core files | Base baseline. |
| **`d5112b5`** | *Fix visualizer harmonic glitch and update ASZSD V2 monogram watermark* | `AetherVisualizerView.kt`, `ic_aszsd_monogram.xml` | Modified wave math, but still used non-integral frequencies (`3.8 * PI` and `7.2 * PI`), leading to visualizer stutter later (`BUG-004`). |
| **`3d99b40`** | *Restore stable Gradle 8.2.1 and AGP 8.2.2 configuration* | `gradle-wrapper.properties`, `build.gradle.kts` | Reverted broken Gradle 8.13/9.7.1 upgrade attempt that was causing build timeouts (`BUG-005`). |
| **`f792f61`** | *feat(ui): update ASZSD monogram logo with gradient chassis and fix gradle wrapper* | `ic_aszsd_monogram.xml`, `gradle-wrapper.properties` | Re-asserted gradle wrapper and updated vector XML. |
| **`99f3d4b`** | *feat(ui): update ASZSD monogram to continuous wireframe trace on transparent background* | `ic_aszsd_monogram.xml` | Single continuous wireframe trace. |
| **`e71c255`** | *fix(hyperos): integrate AudioEffectCenter for Dolby, fix disconnected profile switching, smooth visualizer harmonics, and liquid glass logo shimmer* | `HyperOSHelper.kt`, `MainActivity.kt`, `AetherVisualizerView.kt` | **LOOP SIGNAL 1**: First introduced direct in-process call to `invokeAudioEffectCenter()` in `setHyperOsEffect()`. This fixed Dolby sync (`BUG-001`), but planted the seed for `BUG-002` (the Spatializer crash). |
| **`8740a86`** | *fix(hyperos): sync original sound baseline in AudioEffectCenter, update stealth grey logo, and gitignore cleanup* | `HyperOSHelper.kt`, `MainActivity.kt`, `ic_aszsd_monogram.xml` | **LOOP SIGNAL 2**: Added `setEffectActive(centerInstance, "none", true)` in `invokeAudioEffectCenter()`. **IMMEDIATELY CAUSED FATAL CRASH**: calling `"none"` triggered `Spatializer.setEnabled()` and crashed on every click (`BUG-002`)! |
| **`696cca0`** | *fix(hyperos): prevent BaseEffectPresenter crash by using Settings and Intent broadcasts instead of direct reflection* | `HyperOSHelper.kt` | **LOOP SIGNAL 3 (THE PING-PONG)**: To stop the fatal crash, `invokeAudioEffectCenter` was completely removed from `setHyperOsEffect()`. The crash was fixed, but removing it immediately broke HyperOS Settings sync (`BUG-001` and `BUG-003` returned)! |
| **`3385f37`** | *fix(audio): safe AudioEffectCenter deactivation for original profile and 120Hz Catmull-Rom spline visualizer* | `HyperOSHelper.kt`, `AetherVisualizerView.kt` | **LOOP RESOLUTION ATTEMPT**: Restored `invokeAudioEffectCenter`, but handled "none" by deactivating "dolby" (`false`) and "misound" (`false`) without passing the invalid `"none"` string. Re-asserted settings tables. Replaced visualizer with 120Hz Catmull-Rom spline. |
| **`82bea94`** | *docs: implement regression harness, BUGS.md specification, and coupled bug files* | `BUGS.md`, `docs/bugs/*` | Initial harness specification. |

---

## 2. Identified Loop Patterns & Root Mechanisms

### Loop Pattern 1: The "Sync vs Crash" Ping-Pong
```
[e71c255] Call AudioEffectCenter to sync Dolby/Xiaomi
   │
   ▼
[8740a86] Try to sync "Original" by calling setEffectActive("none", true)
   │ (Causes Fatal SecurityException in Spatializer.setEnabled)
   ▼
[696cca0] Remove AudioEffectCenter completely to stop crash
   │ (Crashes stop, but HyperOS Settings UI stops syncing and defaults to Dolby)
   ▼
[3385f37] Call AudioEffectCenter safely (deactivate dolby/misound without calling "none")
```

### Loop Pattern 2: Disconnected Earbuds vs Speaker Sound Routing
- **Earbuds Connected**: All 3 profiles (`ORIGINAL`, `DOLBY`, `XIAOMI`) are active in HyperOS.
- **Earbuds Disconnected**: HyperOS switches the sound effects page from a 3-way selector to a single Dolby Atmos toggle. 
- Because previous fixes only tested with earbuds connected, switching to "Original" while disconnected caused the speaker to remain locked to Dolby Atmos.

### Loop Pattern 3: Visualizer Jitter vs 120Hz Displays
- **Attempt 1 (`d5112b5`)**: Secondary wave using `sin(x * 7.2 * PI)`. Jittered because of non-integer harmonic boundaries.
- **Attempt 2 (`e71c255`)**: Integer harmonics ($\sin(2\pi \cdot x)$ and $\sin(4\pi \cdot x)$) but inside a repeating `ValueAnimator(0f, 1f, 1000ms)`. Every 1000ms, the animator restart created frame stutter on 120Hz AMOLED panels.
- **Attempt 3 (`3385f37`)**: Replaced `ValueAnimator` with `postInvalidateOnAnimation()` VSYNC loop and Catmull-Rom cubic Bézier interpolation.
