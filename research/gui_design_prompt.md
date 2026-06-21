# Design brief: "Ultimate Sleep" in-game config GUI (Minecraft Java)

You are designing the **graphics and layout** for an **in-game Minecraft Java Edition GUI** (a
mod config screen), NOT a website or desktop app. Deliver mockups + layouts for each screen/page
described below. The visual style must look at home inside Minecraft: a vanilla-consistent look
(dark/semi-transparent panel, standard Minecraft-style button widgets, the Minecraft pixel font,
crisp 1px borders). Everything is rendered at integer pixel scales, so avoid sub-pixel detail,
gradients-that-need-AA, or thin diagonal lines.

## What this GUI is

The server-config admin panel for a sleep mod. It reads the current settings when opened and lets
an admin change them. (For your purposes: just design the controls and layout; the data wiring is
handled in code.) There are ~31 settings grouped into categories, plus a Sleep-Admin management
page, plus a separate in-world vote popup.

## CRITICAL: Minecraft's coordinate space + auto-scale

Minecraft GUIs are drawn in **"GUI pixels"** and then multiplied by the user's GUI Scale (1x, 2x,
3x, 4x, or Auto). Design everything in GUI pixels. We are targeting **GUI scale 2x (minimum) to 4x
(maximum)**, so the panel must remain fully visible even at 4x on common monitors.

- **Design canvas (the panel): 256 wide x 180 tall GUI pixels.** Center it on screen.
  - At 2x that's 512 x 360 real pixels; at 4x it's 1024 x 720 real pixels (fits a 1366x768 laptop
    at 4x). Do not exceed this canvas — anything bigger can get clipped at 4x.
- Because content won't all fit in 256x180, **split it across pages** with navigation buttons
  (see Pages below). Prefer multiple simple pages over one cramped page.

### Element sizing (use these GUI-pixel sizes)

- Standard button height: **20**. Text-field height: **20**. Row pitch (button + gap): **24**.
- Panel inner margin: **8** on every side.
- Title strip at top: **18** tall. Bottom navigation strip: **22** tall.
- That leaves ~**130** tall of usable content = about **5 setting rows per page** (24 each).
- Button widths: full-width **240**; half-width **118** (two per row, 4px gap); compact
  toggle/selection button **70-90**; small icon button (e.g., remove "X") **20 x 20**.
- A setting row = left-aligned label (~140 wide) + right-aligned control (toggle/selection/field).

## Control types — when to use which

Pick the control per setting type:

1. **On/Off toggle button** — for a simple boolean (mutually exclusive on or off). One button that
   shows and flips the state, e.g. `Auto-sleep: [ ON ]` / `[ OFF ]` (color the state: green ON,
   red/grey OFF). Use for all true/false settings.
2. **Selection button (either/or)** — for a setting with a small fixed set (2-3) of mutually
   exclusive choices. Use a cycle-button that displays the current choice and advances on click
   (e.g. `Mode: < SIMPLE >`), or a small segmented row of choices. Use this, NOT a toggle, when
   there are more than two options.
3. **Text input box** — for typed values, especially numbers/percentages where the admin wants
   precision or flexible formats. Example: player sleep percentage accepts `50`, `50%`, or `1/2`.
   Use a text field (optionally with small +/- steppers) for all numeric/percent settings.

Each row should also surface a one-line description as a hover tooltip.

## Pages (one category per page; navigate with Prev / Next + a "Page X of Y" label in the bottom strip)

Design these pages (control type in brackets):

1. **General**
   - enabled [toggle]
2. **Sleep engine**
   - requirement_mode [selection: SIMPLE | VOTE]
   - required_sleep_percentage [text: e.g. 50 / 50% / 1/2]
   - exclude_afk_from_requirement [toggle]
   - skip_mode [selection: INSTANT | ACCELERATE]
   - accelerate_multiplier [text: number]
   - preserve_weather [toggle]
3. **Voting** (used when requirement_mode = VOTE)
   - vote_duration_seconds [text: number]
   - vote_pass_rule [selection: MAJORITY_CAST | PERCENT_CAST | MAJORITY_NON_AFK]
   - vote_pass_percentage [text: percent]
4. **Accessibility**
   - sleep_anytime [toggle]
   - sleep_ignore_monsters [toggle]
   - ignore_bed_too_far [toggle]
   - highlight_blocking_mobs [toggle]
5. **Feedback / messages**
   - show_sleepers_in_chat [toggle]
   - show_sleepers_on_vote_screen [toggle]
   - notify_wake [toggle]
6. **Rewards (on wake)**
   - reward_regeneration [toggle] + reward_regeneration_minutes [text]
   - reward_golden_carrot [toggle]
   - reward_speed_boost [toggle] + reward_speed_boost_percent [text] + reward_speed_boost_minutes [text]
7. **World progression (while sleeping)**
   - world_progression_enabled [toggle]
   - progress_crops [toggle]
   - progress_animal_husbandry [toggle]
   - progress_smelting [toggle]
   - progress_despawn_timers [toggle]
8. **Auto-sleep & AFK**
   - auto_sleep_enabled [toggle]
   - afk_threshold_seconds [text: number]
   - provide_afk_command [toggle]
9. **Sleep Admin Control Center (the LAST page)**
   - A titled list of current "sleep admins" (player names) in a **scrollable list with a visible
     scrollbar on the right** (design the scrollbar + a few example rows + an empty state).
   - Each row has the name on the left and a small **remove "X" button** (20x20) on the right to
     kick that admin.
   - Below the list: a **text input box** for a player name + an **"Add" button** to add a new
     sleep admin.
   - Show the list both partially-scrolled (scrollbar mid-track) and full.

The bottom strip on every page: **[ < Prev ]**, a centered **"Page X of 9"**, **[ Next > ]**, and a
**[ Done ]** button to close. The top strip: the page/category title and a small mod title/logo area.

## Second screen: the Vote popup (separate, smaller overlay)

A compact centered popup (~200 x 90 GUI px) shown to clients during a sleep vote:
- Title/question: "Allow other players to sleep?"
- Two big buttons: **[ Yea ]** (green) and **[ Nay ]** (red).
- A countdown indicator (e.g. "29s" or a thin progress bar).
- A small live list/count of who is currently sleeping (e.g. "2 of 4 sleeping").

## Deliverables

For each of the 9 pages and the vote popup: a mockup at the 256x180 (popup ~200x90) GUI-pixel
canvas, showing exact control placement, the toggle/selection/text styles, tooltips, and the
nav/scrollbar. Provide a shared style sheet (panel background, button states: normal/hover/pressed/
disabled, toggle ON/OFF colors, text-field focus state, scrollbar). Keep it pixel-crisp and
vanilla-consistent so it renders cleanly at 2x-4x GUI scale.
