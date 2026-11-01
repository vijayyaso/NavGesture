# Pause for bank apps

*   **Author:** vijayyaso
*   **Status:** Implemented (phone test pending)
*   **Date:** 2026-11-01

--------------------------------------------------------------------------------

## Overview

Indian bank and payment apps now refuse to open while any third-party
Accessibility service is switched on. NavGesture is an Accessibility service,
so these apps block the phone. This design adds **bank pause** to the
`github` build. The user picks their bank apps once. When one of them opens,
NavGesture gives the phone its own navigation bar back and switches its
service off. When the user leaves the bank app, NavGesture switches itself
back on and hides the bar again.

## The Problem

*   Paytm blocks use until every Accessibility service is off. SBI YONO shows
    a toast and closes. HDFC lists the apps to turn off. Users report this for
    Microsoft's Link to Windows and auto-clicker apps too, so the check is not
    about NavGesture's behaviour (see References).
*   The checks differ per bank. Guardsquare documents a common pattern: the
    bank reads `getEnabledAccessibilityServiceList()` and rejects any service
    not on its own allowlist. No setting inside NavGesture can pass that.
*   Every gesture app checked (Fluid Navigation Gestures, Navigation Gestures,
    Gesture Navigation Bar, XGesture, EdgeGesture, HyperGesture) uses an
    Accessibility service and has the same problem. Their users switch the
    service off by hand, or with Geto or Tasker. A Shizuku-only design avoids
    Accessibility but needs Developer options on, which SBI YONO also blocks,
    and stops at every restart. The owner chose bank pause (2026-11-01).

## Goals

*   With bank pause set up, a picked bank app finds no NavGesture service
    while it is in front, from its second open at the latest (Q1), and the
    user never has a moment with neither gestures nor the phone's own bar.
*   Gestures return without any tap once the user is out of every picked app.
*   The user can always force the gestures back from the notification.
*   The `play` build is unchanged: no row, no events, no watcher.

## Non-Goals

*   Hiding NavGesture from a bank while the service is on. Not possible.
*   Banks that also block Developer options or USB debugging. NavGesture's
    adb grant survives turning Developer options off, so the user can do so.
*   A built-in list of bank package names. The user picks from installed apps.

## Open Questions

*   **Q1: Do the owner's banks check before their first window is drawn?** If a
    bank checks in `Application.onCreate`, it can still see NavGesture once,
    because pausing starts on the bank's first window. The pause then holds,
    so the second open passes. Measured only on the owner's phone, later.
*   **Q2: Does HyperOS let `disableSelf()` and the adb-granted re-enable run
    every time?** The re-enable is the same write `ServiceKeeper.turnOn` uses,
    proven on the POCO F5 on 2026-10-31. On the API 36 emulator both work
    (measured 2026-11-01); the phone is tested later.

## Critical User Journeys (CUJs)

### CUJ 1: Set it up

*   **Given**: The `github` build, gestures on, the adb grant present.
*   **When**: Home › **Bank apps** › the user ticks SBI YONO.
*   **Then**: The screen shows the three needs with a status each: the adb
    grant (with the command to run, since only a computer can fix it), usage
    access and notifications (each with a fix button), and the picked app.

### CUJ 2: Open a bank app

*   **Given**: CUJ 1 done, a third-party launcher, the HyperOS bar hidden.
*   **When**: The user opens SBI YONO.
*   **Then**: The phone's own navigation bar appears, NavGesture leaves the
    enabled Accessibility list, and a notification says gestures are paused
    for SBI YONO. YONO opens (on a cold start possibly only the second time,
    Q1).

### CUJ 3: Leave the bank app

*   **Given**: CUJ 2.
*   **When**: The user taps Home on the phone's own bar.
*   **Then**: About 8 seconds later the service is back, the bar is hidden
    again and the notification is gone. Opening YONO again inside those
    8 seconds keeps the pause, so a bank that closed itself can be reopened.

### CUJ 4: Force the gestures back

*   **When**: The user taps **Resume now** on the notification.
*   **Then**: The service is back at once, even with the bank app in front.
    A Resume-now hold starts: picked apps do not pause again until the user
    has been in an app outside the picked set. Tapping the NavGesture tile
    during a pause does the same.

### CUJ 5: Something is missing

*   **Given**: Usage access or the adb grant is missing.
*   **Then**: The Bank apps screen says which one, and opening a picked app
    does not pause. Without usage access NavGesture could not tell when the
    bank closed; without the grant it could not switch itself back on.
    Notifications denied is not a blocker: the pause still runs, with no
    notice, and the tile is the Resume now.

## Technical Plan (Architecture)

```text
 picked bank app's window appears
          |  TYPE_WINDOW_STATE_CHANGED, packageNames = picked apps only
          v
 NavGestureAccessibilityService.pauseFor(pkg)
          |  1. BankPause.canPause? (github, adb grant, usage access)  no: log once
          |  2. Resume-now hold on and user never left the picked set?   yes: stop
          |  3. save pause {pkg, picked, startedAt} + goingAway flag   (prefs, commit)
          |  4. start BankPauseWatcher (foreground service)   throws: undo, stop
          |  5. GestureBarHider.restoreForShutdown()
          |     bar still hidden by us?                       yes: undo, re-hide, stop
          |  6. disableSelf(); still listed after 3 s?          yes: undo, re-hide
          |     gone from the list: tear down own windows (onUnbind may never come)
          v
 BankPauseWatcher (github only, notification with "Resume now")
          |  every 1 s: usage access gone? -> resume
          |             UsageEvents since startedAt -> FrontApp list
          |             PauseClock.shouldResume (8 s out of the picked set)? -> resume
          |             5 query exceptions in a row? -> resume
          v
 resume: ServiceKeeper.turnOn, retry up to 3 times until the service runs
          |  fails: second notification (id 8, opens the app), clear, stop
          |  3 writes, 4 s apart; usage reads stop once a resume starts
          v
 onServiceConnected: clears the pause, stops the watcher, hides the bar again
```

### A. Hearing only the picked apps

`accessibility_service_config.xml` keeps requesting no events. In `setUp()`,
a collector on `PackageListStore.bankApps(this).packages` updates the
**current** `serviceInfo` (so `canPerformGestures` and the other flags stay):
`eventTypes = TYPE_WINDOW_STATE_CHANGED` and `packageNames` = the picked
packages, or `eventTypes = 0` and `packageNames = null` when the list is
empty or the build is `play`. The service then receives one kind of event,
only from those apps, and still cannot read window content. Events start
with the first DataStore emission after a bind; a bank window shown before
that is missed (same class as Q1).

### B. Pause order and its checks

*   The watcher starts while the edge strips are still on screen. Android's
    list allows a background foreground-service start when the app may start
    activities from the background (the strips allow that, measured by
    `AppSwitcher`) and when battery optimisation is off (setup's Battery
    step). This is not proven for targetSdk 37: a refused start throws, the
    pause is undone and logged, and the e2e suite checks the watcher runs.
*   The bar comes back first and is read back. `restoreForShutdown()` stops the
    observer before writing. If the user hid the bar and it still reads hidden,
    the pause is undone and the bar re-hidden: the service stays on, so there
    is never neither.
*   `disableSelf()` is checked against the enabled list, which is what a bank
    reads: every 500 ms for up to 3 s. Once the entry is gone the service
    takes down its own windows and state (`teardown()`), because the API 36
    emulator dropped the entry at once but did not call `onUnbind` (measured
    2026-11-01). Still listed after 3 s: the pause is undone and the bar
    re-applied. Since the system can later connect the same instance again,
    `setUp()` makes a fresh coroutine scope and `onServiceConnected` resets
    the pausing flag.
*   `onUnbind` reads and clears the `goingAway` flag. Set: it is the pause,
    so ServiceKeeper's "wanted" mark stays true. Not set: the user switched
    the service off, so "wanted" goes false as today, and a saved pause is
    cleared and the watcher stopped.

### C. Knowing when the bank app is gone

*   Each second the watcher calls `hasUsageAccess()`; false resumes at once
    (without the app-op, `queryEvents` returns an empty list, not an error).
*   It queries `queryEvents(startedAt - 1000, now)`, keeps `ACTIVITY_RESUMED`
    packages as `FrontApp`s, drops transient system screens
    (`BankPause.TRANSIENT`: permission controllers, system UI, package
    installers, MIUI security centre), and calls `PauseClock.shouldResume`
    with the picked set saved at pause time. Reading from `startedAt` each
    time means a restarted watcher (`START_STICKY`, null intent) reads the
    same history. Times are wall clock (`System.currentTimeMillis()`), as
    `UsageEvents` are.
*   NavGesture's own UI counts as away: opening NavGesture means the user left
    the bank.
*   Five query exceptions in a row resume, so a broken read never strands it.

### D. Resume-now hold

The notification action starts the watcher with `ACTION_RESUME`; the tile
calls `BankPause.resumeNow`, which calls the watcher's `resumeHook`. Both
save
`holdSince = now` and the picked set, then resume. While a hold is saved,
`pauseFor` reads `FrontApp`s since `holdSince`: if `PauseClock.leftPicked` is
false it does nothing; if true, or if the history cannot be read, it clears
the hold and pauses.

### E. Restarts and stale state

*   `ServiceKeeper.check` (boot, tile, app open), after its `isRunning`
    return: a saved pause with `BankPause.watcherRunning` true returns (the
    pause owns it); with it false the pause is stale, so it is cleared and the
    normal heal runs.
*   The tile's tap during a live pause calls `BankPause.resumeNow` instead of
    `ServiceKeeper.turnOn`.
*   `onServiceConnected`, when a pause is saved, clears it (not the hold) and
    stops the watcher with `stopService`. The saved picked set always
    includes the paused app, so unticking it mid-pause cannot end the pause
    early.

### F. Builds

`BankPauseWatcher` and its manifest entry live in `src/github`. `main` starts
it by class name and reads `BankPause.watcherRunning` and
`BankPause.resumeHook`, which the watcher sets, so the `play` dex has no
watcher. The screen and the event filter are in `main`, gated by
`BuildConfig.IS_PLAY`.

## Alternatives Considered

*   **Shizuku mode without Accessibility.** Rejected by the owner: needs
    Developer options (blocked by SBI YONO), and restarts by hand after every
    reboot.
*   **A shortcut or tile "pause, then open the bank".** Rejected for now: one
    extra tap every time. Kept as the answer if Q1 shows banks that always
    check before their first window.
*   **Polling usage stats from the service instead of events.** Rejected: up
    to a second slower to pause, and battery use all day.
*   **Removing the service with a settings write instead of `disableSelf()`.**
    Rejected: `disableSelf()` is the public API. The write stays the re-enable
    path only.

## Risks & Mitigations

| Risk | Impact | Mitigation |
| :--- | :----- | :--------- |
| A bank checks before its first window (Q1) | Blocked once per cold start | Pause holds 8 s after leaving, so the reopen passes |
| Foreground-service start refused (B) | No pause | Pause undone, service stays on, logged; e2e checks it |
| Bar write ignored (B) | Neither gestures nor bar | Read-back before `disableSelf()`; undo on failure |
| `disableSelf()` refused (Q2) | Bank still blocks | 3 s check undoes the pause; e2e and phone test |
| Re-enable refused or grant lost mid-pause | No auto return | Retries, then a notification; bar already back; tile/app heal (E) |
| Watcher killed by a cleaner | No auto return | Bar already back; `ServiceKeeper.check` heals (E) |
| Usage access revoked mid-pause | Clock blind | Checked every poll; resumes at once |
| Privacy: the app sees which app is in front during a pause | More than today | Package names only, on the phone, only during a pause; disclosure and policy say so |

## Detailed Implementation

### Phase 1: storage and picker (gate: `gradle test`)

*   `settings/PackageListStore.kt` replaces `FavouriteAppsStore.kt`:
    `PackageListStore.favourites(context)` / `.bankApps(context)`, property
    `packages: Flow<List<String>>`, writer `set(packages)`. In a debuggable
    build `bankApps` reads the global setting `navgesture_bank_apps`
    (comma-separated) instead. Call sites: `service/NavGestureAccessibilityService.kt`
    (favourites collector) and `ui/FavouriteAppsScreen.kt`.
*   `ui/AppPicker.kt` (new): `LaunchableApp`, `AppIconImage`, private
    `launchableApps()`, `rememberLaunchableApps(): State<List<LaunchableApp>?>`,
    and `AppChecklist(apps, chosen, onToggle)`, moved out of
    `ui/FavouriteAppsScreen.kt`.

### Phase 2: pause and resume (gate: `gradle test`)

*   `bankpause/PauseClock.kt` (pure) and `PauseClockTest`: bank in front,
    away under and at 8 s, reopen inside 8 s, two picked apps, events out of
    order, first away app counts, `leftPicked`.
*   `bankpause/BankPause.kt` (main): prefs file `bank_pause`; `canPause`,
    `savePause`, `paused`, `clearPause`, `consumeGoingAway`, `setHold`,
    `holdBlocks`, `resumeNow`, `startWatcher`, `stopWatcher`, `frontApps`,
    `TRANSIENT`, `watcherRunning`, `resumeHook`.
*   `app/src/github/kotlin/.../bankpause/BankPauseWatcher.kt` (new): the
    foreground service; channel `bank_pause` (low importance), notification
    id 7, action `com.vijayyaso.navgesture.RESUME_GESTURES`;
    `ServiceCompat.startForeground` with `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`
    on API 34+; `START_STICKY`.
*   `service/NavGestureAccessibilityService.kt`: bank-list collector and event
    filter (A), `onAccessibilityEvent` guard (type, package in list),
    `pauseFor` (B, D), `onUnbind` (B), `onServiceConnected` (E).
*   `setup/ServiceKeeper.kt`: the stale-pause rule (E).
*   `setup/GestureTileService.kt`: a tap during a live pause calls
    `BankPause.resumeNow` (E).
*   `app/src/github/AndroidManifest.xml`: `FOREGROUND_SERVICE`,
    `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, and the service with
    `foregroundServiceType="specialUse"` and
    `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE`. There is no `play`
    manifest to change.
*   `app/src/github/res/values/strings.xml`: notification strings.
*   `sonar-project.properties`: coverage exclusions for `BankPause.kt` and the
    watcher; `PauseClock.kt` stays covered.

### Phase 3: screen and texts (gate: `gradle lint`)

*   `ui/BankAppsScreen.kt` (new): an explanation; three need rows with status
    and fix (adb grant: `GestureBarHider.canWrite`, shows `GRANT_COMMAND`;
    usage access: `hasUsageAccess` / `openUsageAccess`; notifications:
    `NotificationManagerCompat.areNotificationsEnabled`, runtime
    `POST_NOTIFICATIONS` request on Android 13+); the picked list; then
    `AppChecklist` saving through `PackageListStore.bankApps(context).set`.
    Statuses re-read on resume.
*   `ui/MainActivity.kt`: `Screen.BANK_APPS(R.string.section_bank_apps)` and
    its `when` branch. `ui/HomeScreen.kt`: a row tagged `row_bank_apps`,
    hidden when `BuildConfig.IS_PLAY`. `ui/TestTags.kt`: `ROW_BANK_APPS`.
*   `app/src/github/res/values/strings.xml`: overrides `disclosure_reads_body`
    to add "If you pick bank apps, it also learns when one of them opens, and
    which app is in front while a bank pause lasts (app names only)."
    `README.md` gains the feature line ("four things") and its privacy
    paragraph says events come only with bank apps picked. The `play` text and `docs/play-store/disclosure.md` stay.
*   `docs/privacy-policy.md` (github section, date), `README.md` (feature
    line; the adb grant now also serves bank pause),
    `accessibility_service_config.xml` comment, `docs/development.md`
    (eight suites).

### Phase 4: e2e (gate: `e2e/run.sh bank-pause`, `shellcheck`)

*   `service_bound` and `enable_service` move from `e2e/device.sh` to
    `e2e/lib`.
*   `e2e/device.sh` clears a leftover `navgesture_bank_apps`, and after the
    install waits up to 40 s for the service Android restarts to install its
    strips before switching it off and on. Without the wait a cold API 36
    emulator, still running the new APK unoptimised, left the restarted
    service's windows on a dead connection ("token null") in 4 of 6 cold
    runs (measured 2026-11-01).
*   `e2e/bank-pause.sh` (new, debug build only; a release APK prints a skip):
    grant `WRITE_SECURE_SETTINGS`, `GET_USAGE_STATS` and
    `ACCESS_RESTRICTED_SETTINGS`; set `navgesture_bank_apps=com.android.settings`;
    `enable_service`. Cases: opening Settings removes the service from the
    enabled list and starts the watcher; Home brings it back within 15 s
    (if this emulator reports no usage events, the case says so and the
    within 25 s; the tile case below is the proof if not); a second open
    pauses again; then the tile tap
    (`cmd statusbar remove-tile`, `add-tile`, then `click-tile`: after the
    earlier suites SystemUI's copy of the tile went stale and swallowed the
    click, 3 runs of 3) brings it back at once, and
    the Resume-now hold keeps it on when the picked app opens again. Cleanup deletes the
    override and runs `enable_service`, so the collector reloads.
*   Runs before `disclosure` in `all_suites`; added to e2e CI shard 3.

## References

*   Paytm block: https://www.reddit.com/r/IndiaTech/comments/1r8nt4c/
*   HDFC / PayZapp: https://www.reddit.com/r/CreditCardsIndia/comments/1ucblbc/
*   Allowlist pattern: https://www.guardsquare.com/blog/protecting-against-android-accessibility-services-threats
*   Tasker method: https://xdaforums.com/t/module-to-hide-accessibility-service-has-there-been-anything-like-this-before-or-in-the-future.4607477/
*   Foreground service start rules: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
