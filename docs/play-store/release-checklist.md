# Play release checklist

Do the steps in this order. Each one says who does it.

## 1. Create the developer account (owner)

1.  Go to <https://play.google.com/console/signup>.
2.  Choose **Personal** (for yourself) or **Organization** (for a company; needs
    a D-U-N-S number).
3.  Pay the one-time registration fee: US$25, by credit or debit card
    (prepaid cards are not accepted). Checked 2026-11-01 on
    <https://support.google.com/googleplay/android-developer/answer/6112435>.
4.  Finish identity verification and the Android device verification that
    Play asks for. This can take a few days.

> A **personal** account created after 13 November 2023 must run a closed test
> with **at least 12 testers, opted in for 14 days in a row**, before it can
> publish to everyone. Start finding testers now (friends, family, colleagues
> with an Android phone and a Google account).

## 2. Create the app (owner)

1.  Play Console › **Create app**. Name `NavGesture: Edge Gestures`, default
    language English, **App**, **Free**.
2.  Accept the declarations.

## 3. App signing with the existing key (owner)

The play build must carry the same signature as the GitHub build, so phones can
move between them. So the existing release key becomes Play's **app signing
key**. Google holds it from then on; this cannot be undone, and Google cannot
reset it if you lose your own copy.

**First, check the key and back it up.**

```sh
keytool -list -v -keystore $HOME/.config/navgesture/navgesture-release.jks \
  -alias navgesture | grep SHA256
```

It must print `85:27:F9:BA:...:5E:43:11`, the certificate of every GitHub
release (checked 2026-10-31 against v1.1.0). If it differs, stop. Then copy
`~/.config/navgesture/` to an offline place (USB drive) before uploading. The
key is RSA 4096, which meets Play's "RSA 2048-bit or higher" rule.

1.  Play Console › your app › **Protected with Play › Play Store protection ›
    Manage Play app signing**. Accept the terms.
2.  Choose to upload your existing key from a Java keystore. Play shows a
    download link for the **PEPK** tool and the **exact command** to run.
    Copy that command as Play shows it, and point `--keystore` at
    `~/.config/navgesture/navgesture-release.jks` with alias `navgesture`.
3.  Upload the file the command writes. Delete that file afterwards.
4.  **Upload key:** Google recommends a separate key for uploads. Create one
    (`keytool -genkeypair -keystore ~/.config/navgesture/upload.jks -alias upload
    -keyalg RSA -keysize 4096 -validity 10000`) and register its certificate on
    the same page, then sign the `.aab` with it. If you skip this, Play also
    accepts the app signing key as the upload key.

Source: <https://support.google.com/googleplay/android-developer/answer/9842756>
(checked 2026-11-01).

## 4. Fill in App content (owner, answers in this folder)

| Section | Where the answers are |
| :------ | :-------------------- |
| Privacy policy | `https://github.com/vijayyaso/NavGesture/blob/main/docs/privacy-policy.md` |
| App access | All functions available without login |
| Ads | No ads |
| Content rating | Questionnaire: category **Utility, Productivity, Communication or Other**; answer **No** to every content question |
| Target audience | 18 and over (avoids the extra rules for children) |
| Data safety | `data-safety.md` |
| Government apps | No |
| Financial features | None |
| Health | None |
| Sensitive permissions › Accessibility API | `accessibility-declaration.md` (needs the video) |

## 5. Store listing (owner, text ready)

Copy from `listing.md`. Upload the files in `graphics/`.

## 6. Build the bundle (developer)

```sh
gradle bundlePlayRelease
```

The file is `app/build/outputs/bundle/playRelease/app-play-release.aab`.
CI also attaches it to each tagged release run as a build artifact.

## 7. Closed test (owner, personal accounts only)

1.  Test and release › Testing › **Closed testing** › Create track.
2.  Upload the `.aab`. Add testers by email list or Google Group.
3.  Send testers the opt-in link. They must **install and keep the app** and
    stay opted in for **14 days**.
4.  After 14 days with 12 or more testers, Dashboard › **Apply for
    production**. Play asks about the test; answer honestly.

## 8. Production (owner)

1.  Test and release › Production › Create release › upload the same or a
    newer `.aab`.
2.  Choose countries (all, or start with India).
3.  Send for review. First reviews often take several days; Accessibility apps
    can take longer.

## After launch

*   For the first two weeks, open Play Console › **Quality › Android vitals**
    (crashes and ANRs) and **Ratings and reviews** once a day. The Play build
    has no crash reporting of its own, so this is the only signal from phones
    we never tested.

*   Every new version: bump happens in CI; upload the new `.aab` to Play.
*   If the app's use of Accessibility changes, submit the Accessibility
    declaration again (Play requires it).
*   The GitHub build keeps its own updater; Play users update through Play.
