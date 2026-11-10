# App UI redesign: clean grouped look with a tile home

*   **Author:** vijayyaso
*   **Status:** Implemented 2026-11-10 (emulator e2e green, debug + release; installed on the POCO F5)
*   **Date:** 2026-11-10

--------------------------------------------------------------------------------

## Overview

The owner asked for a "completely changed", more modern and simpler app UI.
This design restyles every settings screen inside the app to a clean grouped
look (rows sit inside white rounded cards on a light grey page, like iPhone
Settings) and turns the home screen into a status card plus a grid of large
tiles. Behaviour, settings, strings and the gesture overlays stay the same.

## Owner's answers (2026-11-10)

*   Look: **Clean iOS style** (grouped lists in rounded cards, calm colours).
*   Home layout: **One home + tiles** (big ON/OFF status card on top, a grid
    of big icon tiles below, the same screens behind them).
*   Colours: **Keep blue, refresh it** (matches the launcher icon).
*   Scope: **All settings screens**. The swipe animations, the app-list panel
    and the corner/confirm overlays are out of scope.

## The problem

*   Every screen is one long flat list. Rows from different sections run into
    each other; only a small blue header separates them.
*   Home mixes status, setup, features and "more" in the same row style. The
    owner found it hard to see what is on, what is off, and where to go.
*   Rows show a title and text only, with no icon.

## Goals

*   One look on every screen: page background, rounded group cards, small
    grey group titles, grey footnotes under a group.
*   Home answers "is it working?" in the first card, then shows each feature
    as a tile with an icon, a one-line state and (where it has one) its own
    on/off switch.
*   No feature, setting or string is lost. Every e2e suite passes unchanged.
*   Large font (font scale >= 1.5) still lays out: tiles drop to one column,
    rows stack as they already do.
*   Light and dark themes both follow the new look.

## Non-goals

*   Changing the gesture overlays (`service/*Overlay.kt`), the touch-area
    adjuster's drawing, or the app-list panel.
*   Changing any text, string id or setting default.
*   Wallpaper-based ("dynamic") colour. It stays off because on a neutral
    wallpaper it turns the whole app grey and the status badges muddy
    (comment above `NavGestureTheme` in `Theme.kt`).
*   A custom iOS-looking switch. The Material 3 `Switch` stays, because the
    e2e suites read its `checked` state through uiautomator.
*   Bottom tabs or a new navigation model. The one-level back stack in
    `MainActivity.kt` stays.

## Constraints found in the code

*   **e2e selects by resource id.** Each Compose `testTag` reaches
    uiautomator as a resource id (`testTagsAsResourceId` in
    `MainActivity.kt`); `e2e/lib` finds nodes by it (`find_id`, `tap_id`,
    `set_toggle_id`). Home must still carry
    `row_app_switching`, `row_circle_to_search`, `row_bank_apps`,
    `row_updates`, `row_setup`, `row_gesture_settings`, `row_troubleshooting`,
    `row_about`, `service_status`, `status_turn_on`, and the switches
    `switch_edge_app_list` and `switch_circle_to_search` (flipped from home by
    `e2e/device.sh` and `e2e/circle-to-search.sh`). `to_home` relies on
    `nav_back` being absent on home and present elsewhere.
*   **e2e compares text exactly.** `status-refresh.sh` compares the text of
    the `service_status` and `usage_access` nodes with `strings.xml`, and
    `disclosure.sh` compares `service_status`. Those nodes must keep showing
    exactly that one string.
*   **e2e taps the centre of a node** (`tap_node`). `find_id` first scrolls
    until that centre is more than 260 px above the bottom of the screen. A tile's centre must open its screen, not hit
    its switch.
*   **Content scrolls in one `Column.verticalScroll`** (`MainActivity.kt`). A
    lazy grid inside it would crash on unbounded height, so the tile grid is
    plain rows.
*   **Two flavors.** `Store.kt` exists in `src/github` and `src/play`. The
    play build has no updater and no bank-apps row, so the tile count differs.
*   **Offline build** with only `material-icons-core` in the Gradle cache. No
    new dependency.
*   **A debug e2e run never taps `switch_edge_app_list`**: `e2e/device.sh`
    uses a global-setting override on debuggable builds and taps the switch
    only on a release APK. The home switch is therefore only proven by a
    release-APK run.

## Design

### Colours (`ui/theme/Theme.kt`)

Both schemes set every role in the table below, so no role the app draws
with falls back to Material's purple baseline (the `*Fixed` roles stay
unset; nothing uses them) (the reason is in the comment at the top of
`Theme.kt`). Today the error, inverse and scrim roles are missing. The
inverse ones are used by the reset snackbar, which is why it shows
Material's baseline colours; this change sets all of them, following the
file's own rule that every role is set.

| Role | Light | Dark | Use |
| --- | --- | --- | --- |
| `primary` / `onPrimary` | `#2F6BD8` / `#FFFFFF` | `#7FAAFF` / `#0A2A5E` | switches, filled buttons, links, selected radio and check boxes |
| `primaryContainer` / `onPrimaryContainer` | `#DCE7FD` / `#0A2A5E` | `#203C6B` / `#DCE7FD` | not used by app code; set for completeness |
| `secondary` / `onSecondary` | `#4D5D73` / `#FFFFFF` | `#B6C3D6` / `#1F2B3B` | not used by app code |
| `secondaryContainer` / `onSecondaryContainer` | `#DCE7FD` / `#0A2A5E` | `#203C6B` / `#DCE7FD` | `FilledTonalButton` (light blue) |
| `tertiary` / `onTertiary` | `#2F6BD8` / `#FFFFFF` | `#7FAAFF` / `#0A2A5E` | not used by app code |
| `background` / `onBackground` | `#F2F4F8` / `#11141A` | `#0B0D11` / `#E8EBF0` | the page |
| `surface` / `onSurface` | `#FFFFFF` / `#11141A` | `#1A1D23` / `#E8EBF0` | group cards, tiles, row titles |
| `surfaceVariant` / `onSurfaceVariant` | `#E9ECF1` / `#5F6672` | `#262A31` / `#A3AAB6` | neutral `StatusChip` (via `toneColors`) / group titles, footnotes, row subtitles |
| `surfaceTint` | `#2F6BD8` | `#7FAAFF` | tonal elevation (the adjuster's panel) |
| `surfaceBright` / `surfaceDim` | `#FFFFFF` / `#E1E4EA` | `#2A2E36` / `#0B0D11` | not used by app code |
| `tertiaryContainer` / `onTertiaryContainer` | `#DCE7FD` / `#0A2A5E` | `#203C6B` / `#DCE7FD` | not used by app code |
| `surfaceContainerLowest` | `#FFFFFF` | `#0F1115` | not used by app code |
| `surfaceContainerLow` | `#F7F8FB` | `#15181D` | not used by app code |
| `surfaceContainer` | `#F1F3F7` | `#1A1D23` | not used after this change (the old top bar's scrolled colour) |
| `surfaceContainerHigh` | `#FFFFFF` | `#22262D` | dialogs |
| `surfaceContainerHighest` | `#E9ECF1` | `#2A2E36` | switch track when off |
| `outline` / `outlineVariant` | `#8A919C` / `#E3E6EC` | `#7C838F` / `#2A2E36` | outlined buttons / hairline dividers |
| `error` / `onError` | `#D93025` / `#FFFFFF` | `#FF8A80` / `#5C0F0A` | not used by app code |
| `errorContainer` / `onErrorContainer` | `#FDE7E5` / `#5C0F0A` | `#7A1F18` / `#FFDAD5` | not used by app code |
| `inverseSurface` / `inverseOnSurface` | `#2A2E35` / `#F1F3F7` | `#E8EBF0` / `#1A1D23` | snackbar |
| `inversePrimary` | `#9DBBFF` | `#1F5AC0` | snackbar action |
| `scrim` | `#000000` | `#000000` | not used by app code (dialogs use the platform dim) |

Status colours keep their names in `Theme.kt`:

| Name | Light | Dark |
| --- | --- | --- |
| `success` | `#1E8E4E` | `#6FD39A` |
| `successContainer` / `onSuccessContainer` | `#D5F2E0` / `#0B3D21` | `#1C4430` / `#B7EBCB` |
| `warning` | `#B26A00` | `#FFBA52` |
| `warningContainer` / `onWarningContainer` | `#FFE8C2` / `#3D2800` | `#4E3500` / `#FFDFA8` |

`res/values/colors.xml` and `res/values-night/colors.xml`
`window_background` change to the new `background`, so there is no colour
flash at launch.

**Icon colours.** `Theme.kt` gains `object IconTint` with seven fixed colours,
the same in both themes, each dark enough for a white glyph (at least 3:1):
`BLUE #2F6BD8`, `GREEN #248A3D`, `ORANGE #C9650A`, `PURPLE #7E57C2`,
`RED #D93025`, `GREY #6B7380`, `TEAL #0B7E8A`. They are used only as the
background of an icon square. Assignment:

| Place | Glyph (`Icons.Filled`) | Tint |
| --- | --- | --- |
| App switching tile | `Menu` | BLUE |
| Circle to Search tile | `Search` | PURPLE |
| Gestures tile | `Settings` | GREY |
| Bank apps tile | `Lock` | TEAL |
| Updates tile | `Refresh` | GREEN |
| Setup tile | `CheckCircle` | ORANGE |
| Status card, working | `CheckCircle` | glyph `onSuccessContainer` on `successContainer` |
| Status card, off or stopped | `Warning` | glyph `onWarningContainer` on `warningContainer` |
| Troubleshooting row (home) | `Build` | RED |
| About row (home) | `Info` | GREY |
| Favourite apps row (App switching) | `Star` | ORANGE |

### Shared building blocks (`ui/Components.kt`)

The screens already use a small set of shared rows. The redesign restyles
those and adds three new pieces, so each screen only changes how it groups
its rows.

*   **`SettingsGroup(title, footer, content)`** — new. Draws the optional
    group title (`SectionHeader`, 32 dp from the screen edge), then a rounded
    card (16 dp corners, 16 dp side margin, `surface` colour) holding the
    rows, then the optional footer (small grey text, 32 dp from the screen
    edge). Between two measured children it draws a 0.5 dp hairline in
    `outlineVariant`, inset 16 dp from the start and flush with the card's
    end. The inset is the same for every row, so groups that mix rows with
    and without icons line up.
    The dividers are drawn by a small custom `Layout` that stacks children
    like a `Column` and records each child's bottom edge for the draw step.
    Rows that a screen leaves out with `if` are not children. A plain
    `Column` cannot draw a line *between* arbitrary children: a row does not
    know whether it is the first one in its card.
    **A divider separates children, so one logical block must be one
    child.** Where a screen has a block made of several sibling
    composables (the edge-width label, value, body text, Adjust button and
    hint on Gesture settings; the "Recorded failure" title and its
    monospace text on Troubleshooting, and the same for service history),
    the redesign wraps that block in one `Column` so no line cuts through
    it.
*   **`IconBadge(icon: GroupIcon)`** — new. A 30 dp rounded square (8 dp corners)
    in the tint colour, with an 18 dp white icon. Used as the leading element
    of navigation rows; tiles use a 36 dp version (corners and glyph scale
    with it).
*   **`FeatureTile(...)`** — new (see Home).
*   `SectionHeader` becomes the group title style (grey `labelLarge`, not
    blue, start padding 32 dp so it lines up with the text inside a card).
    `SettingsGroup` uses it, and home calls it once directly for the
    "Features" title above the tile grid, so "Features" and "More" line
    up; other screens stop calling it.
*   `FootNote` keeps its job and its 16 dp padding. It is also used inside
    cards (`AppPicker.kt` loading text, the empty texts in
    `FavouriteAppsScreen.kt` and `BankAppsScreen.kt`), where 16 dp already
    lines up with the rows. Only the `SettingsGroup` footer slot adds the
    extra 16 dp so it lines up under a card.
*   **`GroupIcon(icon: ImageVector, tint: Color)`** — new data class, one
    glyph plus its `IconTint` colour. Used by tiles and navigation rows.
*   `NavigationRow` gains an optional `icon: GroupIcon?` drawn as an
    `IconBadge` in the leading slot. The trailing chevron stays.
*   `SplitSwitchRow` is deleted: its only three callers (two on home, one in
    the github `Store.kt`) become tiles.
*   `ThresholdSlider`, `InfoRow`, `ValueRow` keep their shape; they now sit
    inside a card, so their colours come from `surface`.
*   Buttons inside rows (`OutlinedButton` today) become `FilledTonalButton`
    for the main action and `TextButton` for secondary ones. Test tags move
    with them.

### App bar (`ui/MainActivity.kt`)

*   Home has no app bar. It starts with a large bold title (`app_name`,
    `headlineLarge`, bold, 24 dp below the status bar and 20 dp from the side) drawn as the first item of the
    scrolling content. With no bar there is no `nav_back` node, which is what
    `to_home` expects.
*   Other screens use a `CenterAlignedTopAppBar` with the back arrow
    (`nav_back` tag kept) and the screen title, always on the page colour.
    The content is padded below the bar, so nothing scrolls under it and no
    scroll behaviour is needed.
*   The scaffold background is the page colour.

### Home (`ui/HomeScreen.kt`, `ui/StatusBlock.kt`)

Legend: `[x]` = icon square, `(●)` = switch on, `(○)` = switch off.

```text
 NavGesture                         <- large title
 ┌───────────────────────────────┐
 │ [x]  Gestures are on          │  <- status card, service_status tag
 │      Swipe from the edges ... │
 │      [ Turn on ]  (when off)  │  <- status_turn_on tag
 └───────────────────────────────┘
 Setup, 1 of 2 done                 <- only until essentials are done
 ┌───────────────────────────────┐
 │ step rows (as today)          │
 └───────────────────────────────┘
 Features
 ┌──────────────┐ ┌──────────────┐
 │ [x]     (●)  │ │ [x]     (○)  │  <- icon + own switch
 │ App switching│ │ Circle to    │
 │ On           │ │ Search  Off  │
 └──────────────┘ └──────────────┘
 ┌──────────────┐ ┌──────────────┐
 │ [x]          │ │ [x]          │
 │ Gestures     │ │ Bank apps    │  <- github build only
 │ Touch area 24│ │ 2 apps       │
 └──────────────┘ └──────────────┘
 ┌──────────────┐ ┌──────────────┐
 │ [x]     (●)  │ │ [x]          │
 │ Updates      │ │ Setup        │  <- Updates: github build only
 │ Up to date   │ │ Done         │  <- Setup: once Required is done
 └──────────────┘ └──────────────┘
 More
 ┌───────────────────────────────┐
 │ [x] Troubleshooting         > │
 │ [x] About     Version 1.1.1 > │
 └───────────────────────────────┘
```

*   **Status card**: a `surface` card with 20 dp corners. A 44 dp circle:
    when the status is `WORKING`, a `CheckCircle` glyph in
    `onSuccessContainer` on `successContainer`; when it is `OFF` or
    `STOPPED`, a `Warning` glyph in `onWarningContainer` on
    `warningContainer` (at least 8:1 in both themes); the pairs come from the
    existing `toneColors(Tone.GOOD / Tone.ATTENTION)`. The title (`titleLarge`, bold) keeps the
    `service_status` tag and shows only the status string; the body below it;
    the "Turn on"/"Fix" button becomes full width, keeps `status_turn_on`.
    The restricted-settings hint and "Open app info" stay.
*   **Setup**: unchanged logic. "Essentials" means every Required checklist
    step is done (`checklist.essentialsDone`). Until then the Required steps
    show in a `SettingsGroup` titled with the existing `setup_progress`
    plural ("Setup, 1 of 2 done"). After that, Setup becomes a tile
    (`row_setup`) whose state line is the existing `chip_done` text or the
    `setup_recommended_left` plural (`checklist.recommendedLeft`).
*   **Feature tiles**: built from a list of `HomeTile` specs, laid out two per
    row with `chunked(2)`. Each pair is a `Row` with
    `Modifier.height(IntrinsicSize.Min)` and both tiles
    `weight(1f).fillMaxHeight()`, so a pair has equal height. An odd last
    tile is followed by `Spacer(Modifier.weight(1f))`, so it takes half the
    width, left aligned. At `fontScale >= STACKED_FONT_SCALE` (the existing
    1.5 constant in `Components.kt`, made `internal`) the chunk size is 1.

    ```text
    data class GroupIcon(val icon: ImageVector, val tint: Color)
    data class TileToggle(
        val checked: Boolean, val tag: String, val onChange: (Boolean) -> Unit,
    )
    data class HomeTile(
        val title: String,
        val summary: String,         // existing summary helpers, 2 lines max
        val icon: GroupIcon,
        val tag: String,             // row_* resource id, on the clickable area
        val toggle: TileToggle? = null,
        val onOpen: () -> Unit,
    )
    @Composable fun FeatureTile(tile: HomeTile, modifier: Modifier = Modifier)
    @Composable fun SettingsGroup(
        title: String? = null, footer: String? = null,
        content: @Composable () -> Unit,
    )
    ```

    The Gestures tile summary is the existing `gestureSettingsSummary()`
    text, cut to 2 lines.

    The tile is a `Box`: the clickable area (`tag`, opens the screen) fills
    the tile; the `Switch` (switch tag) sits on top at the top-end corner as a
    sibling, not a child, so the click areas and accessibility nodes stay
    separate and the tile's centre opens the screen. Minimum tile height
    128 dp; summary limited to 2 lines with ellipsis.
*   Tile order: App switching, Circle to Search, Gestures, Bank apps (github
    only), Updates (github only, from `Store`), Setup (when essentials done).
*   **More**: a `SettingsGroup` with Troubleshooting and About as navigation
    rows with icons.

### Store hook (both `Store.kt` files)

`Store.HomeRow(onOpen)` is replaced by `Store.homeTile(onOpen): HomeTile?`,
a composable function that returns the Updates tile spec (github) or `null`
(play). The github version builds the spec from `UpdateViewModel` state with
`updatesSummary(state)` and the `switch_update_check` toggle. This keeps the
play build free of updater classes, as today.

### Icons

The glyph table under Colours is the only list. Every glyph in it is in
`material-icons-core`; the compile step proves it. No new dependency.

### Other screens

**Buttons.** Every button keeps its tag, label and action:

| Where | Today | New |
| --- | --- | --- |
| Status card Turn on / Fix | `Button` | `Button`, full width |
| Status card Open app info | `TextButton` | `TextButton` |
| Setup step "Open…" actions | `OutlinedButton` | `FilledTonalButton` |
| Setup step "I did it" / Undo | `TextButton` | `TextButton` |
| Usage access / notifications / assistant "Grant/Allow/Open" | `OutlinedButton` | `FilledTonalButton` |
| Troubleshooting fix rows "Open" | `OutlinedButton` | `FilledTonalButton` |
| Troubleshooting Refresh, Gesture settings Reset | `TextButton` | `TextButton` |
| Gesture settings Adjust | `OutlinedButton` full width | `FilledTonalButton` full width |
| Updates install / allow installs | `FilledTonalButton` | `FilledTonalButton` |
| Updates check now | `OutlinedButton` | `OutlinedButton` |
| Disclosure Agree / Not now | `Button` / `OutlinedButton` in a `FlowRow` | same types, full width, stacked |

**Navigation rows without an icon:** the About source and license rows and
the two corner-shortcut rows stay without icons (they are value rows inside
their own screen). Only the rows in the glyph table get one.

Each screen keeps its rows, order and logic; its `SectionHeader` + rows runs
become `SettingsGroup(title) { rows }`, and its standalone `FootNote`s become
the group's footer where they explain that group.

Each line below lists one screen's groups in order. A group is written as
**Title**: its rows; *footer: …* names the grey text under that card.
Titles here are short names; the screen shows the existing string for each
(for example **Edges** shows "Touch area"). A
group with no title is written as **(no title)**.

*   **Setup** — **Required**: required steps. **Recommended**: recommended
    steps; *footer: setup footer (HyperOS or generic text)*.
*   **App switching** — **(no title)**: app-list switch, Favourite apps row.
    **List style**: the three style radio rows, drag step slider, adaptive
    speed switch. **Corner shortcuts**: left corner row, right corner row;
    *footer: corner shortcuts body* (moved from above the rows to below).
    **Needs**: usage access row; *footer: usage access explainer*.
    **Gestures it adds**: switch-apps info row, last problem row.
*   **Favourite apps** — **Favourites**: picked apps with move/remove buttons,
    or the empty text inside the card. **All apps**: the checklist.
*   **Bank apps** — the two intro texts as plain footnotes above everything.
    **Needs**: adb grant, usage access, notifications rows. **Picked**: the
    picked apps or the empty text. **All apps**: the checklist.
*   **Circle to Search** — **(no title)**: the switch; *footer: feature body*.
    If on: **Timing**: system-timing switch, hold slider. **Needs**:
    assistant row, last problem row.
*   **Gesture settings** — **Gestures**: cheat-sheet rows. **Edges**: one
    block (edge width value, its body text, the Adjust button, the hint
    text), left back switch, right back switch, landscape-confirm switch. **Distance and
    timing**: the four sliders. **Gesture bar**: hide switch, shown under
    the same condition as today (`isHyperOsDevice() && (canHide ||
    !IS_PLAY)`).
    **Feedback**: haptics switch. Then the Reset button below the cards.
*   **Troubleshooting** — **Known fixes**: the fix rows with their buttons.
    **This device**: the device value rows. **Last problems**: the three
    value rows, then the recorded-failure block and the service-history
    block (title plus monospace text each) inside the same card. Then the
    Refresh button below the cards, then the local-only text as a
    standalone footnote (it sits after the button, so it is not a group
    footer).
*   **About** — **(no title)**: version, website, source, license.
    **Credits**: the two credit rows.
*   **Updates** (github only) — **(no title)**: the update switch; *footer:
    switch body*. If on: **(no title)**: the status row with its buttons.
*   **Disclosure** — the intro text; one card holding the three parts in
    their current order (what the service reads, what it does, what it
    never does); the same two buttons with the same labels, order and tags
    (Agree filled, Not now outlined), now full width and stacked; the
    footnote. No text changes (Play policy text, mirrored in
    `docs/play-store/disclosure.md`).

The `usage_access` node keeps showing only its status string (the explainer
stays a separate footer), so `status-refresh.sh` still matches it exactly.

## Alternatives considered

*   **Restyle only the theme colours.** Smallest change, but leaves the flat
    list and the crowded home that the owner called hard to understand.
    Rejected.
*   **Bottom tabs (Home / Customise / Help).** Owner chose tiles instead.
    Also needs a new navigation model and breaks `to_home` in `e2e/lib`.
*   **`LazyVerticalGrid` for tiles.** Crashes inside the existing
    `verticalScroll` column; moving every screen to lazy lists is far more
    churn for a 4–6 item grid. Rejected.
*   **Material Symbols via `material-icons-extended`.** More glyphs, but the
    build runs `--offline` and only core is cached; the extended artifact is
    also no longer recommended by Google. Rejected.
*   **`SettingsGroup(rows: List<@Composable () -> Unit>)` with a
    `HorizontalDivider` between list items.** Avoids a custom layout, but
    every screen builds its rows with `forEach` and `if`; turning those into
    lists of lambdas is more churn than one 25-line layout. Rejected.
*   **Per-row card backgrounds with spacing as dividers.** Simpler than a
    custom layout, but forces every row composable to paint its own
    background and gives full-width lines instead of inset ones. Rejected in
    favour of the small divider layout.

## Risks

*   **Nested click areas on tiles** could merge the switch into the tile's
    accessibility node and hide `switch_*` from uiautomator. Mitigation: the
    switch is a sibling on top of the clickable area, not a child.
*   **Large fonts** can overflow 2-column tiles. Mitigation: one column at
    font scale >= 1.5; summary capped at 2 lines.
*   **Text contrast** on the new surfaces could drop. Pass bar: body and
    subtitle text at least 4.5:1, glyphs at least 3:1, checked with a WCAG
    contrast formula on the hex values in this doc (a short script during
    the build step), plus light, dark and large-font screenshots on the
    emulator.
*   **White glyphs on light accents** (orange, grey, teal) could be hard to
    see. Mitigation: every `IconTint` value is at 3:1 or better against
    white; the icons are decorative (the title beside them carries the
    meaning).

## Detailed Implementation Plan

All paths are under `app/src/`. No string, setting, test tag or behaviour is
removed or renamed. No new test tag is needed.

### Files changed

1.  `main/kotlin/com/vijayyaso/navgesture/ui/theme/Theme.kt` — new colour
    values for every role in both schemes, now including the error, inverse
    and scrim roles; retuned `success`/`warning` families; new `IconTint`
    colour set. No global `Shapes` override: corners are set where each
    element is drawn.
2.  `main/res/values/colors.xml`, `main/res/values-night/colors.xml` —
    `window_background` = new page colour (`#F2F4F8` / `#0B0D11`).
3.  `main/kotlin/com/vijayyaso/navgesture/ui/Components.kt` — add
    `SettingsGroup`, the divider layout, `IconBadge`, `GroupIcon`,
    `HomeTile`/`TileToggle` data classes and `FeatureTile`; restyle
    `SectionHeader`; add the optional `icon` parameter to `NavigationRow`;
    delete `SplitSwitchRow`.
4.  `main/kotlin/com/vijayyaso/navgesture/ui/MainActivity.kt` — no app bar on
    home; `CenterAlignedTopAppBar` elsewhere with `nav_back`; scaffold
    container colour = background. (The large home title is drawn by
    `HomeScreen.kt`.)
5.  `main/kotlin/com/vijayyaso/navgesture/ui/HomeScreen.kt` — status card,
    setup group, tile grid built from `HomeTile` specs (including
    `Store.homeTile`), "More" group.
6.  `main/kotlin/com/vijayyaso/navgesture/ui/StatusBlock.kt` — new status card
    look (icon circle, full-width button); same tags and texts.
7.  `main/kotlin/com/vijayyaso/navgesture/ui/SetupScreen.kt`,
    `SetupSection.kt` — groups; `OutlinedButton` → `FilledTonalButton`.
8.  `main/kotlin/com/vijayyaso/navgesture/ui/AppSwitchingScreen.kt`,
    `FavouriteAppsScreen.kt`, `BankAppsScreen.kt`, `CircleToSearchScreen.kt`,
    `GestureSettingsScreen.kt`, `TroubleshootingScreen.kt`,
    `AboutScreen.kt`, `DisclosureScreen.kt` — wrap rows in `SettingsGroup`
    per the screen list above; icons per the glyph table; buttons per the
    button table. No logic change. `AppPicker.kt` and
    `CornerShortcutPicker.kt` are not changed: their callers wrap them in a
    group.
9.  `github/kotlin/com/vijayyaso/navgesture/ui/Store.kt` — `HomeRow` →
    `homeTile(onOpen): HomeTile?`.
10. `play/kotlin/com/vijayyaso/navgesture/ui/Store.kt` — same API, returns
    `null`.
11. `github/kotlin/com/vijayyaso/navgesture/ui/UpdatesScreen.kt` — groups.
    `UpdateRows.kt` is not changed (its buttons and tags already fit).

### Files not changed

`TestTags.kt`, `strings.xml`, every file under `service/`, `gesture/`,
`setup/`, `settings/`, `bankpause/`, `update/`, `TouchAreaAdjuster.kt`
(its band and scrim colours are hard-coded overlay colours and stay),
`AppPicker.kt`, `CornerShortcutPicker.kt`, and all `e2e/` scripts.

### Verification

1.  Build in the dev container `h3nc4/hypergesture-dev:18`, after
    `set -a; . ~/.config/navgesture/keystore.env; set +a`, with
    `~/.config/navgesture/navgesture-release.jks` mounted read-only at
    `/ks/release.jks` and `KEYSTORE_PATH=/ks/release.jks`,
    `KEYSTORE_PASSWORD`, `KEYSTORE_ALIAS` passed in. Without them the
    release APK is unsigned; check it with `apksigner verify --print-certs`
    (certificate `8527f9ba…5e4311`) before installing. Tasks: `testGithubDebugUnitTest`,
    `lintGithubDebug`, `lintPlayDebug`, `assembleGithubDebug` (the APK
    `e2e/device.sh` installs by default),
    `assembleGithubRelease` (`app/build/outputs/apk/github/release/app-github-release.apk`),
    `assemblePlayDebug` (play compiles with the null `homeTile`).
2.  e2e on the `ng36` emulator: `e2e/run.sh -a ng36 -k` (all nine
    suites, disclosure last) on the debug APK, keeping the emulator up;
    then `e2e/run.sh -a ng36 -p <release apk> app-switch circle-to-search`,
    which installs the release APK through `device.sh` and is the only run
    that taps `switch_edge_app_list` on home.
3.  Contrast: a throwaway Python script (outside the repo) computes the WCAG
    ratio for every text/background and glyph/background pair named in this
    doc; it must print no pair under 4.5:1 (text) or 3:1 (glyphs).
4.  Screenshots on the emulator of home, App switching, Gesture settings and
    Troubleshooting in light, dark (`cmd uimode night yes`) and font scale
    1.5 (this is the check for the one-column tiles, the odd half-width
    tile and the dividers; there is no Compose UI test setup in this repo,
    `app/src/androidTest` does not exist); settings restored afterwards.
5.  Install on the POCO F5 with `adb install -r -i com.android.vending` when
    the phone is awake; screenshot home (no `uiautomator dump` on the phone).
