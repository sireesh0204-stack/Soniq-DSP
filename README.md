# Soniq DSP // Studio HyperOS Audio Engine 🎧
### Bit-Perfect Audio Pipeline & Dynamic Acoustic Visualizer for Galaxy Buds 2 Pro & HyperOS Devices

---

**Soniq DSP** is an advanced Android audiophile optimization suite engineered to unlock the full acoustic potential of Samsung Galaxy Buds 2 Pro when paired with Xiaomi / Redmi / POCO devices running HyperOS. It seamlessly bridges system-level spatial audio frameworks (Dolby Atmos, Xiaomi MiSound / Sound Effects, and Harman Golden Ear targets) with an organic, GPU-accelerated acoustic visualizer.

---

## ⚡ Core Features

* **Audiophile Headroom & Equalizer Target**:
  - Restores the bit-perfect Harman 2019v2 target curve.
  - Implements `-2.5 dB` digital preamp headroom to completely eliminate inter-sample clipping on Bluetooth A2DP codecs.
* **Instant System Sound Effect Profile Synchronization**:
  - `ORIGINAL`: Bit-perfect flat monitoring profile.
  - `DOLBY`: Activates HyperOS Dolby Atmos music processing.
  - `XIAOMI`: Engages proprietary MiSound acoustic engine.
* **Live Bluetooth Codec & Sample Telemetry**:
  - Real-time detection of active Bluetooth A2DP & LE Audio codecs (AAC, LDAC, LHDC, SBC) and hardware sample rates.
* **Organic Bézier Fluid Visualizer (`AetherVisualizer`)**:
  - Hardware-accelerated dynamic sine/Bézier wave rendering on Android canvas.
  - Smooth multi-harmonic amplitude modulation reacting to touch and playback states.
* **Bespoke Hardware Watermark**:
  - Features the custom **ASZSD** family monogram emblem (Anasuri, Sireesh, Zoro, Sweety, Dhanvika).

---

## 🛠 Tech Stack & Architecture

* **Language**: Kotlin 2.0+ / Android Jetpack
* **Minimum SDK**: API 34 (Android 14) / Tested on Android 16 (API 36)
* **Architecture**: Clean MVVM + Service Bridge
* **Key Frameworks**:
  - Android AudioManager & Bluetooth A2DP hidden APIs via reflection/IPC.
  - Custom Android View with continuous delta-time physics animation.
  - Material Components & ConstraintLayout.

---

## 📄 License

Licensed under the [Apache 2.0 License](LICENSE).
