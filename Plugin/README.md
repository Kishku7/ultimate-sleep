# Ultimate Sleep - Plugin (Bukkit / Paper / Folia)

The Ultimate Sleep **plugin** is the Bukkit-family build: a Bukkit-native reimplementation of the
mod's server-side feature set (no mixins, no NMS). It follows the ChunkSmith plugin pattern:
THREE per-major-line standalone cells, each shipping ONE jar that covers Paper, Spigot, and Folia
for its line via the runtime `Platform` facade.

| Cell | Jar | api-version | Compiled against | Java |
|------|-----|-------------|------------------|------|
| `1.20.x/` | `ultimate-sleep-<ver>+1.20.x-plugin.jar` | 1.20 | folia-api 1.20.6-R0.1-SNAPSHOT | JDK 21, `--release 17` (loads on the Java-17 1.20.1-1.20.4 sub-line) |
| `1.21.x/` | `ultimate-sleep-<ver>+1.21.x-plugin.jar` | 1.21 | folia-api 1.21.11-R0.1-SNAPSHOT | JDK 21, `--release 21` |
| `26.x/`   | `ultimate-sleep-<ver>+26.x-plugin.jar`   | 26.1 | folia-api 26.1.2.build.8-stable | JDK 25, `--release 21` |

## Layout

- `shared_plugin/bukkit/` - ALL plugin logic (engine, vote, AFK, auto-sleep, rewards,
  progression, settings, commands, listeners) + `plugin.yml` / `config.yml`.
- `shared_plugin/platform/` - the Paper/Spigot-vs-Folia scheduler facade. Folia-only API is
  referenced ONLY in `Folia.java`, which is never class-loaded elsewhere, so one jar also loads
  on plain Spigot.
- `1.20.x/`, `1.21.x/`, `26.x/` - thin standalone gradle cells that srcDir the shared source.
  Unlike ChunkSmith there is no `shared_common` include: the mod's engine lives in
  `shared_minecraft` (MC-coupled), so the plugin re-implements the behavior against the Bukkit
  API instead of sharing code. The mod's documented sleep behavior is the shared authority the plugin mirrors.

Build: `pwsh scripts/build-plugin.ps1 [-Only 1.20.x]` from the repo root -> `dist/`.

## How the plugin owns the night skip

The mod redirects the vanilla sleep check with a mixin. The plugin instead pins the
`playersSleepingPercentage` gamerule to 101 on every normal-environment world at enable (original
values restored on disable), so vanilla never self-skips and every skip decision (SIMPLE
percentage or VOTE) goes through the plugin's engine:

- **INSTANT** - jump the world's full time to the next dawn, wake sleepers, clear weather unless
  `preserve_weather`.
- **ACCELERATE** - step the clock every tick (global region scheduler on Folia) so the night
  passes in SLOW/SLOWISH/QUICK/FAST = 10/7.5/5/2.5 wall-clock seconds, then wake.

## Feature parity vs the mod

### Kept (full behavior parity via the Bukkit API)

- SIMPLE percentage mode incl. AFK exclusion, per-sleeper chat progress, deep-sleep gate
  (`getSleepTicks() >= 100` = vanilla `isSleepingLongEnough`).
- VOTE mode 1:1: auto-start on first sleeper, bed = auto-YES, non-blocking action-bar prompt
  (Spigot-compatible bungee-chat path), `/usleep yes|no`, early finish, all three pass rules,
  failed-vote per-player lockout until dawn, unanimous shortcut.
- INSTANT + ACCELERATE skip modes, `preserve_weather`, one-shot `notify_wake` morning broadcast.
- AFK tracking (event-driven movement/look/interaction timestamps, auto-threshold, manual
  `/usleep afk`, `/usleep admin afk <player>`, transition notifications; bed activity never
  cancels AFK) + `exclude_afk_from_requirement` + AFK players get no vote.
- Rewards on the morning edge: Regeneration, golden carrot (drops at feet if inventory full),
  Speed (percent mapped to the nearest vanilla effect level, same as the mod).
- Auto-sleep opt-in (`/usleep auto`, persisted): at dusk, scans the vanilla bed-reach box and
  injects sleep via `HumanEntity.sleep(location, false)` - the same checks as a right-click -
  with a text notice when no bed is reachable.
- Accessibility: `sleep_anytime`, `sleep_ignore_monsters`, `ignore_bed_too_far` via
  `PlayerBedEnterEvent` result overrides (`setUseBed(ALLOW)` on NOT_POSSIBLE_NOW / NOT_SAFE /
  TOO_FAR_AWAY).
- Settings registry: same keys/defaults/flexible parsing (50, 50%, 1/2, 0.5), persisted in
  `config.yml`, live via `/usleep set` / `/usleep admin set`, dump via `/usleep query`.
- Permission tiers: `ultimatesleep.set` / `ultimatesleep.admin` Bukkit nodes (default op) +
  the persisted sleep-admin roster (`/usleep admin admins add|remove|list`); console always
  passes.
- World progression - crops: a bounded `randomTickSpeed` boost equivalent to the skipped night's
  expected random ticks (covers crops, saplings, bamboo, cane/cactus, leaf decay).
- World progression - animal husbandry: `Ageable` age arithmetic (baby growth + breeding
  cooldowns). **Not applied on Folia** (cross-region entity iteration is not permitted).

### Dropped (cannot be done honestly from the Bukkit API), with reasons

- `progress_smelting` (furnace/smoker/blast-furnace fast-forward) and
  `progress_despawn_timers` - both live in block-entity/entity tick internals the Bukkit API
  does not expose; the mod does them with mixins. The keys are REMOVED from the plugin's
  registry rather than silently no-op.
- `highlight_blocking_mobs` - per-player client-side outline rendering; a server plugin cannot
  render on the client (the mod defers this to its client GUI phase too).
- Travelers' Backpack sleeping-bag auto-sleep path - Travelers' Backpack is a Fabric/NeoForge
  mod and cannot be present on a Paper server. Auto-sleep's "home bed preferred" nuance is also
  simplified to "nearest reachable bed" (inside vanilla bed reach they coincide in practice).
- Client GUI + vote popup - the plugin line is server-only by design; `/usleep` does everything
  (this matches the mod's no-client command path, which is fully supported).
- `/afk` stand-down detection - Bukkit's native command collision handling replaces it: if
  another plugin registers `/afk` first, ours remains reachable as `/ultimatesleep:afk`;
  `provide_afk_command=false` turns the alias into a pointer at `/usleep afk`.
