# Ultimate Sleep - Build Guide (minecraft-1.20-26.3 branch)

This branch is the unified multi-loader, multi-version source tree for **Ultimate Sleep**
(version 1.2.0). It builds every shipped jar -- Fabric, NeoForge, and Forge -- from one
shared codebase.

- **What the mod does / how to use it:** see the landing page at
  https://github.com/Kishku7/ultimate-sleep/tree/main
- **Download (players):** https://modrinth.com/mod/ultimate-sleep
- **Report issues / support:** https://github.com/Kishku7/mod_support
- **Full feature/behavior reference for this branch:** [FUNCTIONAL_SPEC.md](FUNCTIONAL_SPEC.md)

---

## What you need installed

| Requirement | Used for |
|---|---|
| JDK 17 (Eclipse Adoptium) | Forge 1.20.1 cell |
| JDK 21 (Eclipse Adoptium) | All other pre-26 cells (Fabric daemon, Forge 1.20.6-1.21.11, NeoForge pre-26) |
| JDK 25 | The 26-line cells (Fabric/26, NeoForge/26) -- run on the system JDK |
| Python 3 + Cog (`pip install cogapp`) | Code generation (`_codegen/`) -- required before any pre-26 cell builds |
| PowerShell 7 (`pwsh`) | The build scripts in `scripts/` |

Each pre-26 cell pins its own JVM via `org.gradle.java.home` in its `gradle.properties`
(pointing at the Eclipse Adoptium install directories under `C:/Program Files/Eclipse
Adoptium/`). If your JDKs live elsewhere, adjust those pins; the Gradle wrapper itself
is committed per cell, so no Gradle install is needed.

## How to build

Run from anywhere in PowerShell 7; each script walks its loader's cells, runs code
generation where the cell needs it, builds with the cell's own Gradle wrapper, and copies
the finished jar into `dist/` as `ultimate-sleep-<modver>+<cell>-<loader>.jar`.

```powershell
# everything, per loader
.\scripts\build-fabric.ps1
.\scripts\build-neoforge.ps1
.\scripts\build-forge.ps1

# a subset (pre-26 cell names and/or 26-line keys)
.\scripts\build-fabric.ps1 -Only 1.21.5,26.1
```

Supporting scripts:

- `scripts\cog-gen.ps1 -Cell Fabric\1.21.11 -McVer 1.21.11 -Loader fabric` -- materialize
  one cell's `gen/` tree by hand. The build scripts call this automatically; you only need
  it directly when iterating on `_codegen/` sources.
- `scripts\check-sync.ps1` -- the cog-twin vs plain-twin drift tripwire (exit 1 on drift).
  Run before every push.

The 26-line cells (`Fabric/26`, `NeoForge/26`) are single projects rebuilt once per 26.x
release line with `-P` property overrides and a `PACK_FORMAT` environment variable; the
build scripts drive this matrix, so you normally never invoke them manually.

## Version coverage

27 jars total from 24 build cells. Each cell folder is named after the MC version it is
built against; the jar it produces claims the full range that build actually serves.

**Fabric (10 jars)**

| Cell | Jar covers |
|---|---|
| `Fabric/1.20.1` | 1.20 - 1.20.4 |
| `Fabric/1.20.6` | 1.20.5 - 1.20.6 |
| `Fabric/1.21.1` | 1.21 - 1.21.1 |
| `Fabric/1.21.2` | 1.21.2 - 1.21.4 |
| `Fabric/1.21.5` | 1.21.5 - 1.21.8 |
| `Fabric/1.21.9` | 1.21.9 - 1.21.10 |
| `Fabric/1.21.11` | 1.21.11 |
| `Fabric/26` | 26.1.x, 26.2, and 26.3 snapshots (three jars from one cell) |

**NeoForge (9 jars)**

| Cell | Jar covers |
|---|---|
| `NeoForge/1.20.6` | 1.20.5 - 1.20.6 |
| `NeoForge/1.21.1` | 1.21 - 1.21.1 |
| `NeoForge/1.21.2` | 1.21.2 - 1.21.4 |
| `NeoForge/1.21.5` | 1.21.5 - 1.21.7 |
| `NeoForge/1.21.8` | 1.21.8 |
| `NeoForge/1.21.9` | 1.21.9 - 1.21.10 |
| `NeoForge/1.21.11` | 1.21.11 |
| `NeoForge/26` | 26.1.x and 26.2 (two jars from one cell) |

**Forge (8 jars)**

| Cell | Jar covers |
|---|---|
| `Forge/1.20.1` | 1.20.1 |
| `Forge/1.20.6` | 1.20.5 - 1.20.6 |
| `Forge/1.21.1` | 1.21.1 |
| `Forge/1.21.5` | 1.21.5 |
| `Forge/1.21.7` | 1.21.6 - 1.21.7 |
| `Forge/1.21.8` | 1.21.8 |
| `Forge/1.21.10` | 1.21.10 |
| `Forge/1.21.11` | 1.21.11 |

**Known gaps (and why)**

- **Forge 1.20.2 - 1.20.4** -- Forge 48 removed the legacy networking API these Forge
  cells use.
- **Forge 1.21 (1.21.0)** -- Forge 51 lacks the tick event the mod relies on.
- **Forge 1.21.2 - 1.21.4 and 1.21.9** -- no suitable Forge releases exist for those
  versions.
- **NeoForge 1.20.5** -- upstream NeoForge for 1.20.5 is beta-only; the 1.20.5-1.20.6 jar
  is built against 1.20.6.
- **NeoForge 26.3** -- no NeoForge release for 26.3 exists yet; the cell matrix is
  extended when upstream ships.
- **Forge 26.x** -- upstream Forge 26.x exists but is not targeted by this branch yet.

## Toolchain matrix

| Cells | Plugin | Gradle | JDK |
|---|---|---|---|
| Fabric 1.20.1 | `fabric-loom-remap` 1.16-SNAPSHOT | 9.4.1 | 21 daemon, `options.release = 17` bytecode |
| Fabric 1.20.6 - 1.21.11 | `fabric-loom-remap` 1.16-SNAPSHOT | 9.4.1 | 21 |
| Fabric 26 | `fabric-loom` 1.16-SNAPSHOT (unobfuscated 26.x, no remap) | 9.4.1 | 25 |
| Forge 1.20.1 | ForgeGradle `[6.0,6.2)` + `org.spongepowered.mixin` 0.7.+ | 8.8 | 17 |
| Forge 1.20.6 - 1.21.11 | ForgeGradle `[6.0,6.2)` | 8.8 | 21 |
| NeoForge 1.20.6 - 1.21.11 | ModDevGradle (`net.neoforged.moddev`) | 9.2.1 | 21 |
| NeoForge 26 | ModDevGradle 2.0.141 | 9.2.1 | 25 |

Notes: Loom 1.16 requires a JVM 21+ runtime, so even the Java-17-target Fabric 1.20.1
cell pins a JDK 21 daemon and downlevels the emitted bytecode. The 26.x line ships
unobfuscated, so the Fabric 26 cell uses plain `fabric-loom` with no mapping/remap step.

## Repository layout

```
minecraft-1.20-26.3/
  Fabric/<cell>/        one Gradle project per Fabric build cell (+ Fabric/26 line cell)
  NeoForge/<cell>/      one Gradle project per NeoForge build cell (+ NeoForge/26 line cell)
  Forge/<cell>/         one Gradle project per Forge build cell
  shared_common/        loader- and version-independent engine code (compiled into every cell)
  shared_minecraft/     plain 26-era copies of the MC-facing shared classes (the "plain twins")
  _codegen/             the code-generation machinery
    compat.py           the era brain: version predicates + per-version constants
    compat_loaders.py   loader-flavour rules
    cog_sources/        the cog twins, by flavour: shared/, shared_pre26/, fabric/,
                        fabric_legacy_net/, forge/, forge_legacy_net/, neoforge/
  scripts/              build-fabric.ps1, build-neoforge.ps1, build-forge.ps1,
                        cog-gen.ps1, check-sync.ps1
  dist/                 build output (final per-cell jars land here)
  research/             design research notes
  FUNCTIONAL_SPEC.md    the authoritative feature/behavior specification
  PROPOSED_1.0.md       original 1.0 proposal document
```

## How the code generation works

Pre-26 Minecraft versions differ in real API shape (networking, tick events, NBT getters,
mixin environments), so the MC-facing sources are maintained once as **cog twins** in
`_codegen/cog_sources/` and materialized per cell:

1. **`compat.py` is the era brain.** It owns the version predicates (Java-17 era,
   modern-networking era, the 26+ era) and per-version constants such as `pack_format`.
   `cog-gen.ps1` mirrors the same era booleans on the PowerShell side.
2. **Cog twins carry inline `[[[cog ... ]]]` blocks.** Running cogapp with a target
   version + loader flavour expands each twin into the exact source that version needs.
3. **Flavours select the loader variant.** `shared/` and `shared_pre26/` serve every
   loader; `fabric/`, `forge/`, and `neoforge/` carry loader-specific files; the
   `*_legacy_net/` flavours cover the pre-modern networking era.
4. **Era file presence.** `cog-gen.ps1` also decides which files exist at all for a given
   version, emits the per-version `pack.mcmeta`, and writes the cell's `mixins.json`
   (compatibility level + refmap wiring) into the cell's `gen/` tree. Cells compile
   `gen/` + `shared_common/`; nothing generated is hand-edited.
5. **Drift protection.** The 26-era cells compile the plain copies in `shared_minecraft/`
   directly. `check-sync.ps1` materializes every cog twin at 26.1 and diffs it against its
   plain twin, so the two representations cannot drift apart silently. It must pass before
   any push.

## License

All Rights Reserved (c) Kishku7.

Ultimate Sleep is distributed exclusively through Modrinth:
https://modrinth.com/mod/ultimate-sleep
