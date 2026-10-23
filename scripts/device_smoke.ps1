# Soniq DSP Device Smoke Test Harness (PowerShell / Windows)
# Guards: BUG-001, BUG-002, BUG-003

$ErrorActionPreference = "Continue"

function Find-Adb {
    if (Get-Command adb -ErrorAction SilentlyContinue) {
        return "adb"
    }
    $sdkAdb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $sdkAdb) {
        return $sdkAdb
    }
    Write-Host "[FAIL] ADB binary not found in PATH or Android SDK platform-tools." -ForegroundColor Red
    exit 1
}

$ADB = Find-Adb
Write-Host "==> Using ADB: $ADB" -ForegroundColor Cyan

# 1. Device Connectivity
$deviceLines = & $ADB devices
$devices = $deviceLines | Where-Object { $_ -match "\bdevice$" }
if (-not $devices) {
    Write-Host "[FAIL] No authorized Android device connected via ADB." -ForegroundColor Red
    exit 1
}
Write-Host "[PASS] Connected device verified: $($devices.Trim())" -ForegroundColor Green

# 2. Package Installation Check
$pkgList = & $ADB shell pm list packages
$pkg = $pkgList | Where-Object { $_ -match "com\.audiophile\.buds2prooptimizer" }
if (-not $pkg) {
    Write-Host "[WARN] Package not installed. Attempting installation from build outputs..." -ForegroundColor Yellow
    $apkPath = "app/build/outputs/apk/debug/app-debug.apk"
    if (-not (Test-Path $apkPath)) {
        Write-Host "[FAIL] Debug APK not found at $apkPath. Run assembleDebug first." -ForegroundColor Red
        exit 1
    }
    & $ADB install -r $apkPath | Out-Null
}

# Ensure permissions
& $ADB shell pm grant com.audiophile.buds2prooptimizer android.permission.WRITE_SECURE_SETTINGS 2>$null | Out-Null

$testResults = [ordered]@{}

# --- TEST BUG-002: Spatializer.setEnabled / AudioEffectCenter crash check ---
Write-Host "`n--- Testing BUG-002: AudioEffectCenter Crash Guard ---" -ForegroundColor Cyan
& $ADB logcat -c
Start-Sleep -Milliseconds 300
& $ADB shell am start -n com.audiophile.buds2prooptimizer/.ui.MainActivity --es PROFILE ORIGINAL_SOUND 2>$null | Out-Null
Start-Sleep -Milliseconds 1200

$logcatErrors = & $ADB logcat -d -s AndroidRuntime:E
$crashMatch = $logcatErrors | Where-Object { $_ -match "MODIFY_DEFAULT_AUDIO_EFFECTS|FATAL EXCEPTION: main.*buds2prooptimizer" }
if ($crashMatch) {
    Write-Host "[FAIL] BUG-002: App crashed on profile switch: $crashMatch" -ForegroundColor Red
    $testResults["BUG-002"] = "FAIL"
} else {
    Write-Host "[PASS] BUG-002: AudioEffectCenter safely switched to ORIGINAL without SecurityException" -ForegroundColor Green
    $testResults["BUG-002"] = "PASS"
}

# --- TEST BUG-001: HyperOS Settings Synchronization ---
Write-Host "`n--- Testing BUG-001: HyperOS Settings Synchronization ---" -ForegroundColor Cyan
# Switch to DOLBY
& $ADB shell am start -n com.audiophile.buds2prooptimizer/.ui.MainActivity --es PROFILE DOLBY_ATMOS 2>$null | Out-Null
Start-Sleep -Milliseconds 800
$dolbyImpl = ((& $ADB shell settings get global effect_implementer) -join "").Trim()
$dolbySwitch = ((& $ADB shell settings get global dolby_effect_switch) -join "").Trim()

# Switch to XIAOMI
& $ADB shell am start -n com.audiophile.buds2prooptimizer/.ui.MainActivity --es PROFILE XIAOMI_SOUND 2>$null | Out-Null
Start-Sleep -Milliseconds 800
$xiaomiImpl = ((& $ADB shell settings get global effect_implementer) -join "").Trim()
$xiaomiState = ((& $ADB shell settings get global misound_state) -join "").Trim()

if ($dolbyImpl -eq "dolby" -and $dolbySwitch -eq "1" -and $xiaomiImpl -eq "misound" -and $xiaomiState -eq "1") {
    Write-Host "[PASS] BUG-001: HyperOS Settings table correctly synchronized for DOLBY and XIAOMI" -ForegroundColor Green
    $testResults["BUG-001"] = "PASS"
} else {
    Write-Host "[FAIL] BUG-001: Settings desync. Dolby: impl=$dolbyImpl sw=$dolbySwitch | Xiaomi: impl=$xiaomiImpl st=$xiaomiState" -ForegroundColor Red
    $testResults["BUG-001"] = "FAIL"
}

# --- TEST BUG-003: Phone Speaker Original Profile Baseline ---
Write-Host "`n--- Testing BUG-003: Speaker Original Baseline ---" -ForegroundColor Cyan
& $ADB shell am start -n com.audiophile.buds2prooptimizer/.ui.MainActivity --es PROFILE ORIGINAL_SOUND 2>$null | Out-Null
Start-Sleep -Milliseconds 800
$origImpl = ((& $ADB shell settings get global effect_implementer) -join "").Trim()
$origSwitch = ((& $ADB shell settings get global dolby_effect_switch) -join "").Trim()

if ($origImpl -eq "none" -and $origSwitch -eq "0") {
    Write-Host "[PASS] BUG-003: Speaker baseline intact (effect_implementer=none, dolby_effect_switch=0)" -ForegroundColor Green
    $testResults["BUG-003"] = "PASS"
} else {
    Write-Host "[FAIL] BUG-003: Speaker baseline mismatch (impl=$origImpl, dolby_switch=$origSwitch)" -ForegroundColor Red
    $testResults["BUG-003"] = "FAIL"
}

Write-Host "`n==========================================" -ForegroundColor Cyan
Write-Host "DEVICE SMOKE TEST SUMMARY" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
$failed = $false
foreach ($key in $testResults.Keys) {
    $status = $testResults[$key]
    if ($status -eq "PASS") {
        Write-Host "  [$key] PASS" -ForegroundColor Green
    } else {
        Write-Host "  [$key] FAIL" -ForegroundColor Red
        $failed = $true
    }
}

if ($failed) {
    exit 1
} else {
    exit 0
}
