# Kaavalu — ಕಾವಲು

An on-device guardian that recognises the behavioural signature of a "digital arrest" scam
while it is happening, interrupts the victim in their own language, and brings their family
in before the money leaves.

No call recording. No blocklists. Just what the phone already knows.

Team Dasen · App Lab, Kalpavikas 2.0, RV University.

## Build and run

Open the folder in Android Studio and press Run, or from a terminal:

```bash
./gradlew :app:assembleDebug        # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # 28 unit tests: engine, markers, translations, SMS
./gradlew installDebug              # to a connected phone
```

Toolchain this was built and verified against:

| Piece | Version | Note |
| --- | --- | --- |
| Gradle | 9.7.1 | via the wrapper in this repo |
| AGP | 9.4.1 | **Kotlin support is built in** — do not add `org.jetbrains.kotlin.android`, AGP 9 rejects it |
| compileSdk | 36 | API 37 has no stable platform yet, so the AndroidX versions in `gradle/libs.versions.toml` are pinned to the newest that compile against 36 |
| minSdk | 31 | `TelephonyCallback` and the cleaner call APIs. **Check every demo phone is Android 12 or newer.** |
| JDK | 25 | Temurin 25 and Android Studio's bundled JBR both work |

If Android Studio offers to upgrade AndroidX dependencies, say no unless you have also
installed an API 37 platform: newer versions of `core-ktx`, `compose` and `lifecycle`
refuse to build against compileSdk 36.

## The shape of the code

Every detector only emits a typed `Signal` onto one shared bus. The engine is the only
thing that scores. The responder is the only thing that acts. That single contract is what
lets four people build in parallel from hour one.

```
com/dasen/kaavalu/
├── KaavaluApp.kt            holds the single RiskEngine
├── Prefs.kt                 guardian number, name, language, trusted numbers
├── Copy.kt                  every user-facing sentence in kn / hi / en
├── Notifications.kt         channels, watch warning, interrupt fallback
├── core/
│   ├── Signal.kt            THE CONTRACT: Signal types + SignalBus + sensitive app list
│   └── RiskEngine.kt        weights, scoring, tiers, sessions
├── detect/
│   ├── ScamCallScreeningService.kt   unknown / unverified cellular caller
│   ├── CallStateWatcher.kt           answered, and for how long
│   ├── CallNotificationListener.kt   WhatsApp voice and video calls
│   ├── AppUsageWatcher.kt            UPI or screen-share app in the foreground
│   └── ContactsChecker.kt            local contact lookup + in-app call memory
├── respond/
│   ├── Responder.kt          tier -> action, the only place escalation is decided
│   ├── Speaker.kt            spoken warning
│   ├── Guardian.kt           SMS to the family
│   └── InterruptActivity.kt  the full-screen warning with its breakdown
├── scan/NoticeScanner.kt     ML Kit OCR + NoticeMarkers (pure, unit tested)
├── service/                  foreground service + boot receiver
└── ui/
    ├── MainActivity.kt       the five tabs and the bottom navigation bar
    ├── Theme.kt              colours and the one-step-larger type scale
    ├── Components.kt         Panel, ActionTile, BigAction, Meter, BreathingShield
    ├── Home.kt               shield status, live risk, the three rules
    ├── ScanScreen.kt         camera -> FileProvider -> OCR -> graded verdict
    ├── AskScreen.kt          speech, with a keyboard fallback that never fails
    ├── Onboarding.kt         first-run ladder, and the Setup tab checklist
    └── DemoConsole.kt        hand-injected signals on compressed time
```

## How the notice and speech scoring works

`NoticeMarkers` scores the *shape* of the scam, not its vocabulary. Each marker carries a
`Cue` — authority, threat, money, secrecy, urgency, isolation, identity, story — and the
score is the sum of the markers that hit **plus a bonus for how many distinct core cues are
present at once**. That is what separates a fake warrant that never prints the phrase
"digital arrest" from a courier slip that happens to say "police station".

| Cues present | Bonus |
| --- | --- |
| 0–1 | 0 |
| 2 | +12 |
| 3 | +24 |
| 4 or more | +34 |

The verdict is graded, never a boolean:

| | Notice | Spoken |
| --- | --- | --- |
| `SCAM` | 55+ | 45+ |
| `SUSPICIOUS` | 28+ | 22+ |
| `UNCLEAR` | below that | below that |
| `UNREADABLE` | fewer than 14 readable characters came back from OCR | — |

`UNREADABLE` matters more than it looks. A blurred photo used to score zero and be rendered
as an all-clear for a document the phone had never actually read. And `UNCLEAR` says, in
words, that it is not proof the notice is genuine.

Written and spoken markers share ids, so the same fact is never counted twice and the
stronger wording wins. `NoticeMarkersRegressionTest` pins down the documents and sentences
that the first marker list let through.

## Tiers

| Score | Tier | What happens |
| --- | --- | --- |
| 40+ | Watch | Silent notification |
| 65+ | Interrupt | Full-screen warning over the call, spoken aloud, call-family and 1930 buttons |
| 80+ | Guardian | SMS to the family guardian with the top reasons |

The score is capped at 100, each signal counts once per session, and the tier never drops
inside a session, so a scammer cannot talk the phone back down. It is deliberately **not**
presented as a probability: the v1 weights are hand-set from how the scam is documented to
work, and every point is traceable to the capability that produced it. Say that to the jury
rather than pretending there is a trained model.

## Languages

English is the default. The family picks the language during setup, and it can be changed
any time from **Warning language** on the home screen, which is also how you show the same
phone to an English, Hindi and Kannada audience in one sitting. Everything the user reads or
hears follows that choice — including the breakdown lines and the guardian SMS. `CopyTest`
fails the build if a signal is added without all three translations.

The wordmark reads **Kaavalu** with ಕಾವಲು underneath it. The Kannada name is the product's
name, not a string to translate — but it is not the first thing a family has to decode
before they will trust the app, so English leads and Kannada sits under it.

## Demo

Home screen → **Demo console**. Turn on **Demo time** (one real second counts as one
simulated minute) and inject signals by hand. The hero path:

1. Unknown WhatsApp video call → 40, Watch
2. wait ~21 seconds → 65, Interrupt (spoken warning, full-screen)
3. Open UPI app → 95, Guardian (SMS to the number saved at setup)

`RiskEngineTest.unknownVideoCallThenUpiAppReachesGuardian` locks that exact path down, so
you find out in CI rather than on stage.

Mirror the phone with `scrcpy` over USB so the jury sees the interrupt.

**Demo ordering matters.** A flagged notice adds +20 to the next unknown call for 48 hours,
so if D scans the fake warrant before the call demo, every number above shifts up by 20 and
the interrupt fires sooner. Either scan after the call demo, or rehearse with the higher
numbers and explain the link — it is a good thing to show, just not to be surprised by.

## Things that will bite you if they are changed back

1. **The camera must not use `TakePicturePreview()`.** It returns a thumbnail, and OCR
   cannot read body text off a thumbnail — the scan then reports "no scam markers found"
   for a notice it never read. `ScanScreen` writes a full-size JPEG through the
   `FileProvider` declared in the manifest and reads that back.
2. **`EXTRA_PREFER_OFFLINE` must not be forced on.** On a phone with no downloaded language
   model it returns nothing at all. `AskScreen` asks online first and retries offline only
   after a network error.
3. **Score every recognizer alternative, not just the first.** "under arrest" and "under a
   rest" are one sentence to a person and two different strings to a regex.
4. **Keep the keyboard fallback.** A demo that depends on a microphone in a loud hall is a
   demo that fails in front of the jury.

## Before the demo — things that still need a real phone

These are the parts that cannot be finished from a desk. None of them block the build.

1. **WhatsApp notification wording.** `CallNotificationListener.ONGOING_HINTS` matches the
   status line of the ongoing-call notification, and it differs by WhatsApp version and
   phone language. Place a call between two team phones, watch `adb logcat -s KaavaluNotif`
   for the line it logs, and adjust the list to what you actually see. Cellular detection
   does not depend on this.
2. **Kannada and Hindi strings.** Every sentence in `Copy.kt` needs a native speaker to read
   it aloud before the demo. A warning that sounds odd is a warning that gets ignored.
3. **Text-to-speech during a live call.** Check the warning is audible on the demo phone
   while a call is in progress. If it is not, the full-screen screen and the vibration
   pattern carry it alone.
4. **Android 14+ full-screen intents.** A non-calling app cannot reliably take over the
   screen. `Responder` raises `InterruptActivity` only when "Display over other apps" is
   granted, and always posts the heads-up notification as the real fallback. Grant the
   overlay permission on the demo phone and test it.
5. **OEM battery killers.** Xiaomi, Oppo, Vivo and Realme kill the foreground service
   regardless of the battery exemption. The onboarding ladder offers the autostart screen on
   those phones — grant it, then leave the app alone for an hour and confirm it is alive.
6. **Bank app package names.** `SensitiveApps` in `core/Signal.kt` lists the UPI and banking
   packages that count. Add whatever is actually installed on the demo phones.

## Privacy

No call audio, no message content and no contact list leaves the phone. A number is compared
against contacts locally. There is no server in v1, so there is nothing to breach. The only
outbound data is an SMS to a number the family chose, containing the reasons, not the
caller's details.
