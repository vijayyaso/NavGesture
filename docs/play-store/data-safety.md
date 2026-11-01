# Data safety form answers

Play Console › Policy and programs › App content › Data safety.
These answers describe the **play** build (no `INTERNET` permission, backup off).

## Data collection and security

| Question | Answer | Why |
| :------- | :----- | :-- |
| Does your app collect or share any of the required user data types? | **No** | The play build has no internet permission, so no data can leave the phone. Play: data "only processed locally on the user's device and not sent off device does not need to be disclosed" ([answer/10787469](https://support.google.com/googleplay/android-developer/answer/10787469), checked 2026-11-01). |
| Is all of the user data collected by your app encrypted in transit? | Not shown (answered No above) | — |
| Do you provide a way for users to request that their data is deleted? | Not shown (answered No above) | — |

## What is processed on the phone only (for your own reference)

| Data | Where it comes from | Used for | Leaves the phone? |
| :--- | :------------------ | :------- | :---------------- |
| Touch position and timing inside the edge strips | Accessibility service | Deciding Back / Home / Recents / app switcher | No |
| Recently opened apps | Usage access (optional) | Listing them in the app switcher | No |
| Gesture settings, favourite apps | User input | Remembering choices | No (cloud backup turned off) |

## If the play build ever gains a network feature

Then this form must be filled in again before that release. Play requires the
answers to match the shipped app.
