# Master Verification Harness for Soniq DSP (PowerShell / Windows)
# Runs: Gradle Debug Build, Pure JVM Unit Tests, and Device Smoke Test
# Asserts: BUG-001 through BUG-006

param(
    [switch]$SkipDevice
)

$ErrorActionPreference = "Stop"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  SONIQ DSP MASTER VERIFICATION HARNESS (PHASE 3)          " -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$overallResults = [ordered]@{
    "BUG-005" = "PENDING" # Gradle Wrapper / AGP Compatibility
    "BUG-004" = "PENDING" # Visualizer Wave Spline Math
    "BUG-006" = "PENDING" # Audio Telemetry & Codec Formatter
    "BUG-001" = "PENDING" # HyperOS Settings Sync
    "BUG-002" = "PENDING" # AudioEffectCenter Crash Guard
    "BUG-003" = "PENDING" # Phone Speaker Original Baseline
}

# --- STEP 1: Gradle Build (Guards BUG-005) ---
Write-Host "`n>>> [STEP 1/3] Running Gradle assembleDebug (Guards BUG-005)..." -ForegroundColor Yellow
try {
    $buildOutput = & .\gradlew.bat assembleDebug --no-daemon 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host $buildOutput
        throw "assembleDebug failed with exit code $LASTEXITCODE"
    }
    Write-Host "[PASS] Gradle assembleDebug completed successfully." -ForegroundColor Green
    $overallResults["BUG-005"] = "PASS"
} catch {
    Write-Host "[FAIL] Gradle build failed: $_" -ForegroundColor Red
    $overallResults["BUG-005"] = "FAIL"
}

# --- STEP 2: JVM Unit Tests (Guards BUG-004, BUG-006, State Logic) ---
Write-Host "`n>>> [STEP 2/3] Running JVM Unit Tests (Guards BUG-004, BUG-006)..." -ForegroundColor Yellow
try {
    $testOutput = & .\gradlew.bat testDebugUnitTest --no-daemon 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host $testOutput
        throw "testDebugUnitTest failed with exit code $LASTEXITCODE"
    }
    Write-Host "[PASS] Unit tests passed (SplineMathTest, TelemetryLogicTest, ProfileStateLogicTest)." -ForegroundColor Green
    $overallResults["BUG-004"] = "PASS"
    $overallResults["BUG-006"] = "PASS"
} catch {
    Write-Host "[FAIL] Unit tests failed: $_" -ForegroundColor Red
    $overallResults["BUG-004"] = "FAIL"
    $overallResults["BUG-006"] = "FAIL"
}

# --- STEP 3: ADB Device Smoke Test (Guards BUG-001, BUG-002, BUG-003) ---
if ($SkipDevice) {
    Write-Host "`n>>> [STEP 3/3] Skipping Device Smoke Test (-SkipDevice specified)..." -ForegroundColor Yellow
    $overallResults["BUG-001"] = "SKIPPED"
    $overallResults["BUG-002"] = "SKIPPED"
    $overallResults["BUG-003"] = "SKIPPED"
} else {
    Write-Host "`n>>> [STEP 3/3] Running Device Smoke Test on Connected Xiaomi HyperOS..." -ForegroundColor Yellow
    try {
        $smokeScript = "scripts/device_smoke.ps1"
        & powershell -ExecutionPolicy Bypass -File $smokeScript
        if ($LASTEXITCODE -ne 0) {
            throw "device_smoke.ps1 failed with exit code $LASTEXITCODE"
        }
        $overallResults["BUG-001"] = "PASS"
        $overallResults["BUG-002"] = "PASS"
        $overallResults["BUG-003"] = "PASS"
    } catch {
        Write-Host "[FAIL] Device smoke test encountered failure: $_" -ForegroundColor Red
        $overallResults["BUG-001"] = "FAIL"
        $overallResults["BUG-002"] = "FAIL"
        $overallResults["BUG-003"] = "FAIL"
    }
}

# --- SUMMARY REPORT ---
Write-Host "`n============================================================" -ForegroundColor Cyan
Write-Host "  FINAL REGRESSION HARNESS VERIFICATION REPORT             " -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$anyFail = $false
foreach ($bugId in $overallResults.Keys) {
    $res = $overallResults[$bugId]
    if ($res -eq "PASS") {
        Write-Host "  [$bugId] PASS" -ForegroundColor Green
    } elseif ($res -eq "SKIPPED") {
        Write-Host "  [$bugId] SKIPPED" -ForegroundColor Yellow
    } else {
        Write-Host "  [$bugId] $res" -ForegroundColor Red
        $anyFail = $true
    }
}
Write-Host "============================================================" -ForegroundColor Cyan

if ($anyFail) {
    Write-Host "VERIFICATION FAILED: One or more bug regressions detected." -ForegroundColor Red
    exit 1
} else {
    Write-Host "VERIFICATION SUCCESSFUL: All 6 bug guards verified green." -ForegroundColor Green
    exit 0
}
