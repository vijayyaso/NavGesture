# Corner shortcut picker page and app shortcuts

*   **Author:** vijayyaso
*   **Status:** Implemented 2026-11-10 (emulator: corner + app-switch e2e green, YouTube Search shortcut run from a corner; installed on the POCO F5)
*   **Date:** 2026-11-10

--------------------------------------------------------------------------------

## Overview

Two changes to the corner shortcuts on the App switching screen:

1.  The pop-up dialog that picks a corner's shortcut is replaced by a full
    page in the grouped-card look of [ui-redesign.md](ui-redesign.md).
2.  A new kind of shortcut: an **app shortcut**, the action an app offers
    when its icon is long-pressed (for example Keep "New note", Clock
    "New alarm", Maps "Home").

## Owner's answers (2026-11-10)

*   "The corner shortcuts pop-up is not good; redesign it."
*   "Add a shortcut option to use app shortcuts, like direct UPI scan by
    GPay."
*   Shortcut source: **fixed shortcuts only, no computer step**. The owner
    was told that GPay "Scan any QR code" will not appear with this choice.
*   Picker: **full page**.

## Constraints found

*   **Only the default home app can read other apps' shortcuts.**
    `LauncherApps.getShortcuts` is gated by
    `ShortcutService.hasShortcutHostPermission`: the default home app, or a
    holder of `ACCESS_SHORTCUTS` (signature/role, not grantable with
    `pm grant`; Android refuses it on the POCO F5). An accessibility service
    gets no exception.
*   **Fixed ("static") shortcuts need no permission.** Any app that can see
    a package can read them. An app declares them in an XML file named by
    the `android.app.shortcuts` meta-data on its launcher activity.
    NavGesture opens that file with `PackageManager.getResourcesForApplication`
    and reads it the way Android's own `ShortcutParser.java` (AOSP main,
    lines 150-300) does:
    *   `<shortcut>` (depth 2): `android:shortcutId` must be a literal
        string; `android:enabled` (default true); `android:icon`;
        `android:shortcutShortLabel` must be a string resource, or the
        shortcut is skipped. A repeated id is skipped.
    *   `<intent>` (depth 3): parsed with the public
        `Intent.parseIntent(resources, parser, attrs)`, exactly as Android
        does. It reads `action`, `data`, `targetPackage` + `targetClass`
        (component only when both are set) and the intent's own
        `<category>` / `<extra>` children. An intent with no action makes
        the whole shortcut invalid.
    *   `<categories>` (depth 3) is shortcut grouping data, not an intent
        category; it is ignored.
    *   A shortcut with no intent is skipped. Android adds
        `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK |
        FLAG_ACTIVITY_TASK_ON_HOME` to the first intent ("same flag as what
        TaskStackBuilder adds", ShortcutParser.java line 182); NavGesture
        does the same, so the target app opens as it does from the home
        screen.
*   **Dynamic shortcuts are out of reach.** GPay publishes "Scan any QR
    code" at run time (`dumpsys shortcut` on the POCO shows it flagged
    dynamic, from the Flutter `quick_actions` plugin), so it is not in any
    XML file and will not be listed.
*   **Measured on the POCO F5 (2026-11-10):** 92 fixed shortcuts in about 40
    apps (Keep 4, Amazon 4, Snapchat 4, Clock 3, Docs 3, Maps 2,
    Calculator 2, Messages 1, Chrome 1 …).
*   **A launcher starts shortcuts with the publishing app's identity**, so a
    fixed shortcut may point at an activity that is not exported. NavGesture
    can only start exported activities. It lists a shortcut only when
    **every** intent in it resolves to an exported, enabled activity of that
    app that needs no permission. An intent without a component gets the
    app's package set, and is resolved with `resolveActivity`.
*   **Starting activities from the background.** `AppSwitcher.kt` (lines
    28-38) notes that a launch from the service works because the touch
    overlay is visible at that moment. A corner swipe has the same overlay
    up, so `startActivities` is allowed. A silent block by the system cannot
    be detected; only exceptions are recorded.
*   Package visibility: the manifest's `<queries>` already declares
    `MAIN`/`LAUNCHER`, which makes every launchable app visible, including
    its resources.
*   The current picker (`CornerShortcutPicker.kt`) is not used by any e2e
    suite; the `corner` suite sets shortcuts through the debug overrides
    `navgesture_left_corner` / `navgesture_right_corner`, read with
    `CornerShortcut.decode`.

## Goals

*   The user picks a corner shortcut on a full page with grouped cards,
    icons and a check mark on the current choice. One search box filters
    app names and shortcut labels.
*   Every fixed shortcut that passes the export check can be picked and run
    from a corner.
*   A stored app shortcut that disappears (app removed, app update dropped
    it) shows as "App shortcut not found" on the App switching screen. On
    the corner it does nothing and sets the corner's last failure (shown on
    Troubleshooting). Every read and launch error is caught, so the service
    does not crash.
*   Existing stored values (`none`, `screenshot`, `app:<package>` …) keep
    working unchanged.

## Non-goals

*   Dynamic or pinned shortcuts (needs the home-app role, root or Shizuku).
*   Legacy `ACTION_CREATE_SHORTCUT` shortcuts.
*   Changing the corner gesture itself or the app-list panel.

## Design

### Model (`gesture/GestureModel.kt`)

New case:

```text
data class AppShortcut(val packageName: String, val shortcutId: String) : CornerShortcut
    id = "shortcut:" + packageName + "/" + shortcutId
```

`decode` gains the `shortcut:` prefix. The package is the text before the
first `/` (package names never contain `/`); the shortcut id is the rest.
If there is no `/` at index 1 or later, or the id is blank, it decodes to
`None`. Unit tests cover the round trip, an id containing `/`, a missing
`/`, a blank package and a blank id.

### Reading and running fixed shortcuts (`service/StaticShortcuts.kt`, new)

```text
data class StaticShortcut(
    val packageName: String, val id: String, val label: String,
    val iconRes: Int,             // 0 = none; decoded by the UI only
    val intents: List<Intent>,
)
object StaticShortcuts {
    var lastFailure: String?      // like AppSwitcher.lastFailure
    fun forPackage(context: Context, packageName: String): List<StaticShortcut>
    fun find(context: Context, packageName: String, shortcutId: String): StaticShortcut?
    fun launch(context: Context, shortcut: StaticShortcut): Boolean
    fun icon(context: Context, shortcut: StaticShortcut): Drawable?   // UI
}
```

*   `forPackage` reads every launcher activity of the package (queried with
    `GET_META_DATA`) and parses its `android.app.shortcuts` XML with the
    rules in Constraints above.
*   If reading one shortcuts file fails (missing resources, malformed XML,
    any exception), that file's shortcuts are skipped; an app with several
    launcher activities keeps the shortcuts of its other files. A shortcut
    that breaks Android's rules is skipped alone.
*   `launch` calls `context.startActivities(intents)`. On an exception it
    sets `lastFailure` to `"<id>: <exception class>"` and returns false.
*   The corner looks the shortcut up again with `find` on every use, so an
    app update that changes the intent is followed. It runs on the
    service's main thread, as the current app launch does.
*   The UI turns `icon()` into a bitmap with the existing
    `Drawable.toIconBitmap` in `AppPicker.kt` (made `internal`), and shows
    it with the existing `AppIconImage`.

### Running it (`service/GestureActionPerformer.kt`)

`performShortcut` gets a branch for `AppShortcut`. It logs
`CORNER_SHORTCUT <id>` first (the `OpenApp` branch does not log today; the
new branch does). Then `find`: not found → `lastFailure = "corner <id>:
shortcut not found"`, return false. Then `launch`: false →
`lastFailure = "corner <id>: " + StaticShortcuts.lastFailure`.

### Picker page (`ui/CornerShortcutPicker.kt`, rewritten; `ui/MainActivity.kt`)

Two new `Screen` entries, `LEFT_CORNER` and `RIGHT_CORNER`, titled with the
existing `label_left_corner` / `label_right_corner`.

*   `AppSwitchingScreen(model, onOpenFavourites, onOpenCorner:
    (BottomCorner) -> Unit)`; `MainActivity` pushes the matching screen.
*   `CornerShortcutRow` (already a `NavigationRow`, tags `row_left_corner` /
    `row_right_corner` kept) takes `onOpen` instead of `onChange`; the
    dialog code is deleted. `AppListStyleChoice` in the same file stays.
*   `CornerPickerScreen(corner: BottomCorner, model: AppModel, onDone: () ->
    Unit)`. Selecting an item saves it to `leftCornerShortcut` or
    `rightCornerShortcut` through `model.update` and calls `onDone`, which
    pops the screen.

```text
 <-        Left corner
 ┌──────────────────────────────┐
 │ [x] Nothing               ✓  │
 └──────────────────────────────┘
 App lists
 ┌──────────────────────────────┐
 │ [x] Recent apps list         │
 │ [x] Favourite apps list      │
 └──────────────────────────────┘
 Phone actions
 ┌──────────────────────────────┐
 │ [x] Notifications            │
 │ [x] Quick settings           │
 │ [x] Screenshot               │
 │ [x] Lock screen              │
 │ [x] Power menu               │
 │ [x] Split screen             │
 └──────────────────────────────┘
 [ Search apps…              ]
 App shortcuts
 ┌──────────────────────────────┐
 │ (Keep icon)  Keep          v │   tap: show/hide its shortcuts
 │      [sc] New note        ✓  │
 │      [sc] New list           │
 │ (Clock icon) Clock         > │
 └──────────────────────────────┘
 Open an app
 ┌──────────────────────────────┐
 │ (icon) Amazon                │
 │ (icon) Calculator            │
 └──────────────────────────────┘
```

*   Rows: leading `IconBadge` (fixed choices) or the app / shortcut icon;
    trailing check mark (`Icons.Filled.Check`, primary colour) on the
    current choice. Rows that pick are `selectable` with
    `Role.RadioButton`. App rows that only open or close their shortcut list
    use `Role.Button` with a state description (expanded / collapsed).
*   The fixed rows come from the existing `CornerShortcut.fixed` list, in
    its order (its KDoc already promises the picker order); the section
    title for each comes from a small `when`.
*   Fixed-choice glyphs (`material-icons-core` plus three small vectors drawn
    in code, because core has no screenshot, power or split-screen glyph):
    Nothing `Clear`/GREY, Recent apps list `AutoMirrored.Filled.List`/BLUE, Favourite apps list
    `Star`/ORANGE, Notifications `Notifications`/RED, Quick settings
    `Settings`/GREY, Screenshot (code vector)/TEAL, Lock screen
    `Lock`/GREY, Power menu (code vector)/RED, Split screen (code
    vector)/PURPLE.
*   Search: one `OutlinedTextField` above both app groups. It filters app
    names (case-insensitive contains) in both groups, and shortcut labels in
    App shortcuts. Empty search shows all.
*   App list: the existing `rememberLaunchableApps()` (labels and icons,
    sorted, home apps and NavGesture removed).
*   App shortcuts: only apps from that list with at least one shortcut
    that passes the export check. An app row expands in place; the app
    holding the current choice starts expanded. While a search is typed,
    every matching app is expanded; each can still be closed by a tap. Shortcuts load once on
    `Dispatchers.IO` with icons; while loading, the group shows the existing
    `switcher_loading` text.
*   App groups are plain `Column`s inside the page scroll, as the existing
    `AppChecklist` is (about 100 apps on the POCO).

### Showing the current choice (`shortcutLabel`)

For `AppShortcut` the label is "App: Shortcut" (new string
`corner_app_shortcut_named` = `%1$s: %2$s`). It is loaded with
`produceState` keyed on the id, on `Dispatchers.IO`. While it loads the row
shows `switcher_loading`. If the lookup finds nothing it shows the new
string `corner_app_shortcut_missing` = "App shortcut not found".

### Cheat sheet

Unchanged: the existing `gesture_corner_how` text covers every corner
shortcut.

## Alternatives considered

*   **Read every shortcut through `dumpsys shortcut` after a one-time
    `pm grant … DUMP`.** Lists dynamic shortcuts such as GPay "Scan any QR
    code". The owner chose no computer step. Also parses debug text whose
    format can change.
*   **Shizuku.** Gives the shell's `ACCESS_SHORTCUTS`; rejected earlier for
    this app (needs Developer options, which some bank apps block).
*   **Become the default home app for a moment** (as Tasker's workaround
    does). Needs adb or root to switch the home app; visible flicker.
*   **Keep the dialog, add a third list.** The dialog already nests a second
    list inside itself; a third level is what the owner called "not good".
*   **Bottom sheet.** Owner chose a full page.

## Risks

*   **Some fixed shortcuts depend on the launcher's identity** (for example
    a shortcut whose target checks the caller). Mitigation: only exported
    targets are listed; a launch exception is recorded as the corner's last
    failure and shown on Troubleshooting.
*   **Malformed or huge shortcut XML** in a third-party app. Mitigation:
    each app is parsed inside `runCatching`; a broken app is skipped.
*   **Owner expectation**: the GPay example will not appear. Stated in the
    owner's answers and in the final report.

## Detailed Implementation Plan

Paths under `app/src/`. Each phase ends with a container build
(`testGithubDebugUnitTest assembleGithubDebug`) that must pass.

### Phase 1 — model

1.  `main/kotlin/com/vijayyaso/navgesture/gesture/GestureModel.kt` — add
    `CornerShortcut.AppShortcut` and the `shortcut:` prefix in `decode`.
2.  `test/kotlin/com/vijayyaso/navgesture/gesture/CornerShortcutTest.kt` —
    round trip, id with `/`, missing `/`, blank package, blank id.

### Phase 2 — reading and running

3.  `main/kotlin/com/vijayyaso/navgesture/service/StaticShortcuts.kt` (new,
    GPL header) — parsing, export check, launch, icon.
4.  `main/kotlin/com/vijayyaso/navgesture/service/GestureActionPerformer.kt`
    — `AppShortcut` branch with its log line.

The parser has no unit test: the project's tests are plain JUnit, and
Robolectric is not in the offline Gradle cache. It is checked on real apps
instead (Verification 3 and 4).

### Phase 3 — picker

5.  `main/kotlin/com/vijayyaso/navgesture/ui/CornerShortcutPicker.kt` —
    delete the dialog; `CornerShortcutRow(onOpen)`; new
    `CornerPickerScreen`; three code vectors; `shortcutLabel` for
    `AppShortcut`.
6.  `main/kotlin/com/vijayyaso/navgesture/ui/AppPicker.kt` — make
    `toIconBitmap` `internal`.
7.  `main/kotlin/com/vijayyaso/navgesture/ui/AppSwitchingScreen.kt` —
    `onOpenCorner` parameter, passed to both corner rows.
8.  `main/kotlin/com/vijayyaso/navgesture/ui/MainActivity.kt` — `Screen`
    entries `LEFT_CORNER`, `RIGHT_CORNER`; the `onOpenCorner` callback; the
    two routes.
9.  `main/res/values/strings.xml` — add `corner_app_shortcut_named`,
    `corner_app_shortcut_missing`, `corner_section_app_lists`,
    `corner_section_phone_actions`, `corner_section_app_shortcuts`,
    `corner_search_apps`, `corner_app_shortcuts_body` (the App shortcuts
    footer: private and run-time shortcuts cannot be shown),
    `corner_expanded` / `corner_collapsed` (the app rows' state
    description). Change `corner_open_app` from "Open an app…" to "Open an
    app" (now the section title). Delete `corner_pick_app` and
    `action_cancel` (only the dialog used them).

Not changed: `TestTags.kt` (the two row tags stay), `e2e/`,
`GestureSettingsRepository.kt` (stores the id string as today).

### Phase 4 — verification

1.  Container build: `testGithubDebugUnitTest lintGithubDebug lintPlayDebug
    assembleGithubDebug assembleGithubRelease`, signed as in
    `ui-redesign.md`.
2.  End-to-end tests on the API 36 emulator (AVD `ng36`): the `corner` and
    `app-switch` suites, debug APK.
3.  Emulator: screenshot the picker for each corner. Pick a fixed shortcut
    of a stock app that lists one. Swipe the corner with
    `input motionevent`, as `e2e/corner.sh` does. Check that the expected
    screen opens.
4.  Phone: only when the screen is on and there is no call. Install with
    `-i com.android.vending`. Compare the apps the picker lists with the
    92 fixed shortcuts `dumpsys shortcut` reported. Inject touches only
    while NavGesture is in front.
