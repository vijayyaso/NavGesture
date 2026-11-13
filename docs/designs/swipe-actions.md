# Swipe actions: pick what every swipe does, and three new side swipes

*   **Author:** vijayyaso
*   **Status:** Phases 0a, 0b and A are built (2026-11-14). Phases B, C
    and D are `[not built]`.
*   **Date:** 2026-11-14

--------------------------------------------------------------------------------

## Overview

Today the code fixes what each swipe does: a side swipe is Back, a bottom
swipe is Home, a bottom swipe that pauses is Recents. Only the two bottom
corners can be given another action. This design lets the user pick the
action for every swipe, adds five actions to the list, and adds three new
kinds of side swipe: a long swipe, a swipe and wait, and separate top and
bottom halves of each side edge.

With nothing changed in settings, the app behaves exactly as it does today.

## Words used in this doc

*   **Action**: what a swipe does (Back, Screenshot, open an app, ...).
*   **Slot**: one place an action is stored, for example "left edge, top
    half, long swipe".
*   **Short swipe**: today's side swipe. Lift past the trigger distance.
*   **Long swipe**: the same swipe continued past the long line.
*   **Swipe and wait**: a side swipe past the trigger, then the finger held
    still until a tick. This doc never calls it "hold", because the code
    already uses "hold" for the bottom Recents pause, and "long press" for
    Circle to Search.
*   **Zone**: the part of a side edge a touch starts in: the whole edge, or
    its top half or bottom half when that edge is split.
*   **Trigger distance**: how far the finger must travel from its DOWN
    point before a swipe arms (the "back swipe distance" setting).
*   **Long line**: a second, larger travel from the DOWN point
    (`longSwipeDp`).
*   **Re-arm**: a side swipe pulled back inside the trigger distance
    disarms; pushed out again it arms again.
*   **Reverted**: an armed swipe released inside the trigger. Nothing runs
    and the touch is not passed on.
*   **Unused**: a touch that became no gesture. It is replayed to the app
    underneath.

## Owner's answers (2026-11-14)

Recorded in `swipe-actions-answers.md`: both "pick the action for existing
swipes" and "add new swipes"; all three new swipes; everything can be
changed, Home included, with a warning; extra actions Previous app, Back,
Home, Recents, Torch.

Decisions the owner was not asked (one question round is the rule), made
here and listed again for the owner at hand-over:

1.  The warning shows while no swipe gives Home, and separately while no
    swipe gives Back. It never blocks a choice. (The owner's words were
    "if Home and Back are both removed"; losing Home alone already strands
    a user, so the doc warns for either. Raised with the owner at
    hand-over.)
2.  Each half has its own short, long and wait slot. An edge that is not
    split shows three slots, so the default page stays small.
3.  Left and right are set separately. Both default to Back.
4.  A swipe that only passes through is never a "wait": the finger must be
    still for the wait time. A quick Back flick stays Back.
5.  Previous app with no usage access opens Recents instead, and the
    picker shows the "Needs usage access" row.
6.  A side zone whose short slot is empty but whose long slot is set gives
    a short swipe back to the app underneath (replayed), as an unused touch
    is today.
7.  With a wait slot set, Back still ticks when it arms, as today.
8.  After the wait buzz, moving the finger again cancels the wait.
9.  Installing an older build over a newer one is not supported. The old
    Back switches are read once, to migrate, and never written again.

## Constraints found in the code

Paths are under `app/src/main/kotlin/com/vijayyaso/navgesture/`.

1.  **The tracker reads "has a hold" as "is the bottom edge".**
    `holdAction` is set only for the bottom (`gesture/GestureTracker.kt:124`)
    and `holdAction == null` selects the side path at lines 244, 271 and
    312-313. A side slot that waits would fall onto the bottom path and lose
    its pull-back and re-arm.
2.  **An edge with Back off has no touch strip.**
    `service/EdgeTouchOverlay.kt:133-137` adds a side strip only when that
    edge's Back switch is on.
3.  **The bubble picks its icon by edge.** Side edges always draw a chevron
    (`service/FluidBubbleOverlay.kt:305-311`); `follow()` knows only `Armed`
    and `RecentsPrimed`.
4.  **Two action types and two runners.** `GestureAction` (Back, Home,
    Recents, Circle to Search, Corner) and `CornerShortcut` (the pickable
    list) each have a runner in `service/GestureActionPerformer.kt`. Every
    corner action waits 80 ms before it runs (`EdgeTouchOverlay.kt:305-311`):
    HyperOS needs that before an app launch, and it keeps the bubble out of
    a screenshot. The list panel is built when the bottom app-list drag is
    on or a corner holds a list (lines 112-114).
5.  **Landscape confirm guards bottom Home, Recents, corners and the
    app-list drag** (`gesture/LandscapeConfirm.kt:79-83`, `123-130`).
6.  **The arm tick is muted when a hold check is scheduled**
    (`gesture/HapticGate.kt:31`).
7.  **Settings have no version, and every save rewrites all keys**
    (`settings/GestureSettingsRepository.kt:54-78`). The old Back switches
    are read by `ui/GestureSettingsScreen.kt`, `ui/StatusBlock.kt`,
    `ui/TouchAreaAdjuster.kt` and `GestureConfiguration.withGestureSettingsOf`.
8.  **A stream can be DOWN then UP with no MOVE** (`GestureTracker.kt:322-327`).
9.  **A touch with no touch event for 5065 ms frees the strips**
    (`service/StreamHold.kt:89-91`, `EdgeTouchOverlay.kt:387-400`).
10. **Side strips cover the middle 80 % of the height**
    (`service/ScreenGeometry.kt:62`).
11. **Torch** (`CameraManager.setTorchMode`, API 23, read 2026-11-14 in the
    Android reference): no permission; throws `CameraAccessException` when
    the camera is in use; the torch goes off if the process that turned it
    on exits; state is reported through `TorchCallback`.

## Goals

1.  Every existing swipe's action can be picked: left short, right short,
    bottom swipe, bottom pause.
2.  New side swipes: long swipe, swipe and wait, and a top/bottom split,
    per edge.
3.  New actions in the one list: Back, Home, Recents, Previous app, Torch.
4.  Defaults reproduce today exactly. With every new slot empty, the
    existing tracker tests pass unchanged and no timer or delay is added to
    Back.
5.  One touch, one outcome: the icon the bubble shows at lift is the action
    that runs.
6.  The user can always get out: the Quick Settings tile turns gestures
    off, and turning them off brings the system bar back.

## Non-goals

*   Circle to Search, the sideways app-list drag and the corner diagonals
    keep their fixed triggers. Corners gain only the new actions.
*   No per-edge distances or times. One long distance and one wait time
    serve both sides.
*   No media or volume actions (not picked).
*   No new swipe kinds on the bottom edge.

## Design

### One action type (`gesture/GestureModel.kt`)

`CornerShortcut` is renamed `SwipeAction`. Its stored ids do not change, so
saved corners keep working. It gains five fixed entries: `Back` (`back`),
`Home` (`home`), `Recents` (`recents`), `PreviousApp` (`previous_app`),
`Torch` (`torch`). Each entry declares what the overlay needs to know, as
properties on the type and not as `when` lists spread over the service:

*   `settles`: it waits 80 ms before running: open app, app shortcut,
    previous app and Screenshot. Every corner action keeps the settle
    whatever it is, as today.
*   `needsListPanel`: recents list, favourites list.
*   `guardedInLandscape`: everything except `None`.
*   `icon`: which glyph the bubble draws (back chevron, home, recents
    squares, and one generic dot-in-ring for every other action).

`GestureAction` keeps `CircleToSearch` and becomes otherwise a thin
carrier: `GestureAction.Run(slot, action)`. `GestureAction.Back/Home/
Recents/Corner` are removed. `Run.id`, which the log and the e2e suites
read, is: `Corner<SIDE>` for a corner slot (as today); `Back`, `Home` or
`Recents` when the action is one of those (as today, capitals kept, also
in failure texts); and the action's stored id for anything else
(`GESTURE_FIRED LEFT notifications`).

`GestureActionPerformer` has one entry point, `perform(action: SwipeAction)`,
replacing `perform(GestureAction)` and `performShortcut`.

### Slots (`gesture/SwipeSlots.kt`, new)

```
SlotId = edge (LEFT | RIGHT | BOTTOM)
       + zone (WHOLE | TOP_HALF | BOTTOM_HALF)  side edges only
       + kind (SHORT | LONG | WAIT | PAUSE)     PAUSE is bottom only
       or CORNER_LEFT | CORNER_RIGHT            the two existing corners
```

`SwipeSlots` is an immutable map from `SlotId` to `SwipeAction` with these
defaults: side `SHORT` = Back for both edges and every zone; bottom `SHORT`
= Home; bottom `PAUSE` = Recents; everything else `None`. It also holds
`leftSplit` and `rightSplit` (default off). A touch on an edge that is not
split reads the `WHOLE` zone; on a split edge a DOWN whose y is above the
strip's centre reads `TOP_HALF`, and one at or below it `BOTTOM_HALF`.
The corner slots stay in `GestureConfiguration` as `leftCornerShortcut` and
`rightCornerShortcut`, under their existing keys; `SwipeSlots` holds the edge
slots, and `givesHome` and `givesBack` read both.
The two halves keep their own stored values, so switching the split off and
on loses nothing.

`GestureConfiguration` gains `slots: SwipeSlots`, `longSwipeDp` (default
120, range 80-240) and `waitMs` (default 350, range 200-800).
`leftEdgeBackEnabled` and `rightEdgeBackEnabled` are removed from the
model; the three screens that read them ask the slots instead
(`slots.edgeInUse(edge)`).

### Stored settings and migration (`settings/GestureSettingsRepository.kt`)

*   One string key per slot, named with the zone from the start:
    `slot_left_whole_short`, `slot_left_top_long`, ..., `slot_bottom_short`,
    `slot_bottom_pause`. Keys `left_split`, `right_split`, `long_swipe_dp`,
    `wait_ms`, and `slots_version` (written as 1).
*   **Reading with no `slots_version`** (an install from before this
    change): `left_edge_back_enabled = false` becomes left `WHOLE`,
    `TOP_HALF` and `BOTTOM_HALF` `SHORT = None`; the same for the right.
    Everything else takes its default. The first save writes
    `slots_version` and removes the two old keys.
*   Installing an older build over this one is not supported: it would
    read its defaults for the two switches.
*   An unknown stored id decodes to `None`, as today.
*   `withGestureSettingsOf` (Reset and its Undo) copies `slots`, the two
    split flags, `longSwipeDp` and `waitMs`.
*   Debug builds read `navgesture_slot_<key>` global settings in place of a
    stored slot, as the corner overrides do today, so the e2e suites can
    set a slot without the UI. A save starts from the stored values without
    any override, so an override is never written.

### Gesture grammar (`gesture/GestureTracker.kt`)

Phase 0 restructures the tracker without changing behaviour. Every
`holdAction` test (lines 221, 244, 271, 312, 313) becomes `edge == BOTTOM`.
`backEnabled` (lines 114-119, 218, 326) becomes "the touched zone has a
slot set". Actions come from `configuration.slots` for the zone resolved
at DOWN.

Order of the tests on one stream, first match wins:

1.  **Corner diagonal** (bottom, as today).
2.  **Long press on the handle**, Circle to Search (bottom, as today).
3.  **Bottom edge**: app-list drag, pause past the Recents line, pull-back,
    flick: all as today. Only the two actions come from slots.
4.  **Side edge**: the rules below.
5.  `Cancel`, a second finger, and the arm time limit: as today. The arm
    time limit applies only before arming, so it never cuts a wait short.

The existing states keep their meaning. `Switcher`, `Fired`, `Corner` and
`longPressPending` are bottom-only and unchanged. `Rising` and `Retracted`
gain the band and the still clock described below. `Primed` is the bottom
pause.

#### Bottom-edge rules

*   If bottom `SHORT` is `None` the bottom still arms, because the pause
    and the list drag need the rise. Every path that would fire `SHORT`
    (a plain release, the flick out of `Primed`, DOWN then UP) returns
    `Unused(SLOT_EMPTY)` instead, and the touch is replayed to the app.
*   If bottom `PAUSE` is `None`, no pause check is scheduled, a pause is a
    plain short swipe, and the bottom arm ticks as a side arm does.

#### Side-edge rules

*   **Travel.** The trigger distance and the long line are both travel
    from the DOWN point along the swipe axis (`travelFrom`), as the trigger
    is today.
*   **Arming.** A side edge arms when any slot of the touched zone is set.
    A whole swipe on a zone with nothing set ends as `Unused(SLOT_EMPTY)`.
*   **Bands.** The pending action is re-read on every MOVE:

    | Travel from DOWN | Pending action |
    | :-- | :-- |
    | under the trigger distance | nothing; a lift here is `Reverted`, as today |
    | past the trigger, under the long line | SHORT |
    | past the long line, LONG slot set | LONG |
    | past the long line, LONG slot empty | SHORT |

    Pulling back under the long line returns to SHORT. Pulling inside the
    trigger disarms, and pushing out re-arms, as today.
*   **Wait.** Only when the zone's WAIT slot is set. The still anchor is
    the sample that armed or re-armed the swipe; a MOVE more than the
    platform touch slop from the anchor becomes the new anchor. The side
    edge uses the bottom pause's per-frame poll (`Armed(scheduleHoldMs)`,
    `onHoldElapsed`, `RearmHold`), because a still finger sends no MOVE.
    `onHoldElapsed` makes WAIT the pending action when
    `now - anchor.timestampMs >= waitMs` and the swipe is not retracted.
    It is judged from timestamps only. A new anchor after that (the finger
    moved on) returns to the band rule. A retract stops the poll; a re-arm
    starts it again with a new anchor. With the WAIT slot empty no poll is
    ever requested.
*   **Lift.** The pending action at the lift point runs. If it is `None`
    (SHORT empty and the finger under the long line) the stream ends as
    `Unused(SLOT_EMPTY)` and is replayed. A stream longer than the replay
    limits (`MAX_SAMPLES` 400, `MAX_REPLAY_MS` 3 s in
    `EdgeTouchOverlay.kt`) is dropped, as any over-long unused touch is.
*   **DOWN then UP with no MOVE.** Judged by `hasArmed` within
    `maximumArmDurationMs`, as today, then by the band rule at the release
    point. WAIT never fires on this path.
*   **No touch events for 5065 ms.** The strips are freed and the stream
    dropped, as today.

#### What the tracker reports

*   `TrackerAction.Armed` gains `pending: SwipeAction`, the action a lift
    would run now (`None` allowed).
*   `TrackerAction.Pending(action, kind)` is sent only for a later change:
    a band change, a wait reached or left, or the bottom pause. It
    replaces `RecentsPrimed`, which becomes `Pending(<pause action>, PAUSE)`.
*   `Fire` carries `GestureAction.Run`.

The grammar has one table-driven JVM test (`SwipeGrammarTest`) with a row
for each rule above. The existing tracker tests change only their expected
values: `GestureAction.X` becomes `Run(slot, SwipeAction.X)` and
`RecentsPrimed` becomes `Pending(Recents, PAUSE)`.

### Overlay (`service/EdgeTouchOverlay.kt`, `FluidBubbleOverlay.kt`, `HapticGate.kt`, `LandscapeConfirm.kt`)

*   A side strip is installed when `slots.edgeInUse(edge)`: any slot of
    that edge, in a zone its split setting can reach, is not `None`.
*   The bubble shows on `Armed` with the icon of `Armed.pending`, and
    changes icon on each `Pending`, with the swap the Recents pause uses
    today. While the pending action is `None` the bubble draws no icon.
*   Haptics (`HapticGate`, still keyed on `TrackerAction`):
    *   `Armed` on the bottom with a `PAUSE` slot set stays quiet, as today.
    *   `Armed` on a side edge ticks, with or without a WAIT slot, unless
        `Armed.pending` is `None`.
    *   `Pending` ticks for kind PAUSE (as `RecentsPrimed` does today) and
        for entering LONG; entering WAIT gives the stronger confirm buzz;
        falling back to SHORT is silent.
    *   `Fire` ticks only when nothing ticked before it, as today.
*   The overlay removes its pending hold callback on `Disarmed` and before
    each new post, so polls never stack.
*   Running an action goes through one path. Every corner `Run` keeps the
    80 ms settle; a side or bottom `Run` takes it when its action
    `settles`. `needsListPanel` actions open the panel. The panel is built
    when the bottom app-list drag is on, or any slot or corner holds a list.
*   Landscape confirm guards every bottom-edge `Run` whose action is
    `guardedInLandscape`, and the app-list drag, as today. Side swipes are
    not guarded, as today.

### New actions (`service/GestureActionPerformer.kt`, `service/Torch.kt` new)

*   **Back, Home, Recents**: the global actions already used.
*   **Previous app**: asks `AppSwitcher.recentApps()` off the main thread
    (the existing shortcut thread), takes the first package that is not the
    app in front (`RecentList.currentFirst` says whether entry 0 is), and
    launches it through `AppSwitcher.launch` on the settle path. With no
    usage access, or an empty list, it performs Recents.
*   **Torch**: `Torch` registers one `TorchCallback` when the service
    starts and removes it when the service ends. It remembers the first
    back camera whose `FLASH_INFO_AVAILABLE` is true and the last reported
    on/off state. `toggle()` calls `setTorchMode(id, !on)`; a
    `CameraAccessException` or a phone with no flash is recorded as the
    last failure and does nothing else. No manifest permission is added.

### Screens (`ui/`)

*   **Gesture settings**, group **Swipes**, replaces the two Back switches:
    three rows, Left edge, Right edge, Bottom edge. Each shows a one-line
    summary of its actions and opens that edge's page. Under them the
    warning rows (below), then **Corner shortcuts** as today.
*   **Edge page** (`SwipeEdgeScreen.kt`, new; one composable used by three
    `Screen` entries, `LEFT_EDGE`, `RIGHT_EDGE` and `BOTTOM_EDGE`, because
    `Screen` is an enum saved by name). Side edge: one row per slot, named "Swipe in", "Swipe far
    in" and "Swipe in and wait"; a switch "Top and bottom do different
    things", which shows the three rows twice under "Top half" and "Bottom
    half". Bottom edge: "Swipe up" and "Swipe up and pause". A row shows
    its action's label and opens the picker.
*   **Distances** stay on Gesture settings under Distance and timing: two
    new sliders, "Far swipe distance" and "Wait time", shown only while any
    long or wait slot is set.
*   **Picker**: `CornerShortcutPicker` becomes `SwipeActionPicker`, taking a
    `SlotId`. Its string names and test tags (`corner_none`,
    `corner_app_...`, `row_left_corner`, ...) stay, because two e2e suites
    use them. The list gains the five actions. Previous app
    shows the "Needs usage access" row while access is missing. Each
    slot's picker is its own `Screen` entry (`LEFT_SWIPE_IN`,
    `RIGHT_SWIPE_IN`, `BOTTOM_SWIPE_UP`, `BOTTOM_SWIPE_UP_PAUSE`), as the
    two corner pickers are, because a screen is saved by its name alone.
*   **Warning**: on Gesture settings and on Home's Gestures tile summary,
    while no slot or corner holds Home ("No swipe goes Home") and while
    none holds Back ("No swipe goes Back"). The text names the way out:
    the NavGesture tile in Quick Settings turns gestures off and brings the
    phone's own bar back. It never blocks a choice.
*   The touch-area adjuster and the status card read `edgeInUse`, and name
    each side by its SHORT action's label where they say "Back" today. The
    status card names the bottom edge's two actions the same way.

### Logs

`GESTURE_FIRED <EDGE> <id>` keeps its form. The debug line
`RECENTS_PRIMED` becomes `PENDING <kind> <id>`. `SWIPE_ACTION <id>` replaces
`CORNER_SHORTCUT <id>` for every action, and `e2e/corner.sh` and
`e2e/app-shortcuts.sh` move to it in the same commit. Phase 0a logs it only
for the actions that logged `CORNER_SHORTCUT` before (not Back, Home,
Recents or an opened app), so that phase adds no log line.

## Alternatives considered

*   **Extend `CornerShortcut` under its name.** Rejected: a type named for
    corners would carry every edge's action, and Back, Home and Recents
    would exist in two types with two runners.
*   **Keep `GestureAction.Back/Home/Recents` and resolve other actions in
    the overlay.** Rejected: the guard, settle delay and icon would stay
    keyed on two types; the reviewers found three places that would miss
    the new slots.
*   **Per-edge distances and wait times.** Rejected: more sliders for no
    asked-for gain; one shared side distance exists today.
*   **A bend along the edge after arming in place of long and halves.**
    Rejected: a thumb arcs by itself, and the owner picked the three named
    swipes.
*   **A small menu of icons on wait.** Rejected: a new overlay and a slower
    gesture; one action per slot is what was asked.
*   **Trial period after moving Home** (old setup returns unless the new
    Home swipe is done in 10 s). Rejected for now: the tile is a tested way
    out, and the owner chose "warn but allow".
*   **Halves split only the short swipe.** Rejected: the owner picked
    halves with their own actions; the split switch keeps the default page
    at three rows.

## Risks

*   **Misfires.** An overshot Back becomes the long action; a Back that
    hesitates becomes the wait action. Mitigation: both slots are empty by
    default, the long line starts at 120 dp, stillness must last `waitMs`,
    and the bubble shows what will run before the lift.
*   **Apps lose side touches they get today** on an edge that had Back off
    and now has a long or wait slot: the strip is back. Short swipes there
    are replayed, but replay is not a perfect touch.
*   **Lock-out** when Home and Back are removed and the gesture bar is
    hidden. The tile turns the service off and the bar returns
    (`navigation/GestureBarHider.kt:104-113`); the warning says so. A user
    who never added the tile can still open NavGesture from the launcher if
    any app can be reached, or use the notification shade.
*   **Tracker regressions.** Phase 0 changes structure under every gesture.
    Proof needed: existing tracker tests unchanged apart from renames, and
    every e2e suite green before any new behaviour is added.
*   **Torch state drift** if another app turns the torch off: covered by
    the callback.

## Detailed Implementation Plan

Each phase ends green (unit tests, lint, all e2e suites) with defaults
unchanged, and is committed on its own.

### Phase 0a: one action type, one runner. No visible change

*   `gesture/GestureModel.kt`: rename `CornerShortcut` to `SwipeAction`;
    the five new entries exist and decode, but are NOT in the picker's
    `fixed` list yet; the properties `settles`, `needsListPanel`,
    `guardedInLandscape`, `icon`; `GestureAction.Run`; `SlotId` (with the
    two corner slots); `TrackerAction.Armed.pending` and
    `TrackerAction.Pending`.
*   `gesture/GestureTracker.kt`: emits `Run` and `Pending`; rules unchanged.
*   `gesture/HapticGate.kt`, `gesture/LandscapeConfirm.kt`: keyed on the
    new shapes; behaviour unchanged.
*   `service/GestureActionPerformer.kt`: one `perform(SwipeAction)`; Back,
    Home, Recents run as they do today; `SWIPE_ACTION` log.
*   `service/EdgeTouchOverlay.kt`: one run path with the settle and panel
    rules above. `service/FluidBubbleOverlay.kt`: icon from the pending
    action. `service/NavGestureAccessibilityService.kt`: the renamed calls.
*   `ui/CornerShortcutPicker.kt`, `ui/GestureSettingsScreen.kt`,
    `ui/MainActivity.kt`, `ui/BackStack.kt`,
    `settings/GestureSettingsRepository.kt`: renames only.
*   Tests, expected values only: `GestureTrackerTest`,
    `EdgeSwitcherGestureTest`, `CircleToSearchGestureTest`,
    `CornerGestureTest`, `HapticGateTest`, `LandscapeConfirmTest`,
    `CornerShortcutTest`.
*   `e2e/corner.sh`, `e2e/app-shortcuts.sh`: `CORNER_SHORTCUT` becomes
    `SWIPE_ACTION`.

### Phase 0b: the tracker stops reading "has a hold" as "is the bottom". No visible change

*   `gesture/GestureTracker.kt`: every `holdAction` test becomes
    `edge == BOTTOM`; `backEnabled` stays on the two booleans for now.
*   `service/EdgeTouchOverlay.kt`: hold callback removed on `Disarmed` and
    before each post.
*   Tests: none change.

### Phase A: slots, and picking the action for today's swipes

*   `gesture/SwipeSlots.kt` (new): `SwipeSlots`, defaults, `edgeInUse`,
    zone resolution, `givesHome`, `givesBack`.
*   `gesture/GestureModel.kt`: `slots`, `longSwipeDp`, `waitMs` on the
    configuration; the two Back booleans removed; reset copies the new
    fields; the five actions join the picker list.
*   `settings/GestureSettingsRepository.kt`: slot keys, `slots_version`,
    migration, debug `navgesture_slot_<key>` overrides.
*   `gesture/GestureTracker.kt`: actions from slots; arming by "any slot
    set"; bottom `SHORT`/`PAUSE` of `None`; side `SHORT` of anything;
    `Unused(SLOT_EMPTY)`.
*   `service/GestureActionPerformer.kt`: Previous app.
*   `service/EdgeTouchOverlay.kt`: strip rule; panel rule.
*   `ui/SwipeEdgeScreen.kt` (new), `ui/SwipeActionPicker.kt` (renamed from
    `CornerShortcutPicker.kt`), `ui/GestureSettingsScreen.kt` (Swipes
    group, warning rows; the two Back switches go), `ui/HomeScreen.kt`
    (tile summary warning), `ui/StatusBlock.kt`, `ui/TouchAreaAdjuster.kt`,
    `ui/MainActivity.kt` + `ui/BackStack.kt` (three `Screen` entries),
    `ui/TestTags.kt`, `res/values/strings.xml`.
*   Tests: `SwipeSlotsTest` (defaults, migration, encode/decode, reset);
    `SwipeGrammarTest` rows for these rules; `GestureSettingsResetTest`
    rewritten for slots; `BackStackTest` for the new entries; warning rule
    test.
*   `e2e/swipe-actions.sh` (new, registered in `e2e/run.sh`; skips on a
    release APK; its exit trap deletes the overrides): left short set to
    Home fires Home; bottom swipe set to Notifications; the warning shows
    when Home is removed and goes when it is restored.

### Phase B: long swipe and top/bottom halves

*   `gesture/GestureTracker.kt`: bands, zone at DOWN.
*   `gesture/HapticGate.kt` + `service/EdgeTouchOverlay.kt`: tick on LONG.
*   `ui/SwipeEdgeScreen.kt`: "Swipe far in" rows and the split switch;
    `ui/GestureSettingsScreen.kt`: "Far swipe distance" slider.
*   Tests: grammar rows for bands, pull-back across the long line, DOWN-UP
    past the long line, zone choice at the centre.
*   `e2e/swipe-actions.sh`: a long left swipe runs its action and a short
    one still goes Back; top and bottom halves run different actions.

### Phase C: swipe and wait

*   `gesture/GestureTracker.kt`: still anchor, poll on arm and re-arm.
*   `service/EdgeTouchOverlay.kt`: poll wiring for side edges, confirm
    buzz; `ui/SwipeEdgeScreen.kt`: "Swipe in and wait" rows;
    `ui/GestureSettingsScreen.kt`: "Wait time" slider.
*   Tests: grammar rows for wait, move-after-wait, retract during wait, no
    poll when the slot is empty; `HapticGateTest` for the side arm tick.
*   `e2e/swipe-actions.sh`: swipe in, wait, lift runs the wait action; a
    quick swipe on the same edge still goes Back.

### Phase D: Torch

*   `service/Torch.kt` (new), started and stopped in
    `service/NavGestureAccessibilityService.kt`;
    `service/GestureActionPerformer.kt`; the picker list; strings.
*   Tests: the toggle rule with a fake camera seam (on, off, in use, no
    flash).

### Docs

`docs/designs/tap-list-and-corner-shortcuts.md`,
`corner-picker-and-app-shortcuts.md` and `ui-redesign.md` get the renames
and the new Gesture settings groups in the phase that changes them.
