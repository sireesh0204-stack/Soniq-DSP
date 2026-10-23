# Soniq DSP Manual Regression Checklist (`CHECKLIST.md`)

This checklist contains **14 concise, sequential manual verification steps** to validate audio DSP behavior, HyperOS framework integration, and visual fidelity on hardware before releasing or merging.

---

## Pre-Flight Setup
- **Target Device**: Xiaomi Redmi Turbo 5 (HyperOS / Android 16 API 36)
- **Audio Gear**: Samsung Galaxy Buds 2 Pro (or any Bluetooth A2DP / USB-C DAC / 3.5mm IEM)
- **Harness Scripts**: `scripts/verify.ps1` (Windows) or `scripts/verify.sh` (POSIX)

---

## 14-Step Regression Protocol

### 1. Build & Installation Verification (`BUG-005`)
- Run `powershell -ExecutionPolicy Bypass -File scripts/verify.ps1`.
- Verify the build succeeds using the local cached Gradle wrapper without socket timeouts.

### 2. App Launch & Initial State
- Launch **Soniq DSP** from the app launcher.
- Verify the deep-void dark UI loads smoothly with the Harman Golden Ears logo and status bar styling.

### 3. Master DSP Bypass Toggle & Haptic Feedback
- Tap **[ BYPASSED ]**.
- Verify crisp haptic tick feedback occurs, the button changes to **[ ACTIVE ]** in cyan (`#00F0FF`), and the DSP foreground notification appears.

### 4. 120Hz Visualizer Fluidity & Decay Check (`BUG-004`)
- Observe the cyan/gold wave while active. Verify fluid 120 FPS motion without periodic 1-second hitching or frame drops.
- Tap **[ ACTIVE ]** to toggle back to **[ BYPASSED ]**.
- Verify the wave smoothly decays to a flat horizontal line within ~300ms and halts rendering loop.

### 5. Profile Switching to DOLBY (`BUG-001`)
- Tap the **DOLBY** card.
- Verify tactile vibration triggers, card highlights with Dolby gold/cyan accent, and telemetry indicates Dolby profile.

### 6. Profile Switching to XIAOMI (`BUG-001`)
- Tap the **XIAOMI** card.
- Verify tactile feedback triggers, card highlights with Xiaomi accent, and telemetry indicates MiSound profile.

### 7. Profile Switching to ORIGINAL (`BUG-001`, `BUG-002`)
- Tap the **ORIGINAL** card.
- Verify tactile feedback triggers, card highlights with Original Bit-Perfect badge.

### 8. HyperOS System Settings Desync Check (`BUG-001`)
- Pull down notification shade, open **Settings > Sound & vibration > Sound effects**.
- Check that the active profile shown in Xiaomi Settings matches the one selected in Soniq DSP (e.g. Dolby Atmos or Xiaomi Sound).

### 9. Crash Immunity Check (`BUG-002`)
- Rapidly tap between **DOLBY**, **XIAOMI**, and **ORIGINAL** 5 times.
- Verify the app does NOT crash, and check logcat to ensure zero `MODIFY_DEFAULT_AUDIO_EFFECTS` exceptions occurred.

### 10. Earbud Disconnect / Speaker Routing (`BUG-003`)
- Place Galaxy Buds back in their charging case (or turn off Bluetooth).
- Check the telemetry status bar above the visualizer.
- Verify it displays `DISCONNECTED // PHONE SPEAKER` in subdued gray (`#757C8A`).

### 11. Phone Speaker Baseline Verification (`BUG-003`)
- With earbuds disconnected, tap **ORIGINAL** in Soniq DSP.
- Open HyperOS **Settings > Sound & vibration > Sound effects**.
- Verify the speaker profile slider shows Dolby Atmos disabled (`dolby_effect_switch = 0`) and unenhanced bit-perfect sound is active.

### 12. Speaker DSP Protection Invariant (`BUG-003`)
- While disconnected on phone speaker, observe the Master Toggle.
- Verify it remains locked to **[ BYPASSED ]** (or tapping it does not engage 5-band Session 0 EQ into the phone speaker).

### 13. Audiophile Telemetry & Codec Formatter Check (`BUG-006`)
- Reconnect Galaxy Buds 2 Pro and start playback of a 24-bit / 48kHz or 96kHz track.
- Verify the telemetry badge reads `PLAYING: 24-BIT / 48kHz // SSC` (or `96kHz`) rather than static 16-bit 44.1kHz.

### 14. Quick Settings Tiles Synchronization
- Swipe down twice to reveal the Quick Settings panel.
- Tap the **Soniq Profile** tile to cycle profiles.
- Tap the **Soniq DSP** tile to toggle Harman EQ active/bypassed.
- Return to Soniq DSP and verify the UI state synchronized instantly.

---

## Pass/Fail Gate
- If **all 14 steps pass**: Release/commit is certified clean.
- If **any step fails**: Log bug reproduction in `BUGS.md`, identify root cause, and run `verify.ps1` before committing.
