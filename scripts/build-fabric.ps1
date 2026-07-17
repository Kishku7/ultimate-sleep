# build-fabric.ps1 -- Ultimate Sleep: builds ALL Fabric cells (pre-26 walkers + the 26-line driver).
# Usage: .\build-fabric.ps1                     -> everything
#        .\build-fabric.ps1 -Only 1.21.5,26.1   -> filter (pre-26 cell names and/or 26.X line keys)
param([string[]]$Only)
$ErrorActionPreference = "Stop"
$repo   = Split-Path $PSScriptRoot -Parent
$fabric = Join-Path $repo "Fabric"
$dist   = Join-Path $repo "dist"
$status = Join-Path $env:TEMP "usleep_fabric_status.txt"
New-Item -ItemType Directory -Force -Path $dist | Out-Null
Remove-Item $status -ErrorAction SilentlyContinue

function Want([string]$key) { return (-not $Only -or $Only.Count -eq 0 -or $Only -contains $key) }
function Jar([string]$cell) {
    Get-ChildItem (Join-Path $cell "build\libs") -Filter "ultimate-sleep-*.jar" |
        Where-Object { $_.Name -notmatch 'sources' } | Sort-Object LastWriteTime | Select-Object -Last 1
}

# ---- pre-26 cells (walk Fabric\<ver> dirs except 26) ----
$cells = Get-ChildItem $fabric -Directory | Where-Object { $_.Name -ne "26" } |
    Sort-Object { [version]($_.Name) } -Descending
foreach ($c in $cells) {
    if (-not (Want $c.Name)) { continue }
    Write-Host "=== US Fabric cell $($c.Name) ==="
    $bg = Join-Path $c.FullName "build.gradle"
    if ((Get-Content $bg -Raw) -match 'srcDirs?\s*=?\s*\[?\s*[''"]gen') {
        & (Join-Path $PSScriptRoot "cog-gen.ps1") -Cell "Fabric\$($c.Name)" -McVer $c.Name -Loader fabric
        if ($LASTEXITCODE -ne 0) { Add-Content $status "FAIL cog $($c.Name)"; throw "cog-gen FAILED $($c.Name)" }
    }
    Push-Location $c.FullName
    & ".\gradlew.bat" clean build --no-daemon
    $rc = $LASTEXITCODE; Pop-Location
    if ($rc -ne 0) { Add-Content $status "FAIL $($c.Name)"; throw "Fabric FAILED $($c.Name)" }
    $modver = (Select-String -Path (Join-Path $c.FullName "gradle.properties") -Pattern '^mod_version=(.+)$').Matches[0].Groups[1].Value
    Copy-Item (Jar $c.FullName).FullName (Join-Path $dist ("ultimate-sleep-{0}+{1}-fabric.jar" -f $modver, $c.Name)) -Force
    Add-Content $status "PASS $($c.Name)"
    Write-Host "  -> $($c.Name) done"
}

# ---- 26 line (one cell, rebuilt per 26.X with -P overrides + PACK_FORMAT) ----
$cell26 = Join-Path $fabric "26"
$matrix = [ordered]@{
  "26.1" = @{ mc="26.1.2";          api="0.152.1+26.1.2"; loader="0.18.6"; lo="26.1-"; hi="26.2"; pf="84"; modver="1.2.0" }
  "26.2" = @{ mc="26.2";            api="0.152.1+26.2";   loader="0.19.3"; lo="26.2-"; hi="26.3"; pf="88"; modver="1.2.0" }
  "26.3" = @{ mc="26.3-snapshot-4"; api="0.155.1+26.3";   loader="0.19.3"; lo="26.3-alpha.4"; hi="26.4"; pf="92"; modver="1.2.1" }
}
$modver = (Select-String -Path (Join-Path $cell26 "gradle.properties") -Pattern '^mod_version=(.+)$').Matches[0].Groups[1].Value
foreach ($v in $matrix.Keys) {
    if (-not (Want $v)) { continue }
    $m = $matrix[$v]
    Write-Host "=== US Fabric $v (mc=$($m.mc)) ==="
    & (Join-Path $PSScriptRoot "cog-gen.ps1") -Cell "Fabric\26" -McVer $m.mc -Loader fabric -KeepCellResources
    if ($LASTEXITCODE -ne 0) { Add-Content $status "FAIL cog $v"; throw "cog-gen FAILED 26 $v" }
    $env:PACK_FORMAT = $m.pf
    Push-Location $cell26
    & ".\gradlew.bat" clean build "-Pmod_version=$($m.modver)" "-Pminecraft_version=$($m.mc)" "-Pfabric_api_version=$($m.api)" "-Ploader_version=$($m.loader)" "-Pmc_lower=$($m.lo)" "-Pmc_upper=$($m.hi)" --no-daemon
    $rc = $LASTEXITCODE; Pop-Location
    Remove-Item Env:\PACK_FORMAT -ErrorAction SilentlyContinue
    if ($rc -ne 0) { Add-Content $status "FAIL $v"; throw "Fabric FAILED $v" }
    Copy-Item (Jar $cell26).FullName (Join-Path $dist ("ultimate-sleep-{0}+{1}-fabric.jar" -f $m.modver, $v)) -Force
    Add-Content $status "PASS $v"
    Write-Host "  -> $v done"
}
Write-Host "Fabric builds complete."
