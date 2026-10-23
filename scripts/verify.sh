#!/usr/bin/env bash
# Master Verification Harness for Soniq DSP (POSIX bash)
# Runs: Gradle Debug Build, Pure JVM Unit Tests, and Device Smoke Test
# Asserts: BUG-001 through BUG-006

set -uo pipefail

echo "============================================================"
echo "  SONIQ DSP MASTER VERIFICATION HARNESS (PHASE 3)          "
echo "============================================================"

BUG_001_RES="PENDING"
BUG_002_RES="PENDING"
BUG_003_RES="PENDING"
BUG_004_RES="PENDING"
BUG_005_RES="PENDING"
BUG_006_RES="PENDING"

# --- STEP 1: Gradle Build (Guards BUG-005) ---
echo -e "\n>>> [STEP 1/3] Running Gradle assembleDebug (Guards BUG-005)..."
if ./gradlew assembleDebug --no-daemon; then
    echo "[PASS] Gradle assembleDebug completed successfully."
    BUG_005_RES="PASS"
else
    echo "[FAIL] Gradle build failed."
    BUG_005_RES="FAIL"
fi

# --- STEP 2: JVM Unit Tests (Guards BUG-004, BUG-006, State Logic) ---
echo -e "\n>>> [STEP 2/3] Running JVM Unit Tests (Guards BUG-004, BUG-006)..."
if ./gradlew testDebugUnitTest --no-daemon; then
    echo "[PASS] Unit tests passed."
    BUG_004_RES="PASS"
    BUG_006_RES="PASS"
else
    echo "[FAIL] Unit tests failed."
    BUG_004_RES="FAIL"
    BUG_006_RES="FAIL"
fi

# --- STEP 3: ADB Device Smoke Test (Guards BUG-001, BUG-002, BUG-003) ---
echo -e "\n>>> [STEP 3/3] Running Device Smoke Test on Connected Xiaomi HyperOS..."
if bash scripts/device_smoke.sh; then
    BUG_001_RES="PASS"
    BUG_002_RES="PASS"
    BUG_003_RES="PASS"
else
    echo "[FAIL] Device smoke test reported one or more failures."
    BUG_001_RES="FAIL"
    BUG_002_RES="FAIL"
    BUG_003_RES="FAIL"
fi

# --- SUMMARY REPORT ---
echo -e "\n============================================================"
echo "  FINAL REGRESSION HARNESS VERIFICATION REPORT             "
echo "============================================================"
echo "  [BUG-001] $BUG_001_RES"
echo "  [BUG-002] $BUG_002_RES"
echo "  [BUG-003] $BUG_003_RES"
echo "  [BUG-004] $BUG_004_RES"
echo "  [BUG-005] $BUG_005_RES"
echo "  [BUG-006] $BUG_006_RES"
echo "============================================================"

if [[ "$BUG_001_RES" != "PASS" || "$BUG_002_RES" != "PASS" || "$BUG_003_RES" != "PASS" || "$BUG_004_RES" != "PASS" || "$BUG_005_RES" != "PASS" || "$BUG_006_RES" != "PASS" ]]; then
    echo "VERIFICATION FAILED: One or more bug regressions detected."
    exit 1
else
    echo "VERIFICATION SUCCESSFUL: All 6 bug guards verified green."
    exit 0
fi
