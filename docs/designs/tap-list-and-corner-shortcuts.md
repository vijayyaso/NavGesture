# Calmer app list, tap-to-pick list, and corner shortcuts

*   **Author:** vijayyaso
*   **Status:** Implemented (emulator tested; phone test pending)
*   **Date:** 2026-11-09

--------------------------------------------------------------------------------

## Overview

Three changes to the bottom edge, from the owner's request of 2026-11-09:

1.  The drag-to-pick app list (swipe up, then drag sideways) is too twitchy.
    The selection jumps too fast, and the icon row jumps when the list loops.
2.  Some users find one long continuous swipe hard. A new **list style**
    setting offers *Drag*, *Tap* or *Both*. In Tap style the same short swipe
    opens the list, the list stays open after the finger lifts, and the user
    touches an app.
3.  A swipe **up and inward from a bottom corner** runs a shortcut the user
    picks per corner: the app list, a system action, or a chosen app.

## Owner's answers (2026-11-09)

*   Problems: "Selection jumps too fast" and "Icon row jumps around".
*   List style: a setting with Drag, Tap and Both.
*   Corner shortcuts: open the app list, system actions, open a chosen app.
*   Corner direction: up and inward (diagonal), one shortcut per corner.

## Goals

*   A finger resting near a boundary between two apps no longer flips the
    selection or buzzes repeatedly (±0.25 step of jitter at a boundary causes
    no change; unit tested).
*   Looping from the last app to the first never makes the row jump back.
*   A user who cannot hold one continuous swipe can still switch apps with a
    short swipe and a touch.
*   A corner diagonal never fires Home, Back or Recents, and a normal Home
    swipe from the middle never fires a corner shortcut.

## Non-goals

*   Animating the panel in and out.
*   Corner shortcuts on the side edges.
*   Changing the app-list trigger (below).
*   Screen-reader support for the touch panel (see Risks).

## Current system, in brief

*   Touch **strips** are thin `TYPE_ACCESSIBILITY_OVERLAY` windows on the left,
    right and bottom edges (`service/EdgeTouchOverlay.kt`). The bottom strip
    is `edgeWidthDp + navBottomPx` tall and, until now, 90 % of the width,
    centred, so both bottom corners stayed natively touchable
    (`service/ScreenGeometry.kt`, `OverlayLayout`).
*   A touch on a strip is one **stream** (DOWN … UP/CANCEL). A per-edge
    `GestureTracker` (`gesture/GestureTracker.kt`, Android-free, JVM tested)
    turns it into actions. A stream that is no gesture is replayed to the app.
*   **App-list trigger** (unchanged): a bottom stream first **arms** Home when
    it rises 10 dp inside a 45° cone; then, while still rising, a MOVE whose
    sideways travel from DOWN is ≥ 40 dp and larger than the rise opens the
    list. Right = recent apps, left = favourites. Before arming, a stream older
    than 1000 ms is given up (`maximumArmDurationMs`).

## Design

### 1. Calmer drag selection

*   The selector step becomes a setting, **"Drag step"**, 40–120 dp, default
    **72 dp** (was a fixed 56 dp). Every style uses it.
*   `offsetDp` is the sideways travel from the point where the list **opened**
    (rightward positive), not from DOWN. The controller owns an unbounded
    integer **step count**, reset to 0 at every open:
    `steps = SwitcherSelection.steps(offsetDp, stepDp, steps)`.
*   **Hysteresis.** `steps()` keeps the previous count while
    `|offsetDp / stepDp − previous| ≤ 0.75`; otherwise it returns
    `round(offsetDp / stepDp)` (Kotlin `roundToInt`, so a fast flick may jump
    several steps). With 72 dp the first change needs 54 dp; going back needs
    the finger 18 dp short of the open point.
*   `offsetDp` is the `SlideTravel` sum, not the raw finger distance: each
    movement is scaled by a speed gain (1.2× at or below 200 dp/s, 3× at or
    above 1500 dp/s, an S-curve between), like a laptop trackpad. The
    "Speed-adaptive sliding" setting (on by default) turns it off.
*   The selected app is `SwitcherSelection.indexFor(steps, count)`
    = `floorMod(steps, count)`. The app in front now is left out of the
    recents list, so every app shown can be picked.
*   **Smooth row.** The panel animates a float position toward the step count
    (`ValueAnimator`, 160 ms, `DecelerateInterpolator`, a new target restarts
    from the current value). Slot pitch stays 56 dp, at most 6 slots.
    *   **Fits** (apps ≤ slots): the row stands still and the highlight slides.
    *   **Ring** (more apps): the apps form a ring, the highlight is fixed in the centre slot, and the
        ring position is the unbounded step count, so a loop keeps moving the
        same way. The ring shows an odd slot count below the app count, so no
        app shows twice; icons fade at the ends.

### 2. List style: Drag, Tap, Both

New setting `appListStyle`: `DRAG` (default), `TAP`, `BOTH`.

| Style | While the finger is down | On lift |
| --- | --- | --- |
| Drag | row; selection follows the finger | opens the selected app |
| Tap | grid, no highlight, not yet touchable | grid becomes touchable |
| Both | row; selection follows the finger | step count ≠ 0: opens it; 0: becomes the touchable grid |

In every style, sliding the finger 32 dp down from the open point closes the
list (today's cancel).

**Touch mode** (the panel after the lift in Tap/Both, or from a corner):

*   Window flags: touchable `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL |
    FLAG_WATCH_OUTSIDE_TOUCH`. Hidden or held = `FLAG_NOT_TOUCHABLE` and
    alpha 0, set together by one `setWindow()` on **every** close path
    (pick, outside touch, timeout, new stream, removal), so a closed panel can
    never eat touches. A touch outside the window reaches the app below and
    sends `ACTION_OUTSIDE`, which closes the panel.
*   Grid: cells 72 × 84 dp (48 dp icon, name under it, 11 sp, one line,
    ellipsized), 12 dp padding. Columns = how many cells fit in the window
    width less 28 dp; at most 3 rows; apps past that are not shown (see
    Risks). Same list and order as the drag list: recents newest first (the
    app in front now left out, at most 8), favourites in
    the user's order.
*   A DOWN and UP in the same cell opens that app and closes the panel. A
    DOWN and UP inside the window but outside every cell closes it. A DOWN and
    UP in different cells does nothing.
*   The panel closes itself after **10 s**; any finger down on it restarts the
    timer, so a slow user is not cut off mid-touch.
*   **Placement:** the panel's bottom edge sits 8 dp above the bottom strip's
    top (`edgeWidthDp + navBottomPx`), and at least 40 dp above the screen
    bottom. The strip therefore never covers a grid row, whatever the edge
    width or navigation mode.

### 3. Corner shortcuts

*   **Where.** A DOWN on the bottom strip with `x ≤ zone` or
    `x ≥ width − zone`, where `zone = min(12 % of the display width, 56 dp)`,
    and only when that corner's shortcut is not `none`. The bottom strip is
    extended to the screen edge **on that side only**; a corner without a
    shortcut keeps its native touch.
*   **What counts.** With `inward = x − x0` (left) or `x0 − x` (right) and
    `up = y0 − y`: `inward > 0`, `up > 0`, `hypot(inward, up) ≥ 32 dp`, and
    `30° ≤ atan2(inward, up) ≤ 75°`.
*   **Order inside the tracker.** The corner test runs first on every MOVE
    while the stream is unarmed or rising, then the arm timeout, then the
    arm and app-list rules. For a corner start the arm timeout does not end
    the stream: an `armTimedOut` flag keeps it alive for the corner test only,
    and a release that never qualified is replayed as before. An UP with no
    MOVE before it (event batching) is tested too.
*   **Steeper than 30°** stays Home or Recents. **Flatter than 75°** is not a
    corner; it is whatever the old rules make of it. Between 45° and 75°
    the corner wins over the app list, because the list needs 40 dp sideways
    while the corner fires at 32 dp along the diagonal.
*   **When it runs.** At the trigger the tracker returns
    `CornerReady(corner)`: one haptic tick, the bubble released. A shallow
    diagonal that armed Home first also got the arm tick, so two light ticks
    are possible. The release returns `Fire(GestureAction.Corner(side))`, id
    `CornerLEFT` or `CornerRIGHT`. The overlay posts the shortcut **80 ms**
    later, after the strips' return to visible has settled (the launch rule
    needs a visible overlay) and the bubble is gone (screenshot). CANCEL or a
    second finger drops it.
*   **Choices** (`CornerShortcut`, stored as its id string):

    | Id | Does |
    | --- | --- |
    | `none` (default) | nothing; normal bottom touch |
    | `recents_list` | recent-apps panel, touch mode |
    | `favourites_list` | favourites panel, touch mode |
    | `notifications` | `GLOBAL_ACTION_NOTIFICATIONS` |
    | `quick_settings` | `GLOBAL_ACTION_QUICK_SETTINGS` |
    | `screenshot` | `GLOBAL_ACTION_TAKE_SCREENSHOT` (API 28) |
    | `lock_screen` | `GLOBAL_ACTION_LOCK_SCREEN` (API 28) |
    | `power_menu` | `GLOBAL_ACTION_POWER_DIALOG` |
    | `split_screen` | `GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN` (API 24) |
    | `app:<package>` | `AppSwitcher.launch(package)` |

    `decode()` maps null, empty, unknown and `app:` with no package to
    `none`. All constants are at or below `minSdk` 30; lint `NewApi` passes.
*   **One runner.** `EdgeTouchOverlay.runCorner()` reads the corner's current
    shortcut: the two list choices go to `EdgeSwitcherController.openForTouch`
    (which runs its own list lookup, the same code as the drag list), every
    other choice to `GestureActionPerformer.performShortcut`, which reuses
    the existing failure recording (`lastFailure`, diagnostics panel).
*   The list shortcuts work even when "Bottom app switcher" is off; the panel
    is then built for them alone.
*   **Landscape confirm.** `LandscapeConfirm.isGuarded()` is true for Home,
    Recents and any `Corner`. `admit()` runs before the post, so a held-back
    first swipe never runs, and the second corner swipe within 2 s runs.

### Settings

| Key | Type | Default |
| --- | --- | --- |
| `app_list_style` | string (enum name) | `DRAG` |
| `switcher_step_dp` | float | 72 |
| `left_corner_shortcut` | string (shortcut id) | `none` |
| `right_corner_shortcut` | string (shortcut id) | `none` |

A missing key falls back to the default, so existing installs move from 56 dp
to 72 dp. "Reset to defaults" resets all four. Debug builds also read the
global settings `navgesture_app_list_style`, `navgesture_left_corner` and
`navgesture_right_corner`, for the e2e suite.

The **App switching** screen gains a "List style" section (three radio rows,
tags `list_style_drag|tap|both`), the "Drag step" `ThresholdSlider`, and a
"Corner shortcuts" section with one `NavigationRow` per corner (tags
`row_left_corner`, `row_right_corner`) that opens a dialog of choices; "Open
an app…" lists apps from `rememberLaunchableApps`.

## Alternatives considered

*   **Tap-only trigger** (touch and hold the strip to open the list). Rejected:
    a still finger on the bottom edge is already Circle to Search and the
    phone's own long press, and a hold is slower than the 40 dp swipe.
*   **Smaller fixed step instead of hysteresis.** A larger step alone still
    flips at the boundary under jitter; hysteresis fixes the flicker, the step
    setting fixes the speed.
*   **Re-cut window (old) instead of a ring.** The re-cut is what jumped.
*   **Two separate corner windows.** Equal effect to extending the bottom strip
    on one side, but two more windows to add, remove and keep non-touchable
    during a stream.
*   **No idle timeout.** Rejected: a panel left open before the screen goes
    off would still be there later, over another app.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| A configured corner's native taps now go through our strip (replayed) | Only the side with a shortcut is widened; the strip is only the bottom edge band |
| A closed touch panel keeps eating taps | Hide always sets `FLAG_NOT_TOUCHABLE`; one code path |
| Panel launch refused as a background start | The panel itself is a visible accessibility overlay; corner launches wait 80 ms for the strips |
| More favourites than the grid holds (about 15) are not shown in Tap style | Accepted; the drag list still reaches all; listed for the phone test |
| No TalkBack support in the panel | Out of scope; the feature is for motor difficulty, not low vision |
| Play declaration lists only Back/Home/Recents | The owner ships GitHub-only; update `docs/play-store/` before any Play upload |

## Testing (done)

*   Unit (JVM): `SwitcherSelectionTest` (hysteresis, jitter, unbounded index,
    grid), `CornerGestureTest` (16 cases: both corners, slow past 1000 ms,
    batched DOWN/UP, steep, flat, 30–75° bounds, short, zone edge, centre,
    `none`, no width, cancel, beats the app list, haptics),
    `CornerShortcutTest`, `LandscapeConfirmTest` (corner guarded).
*   Lint `NewApi` and the rest for both flavours.
*   Emulator: `e2e/corner.sh` (corner fires, steep is Home, corner list opens
    and closes on an outside touch, Tap and Both stay open, timeout, no corner
    without a shortcut) and `e2e/app-switch.sh`.
*   Phone (pending): launching from the grid, the feel of the 72 dp step,
    screenshot without the bubble.
