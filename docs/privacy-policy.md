# NavGesture privacy policy

*Last updated: 2026-11-01*

NavGesture is a free, open-source gesture app by Vijayyaso. This policy covers
both builds: the one on Google Play and the one on GitHub Releases.

## Summary

NavGesture collects no personal data. It has no account, no ads, no analytics
and no crash reporting. Nothing it reads leaves your phone.

## What the app reads, and why

### Accessibility service

NavGesture places thin, invisible strips along the edges of your screen. The
accessibility service reads only the **position and timing of touches inside
those strips**. It uses them to decide whether a touch is a gesture:

- a swipe in from the left or right edge means **Back**,
- a swipe up from the bottom means **Home**, or **Recent apps** after a pause,
- a swipe up and sideways opens the **app switcher**,
- a hold on the bottom edge starts **Circle to Search**, if you turn it on.

A touch that is not a gesture is passed on to the app underneath, exactly
where you touched.

The service is configured so that it **cannot read window content**, and by
default it receives **no accessibility events**. It never sees what is on your
screen, what you type, or your passwords.

### Bank pause (GitHub build only, optional)

Many bank apps refuse to open while an accessibility app is on. If you pick
bank apps on the **Bank apps** screen, the service is told **only when one of
those apps opens a window** (the app's name, nothing on its screen). It then
switches itself off, and while that pause lasts the app reads **which app is
in front** (app names only, through Usage access) to know when you have left,
then switches itself back on. None of this is stored beyond the pause or
leaves your phone. The Google Play build has no bank pause.

### Usage access (optional)

If you grant Usage access, NavGesture reads **which apps you opened
recently** so the app switcher can list them. This list stays on your phone
and is not stored after it is shown.

### Settings you choose

Your gesture settings and favourite apps are saved in the app's private
storage on your phone. They are not included in cloud backups.

## Network use

- **Google Play build:** the app has no internet permission. It cannot send or
  receive anything.
- **GitHub build:** the app goes online only for an update check that is
  **off** until you turn it on. When on, it asks GitHub's public API for the
  latest NavGesture release and, if you agree, downloads it from GitHub. No
  information about you or your phone is sent beyond what any web request
  carries (your IP address, seen by GitHub).

## Sharing

NavGesture shares no data with anyone, because it collects none.

## Children

NavGesture is not directed at children and collects no data from anyone.

## Changes

Changes to this policy are published in this file, and its history is visible
in the project's git history.

## Contact

Vijayyaso — vijayyaso7@gmail.com — https://github.com/vijayyaso/NavGesture
