# build-plugin.ps1 -- Ultimate Sleep: builds the three plugin cells (1.20.x / 1.21.x / 26.x) into dist/.
# Each cell is a STANDALONE gradle build consuming ../shared_plugin in place; each ships ONE jar
# named ultimate-sleep-<ver>+<line>-plugin.jar covering Paper/Spigot/Folia via the runtime
# Platform facade (mirrors ChunkSmith's scripts/build-plugin.ps1).
#
# Usage:
#   pwsh scripts/build-plugin.ps1                 # build all three (serial)
#   pwsh scripts/build-plugin.ps1 -Only 1.20.x    # build a single line (1.20.x | 1.21.x | 26.x)
param([string]$Only)
$ErrorActionPreference = "Stop"
$repo   = Split-Path $PSScriptRoot -Parent
$plugin = Join-Path $repo "Plugin"
$dist   = Join-Path $repo "dist"
$status = Join-Path $env:TEMP "usleep_plugin_status.txt"
New-Item -ItemType Directory -Force -Path $dist | Out-Null
Remove-Item $status -ErrorAction SilentlyContinue

$lines = @("1.20.x", "1.21.x", "26.x")
if ($Only) {
    if ($lines -notcontains $Only) { throw "Unknown cell '$Only' (expected 1.20.x | 1.21.x | 26.x)" }
    $lines = @($Only)
}

foreach ($line in $lines) {
    $cellDir = Join-Path $plugin $line
    $modver = (Select-String -Path (Join-Path $cellDir "gradle.properties") -Pattern '^version=(.+)$').Matches[0].Groups[1].Value
    $jar = "ultimate-sleep-$modver+$line-plugin.jar"
    Write-Host "=== US Plugin $line ==="
    Push-Location $cellDir
    try {
        & ".\gradlew.bat" clean build --no-daemon
        if ($LASTEXITCODE -ne 0) { Add-Content $status "FAIL $line"; throw "Plugin $line build FAILED (rc=$LASTEXITCODE)" }
    } finally {
        Pop-Location
    }
    $src = Join-Path $cellDir ("build\libs\{0}" -f $jar)
    if (-not (Test-Path $src)) { Add-Content $status "FAIL $line (jar missing)"; throw "Missing jar: $src" }
    Copy-Item $src (Join-Path $dist $jar) -Force
    Add-Content $status "PASS $line"
    Write-Host "  -> $(Join-Path $dist $jar)"
}
Write-Host "Plugin builds complete. Jars in $dist"
