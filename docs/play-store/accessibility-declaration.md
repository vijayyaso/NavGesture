# Accessibility API declaration

Play Console › Policy and programs › App content › Sensitive app permissions ›
Accessibility API. NavGesture is **not** an accessibility tool, so it takes the
non-tool path and relies on the in-app prominent disclosure.

## Form answers

| Question | Answer |
| :------- | :----- |
| Is your app an accessibility tool (`isAccessibilityTool`)? | **No** |
| Why does your app use the AccessibilityService API? | **App functionality** |
| Describe the functionality | NavGesture replaces navigation buttons with edge gestures. The service draws thin touch strips along the screen edges, reads the position and timing of touches inside them, and performs Back, Home and Recents with `performGlobalAction`. A touch that is not a gesture is replayed to the app underneath with `dispatchGesture`. The service sets `canRetrieveWindowContent="false"` and requests no accessibility events, so it cannot read screen content or typed text. |
| Does your app collect or share personal or sensitive data through the API? | **No** |
| Link to the video | Your unlisted YouTube link (see the script below) |

## Video script (about 60 seconds, record on a phone with the play build)

Record with Android's screen recorder. Upload to YouTube as **Unlisted**.
Add captions or speak the words in brackets.

1.  Open NavGesture from the app drawer. [Opening NavGesture.]
2.  Home shows "Gestures are off". Tap **Turn on**.
3.  The disclosure screen appears. Scroll slowly so every line is readable.
    [This screen explains what the Accessibility service reads.]
4.  Tap **Not now**. Home shows gestures still off. [Declining keeps the
    service off.]
5.  Tap **Turn on** again. The same disclosure appears again. [The
    disclosure shows every time until the user agrees.]
6.  Tap **Agree**. Android's Accessibility settings open on NavGesture.
7.  Switch NavGesture on and accept Android's own dialog.
    [Granting the Accessibility permission.]
8.  Go back to the app. Home shows "Gestures are on".
9.  Open any app. Swipe in from the left edge: Back. Swipe up from the bottom:
    Home. [The core feature: edge gestures for Back and Home.]

## Prominent disclosure checklist (from Play's policy)

| Policy point | How NavGesture meets it |
| :----------- | :---------------------- |
| Inside the app, not only in the store text | `ui/DisclosureScreen.kt` |
| In normal use, not behind menus | Shown when the user taps Turn on, Fix, or the tile |
| Describes the data accessed | Touch position and timing inside the edge strips |
| Explains use and sharing | Used to decide gestures; never shared; no internet |
| Needs an affirmative action | **Agree** button; **Not now** records nothing |
| Not only in the privacy policy | Separate in-app screen |
| Not bundled with other data disclosures | Usage access is explained on its own screen |
