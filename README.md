# NavGesture

<img src="docs/branding/navgesture-logo.png" alt="NavGesture fluid n logo" width="160" height="160">

> Edge gestures for Xiaomi HyperOS, so you can keep the launcher you actually like.

NavGesture is an edge-gesture app. It does three things:

- **Recents waits for a real pause.** Modelled on the stock POCO launcher (6.01.05), a pause only counts past POCO's own line (about a tenth of the screen up), and it is detected the way POCO detects it: a smoothed follow point catching up with the finger, not a timer. The pause only *arms* Recents, with a tick. Letting go decides, and a quick flick up is still Home.
- **A black drop rises from the edge** under your finger: a round head joined to the edge by a gooey metaball neck (adapted from Ramotion's MIT-licensed fluid slider), moved by Fluid Navigation Gestures' springs. The neck thins as the head pulls away, leans as you slide, and the drop wobbles once as it forms. From the bottom it carries a Home icon; from the sides it carries the Back arrow. The action fires as you let go. It never touches the app underneath.
- **Swipe up from the bottom and drag sideways** and a macOS-style row of app icons appears just above the edge: recent apps when you drag right, your favourites when you drag left. Home screens never appear. Each short drag moves one icon and the row loops at both ends, so the first app is always a short drag away. Lift to open, or swipe down to close. A plain upward swipe with no sideways drag is still Home, and a still pause is still Recents.

It installs as its own app (`com.vijayyaso.navgesture`).

HyperOS ties its gesture navigation to the stock launcher. Switch to Nova, Niagara or Lawnchair and the phone drops you back to three buttons. NavGesture brings the gestures back with one app, without root, ADB, Shizuku or a computer. It works on any phone with Android 11 or newer.

**[Download the latest release](https://github.com/vijayyaso/NavGesture/releases/latest)** · no root, no ADB, no computer needed

## Gestures

| Gesture | Does |
| --- | --- |
| Swipe in from the left or right edge | Back |
| Swipe up from the bottom edge | Home |
| Swipe up from the bottom, pause, then let go | Recent apps |
| Swipe up from the bottom, then drag right | App list: recent apps, one per short drag, loops at both ends |
| Swipe up from the bottom, then drag left | App list: your favourites, one per short drag, loops at both ends |
| Swipe down while the app list is open | Closes the list; nothing opens |
| Touch and hold the middle of the bottom edge | Circle to Search, once it is on |

In landscape the first bottom swipe only shows a pill, and a second one within 2 seconds goes Home, as HyperOS does in games. Every distance and delay is adjustable in the app's Gesture settings.

## Install

1. Install `navgesture.apk` from [the latest release](https://github.com/vijayyaso/NavGesture/releases/latest).
2. Allow restricted settings under Settings › Apps › NavGesture › ⋮ › **Allow restricted settings**.
3. Turn it on under Settings › Accessibility › **NavGesture**, then open the app. Its setup checklist covers the rest.

If Play Protect blocks the install, allow restricted settings as in step 2. If the gestures stop, the app's Troubleshooting screen has the fixes.

## Privacy

Gestures need an accessibility service. This one watches thin strips along the screen edges and turns a swipe into Back, Home, Recents or an app list. Its configuration withholds window content and accessibility events, so it never reads the screen or what you type. The app goes online only for an update check that stays off until you turn it on, and then asks GitHub for the latest release. It has no ads, analytics or account.

## What it can't do

It can't switch on HyperOS's own gesture navigation, since only the system is allowed to do that. It can open the navigation settings so you can hide the three-button bar. [docs/hyperos-navigation.md](docs/hyperos-navigation.md) has the details, and [docs/development.md](docs/development.md) covers working on the code.

## Contributors

- Vijayyaso, NavGesture: Recents, the drop, the edge app list and the packaging
- Henrique Almeida ([@h3nc4](https://github.com/h3nc4)), the base of the gesture service
- Victor Eduardo ([@victoresidoro](https://github.com/victoresidoro)), Circle to Search, the Control Center tile, edge to edge and wrapping labels

Circle to Search starts the way [MiCTS](https://github.com/parallelcc/MiCTS) and [CTSLauncher](https://github.com/chenxiaolong/CTSLauncher) do.

## License

GPL-3.0-or-later, see [LICENSE](LICENSE). Copyright (C) 2026 Henrique Almeida and the contributors above. NavGesture starts from [HyperGesture](https://github.com/h3nc4/HyperGesture) by Henrique Almeida; the changes are in the git history.
