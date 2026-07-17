# cog-gen.ps1 -- Ultimate Sleep: materialize a pre-26 cell's gen/ tree.
# Owns ALL era rules: file copies, era file presence, per-version pack.mcmeta, mixins.json
# emission (compatibilityLevel + refmap), and the cogapp run. Pattern: lava-boats cog-gen.
# Usage: .\cog-gen.ps1 -Cell Fabric\1.21.11 -McVer 1.21.11 -Loader fabric [-SrcLoader forge]
param(
    [Parameter(Mandatory)][string]$Cell,
    [Parameter(Mandatory)][string]$McVer,
    [Parameter(Mandatory)][string]$Loader,
    [string]$SrcLoader = "",
    [switch]$KeepCellResources   # 26 line: emit java + lang from cog_sources, keep the cell's own pack.mcmeta/mixins.json/manifest
)
$ErrorActionPreference = "Stop"
$repo = Split-Path $PSScriptRoot -Parent
$cg   = Join-Path $repo "_codegen"
$srcs = Join-Path $cg "cog_sources"
$cell = Join-Path $repo $Cell
$gen  = Join-Path $cell "gen"
$genJ = Join-Path $gen "src\main\java"
$genR = Join-Path $gen "src\main\resources"
$flavour = if ($SrcLoader) { $SrcLoader } else { $Loader }
$v = [version]($McVer -replace '-.*$','')

# era booleans (mirror _codegen\compat.py -- keep in sync)
$java17    = $v -lt [version]'1.20.5'
$modernNet = $v -ge [version]'1.20.5'
$is26      = $v.Major -ge 26
$legacyMixinCap = ($flavour -ne 'fabric') -and ($v -lt [version]'1.21.2')  # Forge51/neo21.0 bundle Mixin 0.8.5

# resource pack_format per version (Memory/knowledge/pack-formats.md; range form REQUIRED > 64)
$packFormats = @{
  '1.20'='15'; '1.20.1'='15'; '1.20.2'='18'; '1.20.3'='22'; '1.20.4'='22'
  '1.20.5'='32'; '1.20.6'='32'; '1.21'='34'; '1.21.1'='34'; '1.21.2'='42'; '1.21.3'='42'
  '1.21.4'='46'; '1.21.5'='55'; '1.21.6'='63'; '1.21.7'='64'; '1.21.8'='64'
  '1.21.9'='69'; '1.21.10'='69'; '1.21.11'='75'
}
$pf = $packFormats[$McVer]
if (-not $pf -and -not $is26) { throw "cog-gen: no pack_format for $McVer -- extend the table (knowledge/pack-formats.md)" }

Write-Host "[cog-gen] $Cell mc=$McVer loader=$Loader flavour=$flavour pf=$pf java17=$java17 modernNet=$modernNet"

# 1. wipe + base copy: the ONE shared java source of truth is cog_sources/shared (invariants as plain
#    files + the drift files as cog sources -- D16, shared_minecraft eliminated 2026-07-17). shared_common
#    stays an optional plugin-only tree (absent here; guard kept for parity with the plugin-shipping mods).
if (Test-Path $gen) { Remove-Item $gen -Recurse -Force }
New-Item -ItemType Directory -Force -Path $genJ, $genR | Out-Null
robocopy (Join-Path $srcs "shared") $genJ /E /NJH /NJS /NDL /NC /NS /NP | Out-Null
if (Test-Path (Join-Path $repo "shared_common\src\main\java")) {
    robocopy (Join-Path $repo "shared_common\src\main\java") $genJ /E /NJH /NJS /NDL /NC /NS /NP | Out-Null
}

# 3. loader flavour (entrypoints, Platform, net registration, client wiring)
$flavourDir = Join-Path $srcs $flavour
if (-not (Test-Path $flavourDir)) { throw "cog-gen: loader flavour dir missing: $flavourDir" }
robocopy $flavourDir $genJ /E /NJH /NJS /NDL /NC /NS /NP | Out-Null

# 4. era file presence
$pkg = Join-Path $genJ "com\kishku7\ultimatesleep"
if (-not $is26) {
    # pre-26 GUI: GuiGraphics-era screen/widget replace the 26 extractRenderState trio; ScreenCompat
    # (26.1/26.2 reflection bridge) is deleted -- pre-26 uses direct Minecraft.setScreen.
    Remove-Item (Join-Path $pkg "client\UltimateSleepScreen.java"), (Join-Path $pkg "client\ThemedButton.java"), (Join-Path $pkg "client\ScreenCompat.java") -ErrorAction SilentlyContinue
    $pre26gui = Join-Path $srcs "shared_pre26"
    if (-not (Test-Path $pre26gui)) { throw "cog-gen: shared_pre26 GUI flavour not yet written" }
    robocopy $pre26gui $genJ /E /NJH /NJS /NDL /NC /NS /NP | Out-Null
}
if (-not $modernNet) {
    # <1.20.5: CustomPacketPayload records do not exist -> legacy net flavour
    # ({loader}_legacy_net: fabric = ResourceLocation channels on fabric-networking-api-v1;
    # forge = classic NetworkRegistry/SimpleChannel with indexed message classes).
    # Delete the modern payload records AND the flavour-copied modern net twins (ClientNet,
    # UltimateSleepNet, the fabric client entrypoint) before copying the legacy replacements
    # over. The forge client entrypoint (UltimateSleepForgeClient) is era-neutral and stays.
    Get-ChildItem (Join-Path $pkg "net") -Filter "Usleep*Payload.java" | Remove-Item
    Remove-Item (Join-Path $pkg "net\ClientNet.java"), (Join-Path $pkg "net\UltimateSleepNet.java"), (Join-Path $pkg "client\UltimateSleepClient.java") -ErrorAction SilentlyContinue
    $legacyNet = Join-Path $srcs "${flavour}_legacy_net"
    if (-not (Test-Path $legacyNet)) { throw "cog-gen: ${flavour}_legacy_net flavour not yet written" }
    robocopy $legacyNet $genJ /E /NJH /NJS /NDL /NC /NS /NP | Out-Null
}

# 5. resources: shared lang + pack.mcmeta (range form REQUIRED for pf > 64)
robocopy (Join-Path $srcs "shared_resources") $genR /E /NJH /NJS /NDL /NC /NS /NP | Out-Null
# D4 pack.mcmeta form by band (knowledge/pack-formats.md; RESOLVED 2026-07-12, mirrors lava-boats/BV):
if ($KeepCellResources) {
    # 26 line owns its own pack.mcmeta (range-form, per-26.X PACK_FORMAT) -- do not emit one here.
} elseif ([int]$pf -gt 81) {
    # 26.x (major >= 82): exact range-form. (26 cells are NOT cog-gen; kept for safety.)
    $mcmeta = '{"pack":{"description":"Ultimate Sleep","pack_format":' + $pf + ',"min_format":' + $pf + ',"max_format":' + $pf + '}}'
    Set-Content (Join-Path $genR "pack.mcmeta") $mcmeta -NoNewline -Encoding ascii
} elseif ([int]$pf -gt 64) {
    # DEAD ZONE (resource major 65-81: 1.21.9/1.21.10=69, 1.21.11=75). Client resource codec and
    # server data codec disagree -> Fabric + NeoForge ship NO pack.mcmeta (loader synthesises correct
    # per-type metadata); Forge ships the exact range on the DATA major (both codecs new-era).
    if ($Loader -eq 'forge') {
        $dataMajors = @{ '1.21.9'='88'; '1.21.10'='88'; '1.21.11'='94' }
        $dm = $dataMajors[$McVer]
        if (-not $dm) { throw "cog-gen: no dead-zone data-major for $McVer (knowledge/pack-formats.md)" }
        $mcmeta = '{"pack":{"description":"Ultimate Sleep","pack_format":' + $dm + ',"min_format":' + $dm + ',"max_format":' + $dm + '}}'
        Set-Content (Join-Path $genR "pack.mcmeta") $mcmeta -NoNewline -Encoding ascii
    } else {
        Remove-Item (Join-Path $genR "pack.mcmeta") -Force -ErrorAction SilentlyContinue
    }
} else {
    # <= 1.21.8 (major <= 64): plain int.
    $mcmeta = '{"pack":{"description":"Ultimate Sleep","pack_format":' + $pf + '}}'
    Set-Content (Join-Path $genR "pack.mcmeta") $mcmeta -NoNewline -Encoding ascii
}

# 6. mixins.json (8 shared mixins every era; forge adds ForgeSleepMonstersMixin -- the NOT_SAFE
#    monsters override that Fabric/NeoForge do via sleep events Forge does not have).
#    compatLevel + refmap per loader/era.
$compatLevel = if ($java17 -or $legacyMixinCap) { "JAVA_17" } else { "JAVA_21" }
$refmapLine = ""
if (($flavour -eq 'forge') -and $java17) {
    $refmapLine = '  "refmap": "ultimate_sleep.refmap.json",' + "`n"
}
$forgeMixins = ""
if ($flavour -eq 'forge') {
    # Forge has no sleep events: full ServerPlayerSleepMixin (forge twin) + NOT_SAFE monsters mixin.
    $forgeMixins = ",`n    `"ServerPlayerSleepMixin`",`n    `"ForgeSleepMonstersMixin`""
} elseif ($flavour -eq 'fabric') {
    # Fabric keeps ONLY the bedInRange leg pre-26 (shared twin emits no day-gate leg -- fabric-api
    # owns that redirect and conflicts at equal priority; sleep_anytime uses ALLOW_SLEEP_TIME).
    $forgeMixins = ",`n    `"ServerPlayerSleepMixin`""
}
# NeoForge lists NO ServerPlayerSleepMixin: NeoForge patches startSleepInBed (vanilla INVOKEs gone);
# CanPlayerSleepEvent covers all accessibility overrides there. Smoketest-proven 2026-07-05.
$mixinsJson = @"
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.kishku7.ultimatesleep.mixin",
  "compatibilityLevel": "$compatLevel",
$refmapLine  "mixins": [
    "EntityFlagsAccessor",
    "ServerLevelSleepSkipMixin",
    "ServerLevelWeatherMixin",
    "ServerLevelProgressionMixin",
    "FurnaceProgressionMixin",
    "AgeableMobProgressionMixin",
    "ItemEntityProgressionMixin"$forgeMixins
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
"@
if (-not $KeepCellResources) { Set-Content (Join-Path $genR "ultimate_sleep.mixins.json") $mixinsJson -Encoding ascii }

# 7. run cogapp on every gen file carrying a cog marker
$cgFwd = $cg -replace '\\','/'
$count = 0
Get-ChildItem $genJ -Recurse -Filter *.java | ForEach-Object {
    if ((Get-Content $_.FullName -Raw) -match '\[\[\[cog') {
        & python -m cogapp -r -D "ver=$McVer" -D "loader=$flavour" -D "codegen=$cgFwd" $_.FullName | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "cogapp FAILED on $($_.FullName)" }
        $count++
    }
}
Write-Host "[cog-gen] done: $count cogged files, gen ready at $gen"
exit 0
