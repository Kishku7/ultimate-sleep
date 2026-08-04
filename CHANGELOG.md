# Changelog

All notable changes to Ultimate Sleep are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.2.6] - 2026-08-03 (full matrix)

### Fixed
- **`sleep_anytime` let you into the bed and then threw you straight back out**
  ([mod_support #10](https://github.com/Kishku7/mod_support/issues/10)). Vanilla gates sleeping in
  TWO places, and only one was ever bypassed: the ENTRY check inside `ServerPlayer.startSleepInBed`
  (covered by `ServerPlayerSleepMixin` / the loader sleep events), and an ONGOING check inside
  `Player.tick` that runs every tick while asleep and calls `stopSleepInBed(false, true)` the moment
  the day-gate says you may not sleep here. Nothing in the mod touched the second one, so with
  `sleep_anytime` enabled you passed the entry check, entered the bed, and were ejected on the very
  next tick -- and because you never stayed asleep, `areEnoughSleeping` never flipped, the skip never
  triggered, and no progression (crops/animals/smelting/despawn) ran either.
  New `PlayerSleepTickMixin` redirects that `stopSleepInBed` call inside `Player.tick` and skips it
  while `sleep_anytime` is on. Dawn wake-up is unaffected (that runs through
  `ServerLevel.wakeUpAllPlayers`, a different call site).
  **This was never Fabric-only:** NeoForge relies on `CanPlayerSleepEvent`, which likewise fires only
  at entry, so `sleep_anytime` was broken on NeoForge on EVERY version. Forge was already fine (its
  `Player.tick` patch fires `SleepingTimeCheckEvent` there) and so was Fabric below 1.21.11
  (fabric-api hooks the tick site); the new mixin is inert where the gate is already handled.
- **`afk_threshold_seconds = -1` did not actually disable auto-AFK detection.** `AfkManager.tick()`
  ran the raw value through `Math.max(1, ...)`, flooring any zero/negative value to a 1-second
  threshold -- the opposite of "off". Auto-AFK detection is now genuinely skipped when the
  threshold is negative (manual `/usleep afk` still works); if the setting is switched to -1
  mid-session, any player already auto-AFK is immediately cleared.

### Added
- **Admin GUI: "Auto-AFK Detection" ON/OFF button**, on its own row directly above the
  `afk_threshold_seconds` field (Auto-sleep & AFK page). OFF sets the threshold to -1; clicking
  again restores 180s. Landed in both the 26.x (`extractRenderState`) and pre-26 (`render`)
  `UltimateSleepScreen` copies.

### Notes
- Rebuilt across the WHOLE matrix at one version. The AFK fix above had been built for the
  Fabric 1.21.11 cell only (2026-07-31, never released); it ships everywhere here.
- `PlayerSleepTickMixin` needs no era gate: the gate EXPRESSION drifts (`Level.isDay` 1.20-1.21.4,
  `Level.isBrightOutside` 1.21.5-1.21.8, `BedRule.canSleep` via `environmentAttributes`
  1.21.11-26.2, `AbstractBedBlock.getBedRule().canSleep` 26.3+), but the `stopSleepInBed(ZZ)V` call
  it guards is descriptor-identical with a single call site in `tick` on every version from 1.20.1
  to 26.3-snapshot-6. Redirecting the consequence rather than the condition also avoids the
  equal-priority collision with fabric-api's `ALLOW_SLEEP_TIME` redirect (see the 2026-07-05
  smoketest note in `ServerPlayerSleepMixin`).
- The Paper/Folia plugin is unchanged and keeps its own 1.2.0 line (no mixins, different sleep path).
## [1.2.5] - 2026-07-28

### Fixed
- **Issue-tracker URL backfilled across the WHOLE matrix.** 1.2.4 added `contact.issues` to the
  Fabric 26 cell only; every other manifest still shipped none, so Ultimate Sleep was the one
  mod whose jars carried no way to report a bug. All 24 remaining manifests now carry the
  canonical `https://github.com/Kishku7/mod_support/issues`:
  - 7 Fabric cells (1.20.1, 1.20.6, 1.21.1, 1.21.2, 1.21.5, 1.21.9, 1.21.11) -- `contact.issues`
  - 9 Forge cells (1.20.1, 1.20.6, 1.21.1, 1.21.4, 1.21.5, 1.21.7, 1.21.8, 1.21.10, 1.21.11)
    -- top-level `issueTrackerURL`
  - 8 NeoForge cells (1.20.6, 1.21.1, 1.21.2, 1.21.5, 1.21.8, 1.21.9, 1.21.11, 26) -- same
  Every cell was REBUILT so the shipped binaries actually carry it, and all 28 jars were
  verified by reading the manifest back out of the jar (0 without the URL).

### Changed
- Mod-wide version 1.2.4 -> **1.2.5**. Per the versioning rule every rebuilt-and-shipped binary
  gets a bump, and here every cell changed, so this is a full-matrix release rather than the
  usual targeted one.

### Notes
- The Bukkit `plugin.yml` is unchanged: Bukkit has no issue-tracker field, and its `website`
  already points at the mod's repo. The plugin jar therefore keeps its own version line (1.2.0)
  and was not rebuilt for this.

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
