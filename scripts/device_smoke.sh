#!/usr/bin/env bash
# Soniq DSP Device Smoke Test Harness (POSIX bash)
# Guards: BUG-001, BUG-002, BUG-003

set -euo pipefail

ADB="adb"
if ! command -v adb &> /dev/null; then
    if [[ -f "$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" ]]; then
        ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
    else
        echo "[FAIL] adb not found in PATH." >&2
        exit 1
    fi
fi

echo "==> Using ADB: $ADB"

# 1. Device check
DEVICES=$("$ADB" devices | grep -w "device" || true)
if [[ -z "$DEVICES" ]]; then
    echo "[FAIL] No authorized Android device connected via ADB." >&2
    exit 1
fi
echo "[PASS] Device connected: $DEVICES"

# 2. Package install check
PKG=$("$ADB" shell pm list packages | grep "com.audiophile.buds2prooptimizer" || true)
if [[ -z "$PKG" ]]; then
    echo "[WARN] App not installed. Installing debug APK..."
    "$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
fi

# Ensure permissions
"$ADB" shell pm grant com.audiophile.buds2prooptimizer android.permission.WRITE_SECURE_SETTINGS 2>/dev/null || true

# --- TEST BUG-002: Spatializer.setEnabled / AudioEffectCenter crash ---
echo -e "\n--- Testing BUG-002: AudioEffectCenter Crash Guard ---"
"$ADB" logcat -c
sleep 0.3
"$ADB" shell am start -n com.audiophile.buds2prooptimizer/.ui.MainActivity --es PROFILE ORIGINAL_SOUND >/dev/null 2>&1
sleep 1.2

CRASH_CHECK=$("$ADB" logcat -d -s AndroidRuntime:E | grep -E "MODIFY_DEFAULT_AUDIO_EFFECTS|FATAL EXCEPTION: main.*buds2prooptimizer" || true)
if [[ -n "$CRASH_CHECK" ]]; then
    echo "[FAIL] BUG-002: App crashed on profile switch: $CRASH_CHECK"
    BUG_002_RESULT="FAIL"
else
    echo "[PASS] BUG-002: AudioEffectCenter safely switched to ORIGINAL without SecurityException"
    BUG_002_RESULT="PASS"
fi

# --- TEST BUG-001: HyperOS Settings Synchronization ---
echo -e "\n--- Testing BUG-001: HyperOS Settings Synchronization ---"
"$ADB" shell am start -n com.audiophile.buds2prooptimizer/.ui.MainActivity --es PROFILE DOLBY_ATMOS >/dev/null 2>&1
sleep 0.8
DOLBY_IMPL=$("$ADB" shell settings get global effect_implementer | tr -d '\r\n')
DOLBY_SWITCH=$("$ADB" shell settings get global dolby_effect_switch | tr -d '\r\n')

"$ADB" shell am start -n com.audiophile.buds2prooptimizer/.ui.MainActivity --es PROFILE XIAOMI_SOUND >/dev/null 2>&1
sleep 0.8
XIAOMI_IMPL=$("$ADB" shell settings get global effect_implementer | tr -d '\r\n')
XIAOMI_STATE=$("$ADB" shell settings get global misound_state | tr -d '\r\n')

if [[ "$DOLBY_IMPL" == "dolby" && "$DOLBY_SWITCH" == "1" && "$XIAOMI_IMPL" == "misound" && "$XIAOMI_STATE" == "1" ]]; then
    echo "[PASS] BUG-001: HyperOS Settings table correctly synchronized for DOLBY and XIAOMI"
    BUG_001_RESULT="PASS"
else
    echo "[FAIL] BUG-001: Settings desync. Dolby: impl=$DOLBY_IMPL sw=$DOLBY_SWITCH | Xiaomi: impl=$XIAOMI_IMPL st=$XIAOMI_STATE"
    BUG_001_RESULT="FAIL"
fi

# --- TEST BUG-003: Phone Speaker Original Profile Baseline ---
echo -e "\n--- Testing BUG-003: Speaker Original Baseline ---"
"$ADB" shell am start -n com.audiophile.buds2prooptimizer/.ui.MainActivity --es PROFILE ORIGINAL_SOUND >/dev/null 2>&1
sleep 0.8
ORIG_IMPL=$("$ADB" shell settings get global effect_implementer | tr -d '\r\n')
ORIG_SWITCH=$("$ADB" shell settings get global dolby_effect_switch | tr -d '\r\n')

if [[ "$ORIG_IMPL" == "none" && "$ORIG_SWITCH" == "0" ]]; then
    echo "[PASS] BUG-003: Speaker baseline intact (effect_implementer=none, dolby_effect_switch=0)"
    BUG_003_RESULT="PASS"
else
    echo "[FAIL] BUG-003: Speaker baseline mismatch (impl=$ORIG_IMPL, dolby_switch=$ORIG_SWITCH)"
    BUG_003_RESULT="FAIL"
fi

echo -e "\n=========================================="
echo "DEVICE SMOKE TEST SUMMARY"
echo "=========================================="
echo "  [BUG-001] $BUG_001_RESULT"
echo "  [BUG-002] $BUG_002_RESULT"
echo "  [BUG-003] $BUG_003_RESULT"

if [[ "$BUG_001_RESULT" == "FAIL" || "$BUG_002_RESULT" == "FAIL" || "$BUG_003_RESULT" == "FAIL" ]]; then
    exit 1
fi
exit 0

