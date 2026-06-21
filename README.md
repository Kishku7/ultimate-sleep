# Ultimate Sleep

An advanced, all-in-one, fully admin-configurable Minecraft sleep mod for Fabric. It folds the
useful behaviors from ~180 surveyed Modrinth sleep mods (deduped to 52 distinct features) into a
single mod where everything is a toggle. In singleplayer the player is the admin; on a server the
controls are permission-gated. The mod is required on the **server only** -- it works for vanilla
clients via chat/commands, and adds an optional in-game panel for clients that also have it.

- **Loader:** Fabric only (until 1.0; other loaders + backports considered afterward).
- **Minecraft:** built against 26.1.2, declared compatible with `26.1.x` and `26.2.x`
  (`fabric.mod.json` minecraft range `>=26.1 <26.3`). A dedicated NeoForge build is post-1.0.
- **Author:** Kishku7 · **License:** ARR · single source of truth for design: `FUNCTIONAL_SPEC.md`.

Status legend: **[Have]** working today · **[Partial]** partly working / refinement pending ·
**[Planned]** designed, not built yet.

---

## Sleep features

### Night-skip engine

The core. One setting, `requirement_mode`, picks how a night gets skipped.

- **[Have] SIMPLE mode (percentage).** A skip fires when enough eligible players are asleep.
  The threshold is `required_sleep_percentage` (e.g. 50% of 4 players online = 2 needed). No
  voting -- just sleep and the night passes once the count is met.
- **[Have] VOTE mode.** The first player to climb into a bed auto-starts a server-wide vote (so
  nobody waits in bed alone). Everyone non-AFK votes during a countdown window; the result is
  tallied by the chosen pass rule and the night skips on a pass.
  - Vote via `/usleep yes` / `/usleep no` (works on any client), or with the on-screen popup.
  - Getting into a bed mid-vote counts as an automatic YES.
  - Pass rules (`vote_pass_rule`): **MAJORITY_CAST** (yes > no), **PERCENT_CAST** (yes reaches
    `vote_pass_percentage` of votes cast), or **MAJORITY_NON_AFK** (majority of all non-AFK
    players -- the default).
- **[Have] Mod owns every skip.** The vanilla `playersSleepingPercentage` gamerule is pinned to
  101 so the game can *never* skip the night on its own -- even if every player piles into bed.
  The night advances only when Ultimate Sleep decides it should, which keeps behavior
  predictable and debuggable.

### Skip behavior (how the triggered skip is carried out)

- **[Have] INSTANT.** Jump straight to morning (vanilla-style).
- **[Have] ACCELERATE.** Time-lapse the night instead of jumping, at `accelerate_multiplier`
  speed.
- **[Have] Preserve weather.** Optionally keep rain/storms running across the skip instead of
  clearing them.

### Auto-sleep

- **[Have] Per-player opt-in** with `/usleep auto`; the choice is saved per player. Default
  feature state is ON (`auto_sleep_enabled`), admin-disableable.
- **[Have] Dusk auto-bed.** At the earliest sleepable time, the server puts opted-in players to
  bed automatically if they're within reach of a bed -- exactly as if they'd right-clicked it.
- **[Have] Missed-sleep notice** when no usable bed is in reach.
- **[Partial] Home-bed targeting + Travelers' Backpack sleeping bag.** Planned: prefer the
  player's actual spawn bed, and support sleeping in place via a Travelers' Backpack sleeping
  bag (soft dependency). Currently uses the nearest reachable bed and does not yet invoke the
  backpack sleeping bag.

### AFK system

- **[Have] Automatic AFK** after `afk_threshold_seconds` idle (no move/look), plus a manual
  `/usleep afk` toggle. Any non-bed movement clears AFK; lying in bed does not.
- **[Have] Admin force-AFK** a player with `/usleep admin afk <player>`.
- **[Have] Conditional `/afk` alias.** If no other mod already provides `/afk`, Ultimate Sleep
  registers `/afk` as a direct alias of `/usleep afk`. If another mod owns it, we stand down.
- **[Have] Feeds the engine.** AFK players can be excluded from the sleep requirement
  (`exclude_afk_from_requirement`) and get no vote and no vote popup.

### Rewards on waking

Granted on a successful sleep/skip; each is an independent admin toggle, usable in any combo.

- **[Have] Regeneration** for `reward_regeneration_minutes` (default 5).
- **[Have] Golden carrot** -- one per wake; **drops at your feet if your inventory is full** so
  it's never lost.
- **[Have] Speed boost** of `reward_speed_boost_percent`% (default 25) for
  `reward_speed_boost_minutes` (default 5).

### Accessibility / sleep-rule overrides

- **[Have] Sleep anytime** -- bypass the day/time restriction on using a bed.
- **[Have] Ignore monsters** -- sleep even with hostile mobs nearby.
- **[Have] Ignore "bed too far"** -- skip the distance check when entering a bed.
- **[Partial] Highlight blocking mobs** -- glow the monsters that would stop you sleeping.
  Server-side glow is in; per-viewer-only refinement is pending.

### World progression while sleeping

Skipping the night can optionally advance the world, not just the clock. A master toggle
(`world_progression_enabled`) plus per-category sub-toggles so admins can scope the performance
cost.

- **[Have] Crops & plants** -- crop growth, saplings/tree growth, bamboo, sugar cane/cactus, and
  orphaned-leaf decay.
- **[Have] Animal husbandry** -- breeding cooldowns and baby-animal growth.
- **[Have] Smelting** -- furnaces, smokers, blast furnaces continue.
- **[Have] Despawn timers** -- item/entity despawn timers advance (off by default).

### Sleeper visibility & feedback

- **[Have] Sleep status in chat** (`show_sleepers_in_chat`) -- a concise broadcast as players
  start/stop sleeping, e.g. `<name> is sleeping, 1 of 2 required. Need 1 more.` This also serves
  vanilla clients with no UI. The mod is the sole voice -- vanilla's own "x/y" message is
  suppressed to avoid double/confusing counts.
- **[Have] Vote popup** -- a non-blocking bottom-of-screen bar with the question and Yes/No
  buttons plus a private "your vote carried / you were outvoted" result.
- **[Partial] Live sleeper list on the vote screen** (`show_sleepers_on_vote_screen`).
- **[Have] Wake broadcast** (`notify_wake`).

### In-game admin panel (optional client UI)

- **[Have] Server-driven panel** opened with `/usleep gui`. The server pushes the open request
  and the current config to a client that has the mod; the client renders a paginated control
  panel and writes changes back over a private back-channel -- all permission-checked
  server-side. Clients without the mod simply use the commands instead.
- **[Have] Themed UI** matching the dark "Claude Design" mockup: custom-rendered buttons,
  green/grey on-off toggles, yellow cycle selectors, red destructive actions, green/red vote
  buttons, and a scrollable sleep-admin roster page.
- **[Planned] Polish** -- edit-field theming and final pixel matching.

### Flexible settings input

- **[Have] Forgiving values.** Booleans accept `true/yes/1/on` and `false/no/0/off`
  (case-insensitive); percentages accept `50`, `50%`, `1/2`, or `0.5`. Every `set` confirms the
  resolved value back to you.
- **[Have] Tab completion** for setting keys and values.

---

## Commands (`/usleep`)

| command | who | purpose |
|---------|-----|---------|
| `/usleep status` | everyone | short status line (mode, requirement, AFK count, active vote) |
| `/usleep query` | everyone | full settings dump + who owns `/afk` |
| `/usleep afk` | everyone | toggle your own AFK (`/afk` alias when available) |
| `/usleep auto` | everyone | toggle your auto-sleep opt-in |
| `/usleep yes` / `/usleep no` | everyone | vote in the active sleep vote |
| `/usleep gui` | everyone | open the in-game panel (modded clients) |
| `/usleep set <key> <value>` | sleep-admin / op 2 | everyday settings |
| `/usleep admin set <key> <value>` | op 4 / sleep-admin | full settings authority |
| `/usleep admin afk <player>` | op 4 / sleep-admin | force a player AFK |
| `/usleep admin admins add\|remove\|list` | op 4 / sleep-admin | manage the sleep-admin roster |

A designated **sleep-admin** is treated as op-4 for all Ultimate Sleep commands, so owners can
delegate sleep config without handing out vanilla operator.

---

## Settings reference

Grouped as core / engine / rewards / feedback / world-progression / auto-sleep / afk /
accessibility. Persisted to JSON.

| key | type | default |
|-----|------|---------|
| enabled | bool | true |
| requirement_mode | SIMPLE \| VOTE | SIMPLE |
| required_sleep_percentage | int | 50 |
| exclude_afk_from_requirement | bool | true |
| vote_duration_seconds | int | 30 |
| vote_pass_rule | MAJORITY_CAST \| PERCENT_CAST \| MAJORITY_NON_AFK | MAJORITY_NON_AFK |
| vote_pass_percentage | int | 50 |
| skip_mode | INSTANT \| ACCELERATE | INSTANT |
| accelerate_multiplier | int | 60 |
| preserve_weather | bool | false |
| sleep_anytime | bool | false |
| sleep_ignore_monsters | bool | false |
| ignore_bed_too_far | bool | false |
| highlight_blocking_mobs | bool | false |
| show_sleepers_in_chat | bool | true |
| show_sleepers_on_vote_screen | bool | true |
| notify_wake | bool | true |
| reward_regeneration | bool | false |
| reward_regeneration_minutes | int | 5 |
| reward_golden_carrot | bool | false |
| reward_speed_boost | bool | false |
| reward_speed_boost_percent | int | 25 |
| reward_speed_boost_minutes | int | 5 |
| world_progression_enabled | bool | false |
| progress_crops | bool | true |
| progress_animal_husbandry | bool | true |
| progress_smelting | bool | true |
| progress_despawn_timers | bool | false |
| auto_sleep_enabled | bool | true |
| afk_threshold_seconds | int | 180 |
| provide_afk_command | bool | true |

Per-player state (AFK status, auto-sleep opt-in, home bed) is runtime player data, not in the
global settings table.

---

## Roadmap to 1.0

Done: settings registry + persistence, permission tiers + sleep-admin roster, SIMPLE & VOTE
engines, mod-owned skip, INSTANT/ACCELERATE, preserve-weather, accessibility toggles, rewards,
world progression, AFK system + `/afk` alias, auto-sleep (dusk auto-bed), client panel + vote
popup + theming.

Remaining before 1.0: Travelers' Backpack sleeping-bag + home-bed auto-sleep path, per-viewer
mob highlight, vote-screen live sleeper list, GUI edit-field theming, then a 26.2 verification
pass.

Post-1.0: other loaders / backports, and a deferred "flavor" track (nightmares, dreams, sleep
deprivation, sounds, etc.).

---

## Build

```
cd fabric/26.1.2
./gradlew build      # Java 25, Fabric Loom 1.16, Gradle 9.4.1
```

Output jar: `fabric/26.1.2/build/libs/ultimate-sleep-<version>.jar`.

## Layout

```
ultimate-sleep/
  README.md            this file
  FUNCTIONAL_SPEC.md   authoritative design/spec
  research/            Modrinth sleep-mod feature survey (input for the feature set)
  fabric/26.1.2/       the Fabric loom project (build here)
```
