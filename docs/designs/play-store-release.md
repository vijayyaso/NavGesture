# Play Store release and support for any Android 11+ phone

*   **Author:** vijayyaso
*   **Status:** In Review
*   **Date:** 2026-10-31

--------------------------------------------------------------------------------

## Overview

NavGesture ships today as one APK on GitHub Releases. It is built and tested
mainly on one POCO F5 running HyperOS 3 (Xiaomi's version of Android). This design prepares a second build
for Google Play and makes every feature either work on, or not appear on, any
phone with Android 11 (API 30) or newer, and states the measured limits under
Android's own gesture navigation.

Terms used below: **Recents** is Android's own recent-apps screen
(`GLOBAL_ACTION_RECENTS`). The **app row** is NavGesture's own row of app icons
that opens on a swipe up and sideways (`service/SwitcherPanelOverlay.kt`). The
**tile** is NavGesture's Quick Settings tile; HyperOS calls that panel Control
Center.

Publishing itself (paying for the developer account, identity checks, the
closed test, pressing "Release") is the owner's job. This design produces
everything those steps need: the Play build, the in-app disclosure, the store
text, the policy answers, the privacy policy and a step-by-step checklist.

## The Problem

Five things block a Play release today:

1.  **The app updates itself.** It downloads new APKs from GitHub and installs
    them with `PackageInstaller`, using `REQUEST_INSTALL_PACKAGES`. Play policy
    says an app "may not modify, replace, or update itself using any method
    other than Google Play's update mechanism", and self-updating is not an
    allowed use of `REQUEST_INSTALL_PACKAGES`.
2.  **No prominent disclosure.** NavGesture is an Accessibility service and is
    not an accessibility tool in Play's sense (its main purpose is navigation,
    not disability support). Play then requires an in-app disclosure screen
    with an explicit accept action before the user turns the service on, a
    Play Console declaration, and a video of that flow.
3.  **The service switches itself back on.** `ServiceKeeper` (v1.1.1) writes
    `enabled_accessibility_services` to put the service back after a force
    stop, and the tile can turn the service on in one tap. Both skip Android's
    own consent screen. The `play` build disables both (section A).
4.  **HyperOS-only parts show on every phone.** "Hide the gesture bar" writes
    two HyperOS-only global settings (`force_fsg_nav_bar`,
    `hide_gesture_line`). On a Pixel or Samsung the switch appears and has no
    effect.
5.  **The Play build would ask for "restricted settings".** Setup and home
    tell every Android 13+ user to "Allow restricted settings" in App info.
    That applies only to apps installed outside an app store; a Play install
    has no such menu item, and a reviewer sees an app asking to loosen a
    security setting.

Whether "works on any Android 11+ phone" is accurate depends on how
Android's own gesture navigation treats NavGesture's strips. It was measured;
see section E.

## Goals

*   One codebase, two builds: `github` (today's app plus the disclosure
    screen) and `play` (no updater, no network, no self-enable).
*   The `play` build carries no `REQUEST_INSTALL_PACKAGES`, no `INTERNET`, no
    updater code in its dex, and ships as an Android App Bundle (`.aab`).
*   A prominent disclosure that meets each point of Play's Accessibility
    policy, shown in both builds before the app sends the user to the
    Accessibility switch.
*   On a non-HyperOS phone no HyperOS-only control is shown.
*   The seven end-to-end suites (`navigation`, `app-switch`, `back-revert`,
    `status-refresh`, `circle-to-search`, `landscape-confirm`, and the new
    `disclosure`; see `e2e/run.sh`) pass for the `play` and `github` release APKs on the
    Android 16 emulator in three-button mode; the `navigation` and
    `back-revert` suites pass on Android 11; and the `play` release works on
    the POCO F5. Gesture-mode runs (C5) are informational, not a gate.
*   `docs/play-store/` holds every text the Play Console asks for, plus a
    checklist the owner can follow without help.

## Non-Goals

*   Creating the Play developer account, paying the fee, finding the 12
    testers, recording the video, or submitting. Those need the owner's
    identity and money.
*   Per-brand tuning for Samsung One UI, Pixel or others. No such phones are
    available to test on.
*   Claiming `isAccessibilityTool`. NavGesture's main purpose is navigation;
    Play lists launchers and automation tools as not qualifying.
*   Translations. English only for the first Play release.
*   Changing any gesture behaviour or threshold.
*   Blocking the service when it was switched on from Android Settings without
    the in-app disclosure. Android shows its own warning on that path.

## Open Questions

*   **Q1: Will Play accept `WRITE_SECURE_SETTINGS` in the manifest?** It can
    only be granted by `adb`, so it gives the app nothing on install.
    Assumption (unverified until the first upload): Play accepts the
    declaration. If a review rejects it, move it to the `github` manifest; the
    `play` build then never shows "Hide the gesture bar" (see C1).

## Critical User Journeys (CUJs)

### CUJ 1: New Play user turns NavGesture on

*   **Given**: The `play` build is freshly installed on a non-HyperOS phone.
*   **When**: The user opens the app and taps **Turn on**.
*   **Then**: The disclosure screen appears first. **Agree** opens
    Accessibility settings. After the switch is on, home reads
    "Gestures are on" and Back, Home and Recents swipes work.

### CUJ 2: Play user declines the disclosure

*   **Given**: The disclosure screen is showing.
*   **When**: The user taps **Not now**, then taps **Turn on** again.
*   **Then**: **Not now** returns to home with gestures still off and nothing
    opens Settings. The second **Turn on** shows the disclosure again.

### CUJ 3: Phone with Android's own gesture navigation

*   **Given**: A non-HyperOS phone set to gesture navigation.
*   **When**: The user turns NavGesture on and swipes.
*   **Then**: Back from either side and a fast Home swipe from the bottom
    work, as measured in section E. Setup shows the HyperOS bar step (`SetupStep.NAVIGATION_BAR`) as
    unavailable, as it does today, and `GestureSettingsScreen` hides the
    gesture-bar section.

### CUJ 4: GitHub user keeps the extras

*   **Given**: The `github` build on the POCO F5.
*   **When**: The user opens the app, checks for updates, or the service is
    force-stopped.
*   **Then**: Updates and the automatic turn-back-on work as in v1.1.1. The
    disclosure appears on every Turn on (button or tile) until the user taps
    **Agree**; after that it does not appear again until app data is cleared
    or the app is reinstalled, and the tile turns the service on in one tap
    as in v1.1.1.

### CUJ 5: Play user opens Control Center with gestures off

*   **Given**: The `play` build, service off.
*   **When**: The user taps the NavGesture tile.
*   **Then**: If the disclosure was never accepted, the app opens on the
    disclosure. Otherwise Accessibility settings open. In `play`, the tile
    never writes `enabled_accessibility_services`. The full tile rules are in
    section B.

## Technical Plan (Architecture)

### A. Two builds from one codebase

A Gradle flavor dimension `store` with two flavors:

```text
              main source set: gestures, setup, settings, disclosure
                 |                                     |
       src/github                               src/play
       + update/ package (moved)                + Store.kt (no-op hooks)
       + ui/UpdateRows.kt, UpdatesScreen.kt     IS_PLAY = true
       + Store.kt (real hooks)
       + manifest: INTERNET,
         REQUEST_INSTALL_PACKAGES,
         InstallResultReceiver,
         BootCompletedReceiver + RECEIVE_BOOT_COMPLETED
       IS_PLAY = false
       -> app-github-release.apk               -> app-play-release.aab
          published as navgesture.apk             (Play Console)
          (GitHub Releases)
                                               -> app-play-release.apk
                                                  (tests and the F5 only)
```

*   **Updater moves out of `main`.** The `update/` package,
    `ui/UpdateRows.kt` and `ui/UpdatesScreen.kt` move to `src/github`, and
    their tests to `src/testGithub`. Only two `main` files call into them:
    `ui/MainActivity.kt` (builds `UpdateViewModel`, which starts a check in
    its `init`, and shows the Updates screen) and `ui/HomeScreen.kt` (the
    Updates row). Both call a small `Store` object that exists in each flavor
    with the same signatures: the `github` copy delegates to the updater, the
    `play` copy draws nothing and does nothing. The `play` build compiles no
    updater classes. The updater's 39 strings move to `src/github/res`, and
    so does the github wording of `accessibility_service_description` (the
    only string that mentions the update check).
*   **One flag for the remaining shared behaviour.** `BuildConfig.IS_PLAY` gates the two behaviours
    left in `main`: `ServiceKeeper` (auto turn-back-on and one-tap tile,
    through `ServiceKeeper.canTurnOn`) and the gesture-bar section rule in
    C1. It is a compile-time constant. Not gated: `ServiceKeeper.check` also
    shows the phone's own bar again when this app hid it and the service died
    without its unbind (upstream #134). That needs the adb grant, so in `play`
    it runs only for a user who gave it.
*   **Boot receiver goes to `github`.** With self-enable off it only logs the
    navigation mode after boot, a diagnostic line nobody reads in a Play
    install, so `play` drops it and `RECEIVE_BOOT_COMPLETED`.
*   **The merged `play` manifest declares** (all from the main manifest; the
    `play` flavor adds no manifest of its own) `WRITE_SECURE_SETTINGS` (adb-only, see
    Q1) and `PACKAGE_USAGE_STATS` (usage access, granted by the user in
    Settings), plus the system-only `BIND_ACCESSIBILITY_SERVICE` and
    `BIND_QUICK_SETTINGS_TILE` permissions on the two services.
*   **Same app id and version.** Both flavors keep
    `applicationId = com.vijayyaso.navgesture` and share `versionCode`, which
    the existing release automation bumps.
*   **Signing.** The release key is the keystore
    `~/.config/navgesture/navgesture-release.jks`, alias `navgesture`. It
    signs every GitHub release (local builds mount it as `KEYSTORE_PATH`; CI
    restores the same keystore from the `ANDROID_KEYSTORE_B64` secret as
    `android.keystore`, the name `docs/development.md` uses). For Play App
    Signing this key becomes the **app signing key**, uploaded once with
    Google's [PEPK tool](https://support.google.com/googleplay/android-developer/answer/9842756).
    The **upload key** is a new, separate key, as Google recommends; Play
    also accepts the app signing key for uploads if the owner skips it. The Play and GitHub
    builds then carry the same signature, so the owner's phone can take a
    Play update over the GitHub install without uninstalling or losing the
    `adb` grant. Checked 2026-10-31: the keystore certificate SHA-256
    `8527f9ba…5e4311` equals the certificate of the published v1.1.0 release
    APK (`apksigner verify --print-certs`); the key is RSA 4096, valid until
    2054. `release-checklist.md` repeats this check before the PEPK upload.
    The cost: the private key is uploaded to Google as the app signing key,
    and that cannot be undone after the first upload.
*   **No backup or transfer.** `android:allowBackup="false"`, plus
    `android:dataExtractionRules` (Android 12+) and
    `android:fullBackupContent` (Android 11) files that exclude every domain
    from both cloud backup and device-to-device transfer. `allowBackup` alone
    does not stop device transfer on Android 12+. This keeps gesture settings,
    the consent record and `service_keeper/wanted_on` on the phone, matching
    the privacy policy ("They are not included in cloud backups"), and a
    restored or transferred install cannot skip the disclosure.

### B. Prominent disclosure

One screen, in both builds, about the AccessibilityService only. Its exact
text is in [`docs/play-store/disclosure.md`](../play-store/disclosure.md):
what it reads (where and when you touch inside the edge strips), what it does
with it (Back, Home, Recents, the app row, Circle to Search, passing other
touches on), and what it never does (see the screen, read typed text, store
or send touches). Two buttons: **Agree** and **Not now**. Each policy point is
mapped to the screen in
[`docs/play-store/accessibility-declaration.md`](../play-store/accessibility-declaration.md).

**Consent record.** A new `SetupFlag.DISCLOSURE_ACCEPTED`
(DataStore key `disclosure_accepted`, default `false`) and a matching
`SetupRecord.disclosureAccepted`. `AppModel.record` already holds the loaded
record, so the UI reads it without a suspend call. Clear data, or uninstall
then install, resets it; an update over the existing install keeps it.

**The gate.** One routing function in `ui/Actions.kt`:

```kotlin
internal fun openAccessibilityOrDisclosure(
    context: Context,
    disclosureAccepted: Boolean,
    showDisclosure: () -> Unit,
)
```

It calls `showDisclosure()` when consent is missing and `openAccessibility()`
when it is present. `AppModel.requestAccessibility(context)` is the only
caller: it reads `record.disclosureAccepted` and passes
`{ disclosureRequested = true }`. Every Turn on / Fix / Open button that
today calls `openAccessibility` calls `model.requestAccessibility(context)`.
The composable watches `disclosureRequested`; in one `LaunchedEffect` it
pushes `Screen.DISCLOSURE` (title `disclosure_title`) unless it is already on
top, and sets the flag back to `false`, so a second Turn on after Not now
shows the disclosure again.

| Entry point | Stack while shown | **Agree** | **Not now** or Back |
| :---------- | :---------------- | :-------- | :------------------ |
| Home: Turn on / Fix | Home › Disclosure | records consent, pops, opens Accessibility settings | pops to Home |
| Setup: accessibility step | Home › Setup › Disclosure | same, pops to Setup | pops to Setup |
| Troubleshooting: Open | Home › Troubleshooting › Disclosure | same, pops to Troubleshooting | pops to Troubleshooting |
| Tile | Home › Disclosure (new or reused task) | same, pops to Home | pops to Home |

On return from Settings the user is on the screen the disclosure was opened
from. Back from the disclosure is the same as **Not now**.

**The tile.** `MainActivity` becomes `android:launchMode="singleTop"` and
owns an activity-level `openDisclosure` state that it passes into the
composable. It reads `EXTRA_SHOW_DISCLOSURE` (a boolean extra, constant on
`MainActivity`) in `onCreate` (only when there is no saved state) and in
`onNewIntent`, removes the extra, and sets `openDisclosure`; the composable
then sets the stack to Home › Disclosure and clears the flag. A rotation
therefore never reopens the disclosure. Turn on buttons set
`AppModel.disclosureRequested`, which pushes the disclosure the same way.

The tile reads the consent flag from DataStore in its existing coroutine
scope, then acts on the main thread. `TileFace` keeps owning what the tile
shows; what a tap does is the pure function `tileTap()` in
`setup/TileModel.kt`, evaluated in this order, so the one-tap turn-on is
reachable only after consent:

| Service | Consent | Build | Tile tap |
| :------ | :------ | :---- | :------- |
| on | any | any | opens `MainActivity` (unchanged `TileTarget.APP`) |
| off | no | any | opens `MainActivity` with `EXTRA_SHOW_DISCLOSURE` |
| off | yes | `github`, adb grant present, switch not listed | `ServiceKeeper.turnOn` (v1.1.1 one-tap) |
| off | yes | otherwise | `GestureTileService.accessibilityIntent` (unchanged `TileTarget.ACCESSIBILITY_SETTINGS`) |

The service itself does not check consent (Non-Goals).

**Usage access** is explained separately, where it is requested: a footnote
row under the existing `usage_access` row on the App switching screen, text in
`disclosure.md`. The tagged row's text does not change, because
`e2e/status-refresh.sh` compares it exactly.

### C. Any Android 11+ phone

1.  **C1. Hide the gesture-bar section where it cannot work.** Show it only
    on HyperOS (`NavigationModeReader.isHyperOsDevice()`), and in `play` only
    when `GestureBarHider.canWrite()` is already true, so a Play user never
    sees an `adb` instruction.
2.  **C2. No navigation-mode step.** The measurement above shows NavGesture
    works under stock gesture navigation. Setup already shows the HyperOS bar
    step (`SetupStep.NAVIGATION_BAR`) as unavailable and hides Autostart off
    HyperOS (`navigationBarChangeable`, `autostartApplies`), so nothing
    changes there.
3.  **C3. Restricted settings only in `github`.** `restrictedSettingsApply()`
    becomes Android 13+ **and** `!BuildConfig.IS_PLAY`, the same flag as every
    other Play difference. It gates the setup step, the home hint and the
    Troubleshooting row, so the Play build never mentions restricted
    settings.
4.  **C4. Say what gesture navigation may take.** The store listing states
    that on phones using Android's own gesture navigation, the phone may
    handle slow side swipes, the bottom swipe-and-hold (Recents) and the app
    row itself, that Back and Home keep working either way, and that
    three-button navigation gives every NavGesture gesture. The app does not
    hide them, because fast swipes do reach NavGesture (section E).
5.  **C5. Gesture-mode run (information only).** `e2e/emulator.sh` forces three-button mode
    today and exits when `navigation_mode` is not 0; its comment says
    bottom-edge cases cannot pass in gestural mode. A new `-g` flag on both
    `e2e/emulator.sh` and `e2e/run.sh` (which forwards it) instead disables
    the other navbar overlays, enables
    `com.android.internal.systemui.navbar.gestural`, and waits for
    `navigation_mode` = 2. It reproduces section E's second run; it is not a
    release gate, because the phone, not NavGesture, decides what happens to
    slow swipes in that mode. The listing note (C4) is the user-facing
    answer.

### D. Play Console material

`docs/play-store/` holds the text and answers. The privacy policy,
`docs/privacy-policy.md`, was written with this design; its URL,
<https://github.com/vijayyaso/NavGesture/blob/main/docs/privacy-policy.md>,
is the policy link Play asks for, since the project website was retired.

### E. Measurement: stock gesture navigation

Measured on 2026-10-31 on an Android 16 (API 36) Pixel 6
emulator left in its default gesture-navigation mode, with the v1.1.1 release
APK and `adb shell input swipe`:

| Swipe | NavGesture log | Result |
| :---- | :------------- | :----- |
| Bottom, fast up | `GESTURE_FIRED BOTTOM Home` | Home |
| Left edge, rightwards | `GESTURE_FIRED LEFT Back` | One Back, not two |
| Right edge, leftwards | `GESTURE_FIRED RIGHT Back` | One Back, not two |
| Left edge, low (y = 2000 of 2400) | `GESTURE_FIRED LEFT Back` | One Back |

In the measured cases the strips receive the touches and the system's own
Back does not fire a second time.

A second run (2026-11-01, `e2e/run.sh -g back-revert` on the `play` release)
drove slow side swipes, one motion event per `adb` call. There the system took
the swipe: a left swipe brought back to the edge still went Back (the
launcher came to the front), and NavGesture logged neither
`GESTURE_REVERTED` nor `GESTURE_FIRED`. 6 of the suite's 10 checks passed. So
under stock gesture navigation Back still happens, but for slow swipes it is
the phone's Back, without NavGesture's cancel-by-returning and drop. Each strip sets
[`View.setSystemGestureExclusionRects`](https://developer.android.com/reference/android/view/View#setSystemGestureExclusionRects%28java.util.List%3Candroid.graphics.Rect%3E%29)
over itself (`service/EdgeTouchOverlay.kt`, `addStrip`). Android honours only
200 dp of exclusion per side edge, and the side strips are taller than that,
yet single Back was measured both mid-screen (y = 1200) and low (y = 2000);
why the area past the cap also stays single is not proven. A bottom
swipe-and-hold reached the system Recents screen, but the log cannot tell
whether NavGesture or the system opened it. No setup step is needed for the
navigation mode on non-HyperOS phones.

## Alternatives Considered

*   **Separate `applicationId` for Play.** Rejected: two sets of settings and
    two Accessibility entries, and users could not switch builds without
    uninstalling.
*   **Remove the updater everywhere.** Rejected by the owner: GitHub users
    would have to download each release by hand.
*   **Keep the updater in `main` behind a flag and rely on R8.** Rejected:
    `MainActivity` builds the update view model unconditionally and its `init`
    starts a check, so the reference chain stays live, and a GitHub user who
    switches to Play keeps `update_check.enabled=true`. With `update/` outside
    the `play` source set, its absence does not depend on R8.
*   **Two flags (`SELF_UPDATE`, `SELF_HEAL`).** Rejected: they never differ.
*   **Keep auto turn-back-on in `play`.** Rejected by the owner: the `play`
    build removes both paths that enable the service without Android's
    consent screen.
*   **Claim `isAccessibilityTool`.** Rejected: Play reserves it for tools whose
    primary purpose is disability support.
*   **Let Google generate the app signing key.** Rejected for v1: the Play and
    GitHub builds would never install over each other. The fallback is in
    Risks.
*   **Service-side consent gate.** Rejected: it would break the service for a
    user who turned it on from Settings, and Android already warns there.
*   **Require three-button navigation off HyperOS.** Rejected by measurement:
    gestures work in stock gesture mode.

## Risks & Mitigations

| Risk | Impact | Mitigation / status |
| :--- | :----- | :------------------ |
| Play rejects `WRITE_SECURE_SETTINGS` | `play` loses "Hide the gesture bar" | Move it to the `github` manifest; C1 already hides the section (Q1) |
| Signing key uploaded to Google | Irreversible; key custody shared | Certificate checked against v1.1.0; keep an offline backup of the keystore before upload. Fallback: a Google-generated key, after which GitHub and Play installs no longer install over each other |
| Disclosure skipped by turning the service on in Android Settings | Policy question | Accepted: Android shows its own warning there (Non-Goals) |
| Bottom swipes taken by the system on some phones | Recents hold and app row may not work under stock gesture navigation | Listing says so and recommends three-button navigation (C4) |
| Single Back past the 200 dp exclusion cap is unexplained | Possible double Back on some brands | Measured single Back at two heights on API 36; watch Play reviews |
| Stock gesture navigation takes slow side swipes | NavGesture's cancel-by-returning and drop do not show for them; Back still works | Measured (section E); listing note (C4) recommends three-button navigation |
| Only API 30/36 emulators and one POCO F5 tested | Brand-specific failures unseen; `play` has no crash reporting | Read Play Console vitals (crashes, ANRs) and reviews daily for the first two weeks (release checklist) |
| Disclosure regression | Play policy breach | New `disclosure` e2e suite in CI and unit tests for `tileTap()` |
| Closed-test rule (12 testers, 14 days) | Launch delayed | Owner recruits testers before the build is ready |
| Moving `update/` breaks the `github` build | GitHub users lose updates | CI builds both debug variants and runs the updater tests under `testGithub` |

Scalability is not applicable: the `play` build has no network and no
server.

## References

*   Accessibility API policy:
    <https://support.google.com/googleplay/android-developer/answer/10964491>
*   Self-update rule (Device and Network Abuse):
    <https://support.google.com/googleplay/android-developer/answer/9888379>
*   `REQUEST_INSTALL_PACKAGES` policy:
    <https://support.google.com/googleplay/android-developer/answer/12085295>
*   Testing rule for new personal accounts:
    <https://support.google.com/googleplay/android-developer/answer/14151465>
*   PEPK and Play App Signing:
    <https://support.google.com/googleplay/android-developer/answer/9842756>

## Detailed Implementation

### Build, manifests and source sets

*   `app/build.gradle.kts` — `flavorDimensions += "store"`, flavors `github`
    and `play`, `buildFeatures.buildConfig = true`,
    `buildConfigField("boolean", "IS_PLAY", "false"/"true")`. Jacoco:
    `dependsOn("testGithubDebugUnitTest")`, `kotlinClassesDir` =
    `intermediates/built_in_kotlinc/githubDebug/compileGithubDebugKotlin/classes`,
    `coverageExec` =
    `outputs/unit_test_code_coverage/githubDebugUnitTest/testGithubDebugUnitTest.exec`,
    `sourceDirectories` = `src/main/kotlin` and `src/github/kotlin`.
*   `sonar-project.properties` — `sonar.sources` adds `app/src/github/kotlin`
    and `app/src/play/kotlin`; `sonar.tests` adds `app/src/testGithub/kotlin`;
    `sonar.java.binaries` uses the `githubDebug` path above;
    `sonar.coverage.exclusions` points the `update/UpdatePlatform.kt` and
    `update/UpdateViewModel.kt` entries at `src/github`, replaces the main
    `boot/**` entry with `app/src/github/kotlin/.../boot/**`, and adds
    `app/src/github/kotlin/.../ui/**`, `app/src/play/kotlin/.../ui/**` and
    `setup/ServiceKeeper.kt` (Android settings writes, device-only).
*   `app/src/main/AndroidManifest.xml` — remove `INTERNET`,
    `REQUEST_INSTALL_PACKAGES`, `RECEIVE_BOOT_COMPLETED`,
    `InstallResultReceiver` and `BootCompletedReceiver`; set
    `android:allowBackup="false"`; `MainActivity` gets
    `android:launchMode="singleTop"`.
*   `app/src/github/AndroidManifest.xml` (new) — the two permissions, the
    boot permission and the two receivers.
*   `app/src/main/res/xml/data_extraction_rules.xml`,
    `app/src/main/res/xml/backup_rules.xml` (new) —
    exclude every domain from cloud backup and device transfer; referenced
    from the main manifest.
*   `app/src/github/res/values/strings.xml` (new) — the 39 `update_*` strings
    move here from `src/main/res/values/strings.xml`.
    `accessibility_service_description` stays in `src/main/res` (the
    service config references it) with its last sentence removed ("The app
    itself contacts GitHub only if you turn on its update check."), and this
    file overrides it with the full github wording.
*   Move to `app/src/github/kotlin/com/vijayyaso/navgesture/`: all of
    `update/`, `ui/UpdateRows.kt`, `ui/UpdatesScreen.kt`,
    `boot/BootCompletedReceiver.kt`.
*   Move to `app/src/testGithub/`: `kotlin/.../update/*` and
    `resources/github-release-latest.json`.
*   `ui/Store.kt` (new, one copy in `src/github`, one in `src/play`), same
    signatures in both:

    ```kotlin
    internal object Store {
        /** Activity-scoped hook: owns the update view model and its resume check. */
        @Composable fun Bind()
        /** The Updates row on home; draws nothing in play. */
        @Composable fun HomeRow(onOpen: () -> Unit)
        /** The Updates screen body; draws nothing in play. */
        @Composable fun UpdatesScreen()
    }
    ```

    The `github` copy calls `viewModel<UpdateViewModel>()` in each function;
    inside one activity that returns the same instance. `Bind()` moves the
    existing `LifecycleResumeEffect` from `MainActivity`.
*   `ui/MainActivity.kt` — calls `Store.Bind()`, passes nothing update-related
    to `HomeScreen`, and its `UPDATES` branch calls `Store.UpdatesScreen()`.
    The `UPDATES` enum entry stays; `play` never navigates to it.
*   `ui/HomeScreen.kt` — drops the `UpdateViewModel` parameter and calls
    `Store.HomeRow { onOpen(Screen.UPDATES) }`.

### Self-enable gating

*   `setup/ServiceKeeper.kt` — `canTurnOn(context)` =
    `!BuildConfig.IS_PLAY && GestureBarHider.canWrite(context)`. In `check`,
    the turn-back-on part (`heal`) returns early when `BuildConfig.IS_PLAY`
    (so its "adb permission is gone" log still runs in `github`); the
    gesture-bar restore (`GestureBarHider.restoreIfStopped`) runs in both
    builds. `turnOn` returns `false` when `!canTurnOn`.
*   `setup/GestureTileService.kt` — calls
    `tileTap(serviceEnabled = TileFace.of(bound).target == TileTarget.APP,
    disclosureAccepted, canTurnOn = ServiceKeeper.canTurnOn(this) &&
    !DiagnosticsCollector.isAccessibilityServiceSwitchedOn(this))`, the last
    term keeping v1.1.1's "switch not listed" guard.
*   `setup/TileModel.kt` — `TileTap` and `tileTap()`, the tile table in
    section B; `TileModelTest` covers every row.
*   `setup/GestureTileService.kt` — reads consent from DataStore in its
    coroutine scope, then acts on `tileTap()` on the main thread. A failed
    read counts as no consent, so the tile shows the disclosure rather than
    skip it.

### Disclosure

*   `setup/SetupStateRepository.kt` — `SetupFlag.DISCLOSURE_ACCEPTED`
    (key `disclosure_accepted`) and `SetupRecord.disclosureAccepted`
    (default `false`). Writes reuse the generic `set(flag, value)`, through
    `AppModel.acceptDisclosure()`.
*   `app/src/test/.../setup/SetupStateRepositoryTest.kt` — its "each flag
    lands in its own field" map gains
    `SetupFlag.DISCLOSURE_ACCEPTED to SetupRecord::disclosureAccepted`; the
    test asserts the map covers every `SetupFlag`, so it fails without this.
*   `ui/DisclosureScreen.kt` (new) — the text from
    `docs/play-store/disclosure.md`; test tags `DISCLOSURE_AGREE`
    (`disclosure_agree`) and `DISCLOSURE_NOT_NOW` (`disclosure_not_now`) in
    `ui/TestTags.kt`.
*   `ui/MainActivity.kt` — `Screen.DISCLOSURE(R.string.disclosure_title)`,
    `EXTRA_SHOW_DISCLOSURE`, the activity-level `openDisclosure` state,
    `onNewIntent` handling.
*   `ui/AppModel.kt` — `disclosureRequested`, `requestAccessibility()`,
    `acceptDisclosure()`.
*   `ui/Actions.kt` — `openAccessibilityOrDisclosure()` (section B).
*   `ui/StatusBlock.kt` — gains `restrictedSettingsApply: Boolean` and an
    `onTurnOn` callback; `ui/HomeScreen.kt` passes
    `model.restrictedSettingsApply` and `{ model.requestAccessibility(context) }`.
*   `ui/SetupSection.kt`, `ui/TroubleshootingScreen.kt` — their buttons call
    `model.requestAccessibility(context)`.
*   `ui/AppSwitchingScreen.kt` — the usage-access footnote row.
*   `ui/TestTags.kt` stays one shared object; its five update tags are simply
    unused in the `play` build.
*   `res/values/strings.xml` — every string named in `disclosure.md`.
*   `e2e/disclosure.sh` (new) — first switches the service off
    (`settings delete secure enabled_accessibility_services`) and clears app
    data (`pm clear`, which also force-stops the app), then checks: home reads off;
    Turn on shows the disclosure; Not now returns to the app; Turn on shows it
    again; the tile's intent (`EXTRA_SHOW_DISCLOSURE`) shows it; Agree opens
    Android's Accessibility settings; Back returns to the app's home; after
    Agree, Turn on goes straight to Settings. It runs last (it clears data) and binds the service again.
    `e2e/run.sh` lists it last in `all_suites`; `.github/workflows/e2e.yaml`
    adds it to shard 3. `ui/TestTags.kt` gains `status_turn_on` on the
    status card's Turn on / Fix button.

### Any Android 11+ phone

*   `ui/GestureSettingsScreen.kt` — the C1 rule for the Gesture bar section.
*   `ui/Actions.kt`, `ui/AppModel.kt`, `ui/StatusBlock.kt`,
    `ui/TroubleshootingScreen.kt` — `restrictedSettingsApply()` and its three
    uses (C3).
*   `e2e/app-switch.sh` — its first two cases still describe the
    half-screen rule replaced in v1.1.0 (they fail on v1.1.1 as well). They
    become "drag right opens recents" and "drag left opens favourites", with
    a 120 px rise and a 320 px sideways drag, matching `GestureTracker`'s
    rule (sideways at least 40 dp and longer than the rise).
*   `e2e/lib` — two Android 11 fixes found by the API 30 run: `resumed_activity`
    also matches `mResumedActivity` (Android 11 has no `topResumedActivity`),
    and `reset_log` counts the NavGesture lines still in the log after
    `logcat -c` so `gesture_log` skips them (the API 30 image kept the
    previous case's lines after a clear; reading from a device timestamp
    with `logcat -T` returned nothing there).
*   `e2e/emulator.sh`, `e2e/run.sh` — the `-g` flag (C5). `-g` only switches
    the emulator to gesture navigation; it does not change which suites run.
    `run.sh` forwards it as `e2e/emulator.sh ${gestural:+-g}` with a
    `shellcheck disable=SC2086` comment, like the existing `apk_arg` line.

### Release tooling and docs

The published GitHub asset keeps its name, `navgesture.apk`, so
`ReleaseParser.APK_NAME`, `README.md` and existing installs are unaffected.

*   `docker/apk.Dockerfile` — line 40 `assembleRelease` →
    `assembleGithubRelease bundlePlayRelease`; line 44 searches
    `app/build/outputs/apk/github/release`; also copy
    `app/build/outputs/bundle/playRelease/app-play-release.aab` to
    `/out/navgesture-play.aab` and into the final image.
*   `.github/workflows/release.yaml` — upload `navgesture-play.aab` as a
    workflow artifact (not a release asset).
*   `.github/workflows/development.yaml` — line 69
    `lint test jacocoTestReport assembleDebug` →
    `lint test jacocoTestReport assembleGithubDebug assemblePlayDebug`
    (`lint` already covers every variant).
*   `.github/workflows/e2e.yaml` — line 67 `assembleGithubDebug`; line 72
    (upload, a file) `app/build/outputs/apk/github/debug/app-github-debug.apk`;
    line 111 (download, a directory) `app/build/outputs/apk/github/debug`.
*   `e2e/device.sh` — line 31 default APK path →
    `app/build/outputs/apk/github/debug/app-github-debug.apk`; line 51
    message → `gradle assembleGithubDebug`.
*   `scripts/emulator-vnc.sh` — line 106 `installDebug` →
    `installGithubDebug` (there is no `installDebug` once flavors exist).
*   `scripts/build-apk.sh` — comment and output: also drops
    `navgesture-play.aab`.
*   `docs/development.md` — the two flavors and their outputs, the signing
    key's two names, the `-g` flag, the `disclosure` suite, and the suite
    count in the Testing section ("all six" becomes "all seven").
*   `README.md` — the two builds and which one updates itself.

### Play Console material

*   `docs/play-store/listing.md`, `data-safety.md`,
    `accessibility-declaration.md` (including the video script with the
    repeat-disclosure step), `release-checklist.md`, `disclosure.md`, and
    `docs/privacy-policy.md` — written as part of this design.
    `listing.md` carries the C4 gesture-navigation note.
    `release-checklist.md` adds the certificate check before the PEPK upload,
    an offline keystore backup, and two weeks of daily vitals and review
    reading after launch.
*   `docs/play-store/graphics/` — `icon-512.png` (512×512) and
    `feature-graphic.png` (1024×500), made from `docs/branding/navgesture-logo.png`;
    and four 1080×2400 PNG phone screenshots of the `play` release on the
    API 36 emulator: `screenshot-home.png` (home, gestures on),
    `screenshot-disclosure.png` (the disclosure), `screenshot-app-row.png`
    (the app row open over an app), `screenshot-gesture-settings.png`
    (Gesture settings). Play accepts PNG or JPEG, 320 to 3840 px per side,
    9:16 or 16:9, two to eight phone screenshots.

### Order of work

Each phase leaves the tree building and its gate passing.

1.  **Build plumbing and moves** — flavors, manifests, the `update/` move,
    `Store`, Sonar/Jacoco, CI and script task names. Gate:
    `gradle test lint assembleGithubDebug assemblePlayDebug`.
2.  **Consent** — the flag and its test, the disclosure screen, the gate, the
    tile rules and their tests. Gate: `gradle test lint`.
3.  **Any Android 11+ phone** — C1, C3, the `-g` flag, the `disclosure`
    suite, the `app-switch.sh` update. Gate: `e2e/run.sh` (all seven suites).
4.  **Release tooling** — `docker/apk.Dockerfile`, `release.yaml`,
    `scripts/build-apk.sh`. Gate: `scripts/build-apk.sh` produces
    `navgesture.apk` and `navgesture-play.aab`.
5.  **Play material and checks** — `docs/play-store/`, the privacy policy,
    graphics, and the Verification list below.

### Verification

Commands run in the dev image as `docs/development.md` describes (the
`h3nc4/hypergesture-dev` container with the repo mounted at
`/workspaces/navgesture`). Throughout,
`play_apk=app/build/outputs/apk/play/release/app-play-release.apk` and
`github_apk=app/build/outputs/apk/github/release/app-github-release.apk`.

1.  `gradle test lint assembleGithubRelease assemblePlayRelease bundlePlayRelease`
    passes.
2.  `$ANDROID_HOME/build-tools/37.0.0/aapt2 dump permissions "$play_apk"`
    lists no `INTERNET`, `REQUEST_INSTALL_PACKAGES` or
    `RECEIVE_BOOT_COMPLETED`; the same on `"$github_apk"` lists all three.
3.  `! grep -q 'com.vijayyaso.navgesture.update' app/build/outputs/mapping/playRelease/mapping.txt`
    succeeds. Because the `update` sources are not in the `play` source
    set, this proves absence, not just renaming.
4.  On the API 36 AVD (`hypergesture`, from the dev image; locally any API 36
    AVD via `-a`):
    `e2e/run.sh -p app/build/outputs/apk/play/release/app-play-release.apk`
    and the same with `app/build/outputs/apk/github/release/app-github-release.apk`;
    all seven suites pass. Then
    `e2e/run.sh -g -p app/build/outputs/apk/play/release/app-play-release.apk back-revert`
    is recorded, not gated (C5).
5.  Android 11: the dev image only ships the API 36 image, so first
    `sdkmanager --install "system-images;android-30;google_apis;x86_64"`
    (needs network), then create the AVD once with
    `avdmanager create avd -n hypergesture30 -k "system-images;android-30;google_apis;x86_64" -d pixel_6`,
    then
    `e2e/run.sh -a hypergesture30 -p app/build/outputs/apk/play/release/app-play-release.apk navigation back-revert`;
    both suites pass. This AVD is local only; CI stays on API 36.
6.  Install `"$play_apk"` on the POCO F5 and confirm Back, Home, Recents, the
    app row, the tile and the disclosure flow (video script steps 2-9) behave
    as CUJs 1-5 describe. Then clear app data, revoke
    `WRITE_SECURE_SETTINGS`, and check Home, Setup, Troubleshooting and
    Gesture settings: none shows `status_restricted_hint`, the restricted
    settings step, or the `GRANT_COMMAND` text. Then
    `adb shell pm grant com.vijayyaso.navgesture android.permission.WRITE_SECURE_SETTINGS`:
    Gesture settings now shows the Gesture bar section (C1's other branch).
7.  `grep -E 'dataExtractionRules|fullBackupContent' app/build/intermediates/merged_manifests/playRelease/processPlayReleaseManifest/AndroidManifest.xml`
    finds both attributes.
8.  `! aapt2 dump strings "$play_apk" | grep -q 'contacts GitHub'` succeeds.
9.  `shellcheck -o all -x e2e/*.sh scripts/*.sh scripts/hooks/*` passes (the
    `development.yaml` lint gate).
10. `docs/play-store/graphics/` holds six files: the icon, the feature
    graphic and the four screenshots.
