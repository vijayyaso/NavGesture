# HyperOS navigation

The gesture service does not depend on any of this. Everything here concerns the optional second capability: getting HyperOS to hide its three-button navigation bar.

## Current behaviour

`navigation/HyperOSNavigationIntegration.kt` fires the vendor settings intent:

```kotlin
startActivity(
    Intent("com.android.settings.NAVIGATION_MODE_SETTINGS")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
)
```

Confirmed working on a Redmi 14 running Android 16 and HyperOS OS3.0. It opens the navigation-mode screen and the user taps the option. `adb shell am start` reaches that screen by the same `ActivityManager.startActivity` path, so no privileged permission is involved.

Choosing gesture navigation there hides the bar and gains screen space. It does not give a third-party launcher working native gestures, so NavGesture's own recognizer remains the thing that performs navigation.

## TODO: apply the overlay directly

Opening a settings window is a poor substitute for doing the thing. The state to reach is this one:

```bash
cmd overlay enable com.android.internal.systemui.navbar.gestural
```

`cmd overlay list` shows the current state.

This is blocked because the call needs `android.permission.CHANGE_OVERLAY_PACKAGES`, which is signature-level, so only the platform signer can hold it. If a route ever appears that does not require a signature permission, replacing the settings intent with a direct overlay call is the change to make.

## Routes already closed

| Route | Why it fails |
| --- | --- |
| `cmd overlay enable ...` from the app | `CHANGE_OVERLAY_PACKAGES` is signature-level |
| Writing `Settings.Secure` `navigation_mode` | `WRITE_SECURE_SETTINGS` is signature or privileged. Reading it is unrestricted. Without the adb grant the app can only report the mode; with it, "Hide the gesture bar" keeps the mode gestural |
| Shizuku | Apache-2.0 and legally forkable, yet its privileged server still has to be started by ADB or root |
| Granting the permission over adb | On the target device `adb shell` holds neither `WRITE_SECURE_SETTINGS` nor `GRANT_RUNTIME_PERMISSIONS`. Xiaomi keeps both behind the Mi-account "USB debugging (Security settings)" toggle |

Community consensus through 2025 and 2026 is that no ADB-free way exists to force native gestures for a third-party launcher. Lawnchair closed its own issue on this as not planned.

## Also unfinished

Auto-clicking the toggle once the settings screen opens, using the app's own accessibility service (`findAccessibilityNodeInfosByText` with `ACTION_CLICK`). That needs only public APIs and no extra permission, but it is fragile:

- labels are localised
- view ids change between builds
- HyperOS versions drift

Worth doing only as best-effort, with a manual fallback.

HyperOS also resets the navigation mode across a reboot. `boot/BootCompletedReceiver.kt` observes that and reports it. A normal APK cannot restore it. With the adb grant and "Hide the gesture bar" on, `GestureBarHider` writes `navigation_mode` back to gestural when the service connects and whenever the setting changes (ported from upstream #127). It saves the mode from before the first hide and puts it back when the bar is shown (only while `force_fsg_nav_bar` is still 1). After five re-hides inside ten seconds it stops for that window, so it cannot fight a system that keeps writing its own value. This has not been tried on a phone.
