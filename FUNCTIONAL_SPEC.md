# Ultimate Sleep -- Functional Spec

Authoritative design document. Kept in-repo (mirrors trident-killers-4-java's convention).
1.0 feature decisions locked with Dave 2026-06-20 (see PROPOSED_1.0.md for the original
proposal + rationale). Update this as the build proceeds.

## 1. Goal

A single, comprehensive, configurable sleep mod for Fabric. Everything is admin-toggleable;
in singleplayer the player is the admin. The distinct behaviors across ~180 Modrinth sleep
mods (deduped into 52 features) are catalogued in `research/sleep_mods_feature_list.md`.

## 2. Command surface -- /usleep

| Command | Perm | Purpose |
|---------|------|---------|
| `/usleep status` | any | Player-facing summary (mode, requirement, AFK count, active vote). |
| `/usleep afk` | any | Toggle your own AFK. Always available regardless of who owns `/afk`. |
| `/usleep auto` | any | Toggle auto-sleep for yourself (see section 9). |
| `/usleep yes` | any | Vote YES in the active sleep vote (VOTE mode). |
| `/usleep no` | any | Vote NO in the active sleep vote (VOTE mode). |
| `/usleep admin query` | op 2 | Dump all settings + `/afk` owner. Panel reads this on open. |
| `/usleep admin set <key> <value>` | op 2 | Change a setting. Panel writes through this. |

Standalone `/afk` is registered conditionally (section 5). Client vote-popup buttons call
`/usleep yes` / `/usleep no`, so client and command paths are identical.

## 3. Night-skip engine (core)

Driven by `requirement_mode`:

### SIMPLE mode (percentage)
- A skip fires when the sleeping fraction of eligible players reaches
  `required_sleep_percentage`. AFK players are excluded when `exclude_afk_from_requirement`.

### VOTE mode
- When the **first** player gets into a bed, a vote auto-starts (no one waits in bed alone).
- Window = `vote_duration_seconds` (default 30).
- Every **non-AFK** online player votes. AFK players get **no vote and no popup**.
  - Command path (works without the client): `/usleep yes` / `/usleep no`.
  - Client path: popup "Do you want to allow other players to sleep?" with Yea / Nay + countdown.
    Buttons call the commands. (The popup ships in the deferred GUI phase; until then VOTE mode works fully via /usleep yes|no.)
- **Getting into a bed during a vote** = auto-YES; that player's popup closes.
- Pass rule = `vote_pass_rule`, admin-selectable:
  - `MAJORITY_CAST` -- simple majority of votes cast (yes > no).
  - `PERCENT_CAST` -- YES reaches `vote_pass_percentage` of the votes cast.
  - `MAJORITY_NON_AFK` -- majority of non-AFK votes (default).

### Skip behavior (admin-set; applies to whichever mode triggered the skip)
- `skip_mode` = `INSTANT` (jump to morning) or `ACCELERATE` (time-lapse via
  `accelerate_multiplier`). It is *how* the triggered skip is carried out.
- `preserve_weather` -- keep rain/storms across the skip.

## 4. Rewards (admin picks; each independent) -- 1.0

Granted on a successful sleep/skip. Three independent admin toggles, usable in any combination:
- `reward_regeneration` -- Regeneration for `reward_regeneration_minutes` (default 5).
- `reward_golden_carrot` -- give one golden carrot; **drops at the player's feet if the
  inventory is full** (never lost).
- `reward_speed_boost` -- +`reward_speed_boost_percent`% movement speed (default 25) for
  `reward_speed_boost_minutes` (default 5).

## 5. AFK tracking + conditional /afk (scaffolded)

`AfkManager`: auto-AFK after `afk_threshold_seconds` idle (no move/look), plus manual toggle;
movement clears both. Feeds `exclude_afk_from_requirement` and the vote eligibility (AFK = no
vote/no popup).

`AfkCommandManager`: at command registration, if another mod already provides `/afk` we stand
down and report the owner via `admin query` (exact owning mod id = TODO; currently "external").
Else, if `provide_afk_command`, we register `/afk` as a Brigadier redirect alias to the `/usleep afk` node (identical behavior, single source of truth). `/usleep afk` always works regardless.

## 6. Sleeper visibility (feedback)

- `show_sleepers_in_chat` -- chat/broadcast updates (also the no-client path).
- `show_sleepers_on_vote_screen` -- live sleeper + tally list on the client vote popup.
- `notify_wake` -- morning/wake broadcast.

## 7. World progression while sleeping (admin master + categories) -- 1.0

Master `world_progression_enabled`, then per-category sub-toggles so admins scope the cost:
- `progress_crops` -- crops AND plant/tree growth: saplings/tree growth, bamboo, sugar
  cane/cactus, plus orphan-leaf decay (leaves decaying as they would after a tree is cut).
- `progress_animal_husbandry` -- breeding cooldowns + baby-animal growth.
- `progress_smelting` -- furnaces / smokers / blast furnaces.
- `progress_despawn_timers` -- item/entity despawn timers advance.

Somnia-style; perf-sensitive, hence the per-category scoping.

## 8. Admin panel (req 2 & 3) -- client, GUI phase

Client GUI generated from the settings registry. On open: `/usleep admin query` (read settings
+ `/afk` owner) -> render controls grouped by category -> each change sends
`/usleep admin set <key> <value>`. Deferred: NO GUI until every feature works via /usleep commands and the setting set is final (Dave 2026-06-20). Until then, the commands ARE the admin panel.

## 9. Auto-sleep (default ON) -- 1.0

Players opt in with `/usleep auto`. While opted in, at the vanilla earliest-sleepable time
(dusk, ~tick 12542), the **server** injects a sleep request for them -- exactly as if they had
right-clicked the bed -- provided they meet the conditions:
- **(a)** within vanilla reach of their home bed (their spawn bed), OR
- **(b)** carrying a sleeping bag (Travelers' Backpack soft-dependency: in inventory or in
  the backpack), which lets them sleep where they are.

If neither is met, they get a text notice explaining why:
- "Not near your bed."
- with Travelers' Backpack present: "Not near your bed, and you don't seem to have a sleeping
  bag with you.."

`auto_sleep_enabled` (server, **default true**) lets admins disable the whole feature; the
per-player opt-in state is saved per player. Travelers' Backpack integration is a soft-dep
(detected at runtime; the sleeping-bag path is simply unavailable if the mod is absent).

## 10. Settings registry (target for 1.0)

Typed registry; the scaffold ships BOOL/INT and a representative subset. 1.0 adds ENUM (and
likely STRING) types + config-file persistence (both TODO). Groups: core, engine, rewards,
feedback, world-progression, auto-sleep, afk.

| key | type | default | group |
|-----|------|---------|-------|
| enabled | bool | true | core |
| requirement_mode | enum(SIMPLE,VOTE) | SIMPLE | engine |
| required_sleep_percentage | int | 50 | engine |
| exclude_afk_from_requirement | bool | true | engine |
| vote_duration_seconds | int | 30 | engine |
| vote_pass_rule | enum(MAJORITY_CAST,PERCENT_CAST,MAJORITY_NON_AFK) | MAJORITY_NON_AFK | engine |
| vote_pass_percentage | int | 50 | engine |
| skip_mode | enum(INSTANT,ACCELERATE) | INSTANT | engine |
| accelerate_multiplier | int | 60 | engine |
| preserve_weather | bool | false | engine |
| sleep_anytime | bool | false | accessibility |
| sleep_ignore_monsters | bool | false | accessibility |
| ignore_bed_too_far | bool | false | accessibility |
| highlight_blocking_mobs | bool | false | accessibility |
| show_sleepers_in_chat | bool | true | feedback |
| show_sleepers_on_vote_screen | bool | true | feedback |
| notify_wake | bool | true | feedback |
| reward_regeneration | bool | false | rewards |
| reward_regeneration_minutes | int | 5 | rewards |
| reward_golden_carrot | bool | false | rewards |
| reward_speed_boost | bool | false | rewards |
| reward_speed_boost_percent | int | 25 | rewards |
| reward_speed_boost_minutes | int | 5 | rewards |
| world_progression_enabled | bool | false | world-progression |
| progress_crops | bool | true | world-progression |
| progress_animal_husbandry | bool | true | world-progression |
| progress_smelting | bool | true | world-progression |
| progress_despawn_timers | bool | false | world-progression |
| auto_sleep_enabled | bool | true | auto-sleep |
| afk_threshold_seconds | int | 180 | afk |
| provide_afk_command | bool | true | afk |

(Per-player state -- AFK status, auto-sleep opt-in, home bed -- is runtime/player data, not in
the global settings table.)

## 11. Version targeting

Built against MC 26.1.2 (Fabric Loom 1.16, Java 25, mojmap-native). `fabric.mod.json` declares
`minecraft >=26.1 <26.3` so one jar loads on 26.1.x and 26.2.x. NeoForge + backports after 1.0. Versioning: development runs as a 0.x series, commands-first; the client GUI + vote-popup is the LAST phase before 1.0 -- no GUI until every feature works via /usleep commands.
26.x command API in use: `src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)`,
`src.getPlayer()`, `sendSystemMessage`. Watch the 26.2 client API delta (e.g.
`Minecraft.setScreen` removed) when the GUI/vote-popup lands.

## 12. Roadmap / TODO

1. [done] Scaffold: /usleep tree, AFK tracking, conditional /afk, settings registry, green build.
2. Settings: add ENUM/STRING types + config-file persistence; expand to the section-10 set.
3. Night-skip engine: SIMPLE path (percentage + AFK exclusion) + skip_mode + preserve_weather.
4. VOTE mode: server vote state machine (auto-start on first sleeper, 30s, bed=auto-yes,
   AFK = no vote/no popup, vote_pass_rule) + `/usleep yes|no`.
5. Networking: clientbound start/update/end vote payloads; serverbound via the commands.
6. Accessibility toggles (sleep anytime, ignore monsters, ignore bed-too-far, highlight mobs).
7. Sleeper visibility (chat + vote-screen list) + wake broadcast.
8. Rewards module: regeneration (5 min), golden carrot (drop-if-full), +25% speed -- each an
   independent admin toggle.
9. World progression: master + categories (crops incl. trees/bamboo/leaf-decay, animal
   husbandry, smelting, despawn timers).
10. Auto-sleep: per-player opt-in (`/usleep auto`), dusk auto-bed-use via server-injected
    sleep, home-bed proximity OR Travelers' Backpack sleeping-bag check, miss notifications,
    default ON. (soft-dep: Travelers' Backpack.)
11. (LAST phase before 1.0) Client admin panel GUI (query -> render -> set) + the vote popup -- only after every feature above works via /usleep commands.
12. Verify build green + smoketest on a 26.1.2 server; then 26.2 check. -> 1.0.
13. Post-1.0: other loaders, backports, deferred "flavor" track (nightmares, dreams, horror
    entity, deprivation, XP/gifts, sounds, etc. -- see PROPOSED_1.0.md).

## Open decisions (small)

- Auto-sleep dusk time: use the vanilla earliest-sleepable tick (~12542), or expose it as a
  configurable setting?
- Speed-boost reward duration default 5 min (matching regen) ok? Regen amplifier = Regen I?
- Crops category: keep sugar cane / cactus growth included (currently yes)?
