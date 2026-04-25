# Build script for Usage Monitor with proper icon
# Run: powershell -ExecutionPolicy Bypass -File build-with-icon.ps1

$ErrorActionPreference = "Stop"
$projectDir = "C:\Users\edils\workspace\usage-monitor"
$distDir = "$projectDir\build\compose\binaries\main\app\Usage Monitor"
$exePath = "$distDir\Usage Monitor.exe"
$icoPath = "$projectDir\src\desktopMain\resources\icons\app_icon.ico"
$rceditPath = "C:\Users\edils\AppData\Roaming\npm\node_modules\rcedit\bin\rcedit.exe"
$installerFilesDir = "$projectDir\build\installer\files"

Write-Host "=== Step 1: Clean old builds ==="
if (Test-Path "$projectDir\build") {
    Remove-Item "$projectDir\build" -Recurse -Force
}
Write-Host "Build directory cleaned"

Write-Host "=== Step 2: Create distributable ==="
Push-Location $projectDir
& .\gradlew.bat createDistributable
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: createDistributable failed"
    exit 1
}
Pop-Location

Write-Host "=== Step 3: Remove ReadOnly and patch icon with rcedit ==="
if (Test-Path $exePath) {
    Set-ItemProperty -Path $exePath -Name IsReadOnly -Value $false
    Write-Host "ReadOnly removed from: $exePath"
} else {
    Write-Host "ERROR: exe not found at: $exePath"
    exit 1
}

if (Test-Path $rceditPath) {
    & $rceditPath $exePath --set-icon $icoPath
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Icon patched successfully with rcedit"
    } else {
        Write-Host "Warning: rcedit failed with exit code: $LASTEXITCODE"
    }
} else {
    Write-Host "ERROR: rcedit not found at: $rceditPath"
    exit 1
}

Write-Host "=== Step 4: Prepare installer files manually ==="
# Create installer files directory
if (!(Test-Path $installerFilesDir)) {
    New-Item -ItemType Directory -Path $installerFilesDir -Force | Out-Null
}

# Copy all files from distributable to installer files
Copy-Item -Path "$distDir\*" -Destination $installerFilesDir -Recurse -Force
Write-Host "Copied distributable to installer files"

Write-Host "=== Step 5: Create installer ==="
$nsisPath = "C:\Program Files (x86)\NSIS\makensis.exe"
if (Test-Path $nsisPath) {
    Push-Location "$projectDir\src\installer"
    & $nsisPath "UsageMonitor.nsi"
    Pop-Location
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: NSIS failed with exit code: $LASTEXITCODE"
        exit 1
    }
} else {
    Write-Host "ERROR: NSIS not found at: $nsisPath"
    exit 1
}

Write-Host "=== Done ==="
Write-Host "Installer: $projectDir\build\installer\UsageMonitor-Setup-1.0.1.exe"
if (Test-Path "$projectDir\build\installer\UsageMonitor-Setup-1.0.1.exe") {
    $size = (Get-Item "$projectDir\build\installer\UsageMonitor-Setup-1.0.1.exe").Length / 1MB
    Write-Host "Size: $([math]::Round($size, 2)) MB"
}

Write-Host "=== Step 6: Cleanup processes ==="
# Stop Gradle daemons
& .\gradlew.bat --stop 2>$null | Out-Null
Write-Host "Gradle daemons stopped"

# Kill any remaining Usage Monitor processes (ignore if not found)
try {
    Get-Process "Usage Monitor" -ErrorAction Stop | Stop-Process -Force -ErrorAction Stop
    Write-Host "Usage Monitor processes killed"
} catch {
    Write-Host "No Usage Monitor processes running"
}

# Kill Java processes (build residue)
try {
    Get-Process java -ErrorAction Stop | Stop-Process -Force -ErrorAction Stop
    Write-Host "Java processes killed"
} catch {
    Write-Host "No Java processes running"
}

Write-Host "=== Cleanup complete ==="
