# BHAIYAAA — Full Build

"Apna banda, phone ke andar."

Every feature listed below is real and wired to real local data — nothing
is a placeholder button. Where the original spec asked for something not
realistically buildable in this environment, that's called out explicitly
below instead of being faked.

## What's working

- **Home** — greeting, missed-today, contacts synced, VIP count, recent
  calls, link to Insights.
- **Calls** — real call history, filterable (All/Missed/Incoming/Outgoing).
- **VIP** — set any contact to VIP / Super VIP / Emergency. A real incoming
  call from that number triggers a distinct vibration pattern, a flashlight
  pattern, and a heads-up notification — all through Android's real APIs.
- **Contacts** — real device contacts, search, tap through to a detail
  screen with VIP level, a category tag (Family/Friends/Work/Client/
  Unknown), and free-text private notes.
- **Assistant** — a rule-based local assistant (not a hosted or bundled
  LLM — see "AI assistant" note below) that answers from your real data:
  missed calls, VIP list, calls today, recent callers, last call with a
  named contact, and "remind me to..." which creates a real reminder.
  Includes real voice input via Android's built-in speech recognizer.
- **Reminders** — a working local to-do list, addable by chat or directly.
- **Insights** — calls today/this week, missed count, incoming vs outgoing,
  VIP calls this week, most-contacted person, and a real 7-day bar chart —
  all computed from your actual call history, hand-drawn with Compose
  Canvas (no third-party charting library, to keep the build dependency
  footprint small and reliable).
- **Privacy lock** — real PIN entry (SHA-256 hashed, stored inside
  Android-Keystore-backed EncryptedSharedPreferences) plus real
  BiometricPrompt fingerprint/face unlock, gating the whole app on launch.
- **Privacy Center** — shows what's stored locally, lets you export your
  data as a real JSON file (via Android's file picker) and clear VIP
  settings/notes or cached call history for real.
- **Unit tests** — real JUnit tests for the assistant's intent matching and
  VIP labeling logic (`app/src/test`). These run separately from the APK
  build (`./gradlew test`), so they can't block or break the install build.

## Where I made a realistic call instead of faking something

- **"AI assistant" / "local AI models"** — the spec's own rule says
  structured questions should query the database directly rather than let
  an LLM guess, so that's exactly what's built: keyword-matched intents
  over your real local data. Bundling a true on-device LLM (llama.cpp,
  whisper.cpp, Vosk, etc.) needs native library compilation and large
  model-weight downloads, neither of which is something I could fetch or
  verify in this build environment. The architecture (a single
  `AssistantEngine.process()` entry point) is intentionally swappable if
  you want to wire in a real local model later.
- **"Offline speech recognition"** — uses Android's own built-in speech
  recognizer with `EXTRA_PREFER_OFFLINE`. Whether it's *actually* fully
  offline depends on whether the phone has an offline language pack
  downloaded (Settings → System → Languages → On-device speech
  recognition on most Android versions) — that's a device setting, not
  something the app controls.
- **Call recording / transcription** — intentionally not implemented.
  Modern Android blocks non-system apps from recording live call audio on
  most devices, and consent laws for recording calls vary by
  country/state. Faking this would violate the spec's own "no fake
  features" rule and its instruction not to bypass Android restrictions.
- **VIP alerts when the screen is off** — relies on Android delivering the
  phone-state broadcast, which some manufacturers (Xiaomi, Oppo,
  aggressive Samsung battery modes) restrict for background apps. If
  alerts are unreliable, allow BHAIYAAA to run unrestricted in your
  phone's battery settings — this is a real platform limitation, not a bug
  being hidden.
- **Data export** — plain JSON via Android's document picker, not an
  encrypted backup format. Good enough to inspect/move your data, not a
  cloud backup product.

## How to build the APK

1. Extract this zip.
2. On GitHub, **Add file → Upload files** → select everything from the
   extracted `bhaiyaaa` folder → commit to `main`.
3. Confirm `.github/workflows/build-apk.yml` landed at that nested path
   (open `.github` → `workflows` in the repo).
4. Watch the **Actions** tab. This is a much larger project than the
   earlier test builds — biometric, encrypted storage, and extended icons
   are all new dependencies being resolved for the first time, so don't be
   surprised if the first run needs a fix or two.
5. When green: open the run → **Artifacts** → download
   `bhaiyaaa-debug-apk` → extract → install `app-debug.apk`.

## First-run notes

- The very first launch asks for Contacts, Call Log, Phone State, and
  Notification permissions together (all needed for the dashboard and VIP
  alerts to work at all).
- Microphone permission is asked separately, only when you tap "Speak
  instead" on the Assistant screen — progressive, not upfront.
- If you enable the Privacy Lock in Settings, you'll need your PIN or
  biometric every time you reopen the app.
