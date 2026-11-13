# Calmer app list, tap-to-pick list, and corner shortcuts

*   **Author:** vijayyaso
*   **Status:** Implemented (emulator tested; phone test pending)
*   **Date:** 2026-11-09
*   **Revised:** 2026-11-13. Fixes: the cancel distance near the screen
    edge (section 2), grid rows and the 24-favourite limit (Touch mode), the
    wider recents window (section 1). One behaviour change: in landscape the
    app list now waits for a second swipe (section 4).

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
    short swipe and a touch. In landscape with "Confirm in landscape" on,
    the first swipe is held back and a second within 2 s opens the list
    (section 4).
*   A corner diagonal never fires Home, Back or Recents, and a normal Home
    swipe from the middle never fires a corner shortcut.

## Non-goals

*   Animating the panel in and out.
*   Corner shortcuts on the side edges.
*   Changing the shape of the app-list trigger (see Current system). The one
    exception is the landscape hold-back (section 4).
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
*   **App-list trigger** (shape unchanged). A bottom stream first **arms**
    Home when it rises by the "Home swipe distance" setting (default 10 dp)
    inside a 45° cone. Then, while still rising, a MOVE whose sideways
    travel from DOWN is ≥ 40 dp and larger than the rise opens the list: a
    drag to the right opens recent apps, a drag to the left opens
    favourites. When "Bottom app switcher" is on, arming gives one light
    tick to say that a sideways drag can now open the list; when it is off
    there is no arm tick. Before arming, a stream older than 1000 ms is
    given up (`maximumArmDurationMs`).

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
*   **Recents source** (drag list and grid). Usage events from the last 30
    minutes: apps that came to the front and whose process is still alive,
    excluding home screens, this app, MIUI's own scan and install helpers,
    and apps with no launcher entry. Each app counts once, newest first, at
    most 8; then the app in front now is left out, so often 7 show. If fewer
    than 2 apps remain to switch to after that (a long stay in one app,
    which would leave one app or none), the last 24 hours are read instead,
    under the same filters and the same limit of 8
    (`AppSwitcher.recentApps`, `RecentApps.tooFew`). That read is newest
    first too, so apps from the last 30 minutes still come first and it only
    adds older apps. If it also gives fewer than 2, the list shows what
    there is. Both reads run in the list's background load, never on the
    thread that handles touches. Without Usage access (the Android
    permission to read usage events) both reads are empty and the panel says
    "No recent apps".
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

In every style, the list closes when the finger slides down from the open
point by the cancel distance: the distance from the open point to the bottom
of the screen, minus 4 dp, kept between 12 and 32 dp
(`SwitcherCancel.distancePx`).

*   With room to spare this is the old fixed 32 dp. But the list can open
    after a rise of only 10 dp (the default "Home swipe distance"), so the
    open point can be too close to the bottom of the screen for 32 dp.
*   The 4 dp margin assumes a finger's reported centre stops a little short
    of the screen edge. That is an assumption, not a measurement; it is on
    the pending phone test.
*   For an open point 16 to 36 dp above the bottom, the cancel line is
    therefore 4 dp above the bottom of the screen, which a wobble during the
    sideways drag does not reach by accident. The 12 dp floor keeps the line
    at least 12 dp below the open point.
*   An open point under 16 dp above the edge leaves no room for 12 dp, so a
    slide cannot cancel there. The same check runs on the UP event, so a
    finger lifted while moving down may still cancel if its UP position is
    past the line. That is not unit tested (see Testing), and this band is
    listed in Risks.

**Touch mode** (the panel after the lift in Tap/Both, or from a corner):

*   Window flags: touchable `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL |
    FLAG_WATCH_OUTSIDE_TOUCH`. Hidden or held = `FLAG_NOT_TOUCHABLE` and
    alpha 0, set together by one `setWindow()` on **every** close path
    (pick, outside touch, timeout, new stream, removal), so a closed panel can
    never eat touches. A touch outside the window reaches the app below and
    sends `ACTION_OUTSIDE`, which closes the panel.
*   Grid: cells 72 × 84 dp (48 dp icon, name under it, 11 sp, one line,
    ellipsized), 12 dp padding. Columns = how many cells fit in the window
    width minus 28 dp. Rows = how many fit between the panel's bottom edge
    and the status bar, at most 6 (`SwitcherSelection.gridRowsThatFit`).
*   The Favourite apps screen stops at 24 favourites
    (`FavouriteList.MAX_FAVOURITES`). That fits as 4 columns × 6 rows in
    portrait and 8 columns × 3 rows in landscape on a 360 × 640 dp screen with the default strip
    (calculated from the cell size and unit tested, not measured on a
    phone). A smaller screen or a taller bottom strip holds fewer cells,
    and apps past the last cell are not shown (see Risks). A list saved
    with more than 24 before this limit is kept as it is and can only
    shrink.
*   Same list and order as the drag list (see Recents source in section 1);
    favourites in the user's order.
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
    `CornerReady(corner)`: one haptic tick, the bubble released. A diagonal
    that arms Home before it reaches the corner trigger also gives the arm
    tick when "Bottom app switcher" is on (see App-list trigger), so two
    light ticks are possible. The release returns `Fire(GestureAction.Corner(side))`, id
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

### 4. Landscape confirm

Applies only while "Confirm in landscape" is on (an existing setting, on by
default, not changed here) and the screen is in landscape. A held-back
gesture shows the **pill**: a small handle-like bar at the bottom centre of
the screen that asks for a second swipe.

*   **Guarded gestures.** `LandscapeConfirm.isGuarded()` is true for Home,
    Recents and every `Corner`. `admit()` runs before the post, so the
    action of a held-back first swipe is not performed. The corner list
    shortcuts are a `Corner` fire and pass `admit()` only, never
    `admitList()`.
*   **App-list drag.** The sideways drag that opens the app list is guarded
    too (`LandscapeConfirm.onOpenList`, `admitList()`), in every list style.
    Why: in Drag style the lift starts another app, which takes the user out
    of a landscape game as surely as Home does, and a list that pops up over
    a game is unwanted in the other styles as well. The first drag shows the
    pill, gives the lighter "held back" buzz and opens nothing. Its lift
    starts no app, no Home and no replayed tap.
*   **Confirming touch.** The 2 s window counts from the moment the pill
    shows. Any bottom touch that starts inside it is the confirming one,
    whatever raised the pill: a second corner swipe fires, a second drag
    (to either side) opens the list, and a pill raised by a drag lets the
    next Home swipe run, as a pill raised by Home lets the next drag open
    the list.
*   **Pill lifetime.** The pill never expires under a held confirming touch:
    no expiry timer runs while that touch is down (`wakeAtMs`). A confirming
    touch that fires or opens the list drops the pill at once. One that
    ends without doing either (a cancel, a second finger, a swipe pulled
    back before it fired) drops it only if the 2 s have passed. Inside the
    2 s the pill stays and the original window runs on; no new 2 s start.
    A rotation drops the pill.

### Settings

| Key | Type | Default |
| --- | --- | --- |
| `app_list_style` | string (enum name) | `DRAG` |
| `switcher_step_dp` | float | 72 |
| `left_corner_shortcut` | string (shortcut id) | `none` |
| `right_corner_shortcut` | string (shortcut id) | `none` |

A missing key falls back to the default, so existing installs move from 56 dp
to 72 dp. "Reset to defaults" on Gesture settings leaves all four alone: it
resets only the values that screen shows (upstream #123). Debug builds also read the
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
| More favourites than the grid holds are not shown in the touch grid (Tap, Both, corner list shortcuts) | The grid grows from the earlier fixed 3 rows to as many as fit, at most 6, and the Favourite apps screen stops at 24 (see Touch mode for where all 24 fit). On a smaller screen the last ones are still not shown; accepted, no scrolling or paging is built |
| The app list opened under 16 dp above the screen edge cannot be cancelled by sliding down | Reachable by a touch-down close to the edge and a low, flat drag (the default rise is only 10 dp, and the finger may drift back down while it drags sideways). A finger that leaves the screen downward may still cancel through the UP check (section 2). Listed for the phone test |
| In landscape the app list needs two swipes (behaviour change, on by default) | Same switch as Home ("Confirm in landscape"); turning it off restores one swipe |
| No TalkBack support in the panel | Out of scope; the feature is for motor difficulty, not low vision |
| Play declaration lists only Back/Home/Recents | The owner ships GitHub-only; update `docs/play-store/` before any Play upload |

## Testing (phone test pending)

*   Unit (JVM): `SwitcherSelectionTest` (hysteresis, jitter, unbounded index,
    grid), `CornerGestureTest` (16 cases: both corners, slow past 1000 ms,
    batched DOWN/UP, steep, flat, 30–75° bounds, short, zone edge, centre,
    `none`, no width, cancel, beats the app list, haptics),
    `CornerShortcutTest`, `LandscapeConfirmTest` (corner guarded; the app
    list held back, confirmed, and confirmed by a pill a Home swipe raised;
    the pill shown again after the window; portrait and setting-off open at
    once; no timer under a held confirming touch; a confirming touch whose
    end never arrives does not confirm a swipe made after the window), `SwitcherCancelTest` (cancel distance
    far above, 40, 25 and 15 dp above the bottom and at the very bottom;
    a slide shorter than the floor does not cancel), `RecentAppsTest` (a long
    stay in one app leaves too few, two apps are enough, the wider read only adds older apps),
    `SwitcherSelectionTest` (rows that fit; 24 favourites fit at 360 × 640
    dp in both orientations), `FavouriteListTest` (the 24 limit; a longer saved
    list can still shrink). Not unit tested: the UP check of the cancel in
    `EdgeSwitcherController`, which has no JVM test.
*   Lint `NewApi` and the rest for both flavours.
*   Emulator: `e2e/corner.sh` (corner fires, steep is Home, corner list opens
    and closes on an outside touch, Tap and Both stay open, timeout, no corner
    without a shortcut), `e2e/app-switch.sh` and `e2e/landscape-confirm.sh`
    (first landscape drag shows the pill and opens nothing, second opens the
    list).
*   Phone (pending): launching from the grid, the feel of the 72 dp step,
    screenshot without the bubble, the cancel distance near the bottom edge
    (the 4 dp margin), the arm tick and the "held back" buzz, the landscape
    app list.
