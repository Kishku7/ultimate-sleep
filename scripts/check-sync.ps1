# check-sync.ps1 -- Ultimate Sleep: cog-twin vs plain-twin drift tripwire (lava-boats pattern).
# Materializes each cog twin at ver=26.1 and compares CODE LINES against the plain 26-era copy.
# Exit 1 on drift. Run before every GitHub push.
$ErrorActionPreference = "Stop"
$repo = Split-Path $PSScriptRoot -Parent
$cg   = Join-Path $repo "_codegen"
$cgF  = $cg -replace '\\','/'
$srcs = Join-Path $cg "cog_sources"
$tmp  = Join-Path $env:TEMP "usleep-checksync"
Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $tmp | Out-Null

function Normalize([string]$path) {
    $inBlock = $false
    Get-Content $path | ForEach-Object {
        $l = $_.Trim()
        if ($inBlock) { if ($l -match '\*/') { $inBlock = $false }; return }
        if ($l -match '^/\*') { if ($l -notmatch '\*/') { $inBlock = $true }; return }
        if ($l -eq '' -or $l.StartsWith('//') -or $l.StartsWith('*') -or $l.StartsWith('package ')) { return }
        $l
    }
}

$pkg = "com\kishku7\ultimatesleep"
$pairs = @(
    # shared twins vs shared_minecraft (must be identical at 26.1)
    @{ src = "shared\$pkg\compat\Era.java";                        loader = "fabric";   plain = "shared_minecraft\src\main\java\$pkg\compat\Era.java" }
    @{ src = "shared\$pkg\compat\TravelersBackpackCompat.java";    loader = "fabric";   plain = "shared_minecraft\src\main\java\$pkg\compat\TravelersBackpackCompat.java" }
    @{ src = "shared\$pkg\mixin\ServerLevelProgressionMixin.java"; loader = "fabric";   plain = "shared_minecraft\src\main\java\$pkg\mixin\ServerLevelProgressionMixin.java" }
    @{ src = "shared\$pkg\mixin\ServerPlayerSleepMixin.java";      loader = "fabric";   plain = "shared_minecraft\src\main\java\$pkg\mixin\ServerPlayerSleepMixin.java" }
    @{ src = "shared\$pkg\mixin\FurnaceProgressionMixin.java";     loader = "fabric";   plain = "shared_minecraft\src\main\java\$pkg\mixin\FurnaceProgressionMixin.java" }
    @{ src = "shared\$pkg\mixin\ServerLevelWeatherMixin.java";     loader = "fabric";   plain = "shared_minecraft\src\main\java\$pkg\mixin\ServerLevelWeatherMixin.java" }
    # fabric flavour twins vs the plain Fabric 26 cell
    @{ src = "fabric\$pkg\UltimateSleep.java";                     loader = "fabric";   plain = "Fabric\26\src\main\java\$pkg\UltimateSleep.java" }
    @{ src = "fabric\$pkg\net\UltimateSleepNet.java";              loader = "fabric";   plain = "Fabric\26\src\main\java\$pkg\net\UltimateSleepNet.java" }
    # neoforge flavour twins vs the plain NeoForge 26 cell
    @{ src = "neoforge\$pkg\UltimateSleep.java";                   loader = "neoforge"; plain = "NeoForge\26\src\main\java\$pkg\UltimateSleep.java" }
    @{ src = "neoforge\$pkg\net\UltimateSleepNet.java";            loader = "neoforge"; plain = "NeoForge\26\src\main\java\$pkg\net\UltimateSleepNet.java" }
    @{ src = "neoforge\$pkg\client\UltimateSleepNeoForgeClient.java"; loader = "neoforge"; plain = "NeoForge\26\src\main\java\$pkg\client\UltimateSleepNeoForgeClient.java" }
)

$fail = $false
foreach ($p in $pairs) {
    $srcFile = Join-Path $srcs $p.src
    $plainFile = Join-Path $repo $p.plain
    if (-not (Test-Path $srcFile))   { Write-Host "MISSING twin: $($p.src)"; $fail = $true; continue }
    if (-not (Test-Path $plainFile)) { Write-Host "MISSING plain: $($p.plain)"; $fail = $true; continue }
    $mat = Join-Path $tmp (($p.src -replace '[\\/]','_'))
    Copy-Item $srcFile $mat -Force
    & python -m cogapp -r -D ver=26.1 -D "loader=$($p.loader)" -D "codegen=$cgF" $mat | Out-Null
    if ($LASTEXITCODE -ne 0) { Write-Host "COG FAIL: $($p.src)"; $fail = $true; continue }
    $a = @(Normalize $mat); $b = @(Normalize $plainFile)
    $diff = Compare-Object $a $b
    if ($diff) {
        Write-Host "DRIFT: $($p.src) vs $($p.plain)"
        $diff | Select-Object -First 6 | ForEach-Object { Write-Host ("  {0} {1}" -f $_.SideIndicator, $_.InputObject) }
        $fail = $true
    } else {
        Write-Host "OK: $($p.src)"
    }
}
Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
if ($fail) { Write-Host "CHECK-SYNC: DRIFT FOUND"; exit 1 }
Write-Host "CHECK-SYNC: all twins in step"
exit 0
