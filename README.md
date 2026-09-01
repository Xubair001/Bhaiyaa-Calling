# Sukoon

> *Your people. Your quiet.*

A privacy-first, offline-first personal call companion for Android. Native
Kotlin, Jetpack Compose, Material 3. No account, no cloud, no API key, no paid
service — everything runs on the phone and stays there.

---

## Build

```bash
./gradlew assembleDebug     # debug APKs
./gradlew test              # unit tests (no device needed)
./gradlew lintDebug         # Android Lint
```

The Gradle wrapper is committed, so the build uses a pinned Gradle 8.9 rather
than whatever is installed. Requirements: JDK 17, Android SDK with platform 35.

CI runs on every push to `main` ([build-apk.yml](.github/workflows/build-apk.yml)):
unit tests → compile instrumented tests → build APKs → upload artifacts.

Two artifacts are produced. Unless you have a reason to care about download
size, take the first one:

| Artifact | Contains | Use it when |
|---|---|---|
| **`bhaiyaaa-apk`** | `Sukoon-install-this.apk` (~56 MB) | **Almost always.** One file, installs on any phone |
| `bhaiyaaa-apk-per-architecture` | three ~29 MB APKs | You want a smaller download and know your CPU |

The split exists because Vosk ships a native library per CPU architecture.
If you take the per-architecture download: `arm64-v8a` for any modern phone,
`armeabi-v7a` for older 32-bit ARM, `x86_64` for emulators. Picking the wrong
one fails with a bare "app not installed", which is why it isn't the default.

---

## Architecture

```
com.codeaza.bhaiyaaa
├── ai/                 Assistant engine, intent parsing, personality, models, speech
│   ├── model/          Model catalogue, storage, download worker
│   └── speech/         Vosk (offline) and platform recognizers behind one interface
├── data/
│   ├── content/        Hadith content layer (assets JSON, rotation)
│   ├── db/             Room entities, DAOs, migrations, FTS
│   ├── prefs/          DataStore settings
│   ├── export/         JSON export / import
│   └── repository/     Device contacts, call log, recordings, aggregate repository
├── domain/             Models and use cases (insights, search)
├── notifications/      Channels and notification posting
├── prayer/             Prayer times, periods, silence windows, adhan, scheduling
├── service/            Call receiver, alert manager, alarms, WorkManager sync
├── ui/                 Compose screens, components, navigation, theme
└── util/               Phone numbers, permissions, secure prefs, biometrics
```

`AssistantEngine` and `SpeechRecognizerEngine` are interfaces, so a different
local model can be dropped in without touching the UI.

`PrayerTimeCalculator.timesForDay` is the single place a prayer becomes an
instant. Silencing, the adhan, the dashboard and the Hadith rotation all read
from it, so there is no second definition of "when is Asr" anywhere.

---

## What works

Every feature listed is wired to real local data. There are no placeholder
buttons and no seeded demo content anywhere — an empty database renders empty
states, not a plausible-looking chart.

**Dashboard** — greeting in your chosen tone, calls/missed today, VIP count,
recent calls, latest memory, pending reminders, link into Insights.

**Calls** — real call history with All / Missed / VIP / Important / Unknown
filters and search. Tap through for a detail screen where you can mark a call
important, attach a note, or promote it to a memory.

**VIP (3 tiers)** — VIP, Super VIP, Emergency. A real incoming call from a VIP
triggers a distinct vibration waveform, a configurable flashlight pattern, and a
heads-up notification on a per-tier channel. Every tier's pattern is editable
with a **Test alert** button that fires the real thing.

**Contacts CRM** — device contacts with category tags, relationship, importance,
private notes, per-contact alert mute, spam flag, and live call statistics
(total, missed, average length) computed from the call log.

**Memory** — notes you save, indexed with SQLite **FTS4** for fast search, with
provenance on each entry. Optional private memories hidden behind the lock.

**Assistant** — a local rule-based engine over your own data. It answers missed
calls, VIP lists, call counts, most-contacted, last call with a person, memory
recall, when a named prayer is, and creates real reminders from natural language
("remind me to call Ali tomorrow at 5pm"). It can silence the phone and switch
the adhan on or off. Answers carry their sources, a failed turn offers a retry,
and the thread survives the process being killed. When it can't parse a question
it says so rather than guessing.

**Qibla** — the great-circle bearing to the Kaaba from your saved coordinates,
corrected from magnetic to true north with `GeomagneticField` so it is right in
places where a raw compass is fifteen degrees out. Drawn with Compose Canvas, so
it adds nothing to the APK. Says so plainly when the phone has no compass, and
still gives the bearing as a number.

**Hijri date** — on the dashboard, from `java.time.chrono.HijrahDate`. No
library, no network, no data file.

**Ramadan** — during Ramadan only, a card counting down to suhoor or iftar.
Those are Fajr and Maghrib *as the app already computed them* — `RamadanTimes`
names two existing instants rather than calculating a third, so there is no
second source of truth to drift. Counts down on a one-minute tick, not a
one-second one.

**Home-screen widget** — the next quiet window, in plain `RemoteViews` rather
than Glance: four TextViews and a shape drawable, no new dependency, a few KB.
`updatePeriodMillis` is **0** — the platform's widget refresh has a half-hour
floor and wakes the device, which for something that changes five times a day is
almost all waste. It is redrawn from `PrayerScheduler.reschedule` instead, so it
is exact and costs no wake-ups of its own.

**Been a while** — the VIPs you have not spoken to, graded by tier (21 days for
Emergency, 30 for Super VIP, 60 for VIP) so the list stays short enough to read.
Built from state the dashboard already collects: no new query, no new index.

**Call notes** — after a call you answered from a VIP, one silent notification
offers to record or write something down while it is fresh, and taps through to
that call. Only for answered incoming calls: `PHONE_STATE` gives the number on
the ringing broadcast alone, so an outgoing call cannot be attributed to a person
without reading the call log, which lags. Switchable in Settings → Alerts.

**Prayer times** — calculated from coordinates or typed in, per prayer. Each
prayer can only hold a time in its own half of the clock: Fajr is AM, everything
after it is PM. That is enforced in the picker (which has no meridiem toggle to
get wrong), in the DAO (so an import or the assistant cannot write an invalid
one), and by a migration that corrects rows saved before the rule existed. A
time you enter always wins over the calculation, and changing your location
never overwrites one.

**Adhan** — optional, off until you turn it on. Armed from the same prayer times
as everything else, played by a short foreground service on the alarm stream so
it survives Sukoon's own Do Not Disturb window. It cannot play twice for one
prayer, will not play for a prayer you switched off, and stays silent if its
alarm arrives more than fifteen minutes late. **No recording ships in the APK** —
there is no licence to redistribute one. Choose a sound already on the phone, or
record your own.

**Recordings** — record or import a sound, use it as the adhan, or file it
against a specific call from that call's detail screen. Audio is copied into the
app's private storage, so it is not backed up, not readable by other apps, and
goes when the app does. **Not call recording** — see the limitations below for
why that is a platform gate rather than a choice.

**Hadith** — a narration for the current prayer period on the dashboard,
rotating every five minutes with no immediate repeats. Content lives in
`assets/hadith/hadith.json` so it can be reviewed and corrected without touching
code. Every entry names its collection **and hadith number** — one that could
not be confirmed against sunnah.com was dropped rather than cited vaguely — and
anything outside Bukhari and Muslim carries its grading (al-Albani for Abu
Dawud, Darussalam for Tirmidhi and an-Nasa'i). Where two collections are graded
differently the lower grade is recorded, and nothing graded weak is included:
`HadithGrade` has no value for it, so the content file cannot express one.

**Personality** — Professional / Friendly / Bhai Mode, applied through a
`Phrasebook` interface backed by `strings.xml`.

**Insights** — calls today/this week, missed, VIP calls, incoming vs outgoing,
most contacted, longest calls, busiest hours, and a 7-day bar chart drawn with
Compose Canvas (no charting dependency).

**Search** — one query across contacts, calls, memories and reminders, run
concurrently.

**Privacy lock** — PIN plus BiometricPrompt, re-locking when the app
backgrounds. The PIN is stretched with **PBKDF2-HMAC-SHA256 at 200,000 rounds**
and the result held in Keystore-backed `EncryptedSharedPreferences`. A salted
single-round hash is not enough here: a PIN is at most a hundred million
candidates, and a GPU does billions of SHA-256 a second, so anyone who ever got
the hash would recover a 4-digit PIN in under a second. Stretching makes each
guess cost a fifth of a second. Hashes written by earlier versions still open
the app and are re-stretched on the next successful unlock.

The attempt counter and lockout live in secure storage, not in the lock
screen — they used to be `remember`ed in the composable, so force-stopping the
app reset them and the lockout was decorative. Five wrong tries costs 30s,
doubling to a five-minute cap.

**Privacy Center** — what's stored, what's granted, network use, protection
status, and one-tap routes to export/import/delete.

**Data** — JSON export and import via the Storage Access Framework. Import is
additive and never destroys newer data. Call history is excluded by default.

**AI Models** — install optional Vosk speech models (Apache-2.0). Size and
licence shown before any download; Wi-Fi only; delete genuinely removes files.
Nothing downloads on its own and nothing ships in the APK.

**Onboarding, theming, accessibility** — five-page first run with per-permission
explanations; light/dark/system with Android 12+ dynamic colour; sp-based type
that scales with system font size; content descriptions on every non-decorative
icon; VIP tiers never signalled by colour alone.

---

## Deliberate limitations

These are stated in-app under **Settings → About**, not hidden.

- **No call recording or transcription.** `AudioSource.VOICE_CALL` requires
  `CAPTURE_AUDIO_OUTPUT`, which Android grants only to privileged, pre-installed
  apps; becoming the default dialer does not earn it, and Play policy has barred
  the accessibility-service workaround since May 2022. Tools that do manage it —
  BCR, for instance — require root or Magisk to install as a system app. Consent
  law also varies by jurisdiction. So Sukoon does the achievable half: a voice
  note recorded *after* a call, or a file the phone's own dialer produced and you
  imported, filed against that call. It never implies it heard anything.
- **Not a dialer or call screener.** It listens for the system `PHONE_STATE`
  broadcast — the mechanism caller-ID apps use — and takes no privileged role.
- **No call blocking.** "Spam" is a label for your own reference.
- **Alerts depend on the platform.** Some OEMs (Xiaomi, Oppo, aggressive Samsung
  battery modes) delay or drop background broadcasts. Unrestricted battery use
  helps; this cannot be worked around from inside an app.
- **Reminders are inexact.** They use `setAndAllowWhileIdle` rather than
  requesting `SCHEDULE_EXACT_ALARM`, so deep Doze can delay one by minutes.
- **Exports are plain JSON**, not encrypted backups.
- **No adhan recording is bundled.** Sukoon has no licence to redistribute one,
  and an unattributed recording in an app about prayer would be worse than none.
  The adhan plays a sound you chose or recorded, and defaults to the phone's own
  alarm tone.
- **Isha cannot be set past midnight.** Every prayer after Fajr is PM-only,
  which is right everywhere the app is usable and wrong at extreme latitudes.
- **Recordings are not call recordings.** The microphone opens only while you
  hold Record.
- **The assistant is not an LLM.** It's intent matching over direct database
  queries — which is what makes "never invents an answer" a property of the
  design rather than a hope. `AssistantEngine` is an interface, so a local model
  can be added later.

---

## Testing

```bash
./gradlew testDebugUnitTest         # 313 unit tests, JVM + Robolectric
./gradlew connectedDebugAndroidTest # instrumented UI tests (needs a device)
```

Unit tests cover phone-number reconciliation, time-expression parsing, intent
parsing, assistant behaviour (including that it *refuses* to invent answers or a
prayer time it does not have), the AM/PM rule at the domain, DAO and migration
layers, optimistic prayer-time edits and their rollback, the Quiet times section
ordering, prayer periods, when the adhan is allowed to sound, the Hadith content
file's integrity and its no-repeat rotation, Room DAOs, sync idempotency, FTS
index consistency, insight aggregation, and the v3→v4 and v7→v8 migrations
against real databases of those versions.

`./gradlew test` also runs the release variant, where `ReminderUiTest` fails:
Robolectric cannot resolve the launcher activity in the minified build. That is
a known limitation of running Compose UI tests against R8 output, not a product
defect — the same six tests pass in `testDebugUnitTest`.

Instrumented tests cover navigation across all destinations, settings, the
personality preview, and component rendering/accessibility.

---

## Permissions

Requested progressively, each with an in-context explanation, and all optional.

| Permission | Why | Without it |
|---|---|---|
| `READ_CONTACTS` | Names instead of numbers; tagging | Calls show raw numbers |
| `READ_CALL_LOG` | History, insights, assistant answers | Those screens stay empty |
| `READ_PHONE_STATE` | Detect a ringing VIP in time | No VIP alerts |
| `POST_NOTIFICATIONS` | Deliver alerts | Vibration/flash still fire |
| `RECORD_AUDIO` | Voice input, and recording an adhan | Type instead; import a file |
| `INTERNET` | Model downloads only | Everything else works |

Torch control uses `CameraManager.setTorchMode()`, which needs no `CAMERA`
permission — so it isn't requested. Cloud backup is disabled in the manifest.

---

## Stack

Kotlin 2.0.21 · AGP 8.7.3 · Gradle 8.9 · compileSdk 35 · minSdk 26 ·
Compose BOM 2024.10.01 · Room 2.6.1 (KSP, schema v8) · WorkManager · DataStore ·
Biometric · Security-Crypto · Vosk 0.3.47 (Apache-2.0)

All dependencies are free and open source.
