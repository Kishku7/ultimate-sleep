# Changelog

All notable changes to Ultimate Sleep are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.2.4] - 2026-07-28

### Changed
- **Fabric 26.3 cell moved to MC 26.3-snapshot-6** (from snapshot-5): fabric-api
  `0.155.3+26.3` -> `0.156.1+26.3`, `pack_format` `93` -> `94`, dep floor `26.3-alpha.5` ->
  `26.3-alpha.6` (upper bound stays `26.4`). Only this cell changed; the rest of the matrix
  stays 1.2.0/1.2.1.

### Notes
- **No source change required**, but two snapshot-6 deltas land close to this mod and were
  checked against the decompiled source rather than assumed:
  - `AbstractFurnaceBlockEntity` lost `getLootContext` / `getProvidedInteger` /
    `getProvidedFloat` (moved up to `BaseContainerBlockEntity` + the new `ResolvableNumber`).
    `FurnaceProgressionMixin` targets **`serverTick(ServerLevel, BlockPos, BlockState,
    AbstractFurnaceBlockEntity)`**, whose signature is byte-for-byte unchanged at snapshot-6,
    so the mixin target still resolves.
  - `Player.startSleepInBed(AbstractBedBlock, BlockState, BedRule, BlockPos)` keeps its
    signature; only its BODY changed (it now returns `Either.left(OTHER_PROBLEM)` when the
    new `LivingEntity.startSleeping` returns false). The cog-gated 4-arg call from snapshot-4
    still compiles. **Behavioural watch item:** a bed-entry that previously always succeeded
    can now report a problem, so auto-sleep failure paths deserve an in-game look.
  - Commands use the static `SharedSuggestionProvider.suggest(...)`, which is unchanged --
    the new `Predicate` filter was added to `suggestRegistryElements`/`listSuggestions`.

### Fixed
- **The Fabric 26 manifest was missing `contact.issues`** -- Ultimate Sleep was the only mod
  shipping no issue-tracker link, so the pre-publish metadata gate failed it. Added the
  canonical `https://github.com/Kishku7/mod_support/issues` to the 26 cell (the cell being
  shipped). NOTE: the other 7 Fabric cells and the NeoForge/Forge/plugin manifests still lack
  it -- a mod-wide backfill is owed on the next full-matrix pass, since fixing them now would
  mean rebuilding and republishing otherwise byte-identical jars.

## [1.2.3] - 2026-07-27
### Changed
- NeoForge 26 cells rebuilt against the now-PUBLISHED NeoForge builds: 26.1 -> 26.1.2.87, 26.2 -> 26.2.0.35-beta (previously 26.1.2.77 / 26.2.0.8-beta).
- mavenLocal() removed from the NeoForge/26 cell. No source or behaviour change; server-boot smoketested on both cells.

## [1.2.2] - 2026-07-21
### Changed
- MC 26.3 Fabric build advanced from 26.3-snapshot-4 to 26.3-snapshot-5 (Fabric API 0.155.3+26.3, pack_format 93, dependency floor 26.3-alpha.5). Loads and drives into a world on the snapshot's reworked GPU/shader pipeline with no source changes; verified in-world on the headless client harness.

## [1.2.1]
### Fixed
- Resource pack metadata on Minecraft 1.21.9-1.21.11: Fabric and NeoForge builds no longer ship a
  `pack.mcmeta` (each loader now synthesises the correct per-type metadata), and Forge builds carry the
  correct data-pack format range. This stops the client from dropping the mod's resources on those versions.

### Changed
- 26.3 build updated to the current snapshot (26.3-snapshot-4).
- Internal: consolidated to a single code source of truth across the build matrix (no shipped behaviour
  change; the produced jars are byte-for-byte identical on unaffected versions).

## [1.2.0]
### Added
- Full multi-loader, multi-version line: Fabric, NeoForge, and Forge across 1.20 - 26.x, plus
  Paper/Folia plugin builds for 1.20.x / 1.21.x / 26.x.

## [1.1.0]
### Added
- `notify_wake` "good morning" broadcast when a mod-driven skip reaches dawn.
- Live sleeper/vote tally appended to the action-bar vote prompt (`show_sleepers_on_vote_screen`).

## [1.0.0]
### Added
- Initial release: a single, fully admin-configurable sleep mod folding the useful behaviours of the
  surveyed sleep-mod ecosystem into one mod where everything is a toggle. Server-required,
  client-optional.
