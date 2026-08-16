# Ultimate Sleep

The advanced, all-in-one, fully admin-configurable sleep mod. It folds the useful
behaviors from ~180 surveyed Modrinth sleep mods (deduped to 52 distinct features) into a
single mod where everything is a toggle. In singleplayer the player is the admin; on a
server the controls are permission-gated. Ships for **Fabric, NeoForge, and Forge across
Minecraft 1.20 through 26.x**, plus a native **Paper/Folia server plugin** for each major line. The mod is required on the **server only** -- it works for
vanilla clients via chat/commands, and adds an optional in-game panel for clients that
also have it.

## Why Ultimate Sleep

- **One mod instead of five.** Night-skip, sleep voting, auto-sleep, AFK handling, wake
  rewards, sleep-rule overrides, and world progression -- every behavior you would
  normally stack separate sleep mods for, in one place, each one a toggle.
- **Predictable nights.** The mod owns every skip; vanilla can never skip the night on
  its own, so the behavior you configure is the behavior you get.
- **Zero client requirement.** Install it on the server and every player is fully served
  -- vanilla clients get everything through chat and `/usleep` commands.
- **Optional GUI for those who want it.** Players who also install the mod client-side
  get an in-game control panel via `/usleep gui`.
- **Delegate without op.** A designated sleep-admin can configure everything with no
  operator level.

## Download

Get Ultimate Sleep on Modrinth: **https://modrinth.com/mod/ultimate-sleep**

---

## Sleep features

### Night-skip engine

The core. One setting, `requirement_mode`, picks how a night gets skipped.

- **SIMPLE mode (percentage).** A skip fires when enough eligible players are asleep. The
  threshold is `required_sleep_percentage` (e.g. 50% of 4 players online = 2 needed). No voting
  -- just sleep and the night passes once the count is met.
- **VOTE mode.** The first player to climb into a bed auto-starts a server-wide vote (so nobody
  waits in bed alone). Everyone non-AFK votes during a countdown window; the result is tallied by
  the chosen pass rule and the night skips on a pass.
  - Vote via `/usleep yes` / `/usleep no` (works on any client), or with the on-screen prompt.
  - Getting into a bed mid-vote counts as an automatic YES.
  - Pass rules (`vote_pass_rule`): **MAJORITY_CAST** (yes > no), **PERCENT_CAST** (yes reaches
    `vote_pass_percentage` of votes cast), or **MAJORITY_NON_AFK** (majority of all non-AFK
    players -- the default).
- **The mod owns every skip.** A server-side gate stops vanilla's own sleep check so the game
  can *never* skip the night on its own -- even if every player piles into bed. The night
  advances only when Ultimate Sleep decides it should, which keeps behavior predictable. The mod
  is also the sole voice -- vanilla's own "x/y sleeping" message is suppressed.

### Skip behavior (how the triggered skip is carried out)

- **INSTANT.** Jump straight to morning (vanilla-style).
- **ACCELERATE.** Time-lapse the night instead of jumping, by driving the overworld clock rate,
  at one of four named speeds (Slow / Slowish / Quick / Fast = ~10 / 7.5 / 5 / 2.5 real seconds).
- **Preserve weather.** Optionally keep rain/storms running across the skip instead of clearing
  them.

### Auto-sleep

- **Per-player opt-in** with `/usleep auto`; the choice is saved per player. The feature is
  admin-gated by `auto_sleep_enabled` (on by default).
- **Dusk auto-bed.** At the earliest sleepable time, the server puts opted-in players to bed
  automatically if a bed is within reach -- exactly as if they'd right-clicked it.
- **Home-bed targeting.** Prefers your own spawn bed when it is reachable, otherwise the nearest
  reachable bed.
- **Travelers' Backpack sleeping bag** (soft dependency -- runtime-detected, no compile
  dependency). When no bed is reachable, auto-sleep can sleep in place using a loose sleeping-bag
  item or a bag attached to a worn Travelers' Backpack; the placed bag is removed on wake.
- **Missed-sleep notice** when neither a bed nor a sleeping bag is in reach.

### AFK system

- **Automatic AFK** after `afk_threshold_seconds` idle (no move/look), plus a manual `/usleep afk`
  toggle. Any non-bed movement clears AFK; lying in bed does not.
- **Admin force-AFK** a player with `/usleep admin afk <player>`.
- **Conditional `/afk` alias.** If no other mod already provides `/afk`, Ultimate Sleep registers
  `/afk` as a direct alias of `/usleep afk`. If another mod owns it, we stand down.
- **Feeds the engine.** AFK players can be excluded from the sleep requirement
  (`exclude_afk_from_requirement`) and get no vote and no vote prompt.

### Rewards on waking

Granted on a successful sleep/skip; each is an independent admin toggle, usable in any combo.

- **Regeneration** for `reward_regeneration_minutes` (default 5).
- **Golden carrot** -- one per wake; **drops at your feet if your inventory is full** so it's
  never lost.
- **Speed boost** of `reward_speed_boost_percent`% (default 25) for `reward_speed_boost_minutes`
  (default 5). Applied via vanilla Speed levels, so the percentage is approximate.

### Accessibility / sleep-rule overrides

- **Sleep anytime** -- bypass the day/time restriction on using a bed.
- **Ignore monsters** -- sleep even with hostile mobs nearby.
- **Ignore "bed too far"** -- skip the distance check when entering a bed.
- **Highlight blocking mobs** -- outline the monsters that would stop you sleeping, visible only
  to you (a per-viewer glow with a timed revert).

### World progression while sleeping

Skipping the night can optionally advance the world, not just the clock. A master toggle
(`world_progression_enabled`) plus per-category sub-toggles so admins can scope the performance
cost.

- **Crops & plants** -- crop growth, saplings/tree growth, and plant growth.
- **Animal husbandry** -- breeding cooldowns and baby-animal growth.
- **Smelting** -- furnaces, smokers, blast furnaces continue.
- **Despawn timers** -- item/entity despawn timers advance (off by default).

The catch-up is **spread over real time** rather than applied in the tick that skips the night, so
a sleep never stalls the server. `progression_catchup_seconds` (default 10) sets how long the world
takes to settle; the amount of growth is identical either way, and it is computed from vanilla's
random-tick rate, so raising `randomTickSpeed` for your own reasons does not raise the cost of
sleeping. Set it to 0 for the old apply-it-all-at-once behaviour.

### Sleeper visibility & feedback

- **Sleep status in chat** (`show_sleepers_in_chat`) -- a concise broadcast as players start/stop
  sleeping, e.g. `<name> is sleeping, 1 of 2 required. Need 1 more.` This also serves vanilla
  clients with no UI.
- **Non-blocking vote prompt** -- a bottom-of-screen action-bar message (never a blocking screen,
  so gameplay is never frozen) plus a private "your vote carried / you were outvoted" result.
- **Live vote tally** (`show_sleepers_on_vote_screen`) -- the vote prompt can also show the running
  yes/no count and who is currently in bed.
- **Wake announcement** (`notify_wake`) -- a short broadcast once the night has been skipped, so
  everyone knows morning has arrived.

### In-game admin panel (optional client UI)

- **Server-driven panel** opened with `/usleep gui`. The server pushes the open request and the
  current config to a client that has the mod; the client renders a paginated control panel
  (9 pages) and writes changes back over a private, permission-checked back-channel. Clients
  without the mod simply use the commands instead.

### Flexible settings input

- **Forgiving values.** Booleans accept `true/yes/1/on` and `false/no/0/off` (case-insensitive);
  percentages accept `50`, `50%`, `1/2`, or `0.5`. Every `set` confirms the resolved value back to
  you.
- **Tab completion** for setting keys and values.

---

## Settings reference

Typed (bool / int / enum / percent), JSON-persisted. Per-player state (AFK status, auto-sleep
opt-in, home bed) is runtime player data, not in this global table.

| key | type | default |
|-----|------|---------|
| enabled | bool | true |
| requirement_mode | SIMPLE \| VOTE | SIMPLE |
| required_sleep_percentage | percent | 50 |
| exclude_afk_from_requirement | bool | true |
| vote_duration_seconds | int | 30 |
| vote_pass_rule | MAJORITY_CAST \| PERCENT_CAST \| MAJORITY_NON_AFK | MAJORITY_NON_AFK |
| vote_pass_percentage | percent | 50 |
| skip_mode | INSTANT \| ACCELERATE | INSTANT |
| accelerate_speed | SLOW \| SLOWISH \| QUICK \| FAST | QUICK |
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
| reward_speed_boost_percent | percent | 25 |
| reward_speed_boost_minutes | int | 5 |
| world_progression_enabled | bool | false |
| progress_crops | bool | true |
| progress_animal_husbandry | bool | true |
| progress_smelting | bool | true |
| progress_despawn_timers | bool | false |
| progression_catchup_seconds | int | 10 |
| auto_sleep_enabled | bool | true |
| afk_threshold_seconds | int | 180 |
| provide_afk_command | bool | true |

---

## Commands

Everything lives under `/usleep`. The player and informational commands are open to everyone;
the configuration commands are permission-gated. A designated **sleep-admin** needs no operator
level and is treated as op-4 for every Ultimate Sleep command, so owners can delegate sleep
configuration without handing out vanilla operator.

### Open the control panel

- `/usleep gui` -- open the in-game admin panel. Requires the Ultimate Sleep client mod; vanilla
  clients use the chat commands below instead.

### Everyday player commands (everyone)

- `/usleep status` -- short status line: mode, requirement percentage, and current AFK count.
- `/usleep query` -- full settings dump (`key = value`) plus who currently owns `/afk`.
- `/usleep afk` -- toggle your own AFK state.
  - `/afk` -- alias of `/usleep afk`, registered only when no other mod already provides it.
- `/usleep auto` -- toggle your personal auto-sleep opt-in.
  - Available only while the admin has `auto_sleep_enabled` turned on.

### During a sleep vote (VOTE mode, while a vote is running)

- `/usleep yes` -- vote to skip the night.
- `/usleep no` -- vote against skipping.

### Change settings (sleep-admin, or op level 2+)

- `/usleep set <key> <value>` -- change a setting. Setting keys tab-complete, and boolean/enum
  values tab-complete once a key is typed.

### Administration (sleep-admin, or op level 3+)

- `/usleep admin set <key> <value>` -- change any setting at the administration tier.
- `/usleep admin afk <player>` -- force another player into AFK.
- `/usleep admin admins` -- manage the sleep-admin roster:
  - `/usleep admin admins add <player>` -- grant sleep-admin to a player.
  - `/usleep admin admins remove <player>` -- revoke a player's sleep-admin.
  - `/usleep admin admins list` -- list the current sleep-admins.

---

## Source code

The full multi-loader source (with build instructions) lives on the
[`minecraft-1.20-26.3` branch](https://github.com/Kishku7/ultimate-sleep/tree/minecraft-1.20-26.3).

## Report issues / Support

Please file bug reports and feature requests at the shared support hub:
**https://github.com/Kishku7/mod_support**

## License

All Rights Reserved (c) Kishku7.
