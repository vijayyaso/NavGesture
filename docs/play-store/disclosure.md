# In-app disclosure text

This is the exact text of the disclosure screen (`ui/DisclosureScreen.kt`).
Every sentence is about the **Accessibility service** only, so it is true in
both builds. Usage access is explained separately on the App switching screen
(see the end of this file). The string resource name is in brackets.

## Disclosure screen

**Title** [`disclosure_title`]

> About the Accessibility service

**Body** [`disclosure_intro`]

> NavGesture needs Android's Accessibility service to turn edge swipes into
> Back, Home and Recents. Before you turn it on, here is exactly what it does.

**What it reads** [`disclosure_reads_title`, `disclosure_reads_body`]

> **What it reads**
> Only where and when you touch inside thin strips along the left, right and
> bottom edges of the screen.

**What it does with it** [`disclosure_does_title`, `disclosure_does_body`]

> **What it does with it**
> It decides whether the touch is a gesture, then performs Back, Home, Recents
> (Android's recent-apps screen), opens NavGesture's own app row, or starts
> Circle to Search. A touch that is not a gesture is passed on to the app
> underneath, at the same spot.

**What it never does** [`disclosure_never_title`, `disclosure_never_body`]

> **What it never does**
> It cannot see what is on your screen or what you type. It does not store
> your touches, and it does not send them anywhere.

**Buttons**

> [**Agree**] [`disclosure_agree`] — test tag `disclosure_agree`
>
> [**Not now**] [`disclosure_not_now`] — test tag `disclosure_not_now`

**Footnote** [`disclosure_footnote`]

> Agree opens Android's Accessibility settings, where you switch NavGesture on.
> You can switch it off there at any time.

## Usage access sentence (App switching screen)

A separate footnote row **below** the tagged `usage_access` status row, so the
row's text, which `e2e/status-refresh.sh` compares exactly, does not change.

[`usage_access_explainer`]

> With Usage access, NavGesture reads which apps you opened recently, only to
> list them in the app row. The list stays on this phone.
