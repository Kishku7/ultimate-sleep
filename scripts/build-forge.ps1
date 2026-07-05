# build-forge.ps1 -- Ultimate Sleep: builds ALL Forge cells (pre-26 walker; Forge has no 26 line
# in this matrix -- upstream Forge 26.x exists but ultimate-sleep does not target it yet).
# Usage: .\build-forge.ps1                     -> everything
#        .\build-forge.ps1 -Only 1.20.1,1.21.8 -> filter (cell names)
param([string[]]$Only)
$ErrorActionPreference = "Stop"
$repo  = Split-Path $PSScriptRoot -Parent
$forge = Join-Path $repo "Forge"
$dist  = Join-Path $repo "dist"
$status = Join-Path $env:TEMP "usleep_forge_status.txt"
New-Item -ItemType Directory -Force -Path $dist | Out-Null
Remove-Item $status -ErrorAction SilentlyContinue

function Want([string]$key) { return (-not $Only -or $Only.Count -eq 0 -or $Only -contains $key) }
function Jar([string]$cell) {
    Get-ChildItem (Join-Path $cell "build\libs") -Filter "ultimate-sleep-*.jar" |
        Where-Object { $_.Name -notmatch 'sources' } | Sort-Object LastWriteTime | Select-Object -Last 1
}

# ---- pre-26 cells (walk Forge\<ver> dirs) ----
$cells = Get-ChildItem $forge -Directory | Sort-Object { [version]($_.Name) } -Descending
foreach ($c in $cells) {
    if (-not (Want $c.Name)) { continue }
    Write-Host "=== US Forge cell $($c.Name) ==="
    $bg = Join-Path $c.FullName "build.gradle"
    if ((Get-Content $bg -Raw) -match 'srcDir') {
        & (Join-Path $PSScriptRoot "cog-gen.ps1") -Cell "Forge\$($c.Name)" -McVer $c.Name -Loader forge
        if ($LASTEXITCODE -ne 0) { Add-Content $status "FAIL cog $($c.Name)"; throw "cog-gen FAILED $($c.Name)" }
    }
    # gradlew's launcher JVM must not be the system JDK25 -- point JAVA_HOME at the cell's
    # pinned JDK (org.gradle.java.home in gradle.properties governs the build JVM itself).
    $gpRaw = Get-Content (Join-Path $c.FullName "gradle.properties") -Raw
    if ($gpRaw -match 'org\.gradle\.java\.home=(.+)') { $env:JAVA_HOME = $Matches[1].Trim() -replace '/','\' }
    Push-Location $c.FullName
    & ".\gradlew.bat" clean build --no-daemon
    $rc = $LASTEXITCODE; Pop-Location
    if ($rc -ne 0) { Add-Content $status "FAIL $($c.Name)"; throw "Forge FAILED $($c.Name)" }
    $modver = (Select-String -Path (Join-Path $c.FullName "gradle.properties") -Pattern '^mod_version=(.+)$').Matches[0].Groups[1].Value
    Copy-Item (Jar $c.FullName).FullName (Join-Path $dist ("ultimate-sleep-{0}+{1}-forge.jar" -f $modver, $c.Name)) -Force
    Add-Content $status "PASS $($c.Name)"
    Write-Host "  -> $($c.Name) done"
}
Write-Host "Forge builds complete."
