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
│   ├── db/             Room entities, DAOs, migrations, FTS
│   ├── prefs/          DataStore settings
│   ├── export/         JSON export / import
│   └── repository/     Device contacts, call log, aggregate repository
├── domain/             Models and use cases (insights, search)
├── notifications/      Channels and notification posting
├── service/            Call receiver, alert manager, alarms, WorkManager sync
├── ui/                 Compose screens, components, navigation, theme
└── util/               Phone numbers, permissions, secure prefs, biometrics
```

`AssistantEngine` and `SpeechRecognizerEngine` are interfaces, so a different
local model can be dropped in without touching the UI.

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
recall, and creates real reminders from natural language ("remind me to call Ali
tomorrow at 5pm"). Answers carry their sources. When it can't parse a question
it says so rather than guessing.

**Personality** — Professional / Friendly / Bhai Mode, applied through a
`Phrasebook` interface backed by `strings.xml`.

**Insights** — calls today/this week, missed, VIP calls, incoming vs outgoing,
most contacted, longest calls, busiest hours, and a 7-day bar chart drawn with
Compose Canvas (no charting dependency).

**Search** — one query across contacts, calls, memories and reminders, run
concurrently.

**Privacy lock** — PIN (salted SHA-256, hash held in Keystore-backed
`EncryptedSharedPreferences`) plus BiometricPrompt. Re-locks when the app
backgrounds. Rate-limited after repeated failures.

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

- **No call recording or transcription.** Modern Android blocks non-system apps
  from capturing call audio, and consent law varies by jurisdiction. Sukoon
  stores what *you* write down and never implies it heard a call.
- **Not a dialer or call screener.** It listens for the system `PHONE_STATE`
  broadcast — the mechanism caller-ID apps use — and takes no privileged role.
- **No call blocking.** "Spam" is a label for your own reference.
- **Alerts depend on the platform.** Some OEMs (Xiaomi, Oppo, aggressive Samsung
  battery modes) delay or drop background broadcasts. Unrestricted battery use
  helps; this cannot be worked around from inside an app.
- **Reminders are inexact.** They use `setAndAllowWhileIdle` rather than
  requesting `SCHEDULE_EXACT_ALARM`, so deep Doze can delay one by minutes.
- **Exports are plain JSON**, not encrypted backups.
- **The assistant is not an LLM.** It's intent matching over direct database
  queries — which is what makes "never invents an answer" a property of the
  design rather than a hope. `AssistantEngine` is an interface, so a local model
  can be added later.

---

## Testing

```bash
./gradlew test                      # 91 unit tests, JVM + Robolectric
./gradlew connectedDebugAndroidTest # instrumented UI tests (needs a device)
```

Unit tests cover phone-number reconciliation, time-expression parsing, intent
parsing, assistant behaviour (including that it *refuses* to invent answers),
Room DAOs, sync idempotency, FTS index consistency, insight aggregation, and the
v3→v4 migration against a real v3 database.

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
| `RECORD_AUDIO` | Voice input — asked only at the mic button | Type instead |
| `INTERNET` | Model downloads only | Everything else works |

Torch control uses `CameraManager.setTorchMode()`, which needs no `CAMERA`
permission — so it isn't requested. Cloud backup is disabled in the manifest.

---

## Stack

Kotlin 2.0.21 · AGP 8.7.3 · Gradle 8.9 · compileSdk 35 · minSdk 26 ·
Compose BOM 2024.10.01 · Room 2.6.1 (KSP) · WorkManager · DataStore ·
Biometric · Security-Crypto · Vosk 0.3.47 (Apache-2.0)

All dependencies are free and open source.
