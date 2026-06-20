> DECISIONS LOCKED 2026-06-20 -- see FUNCTIONAL_SPEC.md for the authoritative 1.0 design.
> This file is kept as the original proposal + rationale record.

# Ultimate Sleep -- Proposed 1.0 Feature Set

Draft for review. Drawn from `research/sleep_mods_feature_list.md` (52 deduped behaviors
across 180 Modrinth sleep mods). Nothing here is locked until you sign off; once it is,
it folds into FUNCTIONAL_SPEC.md and the settings registry.

## Design principle

1.0 = a best-in-class, fully **configurable night-skip engine** with first-class **AFK
handling** and an **in-game admin panel**. That is what ~90% of players install a sleep mod
for, and it is exactly the cluster of features that dominates the survey (acceleration 13,
heal-on-sleep 11, server skip rules 8, voting 8, AFK 4...).

Flavor / gameplay-expansion features (nightmares, dream dimensions, horror entity, sleep
deprivation, XP/gifts/hunger-cost, etc.) are deliberately **deferred** to a post-1.0 "flavor"
track. Each is a self-contained module; bundling them later as opt-in keeps 1.0 focused and
stable. The mod earns the name "Ultimate" through depth of configuration now and the
expansion roadmap after.

## In scope for 1.0

### A. Night-skip engine (the core)
- **Requirement mode** -- `PERCENTAGE` (X% of eligible players must sleep) or `VOTE`
  (right-click bed -> server-wide vote). Selectable. [catalog: server skip rules #5, voting #7]
- **Skip behavior** -- `VANILLA` (instant skip), `INSTANT` (force-skip, ignores percentage),
  or `ACCELERATE` (time-lapse: fast-forward instead of teleporting to morning). [#1, #19]
- **Preserve weather** -- option to keep rain/storms across a skip. [#23]
- **AFK exclusion** -- AFK players don't count toward the requirement. [#15] (our differentiator)

### B. AFK integration (already scaffolded)
- `AfkManager`: auto-AFK (idle timer) + manual toggle; movement clears it.
- Conditional `/afk`: defer to an existing provider; `/usleep afk` always works.
- `exclude_afk_from_requirement` ties it into the engine.

### C. Accessibility -- optional toggles that let players actually sleep
- `sleep_anytime` -- sleep during the day too. [#8]
- `sleep_ignore_monsters` -- skip the "monsters nearby" block. [#12]
- `ignore_bed_too_far` -- skip the "bed too far away" block. [bed-too-far]
- `highlight_blocking_mobs` -- outline the mobs preventing sleep. [#22]
  (client-rendered; may land with the GUI phase)

### D. Feedback / UX
- Chat notifications: who's sleeping, progress toward the skip, and a wake/morning broadcast. [#20]
- A sleep progress-bar / sleeper-count HUD is proposed **deferred to the GUI phase** (needs
  client rendering); chat notifications cover 1.0. [progress HUD]

### E. Admin panel (your req 2 & 3) -- client, GUI phase
- `/usleep admin query` + `/usleep admin set` are wired. The GUI is generated from the
  settings registry: open -> `query` (read settings + `/afk` owner) -> render -> `set` per change.

### F. Optional reward -- one, popular, cheap
- `heal_on_sleep` -- regenerate some health on a full sleep. Off by default (stays vanilla-friendly).
  The single most common "reward" in the survey (11 mods). Everything richer (buffs, cures,
  XP, gifts) is deferred.

## Proposed settings (expands the current registry)

| key | type | default | group | meaning |
|-----|------|---------|-------|---------|
| `enabled` | bool | true | core | Master switch. |
| `requirement_mode` | enum(PERCENTAGE,VOTE) | PERCENTAGE | engine | How a skip is triggered. |
| `required_sleep_percentage` | int | 50 | engine | % of eligible players needed (PERCENTAGE). |
| `exclude_afk_from_requirement` | bool | true | engine | AFK players don't count. |
| `vote_duration_seconds` | int | 30 | engine | Voting window (VOTE). |
| `vote_pass_percentage` | int | 50 | engine | % of votes to pass (VOTE). |
| `skip_mode` | enum(VANILLA,INSTANT,ACCELERATE) | VANILLA | engine | How the night passes once triggered. |
| `accelerate_multiplier` | int | 60 | engine | Sim ticks per real tick (ACCELERATE). |
| `preserve_weather` | bool | false | engine | Keep rain/storms across a skip. |
| `sleep_anytime` | bool | false | access | Allow sleeping during the day. |
| `sleep_ignore_monsters` | bool | false | access | Ignore the monsters-nearby block. |
| `ignore_bed_too_far` | bool | false | access | Ignore the bed-too-far block. |
| `highlight_blocking_mobs` | bool | false | access | Outline mobs preventing sleep. |
| `notify_sleep_start` | bool | true | ux | Broadcast when a player starts sleeping. |
| `notify_progress` | bool | true | ux | Broadcast progress toward the skip. |
| `notify_wake` | bool | true | ux | Broadcast the morning/wake event. |
| `heal_on_sleep` | bool | false | reward | Heal on a full sleep. |
| `heal_half_hearts` | int | 4 | reward | Amount healed when heal_on_sleep is on. |
| `afk_threshold_seconds` | int | 180 | afk | Idle time before auto-AFK. |
| `provide_afk_command` | bool | true | afk | Register our /afk when none exists. |

Implementation note: the registry currently supports BOOL/INT only -- 1.0 adds an ENUM (and
probably STRING) type, plus config-file persistence (both already flagged as TODOs).

## Deferred to post-1.0 (flavor / expansion track)

Each is a standalone module, bundled later as opt-in: buffs/effects on waking, sleep-deprivation
debuffs, nightmares, dream dimensions, horror/stalker entity, XP from sleep, sleep-triggered
gifts, hunger cost, cure-effects-on-sleep, reverse sleep (day->night), sleep in the Nether/End,
spectate-while-sleeping, sleep sounds, custom sleeping animation, world-progression-while-sleeping
(furnaces/crops sim), sleep logging, and the long tail of single-mod novelties.

## Open decisions for you

1. **Requirement mode** -- ship BOTH percentage + vote (selectable), or percentage only for 1.0?
   (rec: both -- low cost, big audience.)
2. **Skip default** -- keep `VANILLA` instant as default with ACCELERATE/INSTANT as options?
   (rec: yes -- least surprising default.)
3. **Reward layer** -- include the single `heal_on_sleep` toggle in 1.0, or defer ALL rewards?
   (rec: include the one toggle, off by default.)
4. **Accessibility group (C)** -- in 1.0, or post-1.0? (rec: in -- cheap, high-value, on-brand.)
5. **World-progression-while-sleeping** (Somnia-style furnaces/crops sim, 9 mods) -- it's popular
   but heavy/perf-sensitive. Defer (rec), or pull into 1.0?

