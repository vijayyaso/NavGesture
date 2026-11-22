# Live practice on the real edges

*   **Author:** vijayyaso (the owner; one person)
*   **Status:** Built 2026-11-18 (commits 9c88871..110c0ce). Unit tests,
    lint, both builds and shellcheck pass; the live-practice device suite
    passed 75 of 75 on the ng36 emulator. Not yet run on a real phone; the
    Poco F5 check below is the release gate. Written against commit
    `874f804`. Line numbers below are from that commit. Where the build
    differs from this text, the text says "As built". The review is in
    `../../../egm-review/live-practice/consolidated_review_report.md`
    (outside the repository).
*   **Date:** 2026-11-18
*   **Predecessor:** the concepts brief
    `../../../egm-review/live-practice/concepts_brief.md` (outside the
    repository; 2026-11-17 and 2026-11-18), and the built design
    `docs/designs/first-run-demo.md`.

--------------------------------------------------------------------------------

## Overview

Today "Try the gestures" always opens a phone drawn inside the app. The user
swipes on the drawing, not on the real screen edges. This design adds **live
practice**. Once the NavGesture service is working, "Try the gestures" opens
a full-screen page with no drawn phone. The user swipes the real screen
edges. The real strips stay on, and the real recogniser, with the user's own
saved settings, spots each gesture. Instead of running it, the service
reports it to the page. The page shows a big result ("Back detected", "Would
open YouTube") and moves to the next step. While the page holds the gate,
nothing runs ("Safety guarantees", at the top of the Technical Plan).
Before setup (service off), new users keep the drawn demo.

Owner's answers (2026-11-17): use the user's real settings, and skip a step
whose slot is "Nothing"; a big full-screen page with centred text and a
moving arrow at the real edge; the app-list step only reports "Would open
X"; the entry points are the Home row and a button after setup.

Owner's answers (2026-11-18): all seven steps run live; practice is locked to
portrait (upright), so the landscape confirm pill never appears in practice;
with TalkBack touch exploration on, "Try the gestures" opens the drawn demo;
a real-finger check on the Poco F5 must pass before a release.

Defaults the owner accepted (2026-11-18): the gate fails toward real
actions, with a 2 s lease; exit by a Close button, and any Back that arrives
also closes; only taps are replayed in practice; the result reads "Back
detected" and stays until the next swipe; the three log markers stay in
release builds, at info level, with no personal data; a Poco F5 real-finger
check before release.

Owner's answers (2026-11-18, after the review):

1.  **Service restart.** The 2 s lease is kept across a service restart.
    `teardown` does not clear the gate. Nothing reads the gate while the
    service is down, and the lease alone ends it. This replaces the brief's
    "cleared when the service stops". The own-Back snackbar rule still
    applies (section 6).
2.  **Split screen and floating windows.** Practice opens the drawn demo
    there. If the page enters split screen or a floating window during
    practice, it hands off to the drawn demo at the same step. Swipes
    beside it stay real.
3.  **Mid-practice stops.** When the service stops, TalkBack turns on, or
    the step list empties, the page switches to the drawn demo at the same
    step. The page uses the grace that `model.status` already has
    (`ui/AppModel.kt:230-237`), not a second 3 s timer.
4.  **Privacy.** Package names in `PRACTICE_CAUGHT AppList <package>` and
    `ACTION_PERFORMED launch <package>` are accepted, the same as today's
    `SWITCHER_LAUNCHED`. App labels are never logged.
5.  **No "you missed the strip" hint.** The page does not add its own
    off-strip detector (review finding Z10).

## Terms

*   **Service:** NavGesture's Accessibility service,
    `service/NavGestureAccessibilityService.kt`. When on, it puts thin touch
    **strips** on the left, right and bottom screen edges
    (`service/EdgeTouchOverlay.kt`). By default each side strip covers 80 %
    of the screen height and the bottom strip 90 % of the width
    (`OverlayLayout`, `service/ScreenGeometry.kt:61-64`). With a corner
    shortcut set, the bottom strip reaches that corner (`bottomSpan`,
    `EdgeTouchOverlay.kt:262-271`). A side with no action has no strip
    (`:135-140`).
*   **Working:** `ServiceStatus.WORKING` (`diagnostics/ServiceStatus.kt:21-33`):
    the service instance is alive. It does not mean the strips are
    installed yet. `STOPPED` means the switch is on but the service is not
    running. `model.status` shows a stop only after 3 s
    (`STOP_GRACE_MS`, `ui/AppModel.kt:230-237`).
*   **Bank pause:** the service switches itself off while a banking app is
    in front, and back on after (`BankPause`).
*   **Slot:** one place an action can be set
    (`SlotId` in `gesture/GestureModel.kt`). A side has `SHORT`, `LONG` and
    `WAIT` slots per zone; the bottom has `SHORT` and `PAUSE`; each bottom
    corner has one. Any slot can hold any action, so a left `SHORT` swipe can
    be remapped from Back to something else.
*   **Zone:** a part of a side. A side is one `WHOLE` zone, or split into
    `TOP_HALF` and `BOTTOM_HALF`. On a split side the `WHOLE` slots are
    stored but no touch can reach them (`SwipeSlots.zonesOf`).
*   **Stream:** one touch, from finger down to finger up. The overlay gives
    each a number (its stream token), which only grows.
*   **Settle action:** an action that runs 80 ms after the swipe, so the
    strip's own touch ends first (corner shortcuts, Open app; `SETTLE_MS`).
*   **The two app lists:** a bottom swipe up and sideways opens the recent
    apps list (slide right) or the favourites list (slide left). The **list
    style** says how an app is picked: `DRAG` (lift on it), `TAP` (the list
    stays open; tap an app) or `BOTH`.
*   **Debug override:** a `settings put global navgesture_…` value that
    only a debug APK reads (`GestureSettingsRepository.debugOverride`,
    `:159-163`). The device suites use them to set slots without the UI.
*   **Fail toward real actions:** if anything is unclear, the gate turns off
    and swipes run for real.
*   **Action route:** a path in the code by which a swipe makes something
    real happen. Section 2 lists the seven.
*   **Gate:** the new in-memory "practice is on" record, `PracticeSession`
    (section 1).
*   **Report layer:** code in the overlay that sees a finished gesture and,
    while the gate is on, sends it to the page instead of running it.
*   **Backstop:** checks at the last step before each real action. While the
    gate is on they refuse and log. They catch anything the report layer
    missed.
*   **Lease:** the gate turns itself off unless the page renews it in time.
*   **Drawn demo:** the existing `ui/DemoScreen.kt` and `ui/DemoPhone.kt`.
*   **Live page:** the new `ui/PracticeScreen.kt`.

## The Problem

*   The drawn demo teaches the shape of a swipe, not where the real strips
    are. On a real phone the user must also learn to start right at the
    edge. The drawing cannot teach that.
*   The drawn demo uses fixed demo settings (Torch on the long swipe,
    Screenshot on the wait). A user who set their own actions practises
    actions they do not have.
*   With the service on, the drawn demo must pause the strips
    (`service/StripPause.kt`), so the user's real swipes do nothing while it
    shows. That is the opposite of practice.

## Goals

*   With the service working, "Try the gestures" opens the live page. The
    user swipes the real edges with their own settings.
*   The live page has up to seven steps, in the drawn demo's order. A step
    whose slots all hold "Nothing" (or an action this phone refuses, or the
    app list when it is off) is skipped.
*   **Nothing runs while the gate is on** (S1 in "Safety guarantees"). No
    Back, no Home, no app launch, no torch, no screenshot, no replayed
    swipe. This holds for all seven action routes, including the delayed
    ones and the worker thread.
*   **A stuck "practice on" can never leave the phone without gestures**
    (S4). The gate lives in memory and has an owner token and a 2 s lease.
    The service does not touch it when it stops; the lease alone ends it
    (owner, 2026-11-18). Any doubt turns it off, and real gestures return.
*   Logs prove what ran: four new markers, kept in release builds.
*   A "Practise now" button appears in setup once the service is working.
*   The drawn demo keeps working for new users (service off), for TalkBack
    users, in split screen, and as the fallback when the service stops
    mid-practice.

## Non-Goals

*   Removing the drawn demo.
*   Changing how gestures are recognised. `GestureTracker` is not changed.
*   Running any action during practice, even a harmless one.
*   Saving the practice state to disk. The gate is in memory only. The step
    and try count survive rotation like the drawn demo's do, nothing more.
*   A practice step for Circle to Search or the landscape pill.
*   Practice in landscape. The page locks the screen upright.
*   Practice in split screen or a floating window (owner, 2026-11-18).
*   A page-level "you missed the strip" hint (owner, 2026-11-18).
*   Teaching the phone's own navigation gestures.
*   Translations. The app has one `values/` string set.

## Open Questions

None open for the owner. The owner's product answers are in the Overview
(four lists, the last dated 2026-11-18, after the review). The list below
holds the drafting decisions: choices the drafting and the review resolve made.
Each has a stated answer in this doc. They are proposals.

**Drafting decisions, not yet seen by the owner.** Each line gives the
decision, the review's verdict, and where it lives.

1.  **Slot rule for both demos.** `DemoStep.passes` matches the slot, not
    the action. Review: OK; the drawn demo gives the same answers
    (section 5).
2.  **`Screen.PRACTICE`, `BackStack.swapTopFrom` and a one-shot
    `demoStartStep`.** Review: risky as first written. Now `swapTopFrom`
    has an id guard, `PRACTICE` has a title, and "Try again" goes through
    `AppModel.practiceRequested` (sections 6, 7).
3.  **Hand-off to the drawn demo.** Service stop, TalkBack and an empty
    plan are now owner decisions (2026-11-18, answer 3), and split screen
    too (answer 2). The page watches `model.status`, not `running`. Still a
    drafting choice: strips that stay landscape. The page holds no gate
    while they are landscape and hands off only if they are still
    landscape 2 s after they first report it (section 6).
4.  **Re-acquire after a rebind.** Replaced by the owner's answer 1: the
    service no longer clears the gate, so there is no rebind window
    (section 1).
5.  **Favourites pass the App list step.** In the drawn demo a drag to the
    left (favourites) does not pass. On the live page both lists are real
    lists of apps, so picking an app from either passes, and the result
    names the app. Review: OK; the instruction now says "slide left or
    right" (section 9).
6.  **Taps are replayed and count as a try.** A tap is replayed into the
    page, as the accepted default says, and shows "That was a tap. Swipe
    instead." The report layer and the replay share one `isTap` rule
    (12 px). Review: OK. A still hold (under 12 px) is also a "tap"; it
    replays as a long press on the page, which is harmless (section 2).
7.  **No "On lift" chip.** The fluid bubble already shows the action.
    Review: OK. An app result uses the app's own label: "Would open
    YouTube" (section 9).
8.  **`DemoResult.CircleToSearch`.** Review: OK, with a `resultName`
    branch. The draft's `AppPicked.packageName` is dropped: the page names
    the app from `Caught.label`, and the controller writes the log
    (section 5).
9.  **The backstop only logs.** Review: OK. Every practice case in the
    suite asserts that `PRACTICE_BLOCKED` is absent (section 3).
10. **Marker text.** Review: OK; ids match `GestureModel.kt:37-43`. The
    resolve adds a fourth marker, `PRACTICE_MISSED <reason>`, so a test can
    prove a miss (section 4).
11. **Close at top centre.** Review: OK; the side strips are vertically
    centred (`EdgeTouchOverlay.kt:225-234`).
12. **Event buffer of 16.** Review: OK.
13. **Rollback through `practiceOrDemo`.** Review: not one switch as first
    written. Now the Home row, "Practise now", "Try again" and a restored
    back stack all go through it; the rollback also removes the suite from
    `run.sh` and `e2e.yaml` (Non-Functional Requirements).
14. **The `demo` suite with live practice shipped.** Review: switching the
    service off broke the suite's setup and lost the only device proof of
    `StripPause`. Now a debug APK keeps the service on and forces the drawn
    demo with the debug override `navgesture_force_drawn_demo`; a release
    APK switches the service off after setup ("e2e/demo.sh").
15. **A control for every "nothing ran" check.** Review: incomplete. Now
    every such case has a control, and the Close case can fail
    ("e2e/live-practice.sh").
16. **Back during the Back step.** The accepted default says any Back that
    arrives closes the page; the brief also asks the page to explain a
    real Back during the Back step. A closed page cannot show a hint, so
    the page closes and the screen under it shows a snackbar with "Try
    again". The resolve adds: the snackbar shows only if the page's token
    still renews, the strips are installed, and the step is Back
    (section 6).
17. **Cases 6 and 11** (resolve). Case 6 (slow swipe, route 7) stays in
    the suite; a failed control fails the suite. Case 11's real swipe while
    the shade is open moves to the by-hand rows now; the suite keeps the
    notice and the return.
18. **Replay check inside the settle delay** (resolve). The replay
    backstop runs again just before `dispatchGesture` (section 3).
19. **How the page learns split screen** (resolve). It reads
    `isInMultiWindowMode` (section 6). Whether a HyperOS floating window
    reports it is not device-checked; a Poco F5 row checks it.
20. **A list opened in practice closes when the gate goes off** (resolve,
    section 2).

## Critical User Journeys (CUJs)

### CUJ 1: Practise right after setup

*   **Given:** the user just turned the service on; Setup shows the
    Accessibility step as Done.
*   **When:** the user taps "Practise now" on that row.
*   **Then:** the live page opens, upright. A short "Getting the edges
    ready" line shows until the strips are installed. Then "Step 1 of n"
    shows, with a moving arrow at the left and right edges. A swipe in from
    the edge shows "Back detected" in large text. Nothing goes back. After a
    short pause the next step shows.

### CUJ 2: Practise from Home

*   **Given:** the service is working; Home shows.
*   **When:** the user taps "Try the gestures".
*   **Then:** the live page opens, as in CUJ 1. The row's summary reads
    "Practise each swipe on your screen's edges".

### CUJ 3: The user's own settings

*   **Given:** left long swipe = Open YouTube; no wait action anywhere; no
    corner shortcut.
*   **When:** the user goes through the page.
*   **Then:** the steps are Back, Home, Recent apps, App list, Long swipe
    ("Step 5 of 5"). The corner and wait steps are skipped. The long-swipe
    step says "Start at the left edge". A long swipe there shows "Would open
    YouTube". YouTube does not open.

### CUJ 4: App list

*   **Given:** the App list step shows.
*   **When:** the user swipes up a little, slides right (recent apps) or
    left (favourites), and lifts on an app.
*   **Then:** the real list panel opens and moves as usual. On lift it
    closes and the page shows "Would open <app name>". No app opens. In the
    Tap style the list stays open after the lift; the page says "Now tap any
    app in the list"; a tap on an app gives the same result.

### CUJ 5: Focus leaves the page

*   **Given:** the live page is at the Home step.
*   **When:** the user pulls down the notification shade.
*   **Then:** the page shows "Practice paused. Your swipes now do real
    actions." The user's swipes work for real again at once, when Android
    reports the focus change; the 2 s lease is the fallback. When the shade
    closes, the notice goes and the Home step continues.

### CUJ 6: The phone's own Back

*   **Given:** the Back step shows; the user starts a swipe outside a strip
    (for example, very high on the side), and Android's own Back runs.
*   **When:** that Back reaches the app.
*   **Then:** the page closes. The screen under it shows a snackbar: "That
    was your phone's own Back. Start nearer the edge." with "Try again".
    "Try again" opens practice again.

### CUJ 7: Close

*   **Given:** any step.
*   **When:** the user taps "Close" (top centre, away from every strip).
*   **Then:** the page closes, and the very next swipe is real.

### CUJ 8: The service stops during practice

*   **Given:** the live page is at the App list step.
*   **When:** the service stops (switched off, bank pause, or killed) and
    `model.status` leaves `WORKING` (after its 3 s grace).
*   **Then:** the page is replaced by the drawn demo, at the App list step.
    The same happens when TalkBack turns on or the step list empties
    (owner, 2026-11-18).

### CUJ 9: TalkBack user

*   **Given:** TalkBack's touch exploration is on; the service is working.
*   **When:** the user opens "Try the gestures".
*   **Then:** the drawn demo opens, as today, with "Skip this step" shown at
    once (`DemoScreen.kt:128`, `:201`).

### CUJ 10: Split screen

*   **Given:** the service is working; the app is in split screen or a
    floating window.
*   **When:** the user opens "Try the gestures", or the page enters split
    screen during practice.
*   **Then:** the drawn demo opens, at the same step if practice was on
    (owner, 2026-11-18). Nothing beside it is caught: while it has focus,
    its strips are paused, so touches reach the other app; after the user
    taps the other app, the strips work for real again.

### CUJ 11: A swipe whose action would run later

*   **Given:** the left long swipe opens Settings (an Open app action runs
    80 ms after the swipe); the Long step shows.
*   **When:** the user does a long swipe on the left edge.
*   **Then:** the page shows "Would open Settings". Settings does not open,
    not even after the delay. The same holds for a corner shortcut.

### CUJ 12: The last step

*   **Given:** the last step of the plan shows.
*   **When:** the user passes or skips it.
*   **Then:** the Finish card shows, and from then on every swipe is real.

## Technical Plan (Architecture)

### Safety guarantees

These four rules are the safety contract. The rest of the plan serves them.

*   **S1. While the gate is on, nothing runs.** None of the seven action
    routes, no worker task, no replayed swipe (sections 2 and 3). A tap is
    replayed into the window under the strip, which is the page itself in
    every normal case (S3 lists the others).
*   **S2. The gate rule.** The page holds the gate only while all of these
    hold: it is resumed, its window is focused, it is not leaving (the
    screen-change slide, `ui/MainActivity.kt:245`), `progress.step` is not
    null, and `strips?.landscape` is not true. When any of these turns
    false, the page calls `end(token)` at once and stops renewing. When all
    hold again, it calls `begin`. Null `strips` (no strips installed right
    now) keeps the gate; it only pauses the steps. On top of the rule, the
    gate ends by itself 2 s after the last renew (the lease).
*   **S3. Known exceptions.** The page shows, but the gate is off and a
    swipe is real; or the gate is on, but the user may expect a real
    swipe. Each is listed with its bound:

    | Case | What happens | How long | Mitigation | Section |
    | :--- | :--- | :--- | :--- | :--- |
    | Main thread stalls over 2 s | Lease runs out; a swipe is real | Until the next renew | The page calls `begin` again at once if S2 holds | 1 |
    | Strips are landscape (portrait lock not yet honoured) | No gate; a guarded swipe shows the landscape pill | Up to 2 s | After 2 s still landscape, hand-off to the drawn demo | 6 |
    | Focus leaves the page (shade, dialog, screen off) | Real gestures, by design | While unfocused | Notice "Practice paused" | 6 |
    | Split screen or a floating window | Hand-off to the drawn demo (owner, 2026-11-18) | One composition | Detection by `isInMultiWindowMode`; floating window not device-checked | 6 |
    | A non-focusable window covers the page (volume panel, heads-up, picture-in-picture, another app's overlay) | Page stays focused; swipes are caught, nothing runs; a tap replays into the covering window | While it shows | The shade (no top strip), the power key; Close once it goes | 6 |
    | A list opened in practice, then the gate goes off | The list's next touch closes it; no app opens | One touch | Next swipe is real | 2 |
    | Service stops | No strips, so nothing to catch or run; the page waits | Up to 3 s (`model.status` grace) | Hand-off to the drawn demo | 6 |
    | Phone's own Back (outside the strips) | The page closes | — | Own-Back snackbar with "Try again" | 6 |

*   **S4. The worst result of a bug is a real action, never a phone with no
    gestures.** The gate is in memory only. Only `begin` from a page turns
    it on. The lease ends it 2 s after the page stops renewing, whatever
    the reason. Close, the notification shade and the power key always
    work while the page shows.

Where this doc says "nothing runs", it means S1, under S2, with the
exceptions in S3.

### Overview of parts

```text
 finger on a real strip
        |
        v
 TouchCaptureView + GestureTracker   (unchanged recogniser)
        |  TrackerAction
        v
 EdgeTouchOverlay  ---- REPORT LAYER -----------------------------+
   reads the gate at DOWN (per stream) and again before acting   |
   gate on:  PRACTICE_CAUGHT, PracticeEvent to the page, no run   |
   gate off: as today                                             |
        |                                                         |
        v   (gate off, or gate turned on too late)                v
 injected lambdas / AppSwitcher / worker / replay    PracticeSession.events
   ---- BACKSTOP: gate on -> PRACTICE_BLOCKED, refuse    (SharedFlow, no replay)
   gate off -> real action, ACTION_PERFORMED                      |
                                                                  v
 PracticeSession (in memory, owner token, 2 s lease)     PracticeScreen (ui/)
   ^  begin / renew every 500 ms / end                     DemoProgress (shared)
   +------------------------------------------------------ DemoText (shared)
```

**Ideal final result (from the brief):** the gesture source is the only
difference between the two demos. `DemoStep`, `DemoProgress`, the hints and
the result names are shared. The drawn phone gets results from
`DemoEngine`; the live page gets them from the service.

The service and the app run in one process (the brief; also the reason
`StripPause` is in memory, `StripPause.kt:20-26`). So the gate and the
event flow are plain Kotlin objects. No broadcast or intent is used, so no
other app can fake a practice event.

### 1. The gate: `PracticeSession`

A new file `service/PracticeSession.kt`. It holds a pure class
`PracticeGate` (no Android types, JVM tested) and an `internal object
PracticeSession` that wraps one `PracticeGate` with the uptime clock
(`SystemClock.uptimeMillis`, the clock the overlay already uses,
`EdgeTouchOverlay.kt:276`).

```text
PracticeGate(leaseMs = 2_000)
  begin(nowMs): Long          // new owner token (counter, never 0); lease = now + leaseMs
  renew(token, nowMs): Boolean // true only if token is the owner AND the lease has not run out
  end(token)                   // clears only if token is the owner
  isActive(nowMs): Boolean     // owner != 0 AND nowMs < leaseUntil
```

`PracticeSession` supplies the clock, so the page calls `begin()`,
`renew(token)` and `end(token)` with no time. There is no `clear()`: the
service never touches the gate (owner, 2026-11-18).

As built: the gate, clock, strips state, event flow and `blocks` live in an
`internal open class PracticeState(gate, clock, log)`, so a test can build
one with its own clock and log. `PracticeSession` is the object the app
uses, `PracticeState(PracticeGate(), SystemClock::uptimeMillis, Log.i)`.

Rules:

*   **Owner token.** `end` and `renew` with an old token do nothing. So an
    old page's late "off" cannot cancel a new page's "on". This closes the
    gap that `StripPause` has as one true/false flag (first-run-demo.md,
    Open Questions, "Two demo screens overlapping").
*   **Lease.** 2 s. The page renews every 500 ms while the gate rule (S2)
    holds. A renew that finds the lease already expired returns
    false and does not revive it; the page must `begin` again. So the gate
    is off within 2 s of the page stopping its renewals for any reason,
    including a bug that skips `end`.
*   **Fail toward real actions.** Every unclear case turns the gate off:
    lease expired, token unknown, page gone, process killed (memory is
    gone). There is no path that turns it on except `begin` from a page.
*   **Thread safe.** `isActive` is read on the main thread and on the
    performer's worker thread (`GestureActionPerformer.kt:46-48`). The
    owner and lease fields are guarded by one lock. `StripPause` is main
    thread only (`StripPause.kt:25`), so it cannot be reused for this.
*   **Not touched when the service stops** (owner, 2026-11-18). `teardown()`
    (`NavGestureAccessibilityService.kt:379-397`) does not change the gate.
    It removes the overlay, closes the performer and drops every pending
    handler message, so with the service down nothing reads the gate.
    `teardown` runs from `onUnbind` (`:203`), `onDestroy` (`:209`),
    `confirmPause` for the bank pause (`:280`) and `confirmOff` for the
    switch-off (`:334`). A page whose screen goes away stops renewing, and
    the lease ends its gate within 2 s.
*   **Survives overlay rebuilds.** `installOverlay()` (`:350-354`) runs on
    every settings change (`:176-182`), rotation and display change
    (`:373-377`). It calls `EdgeTouchOverlay.install`, which calls
    `remove()` first (`EdgeTouchOverlay.kt:105`). Neither touches the gate.
*   **Kept through a rebind.** UiAutomation (used by `uiautomator` and
    screen readers) unbinds the service for a moment (`AppModel.kt:228`).
    The page keeps renewing while `strips` is null and shows "Getting the
    edges ready". When the strips come back, the gate is already on, so no
    swipe slips through. If a renew ever returns false (the lease ran out,
    for example on a main-thread stall), the page drops the token and calls
    `begin` again at once, if the gate rule (S2) still holds.
*   **Read twice.** The overlay reads `isActive` at finger-down and stores
    the answer for that touch (its stream token). It reads it again just
    before any action. If either reading says practice, the gesture is
    reported and not run (section 2).

`PracticeSession` also holds:

*   `strips: StateFlow<InstalledStrips?>`. `InstalledStrips(configuration:
    GestureConfiguration, landscape: Boolean)`. The overlay sets it at the
    end of `install()` (after `applyInteractivity()`, `EdgeTouchOverlay.kt:149`)
    with the exact configuration the strips were built with, and sets it to
    null in `remove()`. A state, not an event, so a page that starts
    listening after the install still sees it.
*   `events: SharedFlow<PracticeEvent>`: `MutableSharedFlow(replay = 0,
    extraBufferCapacity = 16, onBufferOverflow = DROP_OLDEST)`, emitted with
    `tryEmit` on the main thread. No replay, so an old gesture is never
    delivered to a page that opens later.
*   `blocks(route: String, id: String): Boolean`: the backstop's one check.
    If `isActive`, it logs `PRACTICE_BLOCKED <route> <id>` and returns true.

```text
sealed interface PracticeEvent {
  Caught(result: DemoResult, label: String?)   // a whole gesture; label = app name for the list
  Missed(reason: DemoMiss)                     // no gesture
  ListWaiting                                  // the list stays open for a tap (Tap/Both style)
}
```

The page subscribes before it takes the gate:
`PracticeSession.events.onSubscription { token = begin() }.collect { ... }`.
So no event can be emitted between `begin` and the first collect. `begin`
does not wait for `strips`: by the gate rule, null strips keep the gate and
only pause the steps.

### 2. The report layer (in the overlay)

The seven action routes, where each is caught, and where the backstop sits:

| # | Route today | Code | Report layer (gate on) | Backstop |
| :--- | :--- | :--- | :--- | :--- |
| 1 | Direct fire to `perform` or Circle to Search | `EdgeTouchOverlay.onFire` `:310-323` → `onGestureAction` `:322` → service `:399-405` | At the top of `onFire`: report `Caught`, return. | The `::onGestureAction` method reference (service `:157`), wrapped in a lambda with the check. |
| 2 | Corner and "settle" actions, 80 ms later | `onFire` `:313-316` posts `run` after `SETTLE_MS` (`:723`); `run` `:325-332` → `onAction` | Caught at the top of `onFire`, before the post. | Wrapped `onAction` lambda (service `:160`) runs when the delay ends, so it is the second reading. |
| 3 | App list opened by a drag, which skips `onFire` | `TouchCaptureView.handle` `:587-601` → `controller.open` | The list may open. It is only this app's window. The controller stores the practice reading at open. | — (opening runs nothing) |
| 4 | Lift on an app in the list | `UP` while switching `:523-531` → `EdgeSwitcherController.release` `:167-185` → `launchAndClose` `:218-226` | In `launchAndClose`: report `Caught(AppPicked)`, close, no launch. | Check in `AppSwitcher.launch` (`AppSwitcher.kt:42-60`). |
| 5 | Tap on a row of an open list (stays open until 10 s pass with no touch) | `onPick` `:213-216` → `launchAndClose`; `IDLE_TIMEOUT_MS` `:327` | Same as 4. The reading taken at open is kept until the list closes. | Same as 4. |
| 6 | Long press fires Circle to Search, finger still down | `longPressRunnable` `:470-471` → `handle` → `Fire(CircleToSearch)` → `onFire` | Same as 1. Result `DemoResult.CircleToSearch`. | Same as 1. |
| 7 | Replay of an unused touch into the window below | `handle` `:651-658` → `onUnused` `:334-345` → service `replay` `:412-500` | A tap (`isTap`) is replayed. Any other unused touch is reported as `Missed` and not replayed. | In `replay`: a non-tap path (`isTap` false) is refused, at the call and again just before `dispatchGesture`. |

Some shortcuts then run later on the performer's worker thread
(`GestureActionPerformer.kt:95-114`, `:120-138`). Section 3 covers them.

**How the overlay knows a touch is practice.** `onStreamStart`
(`EdgeTouchOverlay.kt:273-284`) gets a new field write: if
`PracticeSession.isActive()`, `practiceStream = stream`. A helper
`inPractice(stream) = stream == practiceStream || PracticeSession.isActive()`
gives the two readings. `remove()` resets `practiceStream` to 0, like
`hold.reset()` (`:172`).

**One report per touch.** A field `reportedStream` makes `report(stream,
event)` emit at most once per stream. The tracker can report `Unused` twice
for one slow swipe (once from `onMove`, once from `onUp`); the drawn engine
has the same rule (first-run-demo.md, section 3, "One result per touch").
For each `Missed` it emits, `report` logs `PRACTICE_MISSED <reason>`
(section 4).

Changes, each at the line it touches:

*   **`onFire` (`:310-323`)** gets the stream (the lambda at `:203` already
    has it). First line: if `inPractice(stream)`, call `catch(action,
    edge, stream)` and return. `catch` builds the result:
    `GestureAction.Run` → `DemoResult.Ran(run)`;
    `GestureAction.CircleToSearch` → `DemoResult.CircleToSearch`. It logs
    `PRACTICE_CAUGHT <edge> <action.id>` and reports `Caught(result, null)`.
    `GESTURE_FIRED` (`:641`) is not changed: it still says the recogniser
    fired, and existing suites read it. Because the check is the first line,
    a slot set to an app list (`onFire` `:318-321` → `run` `:328-329` →
    `openForTouch`) is caught too: in practice that list never opens. Only
    when the gate turns on during the 80 ms wait can such a list open; its
    launch is then stopped by the controller check and `AppSwitcher.launch`.
*   **`onUnused` (`:334-345`)** gets the tracker's `RejectionReason` (the
    `onUnused` lambda at `:204` and `:450` gains it; `handle` has
    `action.reason` at `:653`). If `inPractice(stream)`: report
    `Missed(Rejected(reason))`. Then, if the touch is a tap, go on to the
    replay as today. Otherwise end the stream and return,
    exactly as the "replay skipped" path does (`:336-339`). This is needed
    because `handle` returns true once `onUnused` is called (`:654-657`), so
    the `UP` skips `onStreamEnd` (`:543`). **One tap rule for both layers:**
    the replay's own test (a local `val isTap`: moved less than
    `TAP_SLOP_PX`, 12 px, service `:417-418`, `:512`) becomes an
    `internal fun isTap(samples)` in `service/TouchReplayer.kt`. The report
    layer and the replay backstop both call it, so a touch can never be "a
    tap" to one and "a swipe" to the other. A still hold (under 12 px) is a
    "tap" too: it replays as a long press on the page and gets the tap
    hint. That is harmless.
*   **Misses with no replay.** A new `TouchCaptureView` lambda
    `onMiss: (Long, DemoMiss) -> Unit` is called for: `Unused` while not
    replayable (`:651-653`, reason as is), `Reverted` (`:650`,
    `DemoMiss.PulledBack`), a second finger (`:509-520`,
    `DemoMiss.Cancelled`), and `ACTION_CANCEL` (`:546-558`,
    `DemoMiss.Cancelled`). The overlay reports it only if
    `inPractice(stream)`. Outside practice the lambda does nothing.
    As built: while an app list is open (`switching`), the overlay reports
    nothing for a second finger or `ACTION_CANCEL`; the controller's
    `cancel()` reports the list's own `Missed(Cancelled)`, so the touch
    still gets exactly one report.
*   **App list open (`:587-594`).** `controller.open(action)` becomes
    `controller.open(action, practice = inPractice(stream))`, through a new
    `TouchCaptureView` lambda `practiceNow: (Long) -> Boolean`. It takes the
    stream because `stream` is private to the nested view
    (`EdgeTouchOverlay.kt:437`, `:455`); it is wired like `admitList`
    (`:202`).
*   **`EdgeSwitcherController`:**
    *   New field `practice`, set in `open` (`:113-130`), false in
        `openForTouch` (`:136-145`), cleared in `cancel` (`:195-211`). A
        field `reported` (reset at open) keeps one report per list.
    *   As built: the controller reports through a
        `practiceReport: (PracticeEvent) -> Unit` constructor parameter
        that the overlay passes.
    *   `launchAndClose` (`:218-226`): it already reads the package before
        `cancel()` clears `apps` (`:219-221`). It now reads the whole
        `PanelApp` (package and `label`, `SwitcherPanelOverlay.kt:43`)
        first. If `practice || PracticeSession.isActive()`: `cancel()`;
        report `Caught(AppPicked(index), label)` and log
        `PRACTICE_CAUGHT AppList <package>`, or report `Missed(Cancelled)`
        when no app was selected; return without `switcher.launch`.
    *   The five other ways a list closes report `Missed(Cancelled)` while
        `practice` is set: slide down (`:159-165`), idle timeout
        (`:86-89`), outside touch (`:93-96`), a new edge touch (`:188-193`),
        and an overlay rebuild (`EdgeTouchOverlay.remove()` calls
        `cancel()`, `EdgeTouchOverlay.kt:154`).
    *   **A list opened in practice ends with the gate.** The idle timer
        restarts on every panel touch (`:97`, `:228-231`), so the reading
        kept from the open could outlive the page. So the panel's touch
        start (`:97`) checks first: if `practice` is set and
        `PracticeSession.isActive()` is false, it logs `SWITCHER_CLOSED
        practice ended`, calls `cancel()` without a report (the page is
        gone), and re-arms nothing. That touch closes the list, no app
        opens, and the next swipe is real (S3). As built: the close marks
        the list as reported, so it sends no `Missed`.
    *   Touch mode (`:176-182`): while `practice` is set, report
        `ListWaiting`.
    *   The touch-mode panel is `FLAG_NOT_FOCUSABLE`
        (`SwitcherPanelOverlay.kt:465-469`), so the page keeps focus and its
        lease during the 10 s wait. The reading kept from the open is a
        second guard.
*   **`install()` / `remove()`** set and clear `PracticeSession.strips`.

The strips, the bubble and the haptics behave as usual in practice. The
fluid bubble already follows the finger and swaps to the action's icon
(`EdgeTouchOverlay.kt:465-466`, `:665`), so the page needs no "On lift"
chip.

### 3. The backstop

Each check calls `PracticeSession.blocks(route, id)`. If it returns true the
action is refused and nothing else happens. The backstop never reports to
the page: by the time it fires, the report layer has already judged the
touch as not practice, so the gate turned on in the last few milliseconds.

| Where | Check | Covers |
| :--- | :--- | :--- |
| Service `setUp`, `onGestureAction` (`:157`, today a method reference; it becomes a lambda) | `blocks("gesture", action.id)` before `onGestureAction` | Routes 1 and 6, and torch, global actions, open app, shortcuts started from there. |
| Service `setUp`, `onAction` lambda (`:160`) | `blocks("action", action.id)` before `performer.perform` | Route 2 after its 80 ms wait. |
| `AppSwitcher.launch` (`AppSwitcher.kt:42`) | `blocks("launch", packageName)`, return false | Routes 4 and 5; also Open app and Previous app. `AppSwitcher` is a concrete class built by the performer (`GestureActionPerformer.kt:36`), so the check goes inside `launch`, its only way to start an app. |
| Worker, `appShortcut`, before `ShortcutCatalog.launch` (`:107-108`) | `if (closed \|\| blocks("shortcut", id)) return@execute` | The shortcut lookup can take seconds (`:89-93`). |
| Worker, `previousApp`, after `:127` | `if (closed \|\| blocks("previous", id)) return@execute` | Both exits: the Recents fallback `global(...)` (`:130`) and `switcher.launch` (`:134`). |
| Service `replay` (`:412-500`) | After the tap test (`:418`, now `isTap(samples)`): if not a tap, `blocks("replay", "swipe")`, return false | Route 7. The caller then frees the strips ("replay refused", `EdgeTouchOverlay.kt:344`). |
| Service `replay`, first line of the delayed runnable (`:461`) | If not a tap, `blocks("replay", "swipe")`: `handler.removeCallbacks(finish)`, `finish.run()`, return, as the refused path does (`:494-496`) | Route 7 when the gate turns on during the settle delay (up to 65 ms, `:451-461`). |

### 4. Log markers

Today Back, Home and Recents log nothing on success (`global` logs only a
failure, `GestureActionPerformer.kt:146-162`), and `GESTURE_FIRED` is
logged before any gate (`EdgeTouchOverlay.kt:641`). So the logs cannot prove
that nothing ran. Four markers, all `Log.i` with tag `NavGesture`. R8
strips `Log.d` and `Log.v` from release builds (docs/development.md:115),
so info level keeps them. (The accepted default says "three markers"; the
fourth, `PRACTICE_MISSED`, was added in the review so a miss can be
proved.)

| Marker | Where | Text |
| :--- | :--- | :--- |
| `PRACTICE_CAUGHT` | Overlay `catch`; controller `launchAndClose` | `PRACTICE_CAUGHT <edge> <action.id>`; `PRACTICE_CAUGHT AppList <package>` |
| `ACTION_PERFORMED` | `global` when `performGlobalAction` returns true (`:150-161`); torch after `toggle()` returns true (`:77-80`); `AppSwitcher.launch` on success (`:58-59`); `appShortcut` after `ShortcutCatalog.launch` returns true (`:108`); `circleToSearch` when no problem (`:166`); `replay` next to `REPLAY_DISPATCHED` (`:492`) | `ACTION_PERFORMED <name>`, `ACTION_PERFORMED torch`, `ACTION_PERFORMED launch <package>`, `ACTION_PERFORMED <shortcut id>`, `ACTION_PERFORMED CircleToSearch`, `ACTION_PERFORMED replay_tap` or `replay_swipe` |
| `PRACTICE_BLOCKED` | `PracticeSession.blocks` | `PRACTICE_BLOCKED <route> <id>` |
| `PRACTICE_MISSED` | Overlay `sendToPractice`, for each `Missed` sent to the page (from the overlay's `report` and from the controller's list closes) | `PRACTICE_MISSED <reason>`: the `RejectionReason` name (for example `TOO_SLOW_TO_ARM`), or `PULLED_BACK` or `CANCELLED`. The release run of case 6 proves the name survives R8. |

**Privacy.** The markers carry action ids, package names and miss reasons
only. The owner accepted the package names (2026-11-18, answer 4). Package
names are already logged at info level today: `GESTURE_FIRED` with an
`app:<package>` or `shortcut:<package>/<id>` id (`GestureModel.kt:192-199`),
`SWITCHER_ROWS` with every package in the list
(`EdgeSwitcherController.kt:282-287`), `SWITCHER_LAUNCHED <package>`
(`:224`) and `PREVIOUS_APP <package>` (`GestureActionPerformer.kt:133`). No
marker logs an app label, a coordinate or a time. The only coordinate log
today is the `DOWN` line at debug level (`EdgeTouchOverlay.kt:496`),
stripped from release. Nothing goes to `ServiceLog` (the diagnostics file).

### 5. The step list from the user's settings

A new pure file `demo/PracticePlan.kt` (no Android types, JVM tested).

**Steps match by slot (edge plus swipe kind), not by action**, because any
slot can be remapped. `DemoStep.passes` (`demo/DemoProgress.kt:42-52`)
becomes one slot rule for both demos:

| Step | Passes when the result is |
| :--- | :--- |
| `BACK` | `Ran`, slot `Edge` on `LEFT` or `RIGHT`, kind `SHORT` (any zone) |
| `HOME` | `Ran`, slot `Edge(BOTTOM, WHOLE, SHORT)` |
| `RECENTS` | `Ran`, slot `Edge(BOTTOM, WHOLE, PAUSE)` |
| `APP_LIST` | `AppPicked` |
| `CORNER` | `Ran`, slot `Corner` (unchanged) |
| `WAIT` | `Ran`, slot `Edge` with kind `WAIT` (unchanged) |
| `LONG` | `Ran`, slot `Edge` with kind `LONG` |

The drawn demo gives the same answers under this rule. Its steps 1 to 4
use the real defaults, where side `SHORT` holds Back, bottom `SHORT` Home
and bottom `PAUSE` Recents (`SwipeSlots.defaultFor`). Its step 7 puts Torch
on the side `LONG` slots only (`DemoConfiguration.kt:45-46`). And a long
swipe on a side whose `LONG` slot is empty fires the `SHORT` slot
(`GestureTracker.bandAt`, `:431-436`). So `DemoProgressTest` and
`DemoEngineTest` keep passing; they gain slot cases.

**Which steps are included.** `PracticePlan.steps(configuration,
unsupported)` returns the steps, in `DemoStep` order, whose slots hold at
least one usable action. An action is usable when it is not
`SwipeAction.None` and not in `unsupported`
(`NavGestureAccessibilityService.unsupported`, `:547`; today only split
screen, wherever the system does not list it, as on HyperOS, `:140-145`). A slot counts only if a touch can reach it
(`SwipeSlots.zonesOf`, the same reach rule as `zoneInUse` and `anySet`).

| Step | Included when |
| :--- | :--- |
| `BACK` | a reachable side `SHORT` slot is usable |
| `HOME` | bottom `SHORT` is usable |
| `RECENTS` | bottom `PAUSE` is usable |
| `APP_LIST` | `configuration.edgeAppListEnabled` (the bottom drag) |
| `CORNER` | a corner shortcut is usable |
| `WAIT` | a reachable side `WAIT` slot is usable |
| `LONG` | a reachable side `LONG` slot is usable |

The configuration is the one the strips were last built with
(`PracticeSession.strips`, with the debug overrides the service reads,
`GestureSettingsRepository.kt:50`). As built: the page keeps it while a
rebind removes the strips, so the plan does not jump. Before the first
install (the page may take the gate before that) it uses
`model.configuration`. A settings change mid-practice rebuilds the strips; the page
then rebuilds the plan, keeps the current step if it is still in the plan,
else moves to the next one that is. The same rule applies when the page is
restored after the process was killed: the saved step may no longer be in
the plan. If the rebuilt plan is empty, the page hands off to the drawn
demo (section 6).

**Where to point the arrow.** As built: `PracticePlan.target(step,
configuration, unsupported)` returns a `List<PracticeTarget>`. For the Back,
Wait and Long steps it holds one `Side(slot)` per side: the first slot in
`SwipeSlots.ALL` order that is usable and whose zone is in `zonesOf(edge)`
(edge and zone). `ALL` lists `WHOLE` before the halves
(`gesture/SwipeSlots.kt:107-117`), and on a split side the `WHOLE` slot keeps
its default, so the zone check matters. It holds `Bottom` for the Home,
Recents and App list steps, and one `Corner` per set corner for the Corner
step. It is empty for a step the plan does not hold.
`PracticePlan.sides(step, configuration, unsupported)` derives
`PracticeSides` (`LEFT`, `RIGHT`, `BOTH`, or null for the bottom) from
`target`, for the instruction text.

**`DemoProgress` and the plan.** `DemoProgress` (`DemoProgress.kt:68-103`)
gets a last parameter `plan: List<DemoStep> = DemoStep.entries` and a
factory `DemoProgress.start(plan)`. `advance()` moves to the next step in
`plan`, not `step.next`. The drawn demo passes nothing and behaves as
today. "Step n of m" on the live page uses the index in the plan. As built:
`withPlan(plan)` re-applies a rebuilt plan: it keeps the step (with tries
and hint) if the plan still has it, else moves to the next step the plan
has.

**`DemoStep` ordinals stay stable.** The drawn demo's saver stores
`step.ordinal` (`DemoScreen.kt:570-578`). The live page's saver does the
same, and the fallback (section 6) passes the step to the drawn demo by
ordinal. So the enum's order must not change; a comment on `DemoStep` says
so.

**`DemoResult` changes.** `AppPicked(index: Int)` stays as it is: the page
names the app from `Caught.label`, and the controller writes the log. A new
`data object CircleToSearch` is added (only the live page gets it; the
drawn engine ignores `ScheduleLongPress`). Every exhaustive `when` over
`DemoResult` gains a branch: `resultName` (`DemoScreen.kt:539-543`, moving
to `ui/DemoText.kt`) and `DemoPhone.kt`.

**An empty plan.** If no step is included, the entry opens the drawn demo
instead (section 7). If the plan empties during practice, the page hands
off to the drawn demo (section 6).

### 6. The live page

A new `ui/PracticeScreen.kt`, behind a new `Screen.PRACTICE`. `Screen`
needs a `@StringRes title` (`MainActivity.kt:129`); `PRACTICE` uses
`R.string.demo_title`, which also names the page for TalkBack (`paneTitle`).
`PracticeScreen` gets `leaving` from `ScreenContent` (`MainActivity.kt:245`).

**Layout.** Full screen, upright. `MainActivity` draws no `ScreenBar` and
no scroll column for `Screen.PRACTICE` (today every screen but Home gets a
bar, `MainActivity.kt:265`, and a `verticalScroll` column, `:269-275`).
From top to bottom:

*   **Close** (tag `practice_close`): a text button at the top centre,
    below the status bar. The side strips are at the far left and right and
    the bottom strip is at the bottom, so the centre top is on no strip.
*   The paused notice (tag `practice_paused`), only while paused.
*   The waiting line "Getting the edges ready…" (tag `practice_waiting`),
    only while `strips` is null or landscape. It is an extra line: `practice_step` and
    `practice_result` stay in the tree, unchanged, while it shows. (Each
    `uiautomator` dump unbinds the service for a moment, so a dump can
    catch this state; not device-checked.)
*   "Step n of m" (tag `practice_step`; for example "Step 1 of 4", n and m
    from the plan), the step title, the "On your phone this does: Back"
    line, and the instruction. Centred. As built: the "does" line names the
    action of the slot the first arrow points at; for the App list step it
    reads "App list".
*   The result (tag `practice_result`), in large text: "Back detected",
    "Would open YouTube". As built: a `Caught` sets it; a `Missed` or "Skip
    this step" clears it. It stays across the step change after a pass.
*   The hint line, as in the drawn demo.
*   "Skip this step" (tag `practice_skip_step`) after two tries, in the
    centre column, never at the screen bottom.
*   A moving arrow at the real edge, drawn on a full-screen canvas: it
    slides from the target edge inward along the path, and repeats. With
    animations off (animator duration scale 0) it is still.

**The page never calls `StripPause`.** A paused strip is not touchable
(`EdgeTouchOverlay.kt:417-433`), so the recogniser would see nothing.

**Taking and keeping the gate.** The page follows the gate rule, S2: it
holds the gate only while it is resumed, its window is focused, it is not
`leaving`, `progress.step` is not null, and `strips?.landscape` is not
true. As in the drawn demo (`DemoScreen.kt:112-126`), the Finish card
returns before the gate effect, so the effect runs only while
`progress.step` is not null. The effect is one `LifecycleResumeEffect`
keyed on window focus (`LocalWindowInfo.current.isWindowFocused`),
`leaving`, and `strips?.landscape == true`:

*   All conditions hold: start a coroutine that collects
    `PracticeSession.events` with `onSubscription { begin }` and renews
    every 500 ms. While `strips` is null, keep renewing, show the waiting
    line, and take no step. If a renew returns false, `begin` again at
    once.
*   Any condition false (paused, unfocused, leaving, landscape strips, the
    Finish card, disposed): `end(token)` at once and stop renewing. The
    lease is only the safety net. `leaving` is true during the 200 ms
    screen-change slide (`SCREEN_CHANGE_MS`, `ui/Motion.kt:45`), while the
    activity is still resumed and focused; without it the page would keep
    the gate through the slide after Close or Back.
*   Unfocused but resumed (notification shade, a dialog): show "Practice
    paused. Your swipes now do real actions." Real gestures work at once.

**Split screen and floating windows** (owner, 2026-11-18). The page reads
`isInMultiWindowMode` of its activity on each composition. Entering or
leaving split screen changes the window size, and the manifest declares no
`configChanges` (`AndroidManifest.xml:71-83`), so the activity is recreated
and the page reads it again. A listener
(`addOnMultiWindowModeChangedListener`) covers a change without a
recreate. When it is true, the page ends its token and hands off to the
drawn demo at the same step. The entry never opens the page there
(section 7). Nothing is caught beside the drawn demo: while it is focused
its strips are paused and not touchable, so touches reach the other app;
once the other app has focus, the strips work for real again
(`DemoScreen.kt:119-121`; first-run-demo.md, Open Questions, "Split
screen"). Whether a HyperOS floating window reports
multi-window mode is not device-checked; a Poco F5 row checks it.

**Portrait lock.** A `DisposableEffect` sets the activity's
`requestedOrientation` to `SCREEN_ORIENTATION_PORTRAIT`. It puts back the
old value only when the page really leaves (its dispose runs while the
activity is not `isChangingConfigurations`). The dispose that the lock's
own recreate causes must not undo the lock. As built: the value from
before the lock is kept with `rememberSaveable`, so it survives the lock's
own recreate. Lint's `SourceLockedOrientationActivity` is suppressed on this
function, with the owner's non-goal (no landscape practice) as the reason. This is not proved yet; a
by-hand row opens practice with the phone sideways. The manifest declares no `configChanges`
(`AndroidManifest.xml:71-83`), so if the phone was sideways the activity is
recreated. The step and tries survive by `rememberSaveable`; the gate is
taken again by the new composition. The strips are rebuilt for the new
rotation (`reinstallIfGeometryChanged`, service `:373-377`), and
`LandscapeConfirm.configure` with `landscape = false` dismisses any pill
(`LandscapeConfirm.kt:62-67`). While `strips?.landscape` is true the page
holds no gate (S2) and shows the waiting line, so a guarded swipe can show
the landscape pill, never a caught result (`admit` runs before `onFire`,
`EdgeTouchOverlay.kt:567-570`, `:639-643`). If the strips are still
landscape 2 s after they first report it (Android did not honour the lock;
when it might not is not checked here), the page hands off to the drawn
demo at the same step. Only `landscape == true` ends the gate; null strips
do not.

**Back.** A `BackHandler` on the page handles any Back that reaches the
app. It closes the page. First it calls `renew(token)`. It asks `AppModel`
to show the snackbar "That was your phone's own Back. Start nearer the
edge." with the action "Try again" only if all of these hold: the renew
returned true, `strips` is not null, and the current step is `BACK`. Then
the gate was on, NavGesture's own Back could not run, and the Back came
from the phone itself (its own edge gesture outside the strips, or a
button). In any other case the page just closes.

"Try again" cannot use the closed page's `open`: each screen's `open` is
`openFrom(entry.id, …)` (`MainActivity.kt:247`), which does nothing once
that entry is no longer on top (`BackStack.kt:55-56`). So the action sets
a new `AppModel.practiceRequested`. `MainActivity` handles it like
`disclosureRequested` (`MainActivity.kt:250-255`): from the current top it
opens `practiceOrDemo(...)` (section 7), then clears the flag.

If closing the page restores a sideways orientation and that recreates
the activity, the snackbar can be lost (`AppModel` lives in `remember`).
Accepted: the page is closed and real gestures work; only the hint is lost.

**Results to progress.** `Caught(result, label)` becomes
`DemoEvent.Performed(result)`, `Missed(reason)` becomes
`DemoEvent.Missed(reason)`, and both go to `DemoProgress.after`
(`DemoProgress.kt:83-97`). `DemoEvent` stays in `DemoEngine.kt`; both
pages use it. A pass waits `EFFECT_SHOWN_MS` (900 ms, `DemoScreen.kt:581`,
today `private`; it moves to `ui/DemoText.kt` as `internal`) and calls
`advance()`. `ListWaiting` shows "Now tap any app in the list" in the hint
line. After the last step the existing `FinishCard` shows
(`DemoScreen.kt:446`, made `internal`). The gate has ended by then (S2), so
on the Finish card every swipe is real. As built: no hand-off runs once
the Finish card shows.

**Hand-off to the drawn demo** (owner, 2026-11-18). The page is replaced by
the drawn demo at the same step when any of these happens:

*   **The service stops.** The page watches `model.status`, which already
    waits `STOP_GRACE_MS` (3 s) before it shows a stop
    (`ui/AppModel.kt:230-237`). When it is no longer `WORKING`, the page
    hands off. There is no second timer. Shorter dips (the UiAutomation
    unbind) only show the waiting line.
*   **TalkBack turns on.** Touch exploration, read with
    `rememberTouchExploration` (`DemoScreen.kt:491-502`, moved to
    `ui/DemoText.kt` and made `internal`).
*   **The plan empties** (section 5).
*   **Split screen or a floating window** (above).
*   **The strips stay landscape** for 2 s (above; a drafting choice).
*   **`practiceOrDemo(...)` no longer returns `Screen.PRACTICE`** (a
    restored back stack, or the rollback; below).

Each hand-off ends the token, sets `demoStartStep` to the current step,
then calls `swapTopFrom`.

**Replacing the page.** A new `BackStack.swapTopFrom(id, screen)`: if
`top.id == id`, it replaces the top entry with a new entry and a new id;
else it changes nothing. This is the same guard every other change has
(`BackStack.kt:53-56`), because screens are keyed by entry id
(`MainActivity.kt:243`). Back from the drawn demo returns to the screen
under the page. The step goes over in a one-shot
`AppModel.demoStartStep: DemoStep?` that `DemoScreen` reads once to start
its `DemoProgress`, then clears.

**A restored back stack.** `backStackOf` restores any known screen name
(`BackStack.kt:82-94`), so `PRACTICE` can come back after the process
was killed. So the page itself is the only place that swaps: its first
hand-off condition is `practiceOrDemo(...) != Screen.PRACTICE`. Like every
hand-off, it sets `demoStartStep` to the page's saved step before
`swapTopFrom`, so the drawn demo opens at that step. This also keeps the
rollback in one function: with `practiceOrDemo` returning `Screen.DEMO`, a
restored page hands off at once, before it takes the gate.

### 7. Entry points

*   **Home row** (`HomeScreen.kt:168-173`): opens the screen that a small
    UI function `practiceOrDemo(model, touchExploration, multiWindow)`
    picks, using `PracticePlan.useLive`: `Screen.PRACTICE` when
    `model.status == ServiceStatus.WORKING`, touch exploration is off, the
    activity is not in multi-window mode, and the plan is not empty; else
    `Screen.DEMO`. `WORKING`, not "not `OFF`": a `STOPPED` service has no
    strips. At entry the plan is built from `model.configuration`. As built:
    that flow applies debug overrides only when the app's settings are read,
    so an override set after the app started may be missing there; the page
    re-checks with the strips' configuration once it opens (section 6). The
    plan also uses `NavGestureAccessibilityService.unsupported.value`. On a
    debug APK only, `AppModel.forcesDrawnDemo()` reads
    `navgesture_force_drawn_demo` through the now top-level `internal fun
    debugOverride(context, name)` (it moved out of
    `GestureSettingsRepository`, `:159-163`) on each call, so no rebind is
    needed; `1` makes `practiceOrDemo` return `Screen.DEMO`; the `demo`
    suite uses it. `rememberPracticeEntry(model)` keeps touch exploration
    and `rememberMultiWindow()` current and returns a lambda that calls
    `practiceOrDemo` at the moment of the tap. The summary reads
    `practice_row_summary` when the answer is live, else
    `demo_row_summary`.
*   **Every way in goes through `practiceOrDemo`:** the Home row,
    "Practise now", "Try again" (through `practiceRequested`, section 6)
    and a restored back stack (section 6). Nothing else opens
    `Screen.PRACTICE`.
*   **"Practise now"** in setup. The Accessibility row is drawn by
    `SetupRows` (`SetupSection.kt:62-72`), used on Home (`HomeScreen.kt:252`)
    and on Setup (`SetupScreen.kt:28`, `:40`). Today a Done row has no
    button, except a confirmed manual step, which shows Undo (`StepRow`,
    `:145-172`, `:146`, `:164-170`). `StepRow` gains `doneAction:
    Pair<Int, () -> Unit>?`, shown only in the Done state. `SetupRows`
    gains `onPractise: (() -> Unit)?`; for the Accessibility step it passes
    `R.string.practice_now to onPractise` while the status is `WORKING`.
    `SetupScreen` and `SetupGroup` get `onOpen` and pass
    `{ onOpen(practiceOrDemo(...)) }`. `ScreenContent` calls
    `SetupScreen(model)` today (`MainActivity.kt:344`); it now passes
    `onOpen = open`.
*   **First run** is not changed. `FirstRun.firstStack` opens the drawn demo
    only when the service is off (`demo/FirstRun.kt:32-39`).

### 8. Shared and drawn-only parts

| Part | Shared | Drawn-only | Live-only |
| :--- | :--- | :--- | :--- |
| `DemoStep`, `DemoHint`, `DemoProgress` (`demo/DemoProgress.kt`) | yes | | |
| `DemoResult`, `DemoMiss` (moved to `demo/DemoResult.kt`) | yes | | |
| `DemoEvent` (stays in `demo/DemoEngine.kt`; both pages feed it to `DemoProgress.after`) | yes | | |
| `DemoEngine` (`demo/DemoEngine.kt`) | | yes | |
| `DemoConfiguration`, `DemoTouchZone` | | yes | |
| `hintText`, `title`, `instruction`, `resultName` (moved to `ui/DemoText.kt`) | yes | | |
| `DemoPhone.kt`, the drawn `DemoScreen` body, `StripPause` | | yes | |
| `PracticePlan`, `PracticeSession`, `PracticeScreen`, the live strings | | | yes |

`DemoResult` and `DemoMiss` move out of `DemoEngine.kt` (`:33-57`) because
the service now uses them and must not depend on the engine. The package
stays `com.vijayyaso.navgesture.demo`, so no import changes. This adds one
dependency: `service` may import `demo` (`DemoResult`, `DemoMiss`). `demo`
never imports `service`; `PracticePlan` takes the
unsupported set as a parameter.
`hintText` (`DemoScreen.kt:505-536`), `resultName` (`:539-543`), `title`
(`:546-555`), `instruction` (`:558-567`) and `EFFECT_SHOWN_MS` (`:581`)
move to `ui/DemoText.kt` and become `internal`, with live variants beside
them.

### 9. Strings

The current copy talks about a drawn phone (`demo_row_summary`,
`demo_do_back`, `demo_hint_not_on_edge`, `demo_hint_not_in_corner`,
`strings.xml:118`, `:128`, `:143-144`). The live page uses its own titles
and instructions, because a remapped slot makes "Back" the wrong name for
step 1. The shared hints that do not say "drawn" are reused
(`demo_hint_other`, `demo_hint_recents_home`, `demo_hint_wait_back`,
`demo_hint_long_back`, `demo_hint_list_closed`, `demo_hint_cancelled`,
`demo_hint_pulled_back`, `demo_hint_tap`, `demo_hint_short`,
`demo_hint_wrong_way`, `demo_hint_slow`, `demo_hint_slanted`,
`demo_hint_nothing`). `NotOnEdge` cannot happen live: a touch that starts
off a strip never reaches the service.

New strings:

| Name | Text |
| :--- | :--- |
| `practice_row_summary` | Practise each swipe on your screen's edges |
| `practice_now` | Practise now |
| `practice_close` | Close |
| `practice_waiting` | Getting the edges ready… |
| `practice_paused` | Practice paused. Your swipes now do real actions. |
| `practice_does` | On your phone this does: %1$s |
| `practice_step_back` | Swipe in from the side |
| `practice_step_home` | Swipe up from the bottom |
| `practice_step_recents` | Swipe up and stop |
| `practice_step_app_list` | Swipe up, then slide sideways |
| `practice_step_corner` | Swipe from a bottom corner |
| `practice_step_wait` | Swipe in and hold |
| `practice_step_long` | Long swipe in |
| `practice_do_back` | Start at the very edge of the screen, on the %1$s. Swipe in a little, then lift. |
| `practice_do_home` | Start at the very bottom of the screen. Swipe up and lift while your finger still moves. |
| `practice_do_recents` | Start at the very bottom. Swipe up, stop, then lift. |
| `practice_do_app_list` | Start at the very bottom. Swipe up a little, then slide left or right. Lift on any app. |
| `practice_do_corner` | Start in the bottom %1$s corner. Swipe up and in at a slant. |
| `practice_do_wait` | Start at the %1$s edge. Swipe in, then hold still until it buzzes. |
| `practice_do_long` | Start at the %1$s edge. Swipe in far, then lift. |
| `practice_side_left` / `practice_side_right` / `practice_side_both` | left / right / left or right |
| `practice_result_detected` | %1$s detected |
| `practice_result_would_open` | Would open %1$s |
| `practice_result_would_run` | Would run %1$s |
| `practice_result_circle` | Circle to Search detected |
| `practice_hint_list_tap` | Now tap any app in the list. |
| `practice_own_back` | That was your phone's own Back. Start nearer the edge. |
| `practice_try_again` | Try again |

Result wording: Back, Home and Recents use `practice_result_detected` with
`shortcutLabel` (`SwipeActionPicker.kt:127-151`). An app uses
`practice_result_would_open` with the app's own label: for a list pick,
`Caught.label`; for Open app, the label from `rememberLaunchableApps`, or
the package name while the list loads or when the app is gone. As built: if
the app list gives no label, the result uses the existing `demo_name_app_list`
name.
`shortcutLabel` is not used for apps: for `OpenApp` it returns "Open
YouTube" (`corner_open_named_app`, `SwipeActionPicker.kt:141-147`,
`strings.xml:198`), which would read "Would open Open YouTube". Every
other action uses `practice_result_would_run` with `shortcutLabel`.

## Alternatives Considered

### Alternative A: Reuse `StripPause` for live mode

Rejected: a paused strip is not touchable, so the recogniser sees nothing
(`EdgeTouchOverlay.kt:419`).

### Alternative B: Gate only `perform()`

Rejected: the app list launches apps through `AppSwitcher.launch` from the
controller (`EdgeSwitcherController.kt:222`), the list opens without
`onFire`, and the replay is a separate route.

### Alternative C: Let Back, Home and Recents really run, with a "come back here" card

The Corner, Wait and Long steps would be listed as text. Cheaper. Rejected: it
conflicts with the owner's choice that nothing runs.

### Alternative D: Talk through broadcasts or intents

Rejected: other apps can fake them. The service and the app share
one process, so an in-memory flow is used.

### Alternative E: Match steps by action (as the drawn demo did)

Rejected: slots can be remapped, so a user whose left short swipe
opens an app could never pass a step that waits for Back.

### Alternative F: One true/false flag, like `StripPause`

Rejected: on the Poco F5 NavGesture hides the phone's own bar, so its
strips are the only Back and Home. A stuck flag there would leave no
working Back or Home anywhere. The owner token and the lease make it fail
toward real actions.

### Alternative G: Clear the gate when the service stops

The brief's first answer. `teardown` would call `clear()`, and the page
would take a new token after each rebind. Rejected (owner, 2026-11-18):
the page learns of the clear only on its next renew, so after a short
UiAutomation unbind a swipe could be real for up to 500 ms. A generation
number in `InstalledStrips` would shrink that window but not close it, and
adds state. Nothing reads the gate while the service is down, so the lease
alone is enough.

### Alternative H: Close the page on a mid-practice stop

When the service stops, TalkBack turns on or the plan empties, end the
token and close the page with a snackbar. Cheaper: no `swapTopFrom`, no
`demoStartStep`. Rejected (owner, 2026-11-18): the user would lose the
step; the drawn demo continues it.

### Superseded: first-run-demo.md Alternatives B and D

`first-run-demo.md` rejected Alternative B ("Live guide with the real
service") and Alternative D ("Full-screen practice on the real edges"). The
reason given was that before setup the phone's own navigation takes the
edges, and the app cannot pause that. That reason holds only while the
service is off. **For the service-on case, this design supersedes B and
D.** For the service-off case they stay rejected, and the drawn demo stays.
`first-run-demo.md` itself is not edited.

## Non-Functional Requirements

*   **Safety:** see "Safety guarantees" S1-S4 at the top of the Technical
    Plan. S3 is the list of known risks and their bounds.
*   **Privacy:** no touch data is stored. The new logs carry only action
    ids, package names and miss reasons; package names are already logged
    today, and the owner accepted them (section 4). No app label is
    logged. No new permission.
*   **Accessibility:** with touch exploration on, the drawn demo opens
    (owner, 2026-11-18). Close, Skip this step and Try again are normal
    buttons with labels.
*   **Themes and font:** readable in light and dark. At 200 % font the
    centre column scrolls inside itself; Close stays at the top.
*   **Play policy:** no change to what the service does with window
    content. The page enables nothing.
*   **Both flavors:** all code is in `main`.
*   **Rollback:** every way into practice chooses the screen in one
    function, `practiceOrDemo`: the Home row, "Practise now", "Try again"
    and a restored back stack (sections 6, 7). Making it always return
    `Screen.DEMO` turns live practice off. The rollback also removes
    `live-practice` from `all_suites` in `e2e/run.sh` (`:65`) and from the
    shard in `.github/workflows/e2e.yaml` (`:90`); otherwise that suite
    fails on every run.

## Detailed Implementation Plan

### Build order

Five phases. Each one builds, and `gradle test` and every existing device
suite pass at its end. Dependency rule: `service` may import `demo`;
`demo` never imports `service`.

1.  **Shared demo types.** `demo/DemoResult.kt` (the move, and
    `CircleToSearch` with its `resultName` and `DemoPhone` branches);
    `DemoProgress` slot rule, `plan`, `start(plan)`; `demo/PracticePlan.kt`;
    their unit tests. The drawn demo behaves as today.
2.  **Gate, markers and backstop.** `TouchReplayer.isTap`;
    `service/PracticeSession.kt`; the `ACTION_PERFORMED` markers; every
    backstop check (section 3). Nothing takes the gate yet, so all
    existing suites must pass unchanged.
3.  **Report layer.** The overlay and controller changes (section 2),
    `PRACTICE_CAUGHT` and `PRACTICE_MISSED`. Still no page takes the gate.
4.  **The page and the entry points.** `ui/DemoText.kt`,
    `ui/PracticeScreen.kt`, `Screen.PRACTICE`, `swapTopFrom`,
    `demoStartStep`, `practiceRequested`, `practiceOrDemo` with the debug
    override, `SetupRows` and `StepRow`, the strings.
5.  **Suites and docs.** `e2e/live-practice.sh`, the `demo.sh` and `lib`
    changes, `run.sh`, `e2e.yaml`, `docs/development.md`, `README.md`.

### Target Files to Create/Modify

Paths are under `app/src/main/kotlin/com/vijayyaso/navgesture/` unless they
start with another folder. Test paths are under
`app/src/test/kotlin/com/vijayyaso/navgesture/`.

| File | Action | Change and reason |
| :--- | :--- | :--- |
| `service/PracticeSession.kt` | (added) | `PracticeGate`, `PracticeSession`, `InstalledStrips`, `PracticeEvent` (section 1); `blocks` and the `PRACTICE_BLOCKED` log (section 3). No `clear()`. |
| `service/EdgeTouchOverlay.kt` | (modified) | `practiceStream`, `reportedStream`, `inPractice`, `catch`, `report` (with `PRACTICE_MISSED`); `onFire` takes the stream; `onUnused` takes the reason and skips swipe replay in practice; new `onMiss` and `practiceNow: (Long) -> Boolean` lambdas on `TouchCaptureView`; `strips` set in `install`, cleared in `remove` (section 2). |
| `service/EdgeSwitcherController.kt` | (modified) | `open(action, practice)`; `practice` and `reported` fields; `launchAndClose` reads the `PanelApp` first and reports in practice; the five close paths report `Missed(Cancelled)`; the panel's touch start closes a practice list once the gate is off; touch mode reports `ListWaiting` (section 2). |
| `service/NavGestureAccessibilityService.kt` | (modified) | `::onGestureAction` becomes a lambda with the check, and the `onAction` lambda gets one, in `setUp`; `teardown` does not touch the gate; `replay` refuses non-tap paths in practice, at the call and in the delayed runnable, and logs `ACTION_PERFORMED replay_tap` / `replay_swipe` (sections 1, 3, 4). |
| `service/GestureActionPerformer.kt` | (modified) | Worker checks in `appShortcut` and `previousApp`; `ACTION_PERFORMED` in `global`, the torch branch, `appShortcut` and `circleToSearch` (sections 3, 4). |
| `service/TouchReplayer.kt` | (modified) | `internal fun isTap(samples)` and `TAP_SLOP_PX` moved here from the service, so the report layer and `replay` share one tap rule. |
| `service/AppSwitcher.kt` | (modified) | `blocks("launch", …)` at the top of `launch`; `ACTION_PERFORMED launch <package>` on success. |
| `demo/DemoResult.kt` | (added) | `DemoResult` and `DemoMiss` moved from `DemoEngine.kt`; new `CircleToSearch`. `AppPicked(index)` unchanged. |
| `demo/DemoEngine.kt` | (modified) | The two types removed (moved). `DemoEvent` stays. |
| `demo/DemoProgress.kt` | (modified) | Slot rule in `DemoStep.passes`; `plan` parameter, `start(plan)`, `advance()` by plan; a comment that the enum order is saved by ordinal. |
| `demo/PracticePlan.kt` | (added) | `PracticeTarget`, `PracticeSides`, `steps`, `target`, `sides` (section 5); `useLive(working, touchExploration, multiWindow, planEmpty)`. |
| `ui/PracticeScreen.kt` | (added) | The live page (section 6), its saver (step ordinal and tries), the gate effect under the gate rule (takes `leaving`), portrait lock and the 2 s landscape hand-off, multi-window check, `BackHandler` with the own-Back rule, the hand-offs on `model.status`, TalkBack and an empty plan; `practiceOrDemo`, `rememberPracticeEntry`, `rememberMultiWindow`. |
| `ui/DemoText.kt` | (added) | `hintText`, `title`, `instruction`, `resultName` (with a `CircleToSearch` branch) and `EFFECT_SHOWN_MS` moved from `DemoScreen.kt` and made `internal`; live `practiceTitle`, `practiceInstruction`, `practiceResult`; `rememberTouchExploration` moved here. Without the new branch the exhaustive `when` in `resultName` does not compile. |
| `ui/DemoScreen.kt` | (modified) | The moved helpers removed; `FinishCard` made `internal`; reads `AppModel.demoStartStep` once to start at a step. |
| `ui/DemoPhone.kt` | (modified) | Its `when` over `DemoResult` (`effectOf`, `:119-122`) handles `CircleToSearch` (no effect; never sent by the engine). |
| `ui/MainActivity.kt` | (modified) | `Screen.PRACTICE(R.string.demo_title)` (every `Screen` needs a title, `:129`); no `ScreenBar` and no scroll column for it; `ScreenContent` branch that passes `leaving` and a `swap` callback (`swapTopFrom(entry.id, …)`) that the page uses for every hand-off, including a restored entry; `SetupScreen(model, onOpen = open)` (`:344`); `practiceRequested` handled like `disclosureRequested` (`:250-255`). |
| `ui/BackStack.kt` | (modified) | `swapTopFrom(id, screen)`, with the top-id guard. |
| `ui/HomeScreen.kt` | (modified) | Row opens `practiceOrDemo`; live summary; `SetupGroup` passes `onPractise`. |
| `ui/SetupScreen.kt` | (modified) | Takes `onOpen`; passes `onPractise` to both `SetupRows` calls. |
| `ui/SetupSection.kt` | (modified) | `SetupRows(onPractise)`; `StepRow(doneAction)`; "Practise now" on the Done Accessibility row while `WORKING`. |
| `ui/AppModel.kt` | (modified) | `demoStartStep`; `practiceRequested` (set by "Try again"); `showMessage(text, actionLabel, onAction)` for the own-Back snackbar. `STOP_GRACE_MS` stays `private`: the page reads `model.status`, which already applies it. |
| `settings/GestureSettingsRepository.kt` | (modified) | `debugOverride` (`:159-163`) becomes an `internal` top-level function, so `practiceOrDemo` can read `navgesture_force_drawn_demo`. |
| `ui/TestTags.kt` | (modified) | `PRACTICE_PAGE` (`practice_page`), `PRACTICE_STEP` (`practice_step`), `PRACTICE_RESULT` (`practice_result`), `PRACTICE_WAITING` (`practice_waiting`), `PRACTICE_PAUSED` (`practice_paused`), `PRACTICE_CLOSE` (`practice_close`), `PRACTICE_SKIP_STEP` (`practice_skip_step`), `SETUP_PRACTISE` (`setup_practise`). |
| `app/src/main/res/values/strings.xml` | (modified) | The strings in section 9. |
| `service/PracticeSessionTest.kt` (test) | (added) | See the list below. |
| `demo/PracticePlanTest.kt` (test) | (added) | See the list below. |
| `demo/DemoProgressTest.kt` (test) | (modified) | Slot rule cases; plan order and skipping; `start(plan)`; finish after the last plan step. |
| `demo/DemoEngineTest.kt` (test) | (modified) | Slot-rule parity cases: the drawn engine's results pass the same steps under the slot rule. `AppPicked(index)` is unchanged. The parity tests stay. |
| `ui/BackStackTest.kt` (test) | (modified) | `swapTopFrom`: replaces the top with a new id when the id is the top; no change for another id; a saved `PRACTICE` name restores. |
| `e2e/live-practice.sh` | (added) | See below. |
| `e2e/demo.sh` | (modified) | See below. |
| `e2e/lib` | (modified) | `to_home` also taps `practice_close` when it shows, or `demo_done` when the live page shows its Finish card (which has no Close) (it stops when `nav_back` is absent, `lib:473-484`, and the live page has no bar). `swipe_up_drag` moves here from `corner.sh:63` and `app-switch.sh:42`, which then use it. |
| `e2e/run.sh` | (modified) | Add `live-practice` to `all_suites` (`run.sh:65`), before `demo`. |
| `.github/workflows/e2e.yaml` | (modified) | Add `live-practice` to the shard that runs `demo` (`:90`), before it. |
| `docs/development.md` | (modified) | One paragraph on live practice and the four markers under Screens (`:89`); "thirteen" to "fourteen" and the suite list (`:115`); `demo` uses `navgesture_force_drawn_demo` on a debug APK and switches the service off on a release APK; `to_home` also closes the live page. |
| `README.md` | (modified) | One line in the feature list. |

`PracticeSessionTest` (on `PracticeGate`, every time passed in):

*   Not active by default.
*   `begin(t)` makes it active: `isActive(t + 1999)` is true and
    `isActive(t + 2000)` is false.
*   `renew` with the owner token at `t + 1500` returns true; then
    `isActive(t + 3499)` is true.
*   `renew` at `t + 2000` (lease ended) returns false and it stays
    inactive.
*   `renew` and `end` with an old token do nothing to a newer owner (two
    pages overlapping).
*   `end` with the owner token makes it inactive at once.
*   Tokens are never 0 and never repeat.
*   Two threads: `isActive` from a second thread sees `begin` and `end`
    (a smoke test of the lock).

`PracticeSession.blocks` (through a gate with a test clock): returns false
and logs nothing when inactive; returns true when active.

`TouchReplayer.isTap`: a path that moved 11 px is a tap; 12 px is not.

`PracticePlanTest`:

*   Defaults: Back, Home, Recents, App list (list on by default).
*   Each step is dropped when its slots are all `None`.
*   A slot holding an unsupported action (split screen) counts as unset.
*   A split side: a `WAIT` only in the top half includes `WAIT`; the target
    is that zone.
*   A split side whose `WHOLE` `SHORT` slot keeps Back while both halves
    hold Home: `target(BACK)` is a half, never `WHOLE` (zone in
    `zonesOf(edge)`).
*   `APP_LIST` follows `edgeAppListEnabled`.
*   `target` and `sides` for left only, right only, and both.
*   `useLive`: true only when working, touch exploration off, not in
    multi-window mode, and the plan is not empty; false for each one
    alone.

`DemoProgressTest` additions: with the plan `[BACK, HOME, LONG]`, `advance`
goes BACK → HOME → LONG → finished; a `Ran` from a side `SHORT` slot whose
action is not Back passes `BACK`; a `Ran` from a side `LONG` slot whose
action is not Torch passes `LONG`; a side `SHORT` swipe does not pass
`LONG`; `AppPicked` passes `APP_LIST`; `CircleToSearch` passes no step.

`e2e/demo.sh`. The Home row now opens the live page while the service is
working. The suite keeps the drawn demo in two ways:

*   **Debug APK** (the normal run). `settings put global
    navgesture_force_drawn_demo 1` before `open_screen row_demo`, so
    `practiceOrDemo` returns `Screen.DEMO` with the service on. The suite
    runs as today, so `demo.sh:91-105` still proves on a device that a
    drawn Back or Home with the service on runs no real action (the strip
    pause). It also tests the rollback switch on every run. `trap restore
    EXIT INT TERM`, where `restore` deletes the override.
*   **Release APK** (`-p`; the override is ignored there). Run `geometry`
    and `warm_input` first, as today (`demo.sh:36-38`); `warm_input` waits
    for `GESTURE_FIRED LEFT Back` (`lib:233-248`), which needs the
    service. Then `settings delete secure enabled_accessibility_services`
    (as `disclosure.sh:54`), wait until `! service_bound`, then `sleep 4`:
    `model.status` shows the stop only after its 3 s grace
    (`AppModel.kt:230-237`). `trap restore EXIT INT TERM`, where `restore`
    runs `enable_service` (`lib:167-185`). The "ran no real Back/Home"
    checks then prove less: with the service off, no strip exists.

The suite tells the two apart as `corner.sh:36-39` does (`dumpsys package`
and `DEBUGGABLE`).

`e2e/live-practice.sh`. Expects the service on (prepared by
`e2e/device.sh`). Setup: `require_adb`, `geometry`, `warm_input`. Cases
marked "debug" use the debug-only overrides (as `swipe-actions.sh` and
`corner.sh` do) and are skipped on a release APK, with the same check as
`corner.sh:36-39`. The others also run with `-p` on the release APK, which
is what proves the markers survive R8. An exit trap
(`trap cleanup EXIT INT TERM`) deletes every override set here and
rebinds.

Each `uiautomator` dump can unbind the service for a moment
(`AppModel.kt:228`). The gate stays on through it (section 1), but the
strips are gone for that moment. So after every `ui_nodes` call the suite
waits for the strip (`wait_strip 0`, `lib:539`; the argument is the
rotation) and 1 s more before the next swipe. The "nothing ran" checks read
logcat, not the screen. An `assert_no_log` that follows an action on the
worker thread waits `sleep 2` first.

Helpers in the suite:

*   `practice_step_reaches <n>`: waits up to 5 s until `practice_step`
    reads "Step <n> of <m>" (a copy of `step_reaches`, `demo.sh:44-56`,
    for `practice_step`).
*   `advance_to <n>`: from the current step, for each step before `<n>`:
    the step's passing swipe when the suite knows it (Back, Home,
    Recents), else two taps on the left strip (each is a try) and a tap on
    `practice_skip_step` (shown after two tries). After each,
    `practice_step_reaches` the next number, then `wait_strip 0; sleep 1`.
    Its taps log `ACTION_PERFORMED replay_tap`, so every case runs
    `reset_log` after `advance_to` and before its own swipe.
*   `close_practice`: taps Close, or Done on the Finish card, checks the
    page is gone, then `wait_strip 0; sleep 1`.
*   Overrides and the rebind come before practice opens: set them, rebind,
    wait for `service_bound` and `wait_strip 0`, then open practice.

**Rules for every case.** Every "nothing ran" check has a control: the
same swipe with practice closed must log `ACTION_PERFORMED` (or
`SWITCHER_LAUNCHED`). A failed control fails the suite. Every case in
practice also runs `assert_no_log PRACTICE_BLOCKED`: the report layer, not
the backstop, must catch each gesture. There are no optional cases: each
case below either is in the suite and fails it, or is a by-hand row in the
Verification Plan.

1.  **Control, route 1 (Back, Home, Recents):** `open_screen row_troubleshooting`;
    `reset_log`; Back swipe on the left strip: `assert_log
    ACTION_PERFORMED Back`. Then, from the same screen, a bottom swipe:
    `assert_log ACTION_PERFORMED Home`; `open_screen row_troubleshooting`
    again; `swipe_and_hold` up past the line: `assert_log ACTION_PERFORMED
    Recents`.
2.  **Open:** `open_screen row_demo practice_page`. `demo_phone` must be
    absent. `practice_step_reaches 1`.
3.  **Route 1 in practice, after a dump:** `wait_strip 0; sleep 1`;
    `reset_log`; the same Back swipe. `assert_log PRACTICE_CAUGHT LEFT
    Back`; `assert_no_log ACTION_PERFORMED`; `assert_no_log
    PRACTICE_BLOCKED`; `practice_page` still shows; `practice_result` reads
    "Back detected"; `practice_step_reaches 2`. The swipe follows a dump
    (which unbinds and rebinds the service), so this also proves the gate
    survives a rebind (owner, 2026-11-18, answer 1).
4.  **Home:** bottom swipe. `PRACTICE_CAUGHT BOTTOM Home`; no
    `ACTION_PERFORMED`; no `PRACTICE_BLOCKED`; resumed activity is still
    ours (`resumed_activity`, `lib:152`). Control: case 1.
5.  **Recents:** `swipe_and_hold` up past the line. `PRACTICE_CAUGHT
    BOTTOM Recents`; no `ACTION_PERFORMED`; no `PRACTICE_BLOCKED`.
    Control: case 1.
6.  **Route 7:** a slow left-strip swipe, 20 dp inward over 2 s: it never
    reaches the 24 dp trigger inside the 1 s arm window (80 dp would arm at
    0.6 s), so it ends `TOO_SLOW_TO_ARM` and is replayed as a swipe today.
    `assert_log PRACTICE_MISSED TOO_SLOW_TO_ARM`; `assert_no_log
    ACTION_PERFORMED replay_swipe`; no `REPLAY_DISPATCHED`; no
    `PRACTICE_BLOCKED`. Control: close practice, same swipe, `assert_log
    ACTION_PERFORMED replay_swipe`. No existing suite asserts a non-tap
    replay (the current ones replay taps and holds, `navigation.sh:87-93`,
    `app-switch.sh:107-113`), so this control is new. It is in the suite: a
    failed control fails the suite. The release run of this case also
    proves the `PRACTICE_MISSED` reason name survives R8.
7.  **Route 2 (debug):** `navgesture_slot_left_whole_long` =
    `app:com.android.settings`; rebind. In practice, `advance_to` the Long
    step; a long left swipe: `PRACTICE_CAUGHT LEFT
    app:com.android.settings`, no `ACTION_PERFORMED launch`, no
    `PRACTICE_BLOCKED`, Settings not resumed after `sleep 2`. Control:
    practice closed, same swipe, `ACTION_PERFORMED launch
    com.android.settings`.
8.  **Routes 3 and 4 (debug):** favourites set to launchable apps, as
    `corner.sh:144-149` does (`navgesture_favourites`), and
    `navgesture_app_list_style` = `DRAG` (a lift opens an app only when
    `opensOnLift` is true, `GestureModel.kt:83-87`, `EdgeSwitcherController.kt:176`).
    `advance_to` the App list step; a bottom swipe dragged left
    (`swipe_up_drag`, now in `lib`), then lift. `PRACTICE_CAUGHT AppList
    <package>`; no `SWITCHER_LAUNCHED`; no `ACTION_PERFORMED`; no
    `PRACTICE_BLOCKED`. Control: practice closed, same drag,
    `SWITCHER_LAUNCHED`. This control is new: no suite today asserts
    `SWITCHER_LAUNCHED` after a `DRAG` lift. A failed control fails the
    suite. The emulator has no usage events (`corner.sh:23-25`), so the
    recents list is empty and only the favourites drag can pick an app.
9.  **Route 5 (debug):** the same favourites, style `TAP`. At the App list
    step, the drag and lift log `SWITCHER_TOUCH_MODE` (no dump here: one
    unbinds the service, which closes the list). Tap the first cell, at the place
    `corner.sh:158-167` computes. `PRACTICE_CAUGHT AppList <first>`; no
    `SWITCHER_LAUNCHED`; no `PRACTICE_BLOCKED`. Control: `corner.sh:171`
    already proves the same tap launches outside practice.
10. **Corner (debug):** `navgesture_left_corner` = `notifications`.
    `advance_to` the Corner step: `PRACTICE_CAUGHT BOTTOM CornerLEFT`; no
    `ACTION_PERFORMED notifications`; no `PRACTICE_BLOCKED`. Control:
    practice closed, the same `corner_swipe`: `ACTION_PERFORMED
    notifications`, then `cmd statusbar collapse`.
11. **Focus loss and return:** in practice, `cmd statusbar expand-settings`
    (as `off-switch.sh:78`); `sleep 1`; `cmd statusbar collapse`
    (`off-switch.sh:80`). `practice_page` shows and `practice_paused` does
    not. `wait_strip 0; sleep 1`; Back swipe: `PRACTICE_CAUGHT LEFT Back`;
    no `PRACTICE_BLOCKED` (the gate was taken again). That a strip takes a
    real swipe while the shade is open is not proved by any suite today, so
    that part is a by-hand row now.
12. **Arriving Back:** reopen practice at step 1; `wait_strip 0; sleep 1`;
    `input keyevent KEYCODE_BACK`. `practice_page` is gone; the snackbar
    shows: the dump contains the text of `practice_own_back` from "own
    Back" on (the apostrophe is escaped in the string file and may be in
    the dump). Then `tap_node` on the node line with the text of
    `practice_try_again`, from the same dump (`tap_node` takes a node line,
    not a tag): `practice_page` shows again.
13. **Close, then a real Back at once:** reopen practice at step 1. Read
    the bounds of `practice_close` from one dump (`bounds_of_id`,
    `lib:348-351`); `wait_strip 0; sleep 1`. A Back swipe must log `PRACTICE_CAUGHT LEFT Back` (the
    gate is proven on). Then `reset_log` and, in one `adb shell`: `input
    tap <cx> <cy>; sleep 0.3; input swipe 2 $MID_Y 300 $MID_Y 120`.
    `assert_log ACTION_PERFORMED Back`. With no dump in between, the gate
    could only be off because Close ended it at once, not through the
    lease or a rebind. This also proves the `leaving` rule (the slide
    takes 200 ms).
14. **Finish card (debug):** a one-step plan: `none` in
    `navgesture_slot_right_whole_short`,
    `navgesture_slot_bottom_short` and
    `navgesture_slot_bottom_pause`, and `navgesture_edge_app_list`
    off; rebind. Open practice ("Step 1 of 1") and pass Back. The Finish card shows. `wait_strip 0;
    sleep 1`; `reset_log`; Back swipe: `assert_log ACTION_PERFORMED Back`.
15. **Cleanup:** the exit trap deletes every override set here and
    rebinds.

By-hand rows, not in the suite (Verification Plan): route 6 (Circle to
Search needs an assistant; the unit tests cover the `CircleToSearch`
result); a real swipe while the shade is open; the hand-off on a service
stop; the TalkBack path; split screen; a second finger (`input
motionevent` has one pointer); the case 9 hint text (by-hand row 14). The 2 s lease on its own is a unit test.

### Verification Plan

Commands run in the build container, as `docs/development.md` describes.

| What | How | Covers |
| :--- | :--- | :--- |
| Unit tests | `gradle test` | Gate and `blocks` (`PracticeSessionTest`), `isTap`, plan (`PracticePlanTest`), slot rule and plan progress (`DemoProgressTest`), engine parity (`DemoEngineTest`), back stack. |
| Lint | `gradle lint` | Code checks. |
| Both builds | `gradle assembleGithubDebug assemblePlayDebug` | Both flavors compile. |
| Scripts | `shellcheck -o all` on the changed scripts | Script checks. |
| Device suites | `./e2e/run.sh live-practice demo disclosure`, then `./e2e/run.sh` (all fourteen) because the touch path, the performer and the replay changed | CUJ 1, 2, 4, 5 (return only), 6, 7, 11, 12; routes 1, 2, 3, 4, 5, 7; the gate through a rebind; the drawn demo's strip pause with the service on (`demo`, debug APK). |
| Release markers | `./e2e/run.sh -p navgesture.apk live-practice demo` | The markers survive R8; the default-slot cases pass on release; `demo` with the service switched off. |
| By hand on the emulator | See the list below. | CUJ 3, 4 (the tap hint), 5, 8, 9, 10; the portrait lock; the S3 rows. |
| Phone | Poco F5, release build, real fingers. See the checklist below. | Real fingers and the phone's own edge gesture. **Blocks a release** (owner, 2026-11-18). The owner does it; the build job does not. |

By hand on the emulator (debug build). Each row names what must be seen.

1.  Every step with the default settings: each shows its result and moves
    on; the Finish card shows last.
2.  CUJ 3, a remapped long swipe (Open YouTube or any app): "Would open
    <app>", and the app does not open.
3.  Open practice with the phone sideways: the page turns upright and
    stays upright. Leaving it lets the app rotate again.
4.  Rotate at the App list step: it stays upright and keeps the step.
5.  200 % font and the dark theme: readable; Close stays at the top.
6.  Notification shade open at the Home step: the notice shows, and a
    left-strip Back swipe goes back for real. After the shade closes, the
    step continues.
7.  Split screen (CUJ 10): opening practice there opens the drawn demo.
    Entering split screen during practice hands off to the drawn demo at
    the same step. After tapping the other app, its strip swipes are real.
8.  Switch the service off at the App list step (CUJ 8): after about 3 s
    the drawn demo shows at the App list step.
9.  TalkBack on (CUJ 9): the drawn demo opens. TalkBack turned on during
    practice: the drawn demo takes over at the same step.
10. The volume panel over the page (press volume): swipes are caught and
    nothing runs; the shade and the power key still work.
11. Screen off and the lock screen during practice: while locked, strip
    swipes are real (the page has lost focus). After unlock the page shows
    again.
12. A second finger during a swipe: the page shows the cancelled hint and
    nothing runs.
13. Drawn demo with the service on and TalkBack on: a drawn Home swipe runs
    no real Home (strip pause).
14. App list step, Tap style: after the lift, the hint reads "Now tap any
    app in the list." (The case 9 suite row cannot check this: a dump
    unbinds the service and closes the list.)

Poco F5 checklist (release build, real fingers, the owner's own settings).
The owner writes the date, the build and pass or fail per row into this
doc's Status line, as `first-run-demo.md` did.

1.  Every included step passes with its real swipe. Expected: each result
    shows, and nothing runs.
2.  At the Back step, a swipe high on the side edge (outside the strip).
    Expected: the page closes, the own-Back snackbar shows, and "Try
    again" opens practice again.
3.  Tap Close, then at once Back and Home. Expected: both work within
    1 s.
4.  Shade at the Home step. Expected: real gestures while it is open; the
    step continues after it closes.
5.  Finish card. Expected: Back and Home work for real.
6.  Split screen, and a HyperOS floating window. Expected: the drawn demo
    opens or takes over; after tapping the other app, its strip swipes are
    real. This row also checks that a floating window reports
    multi-window mode.
7.  Volume panel over the page. Expected: nothing runs; the shade and the
    power key work.
8.  Lock and unlock the phone during practice. Expected: real gestures
    while locked; the page shows again after unlock.

<!-- Provenance: drafted with the EGM draft method from egm-review/live-practice/concepts_brief.md. -->
*Drafted with EGM*
