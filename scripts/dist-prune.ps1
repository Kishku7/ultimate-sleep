# dist-prune.ps1 -- drop superseded jars from dist/ so it only ever holds the CURRENT mod version.
#
# WHY: every full matrix run drops 28 jars into dist/ and nothing ever removed the previous set.
# Seven versions had stacked up to 89 jars / 8.3 MB before this existed (cleaned 2026-08-03).
# Ported from the m1 script of the same name -- including the traps it learned the hard way.
#
# DESIGN -- prune by VERSION, never "empty the folder":
# A full sweep is three drivers run back to back (build-fabric, build-neoforge, build-forge). If
# each driver blanked dist/ on entry, the second would delete the first's jars from the SAME run.
# So this removes only artifacts whose version differs from the current one. That makes it
# idempotent and order-independent: every driver can call it on entry, in any order, and a
# completed sweep leaves exactly one version behind.
#
# VERSION SOURCE -- the cells' gradle.properties, which are bumped together. Read them all and
# require agreement rather than trusting any single hand-maintained field.
#
# THE PLUGIN JARS ARE EXEMPT. Paper/Folia artifacts (ultimate-sleep-<ver>+<line>-plugin.jar) DO
# carry a "+", so they match the version pattern -- but the plugin runs its OWN version line
# (1.2.0, per the mod-rules "plugins version differently" clause) and its current jar is therefore
# NOT the mod's current version by definition. Pruning on the mod version would delete a perfectly
# current, published-matching plugin binary every single mod build. So -plugin.jar is skipped here,
# and this script is NOT wired into build-plugin.ps1. Learned 2026-08-03: the first pass pruned the
# three 1.2.0 plugin jars and they had to be rebuilt.
# Names that parse to no version at all are also left alone rather than guessed at.
#
#   pwsh scripts\dist-prune.ps1              # prune to the current version
#   pwsh scripts\dist-prune.ps1 -WhatIfOnly  # report what WOULD go, delete nothing
param(
  [switch]$WhatIfOnly
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$dist = Join-Path $root 'dist'
if (-not (Test-Path $dist)) { Write-Host "dist-prune: no dist/ yet, nothing to do."; return }

# --- resolve the current mod version from the cells -------------------------
$props = Get-ChildItem (Join-Path $root 'Fabric'),(Join-Path $root 'Forge'),(Join-Path $root 'NeoForge') `
           -Filter 'gradle.properties' -Recurse -ErrorAction SilentlyContinue |
           Where-Object { $_.FullName -notmatch '\\build\\|\\gen\\' }
# @(...) IS LOAD-BEARING. Sort-Object -Unique returns a SCALAR STRING when every cell agrees
# (the normal case). Indexing a string yields a CHARACTER, so $vers[0] becomes '1' instead of
# '1.2.6' -- every jar would then mismatch and the whole of dist/ would be deleted. That cost m1
# 37 jars on 2026-08-01. .Count is 1 on a bare string too, so the disagreement guard below does
# NOT catch it -- the shape gate further down is what does.
$vers = @($props | ForEach-Object {
  $m = Select-String -Path $_.FullName -Pattern '^mod_version\s*=\s*(.+)$'
  if ($m) { $m.Matches.Groups[1].Value.Trim() }
} | Sort-Object -Unique)

if (-not $vers)        { throw "dist-prune: no mod_version found in any cell gradle.properties -- refusing to delete." }
if ($vers.Count -gt 1) {
  # Cells disagree: a bump is half-applied. Pruning now could delete jars that are still wanted.
  Write-Host ("dist-prune: SKIPPED -- cells disagree on mod_version ({0}). Finish the bump, then rerun." -f ($vers -join ', '))
  return
}
$cur = $vers[0]

# Shape gate: whatever we resolved must LOOK like a version before it may authorise deletions.
# A malformed value matches nothing in dist/ and would therefore condemn everything, so refuse
# loudly instead. This is the backstop for the scalar/array trap above.
if ($cur -notmatch '^\d+\.\d+(\.\d+)?') {
  throw "dist-prune: resolved mod_version '$cur' is not a version -- refusing to delete anything."
}

# Emptying dist/ of mod jars is EXPECTED at the start of a sweep: the version has just been
# bumped, so dist/ still holds only the previous release and none of the new one exists yet. So
# this is a NOTICE, not a veto -- vetoing here would defeat the point of pruning before a run.
$present = @(Get-ChildItem $dist -File | Where-Object { $_.Name -match '^ultimate-sleep-.+?\+' -and $_.Name -notmatch '-plugin\.jar$' })
$keepers = @($present | Where-Object { $_.Name -match ('^ultimate-sleep-' + [regex]::Escape($cur) + '\+') })
if ($present.Count -gt 0 -and $keepers.Count -eq 0) {
  Write-Host ("dist-prune: dist/ holds {0} artifact(s), none at {1} -- clearing for a fresh {1} run." -f $present.Count,$cur)
}

# --- prune anything that is not the current version -------------------------
# Artifact names are ultimate-sleep-<modver>+<cell>-<loader>.jar, so the version is the text
# between the leading "ultimate-sleep-" and the "+".
$doomed = Get-ChildItem $dist -File | Where-Object {
  $_.Name -notmatch '-plugin\.jar$' -and $_.Name -match '^ultimate-sleep-(.+?)\+' -and $Matches[1] -ne $cur
}

if (-not $doomed) { Write-Host ("dist-prune: dist/ already clean at {0}." -f $cur); return }

$mb = [math]::Round((($doomed | Measure-Object Length -Sum).Sum / 1mb), 1)
if ($WhatIfOnly) {
  Write-Host ("dist-prune: WOULD remove {0} file(s) ({1} MB), keeping {2}:" -f $doomed.Count,$mb,$cur)
  $doomed | ForEach-Object { Write-Host ("  " + $_.Name) }
} else {
  $doomed | Remove-Item -Force
  Write-Host ("dist-prune: removed {0} superseded file(s) ({1} MB); dist/ now holds {2} only." -f $doomed.Count,$mb,$cur)
}
