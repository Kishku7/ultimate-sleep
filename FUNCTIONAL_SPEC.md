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
| `/usleep yes` | any | Vote YES in the active sleep vote (VOTE mode). |
| `/usleep no` | any | Vote NO in the active sleep vote (VOTE mode). |
| `/usleep admin query` | op 2 | Dump all settings + `/afk` owner. Panel reads this on open. |
| `/usleep admin set <key> <value>` | op 2 | Change a setting. Panel writes through this. |

Standalone `/afk` is registered conditionally (see section 4). The client vote GUI buttons
call `/usleep yes` / `/usleep no`, so client and command paths are identical.

## 3. Night-skip engine (core)

Driven by `requirement_mode`:

### SIMPLE mode (percentage)
- A skip fires when the sleeping fraction of eligible players reaches
  `required_sleep_percentage`. AFK players are excluded when `exclude_afk_from_requirement`.

### VOTE mode
- When the **first** player gets into a bed, a vote auto-starts (no one waits in bed alone).
- Window = `vote_duration_seconds` (default 30).
- Every online player votes:
  - Command path (works without the client): `/usleep yes` / `/usleep no`.
  - Client path: a popup "Do you want to allow other players to sleep?" with Yea / Nay and a
    countdown. Buttons call the same commands.
- **Getting into a bed during a vote** = auto-YES; that player's popup closes.
- Outcome: if YES reaches `vote_pass_percentage` of eligible voters, the skip fires; otherwise
  the vote fails and the night continues. AFK players are excluded from the eligible set.

### Skip behavior (admin-set; applies to whichever mode triggered the skip)
- `skip_mode` = `INSTANT` (jump to morning) or `ACCELERATE` (time-lapse via
  `accelerate_multiplier`). Tied to the requirement outcome -- it is *how* the triggered skip
  is carried out.
- `preserve_weather` -- keep rain/storms across the skip.

## 4. AFK tracking + conditional /afk (scaffolded)

`AfkManager`: auto-AFK after `afk_threshold_seconds` idle (no move/look), plus manual toggle;
movement clears both. Feeds `exclude_afk_from_requirement` and the vote's eligible-voter set.

`AfkCommandManager`: at command registration, if another mod already provides `/afk` we stand
down and report the owner via `admin query` (exact owning mod id = TODO; currently "external").
Else, if `provide_afk_command`, we register `/afk`. `/usleep afk` always works regardless.

## 5. Sleeper visibility (feedback)

Players can always see who is sleeping / how the count or vote is going:
- `show_sleepers_in_chat` -- chat/broadcast updates (also the no-client path).
- `show_sleepers_on_vote_screen` -- live sleeper + tally list on the client vote popup.
- `notify_wake` -- morning/wake broadcast.

## 6. Rewards (admin picks) -- 1.0 module, design pending

A reward is granted on a successful sleep/skip. Admin enables the module and selects which
rewards apply. Starter menu (to confirm): heal, clear negative effects, short buff(s),
food/saturation top-up. (See open decisions + roadmap.)

## 7. World progression while sleeping (admin toggle + categories) -- 1.0

Master `world_progression_enabled`, then per-category sub-toggles so admins pick what advances
during a skip. Proposed categories (to confirm): crops, animal husbandry (breeding + baby
growth), smelting (furnaces/smokers/blast), despawn timers. Perf-sensitive -- categories let
admins scope the cost. (Somnia-style; 9 mods in the survey.)

## 8. Admin panel (req 2 & 3) -- client, GUI phase

Client GUI generated from the settings registry. On open: `/usleep admin query` (read settings
+ `/afk` owner) -> render controls grouped by category -> each change sends
`/usleep admin set <key> <value>`. Deferred until the setting set is final (it is now close).

## 9. Settings registry (target for 1.0)

Typed registry; the scaffold ships BOOL/INT and a representative subset. 1.0 adds ENUM (and
likely STRING) types + config-file persistence (both TODO). Groups: core, engine, accessibility,
feedback, rewards, world-progression, afk.

| key | type | default | group |
|-----|------|---------|-------|
| enabled | bool | true | core |
| requirement_mode | enum(SIMPLE,VOTE) | SIMPLE | engine |
| required_sleep_percentage | int | 50 | engine |
| exclude_afk_from_requirement | bool | true | engine |
| vote_duration_seconds | int | 30 | engine |
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
| rewards_enabled | bool | false | rewards |
| reward_heal | bool | true | rewards |
| reward_heal_half_hearts | int | 4 | rewards |
| reward_clear_negative_effects | bool | false | rewards |
| reward_buffs | bool | false | rewards |
| reward_food | bool | false | rewards |
| world_progression_enabled | bool | false | world-progression |
| progress_crops | bool | true | world-progression |
| progress_animal_husbandry | bool | true | world-progression |
| progress_smelting | bool | true | world-progression |
| progress_despawn_timers | bool | false | world-progression |
| afk_threshold_seconds | int | 180 | afk |
| provide_afk_command | bool | true | afk |

## 10. Version targeting

Built against MC 26.1.2 (Fabric Loom 1.16, Java 25, mojmap-native). `fabric.mod.json` declares
`minecraft >=26.1 <26.3` so one jar loads on 26.1.x and 26.2.x. NeoForge + backports after 1.0.
26.x command API in use: `src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)`,
`src.getPlayer()`, `sendSystemMessage`. Watch the 26.2 client API delta (e.g.
`Minecraft.setScreen` removed) when the GUI/vote-popup lands.

## 11. Roadmap / TODO

1. [done] Scaffold: /usleep tree, AFK tracking, conditional /afk, settings registry, green build.
2. Settings: add ENUM/STRING types + config-file persistence; expand to the section-9 set.
3. Night-skip engine: SIMPLE (percentage) path with AFK exclusion + skip_mode (INSTANT/ACCELERATE)
   + preserve_weather.
4. VOTE mode: server vote state machine (auto-start on first sleeper, 30s window, bed=auto-yes,
   eligible-voter set excludes AFK) + `/usleep yes|no`.
5. Networking: clientbound start/update/end vote payloads; serverbound handled via the commands.
6. Accessibility toggles (sleep anytime, ignore monsters, ignore bed-too-far, highlight mobs).
7. Sleeper visibility (chat + vote-screen list) + wake broadcast.
8. **Rewards module** (admin-selectable: heal / clear effects / buffs / food) -- ADDED per Dave 2026-06-20.
9. **World progression while sleeping** (master + category toggles: crops / animal husbandry /
   smelting / despawn timers) -- ADDED per Dave 2026-06-20.
10. Client admin panel GUI (query -> render -> set) + the vote popup.
11. Verify build green + smoketest on a 26.1.2 server; then 26.2 check. -> 1.0.
12. Post-1.0: other loaders, backports, and the deferred "flavor" track (nightmares, dreams,
    horror entity, deprivation, XP/gifts, sounds, etc. -- see PROPOSED_1.0.md).

## Open decisions (small)

- Rewards menu: confirm the 1.0 reward set (heal, clear-negative-effects, buff(s), food). Which
  buffs, if any, for 1.0?
- World-progression categories: confirm the 1.0 list (crops, animal husbandry, smelting, despawn
  timers) -- add/remove any?
- Vote pass rule: simple majority of votes cast, or `vote_pass_percentage` of eligible players
  (current draft)?
