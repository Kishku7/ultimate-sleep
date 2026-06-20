# Ultimate Sleep -- Functional Spec

Authoritative design document for the mod. Kept in-repo (mirrors the convention used by
trident-killers-4-java). Update this as features are locked in.

## 1. Goal

A single, comprehensive, configurable sleep mod for Fabric. The distinct behaviors observed
across ~180 existing Modrinth "Sleep" mods (deduped into 52 features) are catalogued in
`research/sleep_mods_feature_list.md`; that catalogue is the menu we draw the 1.0 feature set from.

## 2. Command surface -- /usleep

All player + admin interaction is namespaced under `/usleep`.

| Command | Perm | Purpose |
|---------|------|---------|
| `/usleep status` | any | Player-facing summary (enabled, required %, current AFK count). |
| `/usleep afk` | any | Toggle your own AFK state. Always available regardless of who owns `/afk`. |
| `/usleep admin query` | 2 | Dump every setting (key, value, type, description) plus the current `/afk` owner. |
| `/usleep admin set <key> <value>` | 2 | Set one setting. Generic key/value while the feature set is in flux. |

Once the feature set is locked, `admin set` can grow per-setting literal subcommands (better
tab-completion) generated from the settings registry.

## 3. AFK tracking (requirement 1)

`AfkManager` keeps a per-player record updated every server tick.

- **Auto-AFK:** no change in position or look direction for `afk_threshold_seconds` -> AFK.
- **Manual AFK:** `/usleep afk` (or our `/afk`, when active) toggles it explicitly.
- Any movement clears both flags. State is rebuilt against online players each tick, so
  logged-off players are dropped.
- Intended use: AFK players can be excluded from the sleep-skip requirement
  (`exclude_afk_from_requirement`).

## 4. The /afk command -- conditional registration (requirement, intro)

On command registration `AfkCommandManager` checks the Brigadier dispatcher for an existing
`afk` literal:

- **Found** -> another mod owns `/afk`. We do **not** register ours. Owner recorded as `EXTERNAL`.
- **Not found** and `provide_afk_command` is true -> we register `/afk` (toggles AFK). Owner = `ULTIMATE_SLEEP`.
- **Not found** and `provide_afk_command` is false -> owner = `NONE`.

`/usleep afk` always works regardless, so AFK is reachable even when we defer `/afk`.

Open issues (tracked in code TODOs):
- Brigadier doesn't expose which mod registered a node, so an external `/afk` is currently
  only reported as `EXTERNAL` (exact mod id = TODO: provider lookup or Fabric mod scan).
- Cross-mod registration order isn't guaranteed; if conflicts appear, move the check to
  `SERVER_STARTED`.

## 5. Admin panel (requirements 2 & 3) -- client, later

A client-side GUI (client entrypoint `UltimateSleepClient`, currently a no-op).

- **On open (requirement 3):** issue `/usleep admin query` first to learn the current settings
  AND which mod owns `/afk`, then render controls from that.
- **On change (requirement 2):** apply each control by sending `/usleep admin set <key> <value>`.

The GUI is deliberately deferred until the full set of settings/features is decided -- the panel
is generated from the settings registry, so the registry is the source of truth.

## 6. Settings (initial skeleton -- expected to grow)

In-memory typed registry (`Settings`). Persistence to a config file is a TODO.

| key | type | default | meaning |
|-----|------|---------|---------|
| `enabled` | bool | true | Master switch. |
| `required_sleep_percentage` | int | 50 | % of eligible players needed to skip the night. |
| `exclude_afk_from_requirement` | bool | true | AFK players don't count toward the requirement. |
| `afk_threshold_seconds` | int | 180 | Idle time before auto-AFK. |
| `provide_afk_command` | bool | true | Register our `/afk` when none exists. |

## 7. Version targeting

Built against MC 26.1.2 (Fabric Loom 1.16, Java 25, mojmap-native). `fabric.mod.json` declares
`minecraft >=26.1 <26.3` so the one jar loads on 26.1.x and 26.2.x. A NeoForge build and any
backports come after 1.0. Watch the 26.2 client API delta (e.g. `Minecraft.setScreen` removed)
when the GUI lands.

## 8. Roadmap

1. [done] Scaffold: /usleep tree, AFK tracking, conditional /afk, settings registry.
2. Pick the 1.0 feature set from `research/sleep_mods_feature_list.md`; expand `Settings`.
3. Implement core sleep logic (night-skip / acceleration / requirement + AFK exclusion).
4. Settings persistence (config file load/save).
5. Build the client admin panel GUI (query -> render -> set).
6. Verify build green + smoketest on a 26.1.2 server; then 26.2 check.
7. 1.0 -> port to other loaders + consider backports.
