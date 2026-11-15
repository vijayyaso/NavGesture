# First-run practice demo

*   **Author:** vijayyaso (the owner; one person)
*   **Status:** Approved (owner's answers of 2026-11-15); not built
*   **Date:** 2026-11-15
*   **Revised:** 2026-11-15, after the design review. Changes: settings per
    step, real-size Recents line, runtime inset, one result per touch, the
    first-run decision, when the demo is marked as seen.

--------------------------------------------------------------------------------

## Overview

A new user opens NavGesture and sees a status block and a setup checklist. The
app never shows what a swipe looks like before it asks for the Accessibility
permission. This design adds a **practice demo**: a phone drawn inside the
app. The user swipes on the drawn phone and the app shows what each swipe
does. The demo needs no permission, because the swipes are read by the app's
own screen and nothing leaves it.

Owner's answers (2026-11-15): a practice screen, not slides; shown on first
open, before setup, and skippable; teaches the seven gestures listed below;
can be opened again from a row on Home.

## Terms

*   **Service:** NavGesture's Accessibility service. When on, it puts thin
    touch **strips** on the left, right and bottom screen edges. Default 20 dp
    wide; the user can set 8 to 64 dp.
*   **`GestureTracker`** (`gesture/GestureTracker.kt`): the existing class
    that reads one finger on one edge and decides which gesture it is. It has
    no Android types and is tested on a plain JVM (Java runtime, no phone).
*   **Tracker results** (`TrackerAction` in `gesture/GestureModel.kt`):
    `Armed` (far enough; a release would now run something), `Pending` (what
    a release would run has changed), `RearmHold` (poll again), `Disarmed`
    (pulled back), `OpenSwitcher` (the app list opens), `CornerReady`,
    `Fire` (run this action), `Reverted` and `Unused(reason)` (no gesture).
*   **Hold poll:** a still finger sends no move event. So while a finger is
    down, the driver calls `onHoldElapsed(nowMs)` every frame. This is how a
    pause is seen.
*   **Slot:** one place an action can be set. A side edge has `SHORT`,
    `LONG` and `WAIT` slots; the bottom has `SHORT` and `PAUSE`; each bottom
    corner has one.
*   **Setup done:** `model.checklist.essentialsDone` is true.
*   **Disclosure:** the consent screen Google Play requires before the
    Accessibility settings open.
*   **Flavors:** the two builds, `github` and `play`.

The seven gestures, as the real defaults and the demo both define them:

| # | Gesture | Finger path | Real setting |
| :--- | :--- | :--- | :--- |
| 1 | Back | Start on the left or right edge. Move inward at least 24 dp. Lift. | On by default. |
| 2 | Home | Start on the bottom edge. Move up at least 10 dp. Lift while moving. | On by default. |
| 3 | Recent apps | Start on the bottom edge. Move up past the Recents line (10.4 % of the real screen height). Stop. Lift. | On by default. |
| 4 | App list | Start on the bottom edge. Move up, then drag right at least 40 dp. Slide to pick. Lift. | On by default ("App switching"). |
| 5 | Corner shortcut | Start in a bottom corner. Move up and inward on a diagonal, at least 32 dp. Lift. | Optional ("Bottom-left corner", "Bottom-right corner"). |
| 6 | Swipe and wait | As Back, then hold still for 350 ms. Lift. | Optional ("Left edge", "Right edge"). |
| 7 | Long swipe | As Back, but move inward past the long line (120 dp). Lift. | Optional ("Left edge", "Right edge"). |

## The Problem

*   The gestures only work after the setup steps. A new user must trust the
    app before seeing any result.
*   Gestures 4 to 7 are never explained in the app. A user finds them only by
    opening every settings page.

## Goals

*   A fresh install opens on the demo, unless the Control Center tile opened
    the app (CUJ 6). The user can finish or skip it without any permission.
*   The demo teaches the seven gestures above, one per step, in that order.
*   A practice swipe is judged by the same `GestureTracker` code and the
    same thresholds as the real service at its default settings. The two
    stated differences are in "Demo settings" below.
*   A user is never stuck: the whole demo can be skipped at any time, and a
    step can be skipped after two tries that did not pass.
*   A user whose service is on, or who accepted the disclosure before, is not
    shown the demo by itself.
*   The demo can be opened again from Home at any time.

## Non-Goals

*   No real action runs. The demo never presses Back, never opens an app, and
    never turns the real flash on.
*   The demo does not change any saved setting. It reads one: the edge width,
    for layout only.
*   The demo does not replace the setup checklist or the disclosure.
*   No Circle to Search step and no landscape "swipe twice" step.
*   The demo does not draw the real fluid bubble or the real app-list panel.
    Both are overlay windows owned by the service.
*   No translations. The app has one `values/` string set.

## Open Questions

None. Decisions made during review are in the text: the demo is marked as
seen when it first shows (section 5); after the tile path the demo does not
show later, because that user accepts or declines the disclosure and can use
the Home row (CUJ 6).

## Critical User Journeys (CUJs)

### CUJ 1: New user finishes the demo

*   **Given:** a fresh install; the service is off.
*   **When:** the user opens the app from its icon.
*   **Then:** the demo shows "Step 1 of 7". Each correct swipe plays its
    effect on the drawn phone and moves to the next step. After step 7 a
    finish card offers "Set up now" and "Done". "Set up now" opens the Setup
    screen with Home under it. The demo does not show by itself again.

### CUJ 2: New user skips

*   **Given:** the demo is showing on first open.
*   **When:** the user taps "Skip", taps the back arrow, or presses Back.
*   **Then:** Home shows. The demo does not show by itself again.

### CUJ 3: User is stuck on one step

*   **Given:** step 3 (Recent apps) is showing.
*   **When:** the user makes two tries that do not pass. A try is a miss (no
    gesture) or another gesture (for example a plain Home swipe).
*   **Then:** after each try a hint says what happened (for example "That
    was Home. Swipe up, then stop before you lift"). After the second try a
    "Skip this step" button appears. It moves to step 4.

### CUJ 4: Existing user upgrades

*   **Given:** the service is on, or the disclosure was accepted before.
*   **When:** the user opens the new version.
*   **Then:** Home shows as before. The "Try the gestures" row is on Home.

### CUJ 5: Replay

*   **Given:** any state, Home showing.
*   **When:** the user taps "Try the gestures".
*   **Then:** the demo opens at step 1. The finish card shows "Set up now"
    only while setup is not done. With the service on, a practice swipe runs
    no real action, whatever the saved edge width.

### CUJ 6: Tile opens the disclosure on a fresh install

*   **Given:** a fresh install; the user taps the Control Center tile first.
*   **When:** the app opens with the disclosure extra, also when that intent
    arrives while the saved record is still loading.
*   **Then:** Home with the disclosure on top shows, as today. The demo does
    not cover it and is marked as seen.

### CUJ 7: Rotation during the demo

*   **Given:** step 4 is showing after one failed try.
*   **When:** the screen rotates.
*   **Then:** step 4 still shows, with the same try count.

## Technical Plan (Architecture)

### Overview of parts

```text
 finger on the drawn phone (Compose pointer input, local px, uptime ms)
        |
        v
 DemoEngine  (demo/, no Android types, JVM tested)
   - picks the edge at DOWN
   - feeds one GestureTracker per edge (settings of the current step)
   - runs the hold poll from frame(nowMs)
   - follows the app-list drag
   - gives at most one result per touch
        |  DemoEvent
        v
 DemoProgress (demo/, no Android types, JVM tested)
   - current step, tries, hint, celebrating, finished
        |
        v
 DemoScreen (ui/)  draws the phone, the effect, the text, the buttons
```

**Cost accepted:** `DemoEngine` is a second driver of the tracker. The first
is `TouchCaptureView` in `service/EdgeTouchOverlay.kt`. That class is tied to
windows, haptics, replay of unused touches and the stream hold, so sharing it
would mean a refactor of the service's touch path for a teaching feature.
The engine copies three small rules from it (hold poll, second-finger
cancel, app-list hand-off). A parity test guards the recogniser itself (see
tests).

### 1. The drawn phone, its place and its coordinates

*   The drawn phone is a rounded box, 9 wide to 19 high. Width =
    `min(320 dp, window width − 2 × inset)`, and at least 200 dp. Its size
    does not depend on the hint text: the text area above it has a fixed
    minimum height of three lines.
*   **Inset** (the gap to the real left and right edges), computed at
    runtime, per side: `max(40 dp, system gesture inset, strip) + 8 dp`.
    `strip` is the saved `edgeWidthDp` plus the navigation-bar inset on that
    side when the service is not `OFF`, else 0. Reason: on a replay the
    service's own strips are live under the demo, up to 64 dp wide, and a
    practice swipe that starts on one would run a real Back.
*   The demo stays in the shared `verticalScroll` column of `MainActivity`,
    so a short window (landscape, split screen, large font) scrolls and no
    button is cut off. The drawn phone consumes every pointer change it gets,
    so a swipe on it never scrolls the page. The button row sits between the
    drawn phone and the real bottom edge.
*   Samples are in the drawn phone's **local pixels**. The tracker gets
    `screenWidthPx` = the drawn width, so corner zones sit on the drawing
    (zone = `min(12 % of width, 56 dp)`). It gets `screenHeightPx` = the
    **real display height**, so the Recents line, the tail gap and the lift
    are the real sizes. Everything else in the tracker is fixed in dp. With
    this, the same finger path gives the same result in the demo and for
    real. `screenHeightPx / 2` is also the split point of a side edge; the
    demo never splits sides, so this has no effect.
*   Time: each sample uses the pointer event's uptime. The frame poll reads
    `SystemClock.uptimeMillis()`. The real overlay pairs the same two clocks.
*   A DOWN is ignored until the screen has settled (`LocalScreenSettled`),
    because a node that is still sliding in reports a still finger as moving.

### 2. Demo settings, per step

`DemoConfiguration.forStep(step, widthDp)` returns a `GestureConfiguration`.
Steps 1 to 4 use the defaults unchanged. A step adds only what it teaches,
so a slow Back swipe in step 1 is still Back, as it is for real.

| Step | Change to the defaults |
| :--- | :--- |
| 1 to 4 | None. (`edgeAppListEnabled` is true and `swapAppLists` false by default.) |
| 5 | `leftCornerShortcut` and `rightCornerShortcut` = `Notifications`. |
| 6 | `SlotId.Edge(LEFT, WHOLE, WAIT)` and `(RIGHT, WHOLE, WAIT)` = `Screenshot`. |
| 7 | `SlotId.Edge(LEFT, WHOLE, LONG)` and `(RIGHT, WHOLE, LONG)` = `Torch`. No wait action. |

Two demo-only differences from the real service, stated to the reader here
and nowhere hidden:

*   **Start band:** the engine accepts a DOWN within 28 dp of a drawn edge
    (the real default strip is 20 dp). The band only chooses the edge; the
    tracker does not read it. The bands run the full length of each drawn
    edge; real side strips cover the middle 80 %.
*   **Long line:** `longSwipeDp = min(120, 0.45 × widthDp)`, so a long swipe
    fits on a 200 dp drawing. At 267 dp and wider it is the real 120.

The engine builds new trackers when the step changes, and only while no
finger is down.

### 3. `DemoEngine`

Constructor: drawn width and height in px, real display height in px,
density, and the configuration. It owns three trackers. Each is built with
`screenWidthPx` and `screenHeightPx` as in section 1 and `handleCenterXPx`
null. (The real overlay passes the screen centre for the bottom strip; it
only matters for Circle to Search, which is off, so `ScheduleLongPress`
never arrives and is ignored.)

*   `down(sample)`: picks the edge. Bottom band first (y ≥ height − band),
    then left (x ≤ band), then right (x ≥ width − band). A touch elsewhere
    gives `Missed(NOT_ON_EDGE)`.
*   `move(sample)`, `up(sample)`, `cancel()`: forwarded to the active tracker.
    A second finger gives `Missed(CANCELLED)`.
*   **One result per touch:** after the first `Performed` or `Missed`, the
    engine ignores everything until all fingers are up and a new DOWN comes.
    The tracker itself can report `Unused` twice for one slow swipe (once
    from `onMove`, once from `onUp`); the real overlay guards this with its
    `abandoned` flag.
*   `frame(nowMs)`: called once per frame while a finger is down. The engine
    keeps a "poll due at" time. It is set from `Armed.scheduleHoldMs`,
    `RearmHold.delayMs` and `Pending.scheduleHoldMs`. It is cleared on
    `OpenSwitcher`, `CornerReady`, `Fire`, `Disarmed` and `Unused`, as the
    overlay does. When `nowMs` reaches it, the engine calls
    `onHoldElapsed(nowMs)` and handles the result like any other.
*   App list: on `OpenSwitcher` the engine stops feeding the tracker. It
    measures sideways travel from `OpenSwitcher.origin` (the sample where
    the list opened) with `SlideTravel(adaptive = true)`, as the real
    `EdgeSwitcherController` does, then
    `SwitcherSelection.steps(travel, switcherStepDp, previous)` and
    `indexFor(steps, 4)`. The demo list has four fixed apps; the list loops,
    so every app can be reached on a narrow drawing. UP gives
    `Performed(AppPicked(index))`; the tracker returns nothing on that UP,
    so the engine makes the event itself. A slide back down cancels the
    list, by the same `SwitcherCancel` rule the real list uses, and gives
    `Missed(CANCELLED)`. If `recents` is false (a drag to the
    left, which opens favourites for real), UP gives
    `Performed(FavouritesOpened)`.

Events returned (a list per call, usually empty or one item):

*   `Ready(action)`: from `Armed.pending`, `Pending.action`, and
    `CornerReady` (the corner's action). The UI names the action a release
    would run.
*   `Unready`: from `Disarmed`. The UI clears that name.
*   `ListOpened`, `ListMoved(index)`: the UI shows and moves the icon row.
*   `Performed(result)`: `result` is a `DemoResult`: `Ran(run:
    GestureAction.Run)`, `AppPicked(index)` or `FavouritesOpened`.
*   `Missed(reason)`: `reason` is a `DemoMiss`: `NOT_ON_EDGE`, `CANCELLED`,
    `PULLED_BACK` (from `Reverted`), or `Rejected(RejectionReason)`.

### 4. Steps and `DemoProgress`

| # | Step | Passes when the result is | Effect on the drawn phone |
| :--- | :--- | :--- | :--- |
| 1 | Back | `Ran`, action `Back` | The detail page slides away; the list page shows. |
| 2 | Home | `Ran`, action `Home` | The app shrinks to its icon on a home grid. |
| 3 | Recent apps | `Ran`, action `Recents` | The app becomes a card between two others. |
| 4 | App list | `AppPicked` | A row of four icons; the picked one opens. |
| 5 | Corner shortcut | `Ran`, slot is `SlotId.Corner` | A notification shade drops from the top. |
| 6 | Swipe and wait | `Ran`, slot is `SlotId.Edge` with kind `WAIT` | A white flash and a small screenshot thumbnail. |
| 7 | Long swipe | `Ran`, action `Torch` | A torch icon lights; the page gets a warm tint. |

Each effect is drawn with plain Compose shapes and the app's existing icons.
On each step the start band (or corner) is highlighted and a still arrow
shows the path. The arrow hides while a finger is down. There is no looping
animation.

`DemoProgress` is an immutable value:

```text
DemoProgress(step: DemoStep?, tries: Int, hint: DemoHint?, celebrating: DemoResult?)
  finished: Boolean        = step == null
  canSkipStep: Boolean     = tries >= 2
  after(event): DemoProgress   // only Performed and Missed change it
  advance(): DemoProgress      // next step, tries 0, no hint; after step 7, finished
```

*   A `Performed` that passes the step: `celebrating` is set. The UI plays
    the effect for 900 ms, then calls `advance()`. While `celebrating` is
    set, the UI sends no touches to the engine.
*   Any other `Performed`: `tries + 1`. The UI plays that effect briefly and
    the hint names it. `DemoHint.Other(result)`.
*   A `Missed`: `tries + 1`, `DemoHint.Miss(reason)`.
*   Hint text is chosen in the UI by one `when` over the hint and the step.
    Per-step texts exist for the common cases (Home in step 3; Back in steps
    6 and 7; `FavouritesOpened` in step 4: "Drag to the right. Left opens
    your favourite apps"); the rest use one text per reason.
*   "Skip this step" shows when `canSkipStep` is true. It also shows at once
    when touch exploration (a screen reader such as TalkBack) is on, because
    that user cannot draw these swipes.
*   `step` and `tries` are saved across rotation with `rememberSaveable`.

### 5. First-run rule and navigation

*   New flag `SetupFlag.DEMO_SEEN` (`demo_seen`), field
    `SetupRecord.demoSeen`.
*   One pure function decides the first stack:

    ```text
    firstScreens(record, serviceOn, tileAskedDisclosure): List<Screen>
      tileAskedDisclosure            -> HOME, DISCLOSURE
      record.demoSeen                -> HOME
      serviceOn                      -> HOME
      record.serviceEverEnabled      -> HOME
      record.disclosureAccepted      -> HOME
      else                           -> HOME, DEMO
    ```

    `serviceOn` is the live `model.status != ServiceStatus.OFF`. It is
    needed because `serviceEverEnabled` is only written later, by
    `AppModel.refresh`, so a service turned on in Settings (or by the device
    tests) would otherwise get the demo.
*   In `NavGestureApp` the saved stack becomes nullable. While it is null and
    `model.recordLoaded` is false, only the background is drawn. When the
    record has loaded, the stack is set from `firstScreens`, the tile request
    is cleared, and `DEMO_SEEN` is saved in every case except plain `HOME`
    for an already-seen record. So no Home frame shows before the demo, and
    the tile request is read at the moment of the decision, not before. A
    stack restored after rotation is not null and shows at once. The existing
    tile effect (`replacedBy(HOME, DISCLOSURE)`) runs only when the stack is
    not null.
*   Marking the demo as seen when it first shows means a process killed
    during the demo does not show it again. The Home row covers that case.
    This avoids writing the flag from a disposal, which also runs on
    rotation.
*   New `Screen.DEMO`, title "Try the gestures". It uses the shared top bar,
    so its back arrow has the `nav_back` tag, and the device-test helper
    `to_home` closes it like any other screen.
*   Signature: `DemoScreen(model, onClose, onSetUp)`. `ScreenContent` gets
    one new parameter, `replace: (List<Screen>) -> Unit`, which sets the
    stack with `replacedBy`. "Set up now" calls it with `HOME, SETUP`; it
    shows while `!model.checklist.essentialsDone`. "Done" and "Skip" call
    `onClose` (back to Home).
*   Home gets a "Try the gestures" row at the top of the "More" group.

## Alternatives Considered

### Alternative A: Animated slides

Pages that play a gesture animation. Rejected: the user only watches, and the
owner asked for practice.

### Alternative B: Live guide with the real service

A card that ticks off real swipes after setup. Rejected: it runs only after
the permission is given, so a new user cannot try a swipe before deciding.

### Alternative C: A second, simpler swipe recogniser for the demo

Rejected: two recognisers drift apart, and a swipe that passes in the demo
could fail for real. `GestureTracker` has no Android types and takes its
screen size as arguments.

### Alternative D: Full-screen practice on the real edges

Rejected: before setup the phone's own navigation takes those touches, and
after setup the app's own strips do.

### Alternative E: One fixed demo configuration for all steps

The first draft. Rejected: with wait and long actions set from step 1, a Back
swipe that pauses 350 ms would run Screenshot, which never happens at the
real defaults.

## Non-Functional Requirements

*   **Privacy:** no touch data is stored or logged. No new permission.
*   **Accessibility:** Skip and "Skip this step" are normal buttons with
    labels. The drawn phone has one content description naming the step.
    With touch exploration on, every step can be skipped at once.
*   **Small windows:** at 200 % font scale, in landscape and in split
    screen, every button can be reached by scrolling.
*   **Themes:** readable in the light and the dark theme.
*   **Play policy:** the demo runs before the disclosure and enables
    nothing, so the disclosure flow is unchanged.
*   **Both flavors:** all code is in `main`.

## Detailed Implementation Plan

### Target Files to Create/Modify

Paths are under `app/src/main/kotlin/com/vijayyaso/navgesture/` unless they
start with another folder. Test paths are under
`app/src/test/kotlin/com/vijayyaso/navgesture/`.

| File | Action | Change and reason |
| :--- | :--- | :--- |
| `demo/DemoConfiguration.kt` | Create | `forStep(step, widthDp)` from section 2. |
| `demo/DemoEngine.kt` | Create | Engine, `DemoEvent`, `DemoMiss`, `DemoResult` from section 3. |
| `demo/DemoProgress.kt` | Create | `DemoStep`, `DemoHint`, `DemoProgress` from section 4. |
| `demo/FirstRun.kt` | Create | `firstScreens` logic from section 5 as a pure function that returns whether to open the demo, the disclosure or Home. |
| `ui/DemoScreen.kt` | Create | The screen: step text, "Step n of 7" (tag `demo_step`), hint, drawn phone with pointer input and frame poll, buttons, finish card. |
| `ui/DemoPhone.kt` | Create | The drawn phone's pages, start-band highlight, path arrow and the seven effects. |
| `ui/MainActivity.kt` | Modify | `Screen.DEMO`; nullable first stack and the decision from section 5; `ScreenContent` branch. |
| `ui/HomeScreen.kt` | Modify | "Try the gestures" row in "More". |
| `ui/TestTags.kt` | Modify | `ROW_DEMO` (`row_demo`), `DEMO_PHONE` (`demo_phone`), `DEMO_STEP` (`demo_step`), `DEMO_SKIP` (`demo_skip`), `DEMO_SKIP_STEP` (`demo_skip_step`), `DEMO_DONE` (`demo_done`). |
| `setup/SetupStateRepository.kt` | Modify | `DEMO_SEEN` flag and `demoSeen` field. The existing `AppModel.setFlag` saves it; `AppModel.kt` does not change. |
| `app/src/main/res/values/strings.xml` | Modify | Title, seven step titles and instructions, hints, buttons, finish card, row label. |
| `demo/DemoEngineTest.kt` (test) | Create | See the list below. |
| `demo/DemoProgressTest.kt` (test) | Create | Pass, other gesture, miss, skip after two tries, celebrate, advance, finish. |
| `demo/FirstRunTest.kt` (test) | Create | Each row of `firstScreens`, including tile plus fresh install. |
| `setup/SetupStateRepositoryTest.kt` (test) | Modify | Its flag map must list every `SetupFlag`; add `DEMO_SEEN to SetupRecord::demoSeen`. |
| `ui/BackStackTest.kt` (test) | Modify | A saved `DEMO` name restores. |
| `e2e/demo.sh` | Create | See below. |
| `e2e/disclosure.sh` | Modify | See below. |
| `e2e/lib` | Modify | A `bounds_of_id` helper that reads a node's bounds from `ui_nodes` without scrolling. |
| `e2e/run.sh` | Modify | Add `demo` to `all_suites`, before `disclosure`. |
| `.github/workflows/e2e.yaml` | Modify | The shards name their suites; add `demo` to the shard that runs `disclosure`, before it. |
| `docs/development.md` | Modify | One paragraph on `demo/` and the first-run rule; the suite count and list in the Testing section; `to_home` also closes the first-run demo. |
| `README.md` | Modify | One line in the feature list. |

`DemoEngineTest` cases, each on a 300 × 633 dp and a 200 × 422 dp drawing,
density 3, real height 800 dp:

*   Each of the seven gestures gives its passing result under its own step's
    configuration.
*   **Parity:** the Back, Home and Recents sample streams give the same
    `Fire` from a tracker built the way `EdgeTouchOverlay.addStrip` builds
    it (default configuration, real width and height).
*   Step 1: a Back swipe that pauses 400 ms, and one that travels 150 dp,
    are both still Back.
*   A touch in the middle gives `NOT_ON_EDGE`.
*   A swipe slower than the arm time gives exactly one `Missed`.
*   A second finger gives exactly one `Missed(CANCELLED)`, and the UP that
    follows gives nothing.
*   A drag to the left in step 4 gives `FavouritesOpened`.
*   The app list reaches all four indexes on the 200 dp drawing.

`e2e/demo.sh`. The lookup helpers `find_id`, `tap_id` and `text_of_id`
scroll the page with mid-screen swipes before they look. On the demo those
swipes would land on the drawn phone and count as tries. So on the demo
screen the suite reads `ui_nodes` directly, with `bounds_of_id` and
`tap_node`. Steps:

1.  `open_screen row_demo` (the row is on Home, where scrolling is safe).
    Wait until `ui_nodes` has `demo_phone`.
2.  Read the bounds of `demo_phone` and the density (`wm density`).
3.  Back: `input swipe` at mid height, from 10 dp inside the left drawn
    edge, 60 dp inward, 120 ms. (More than the 24 dp trigger, under the long
    line.)
4.  Poll `ui_nodes` for up to 5 s until the `demo_step` text contains "2"
    (the effect lasts 900 ms).
5.  Home: `input swipe` at the drawn phone's centre x, from 10 dp above its
    bottom edge, 150 dp up, 120 ms. Poll for "3".
6.  After each swipe `demo_phone` must still be on screen. The suite runs
    with the service on, so this proves a practice swipe runs no real Back
    or Home.
7.  Tap `demo_skip` with `tap_node`. Check that `demo_phone` is gone and
    `row_demo` shows.

`e2e/disclosure.sh`, between its `pm clear` and its first `open_home`:

1.  `open_test_app`; `ui_nodes` must have `demo_phone` (first open shows the
    demo).
2.  Tap `nav_back` with `tap_node`; `demo_phone` must be gone.
3.  Press the Home key, `open_test_app` again; no `demo_phone` (not shown
    twice).
4.  Tile path (CUJ 6): `pm clear` again, then `am start` of the main
    activity with `--ez com.vijayyaso.navgesture.SHOW_DISCLOSURE true`;
    `disclosure_agree` must show and `demo_phone` must not. Press Back, so
    the existing flow starts from Home as before.

### Verification Plan

Commands run in the build container, as `docs/development.md` describes.

| What | How | Covers |
| :--- | :--- | :--- |
| Unit tests | `gradle test` | Engine, progress, first-run rule (CUJ 3, 4, 6), back stack. |
| Lint | `gradle lint` | Code checks. |
| Both builds | `gradle assembleGithubDebug assemblePlayDebug` | Both flavors compile. |
| Scripts | `shellcheck -o all` on the changed scripts | Script checks. |
| Device suites | `./e2e/run.sh demo disclosure` on the emulator | CUJ 1 (first open), CUJ 2, CUJ 5. |
| By hand on the emulator | All seven steps to the finish card; rotate at step 4 (CUJ 7); two failed tries show "Skip this step" (CUJ 3); dark theme; 200 % font and landscape: every button reachable; edge width set to 64 dp with the service on: a practice Back does not leave the demo | CUJ 1, 3, 5, 7 and the small-window rule. |
| Phone | Poco F5, clean install, all seven steps | Real fingers. Not done by the build job; the owner does it. |
