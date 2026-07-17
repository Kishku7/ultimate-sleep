# build-neoforge.ps1 -- Ultimate Sleep: builds ALL NeoForge cells (pre-26 walkers + the 26-line driver).
# Usage: .\build-neoforge.ps1                    -> everything
#        .\build-neoforge.ps1 -Only 1.21.5,26.1  -> filter (pre-26 cell names and/or 26.X line keys)
# NOTE: no NeoForge exists for 26.3 yet (legitimate gap; matrix updated when upstream ships).
param([string[]]$Only)
$ErrorActionPreference = "Stop"
$repo = Split-Path $PSScriptRoot -Parent
$neo  = Join-Path $repo "NeoForge"
$dist = Join-Path $repo "dist"
$status = Join-Path $env:TEMP "usleep_neoforge_status.txt"
New-Item -ItemType Directory -Force -Path $dist | Out-Null
Remove-Item $status -ErrorAction SilentlyContinue

function Want([string]$key) { return (-not $Only -or $Only.Count -eq 0 -or $Only -contains $key) }
function Jar([string]$cell) {
    Get-ChildItem (Join-Path $cell "build\libs") -Filter "ultimate-sleep-*.jar" |
        Where-Object { $_.Name -notmatch 'sources' } | Sort-Object LastWriteTime | Select-Object -Last 1
}

# ---- pre-26 cells ----
$cells = Get-ChildItem $neo -Directory | Where-Object { $_.Name -ne "26" } |
    Sort-Object { [version]($_.Name) } -Descending
foreach ($c in $cells) {
    if (-not (Want $c.Name)) { continue }
    Write-Host "=== US NeoForge cell $($c.Name) ==="
    $bg = Join-Path $c.FullName "build.gradle"
    if ((Get-Content $bg -Raw) -match 'srcDirs?\s*=?\s*\[?\s*[''"]gen') {
        & (Join-Path $PSScriptRoot "cog-gen.ps1") -Cell "NeoForge\$($c.Name)" -McVer $c.Name -Loader neoforge
        if ($LASTEXITCODE -ne 0) { Add-Content $status "FAIL cog $($c.Name)"; throw "cog-gen FAILED $($c.Name)" }
    }
    Push-Location $c.FullName
    & ".\gradlew.bat" clean build --no-daemon
    $rc = $LASTEXITCODE; Pop-Location
    if ($rc -ne 0) { Add-Content $status "FAIL $($c.Name)"; throw "NeoForge FAILED $($c.Name)" }
    $modver = (Select-String -Path (Join-Path $c.FullName "gradle.properties") -Pattern '^mod_version=(.+)$').Matches[0].Groups[1].Value
    Copy-Item (Jar $c.FullName).FullName (Join-Path $dist ("ultimate-sleep-{0}+{1}-neoforge.jar" -f $modver, $c.Name)) -Force
    Add-Content $status "PASS $($c.Name)"
    Write-Host "  -> $($c.Name) done"
}

# ---- 26 line (one cell, rebuilt per 26.X with -P overrides + PACK_FORMAT) ----
$cell26 = Join-Path $neo "26"
$matrix = [ordered]@{
  "26.1" = @{ mc="26.1.2"; neo="26.1.2.77";     neoRange="[26.1,)";           mcRange="[26.1,26.2)"; pf="84" }
  "26.2" = @{ mc="26.2";   neo="26.2.0.8-beta"; neoRange="[26.2.0.0-beta,)";  mcRange="[26.2,26.3)"; pf="88" }
}
$modver = (Select-String -Path (Join-Path $cell26 "gradle.properties") -Pattern '^mod_version=(.+)$').Matches[0].Groups[1].Value
foreach ($v in $matrix.Keys) {
    if (-not (Want $v)) { continue }
    $m = $matrix[$v]
    Write-Host "=== US NeoForge $v (mc=$($m.mc), neo=$($m.neo)) ==="
    & (Join-Path $PSScriptRoot "cog-gen.ps1") -Cell "NeoForge\26" -McVer $m.mc -Loader neoforge -KeepCellResources
    if ($LASTEXITCODE -ne 0) { Add-Content $status "FAIL cog $v"; throw "cog-gen FAILED 26 $v" }
    $env:PACK_FORMAT = $m.pf
    Push-Location $cell26
    & ".\gradlew.bat" clean build "-Pminecraft_version=$($m.mc)" "-Pneo_version=$($m.neo)" "-Pneoforge_range=$($m.neoRange)" "-Pmc_range=$($m.mcRange)" --no-daemon
    $rc = $LASTEXITCODE; Pop-Location
    Remove-Item Env:\PACK_FORMAT -ErrorAction SilentlyContinue
    if ($rc -ne 0) { Add-Content $status "FAIL $v"; throw "NeoForge FAILED $v" }
    Copy-Item (Jar $cell26).FullName (Join-Path $dist ("ultimate-sleep-{0}+{1}-neoforge.jar" -f $modver, $v)) -Force
    Add-Content $status "PASS $v"
    Write-Host "  -> $v done"
}
Write-Host "NeoForge builds complete."
