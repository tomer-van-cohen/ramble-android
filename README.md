# Ramble for Android

## Download for Android

Download the signed APK from [GitHub Releases](https://github.com/tomer-van-cohen/ramble-android/releases). Open it on your Android phone and allow installation from your download source. Android 11 or newer is required.

This repository contains the app source. Your transcription API key stays on your device; you supply your own Groq or OpenAI key during setup. Google Drive is optional.


**0.3.5-alpha — review hardening:** folder setup always waits for Continue;
external recording shortcuts require explicit confirmation, while in-app and tile recording
use a private activity. Audio preparation checkpoints each five-minute window and resumes
without decoding the prefix again. Definite provider rejections, pre-upload network failures,
ambiguous uploads and low storage have separate recovery paths. Settings include original-audio
retention, cleanup and recording deletion; interrupted capture is visible for recovery.
UI text lives in Android resource catalogs and the main activity is separated from screen components.


**Earlier UI walkthrough fixes:** exact recording durations,
honest paused-transcription feedback, and system-bar contrast after theme changes.


**0.3.3-alpha — one app entry:** Ramble is the only app-drawer icon. Long-press it
for Record, add an optional recording shortcut, or use the Quick Settings tile.
Quick recording requires completed onboarding and a configured transcription key.
Samsung Side button / Pixel Quick Tap can open Ramble; tap the microphone to record.
Direct recording remains available through the Record shortcut and tile. Updating
keeps recordings and settings; a validated replacement key resumes recordings blocked
on that provider's credentials.


0.3.2-alpha adds system MediaStore audio-event wakeups with the existing periodic SAF scan as fallback. See [MEDIASTORE-WAKE.md](docs/MEDIASTORE-WAKE.md) for emulator integration and cold-process evidence and OEM limits.


Background follow-up update (0.3.1-alpha): finalized recordings now request bounded follow-up scans instead of waiting solely for another periodic scan. Refresh now is visible above the home list. This reduces an avoidable delay; OEM/Doze background scheduling is still best effort. See [BACKGROUND-DELIVERY.md](docs/BACKGROUND-DELIVERY.md).


A native, source-available Android app for turning new Samsung recordings into text using your own Groq or OpenAI key. Generated text is saved on your phone and optionally in your Google Drive for your existing AI assistants. No Ramble account/server or computer is needed at runtime. The hosted Ramble provider remains disabled **Coming soon**.

The implementation uses the supplied [Ramble prototype](design/prototype.html) and [design system](design/RAMBLE-FLOW-DESIGN.md). The prototype is a design reference; install the APK to use the native app.

## Current device-validation build

**0.3.0-alpha:** a real in-app first-recording trial (optional microphone permission), recorded audio → normal transcription/local/Drive pipeline → first transcript/list → optional visual quick-record guide. Guides distinguish Samsung Side button, Pixel Quick Tap and a Xiaomi/generic Home-screen shortcut. Existing encrypted keys, baselines, signing identity and folder IDs are retained. See [first-recording flow](docs/FIRST-RECORDING.md).


New finalized AAC/M4A recordings are queued **automatically**, without per-recording approval. The app verifies the container's completed duration/sample index, observes the same proof at least 60 seconds apart, and rechecks an immutable snapshot before sending. An open/paused/incomplete or unsupported container waits; stable size alone never triggers upload. Historical baseline recordings are excluded. Real Samsung pause/stop and battery behavior still need a device pilot. The app includes an optional, foreground-only 30-second onboarding recording with microphone consent; it does not record telephone calls itself.

## Build and install

Requirements: JDK 17, Android SDK platform 36 and build tools 35/36; SDK tools and a network connection for first Gradle dependency download. Configure `ANDROID_HOME` or an ignored `local.properties` with your SDK directory. The checked-in wrapper pins Gradle and its SHA-256. Dependency lockfiles and SHA-256 verification metadata are included.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
# Optional emulator/device tests:
./gradlew connectedDebugAndroidTest
# Install on an already-authorized development device:
scripts/install.sh
```

A debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. For a release variant run `./gradlew assembleRelease`; `scripts/sign-local-release.py` can sign the unsigned output using a persistent private local key outside the repository and emit a checksum. Keep that key safe for future upgrades. Debug and release signatures differ: don't switch them on an installed profile without understanding that Android may require uninstall/data removal.

Users can sideload a supplied signed APK by approving installation from its download source; developer mode/ADB is not required for ordinary use. Google Play release/approval and public repository publication are separate actions.

## First run

1. Choose Groq (default) or OpenAI and validate your API key. The app stores it with Android Keystore-backed encryption.
2. On Samsung/Xiaomi, approve the suggested recording folder in Android's picker, review the connected sources, then press Continue when ready. The page never advances automatically. Pixel shows instructions for explicitly sharing each recording instead. Custom folders can be managed in Settings.
3. Choose whether to connect Google Drive. Local Downloads output works without Google setup. The publisher/self-builder must complete [Google OAuth configuration](docs/GOOGLE-DRIVE-SETUP.md) for that installed package/signature before Drive consent can succeed.
4. Copy the optional Hebrew/English instructions into an AI assistant that already supports your Google Drive account, and follow the quick-record guide if useful.
5. Try the in-app first recording: tap Record, allow the microphone, speak and stop. Its actual transcript appears in the list. Then optionally configure quick recording with the visual guide. Record as usual. Background discovery is best effort around every 15 minutes; completed supported recordings enter the transcription queue automatically. One text file is created, with ordered chunks/checkpoints for long audio. An uncertain paid request requires approval before retry.

Transcription initially follows the interface language (Hebrew or English); an explicit Auto detect selection is preserved. OpenAI uses `gpt-transcribe` with `languages[]`, while Groq uses `whisper-large-v3` with `language`.

Default text destination: `Download/Ramble`. A custom local folder can be granted in settings. Drive receives only generated text. Neither source recordings nor existing external transcript files are deleted on disconnect/uninstall.

## Documentation

- [Actual build, test and visual results](docs/VALIDATION.md)
- [Implementation status and validation boundaries](docs/IMPLEMENTATION-STATUS.md)
- [Runtime architecture](docs/ARCHITECTURE.md)
- [Complete technical specification](docs/TECHNICAL-SPEC.md)
- [Product contract](docs/PRODUCT.md) and [UX handoff](docs/UX-HANDOFF.md)
- [Samsung device pilot](docs/DEVICE-PILOT.md)
- [Google Drive setup](docs/GOOGLE-DRIVE-SETUP.md)
- [Security/privacy](SECURITY.md), [contributing](CONTRIBUTING.md), [license](LICENSE), [third-party notices](THIRD_PARTY_NOTICES.md)

The commercial planning track is separate and is not part of this source distribution.

## Preparing a public source snapshot

Read [the source/security review](docs/OPEN-SOURCE-REVIEW.md) and [SECURITY.md](SECURITY.md). Run `python3 scripts/check-public-source.py`, `python3 scripts/test-source-boundary.py`, and an independent Gitleaks scan before publishing. `python3 scripts/package-source.py` writes an allowlisted source ZIP and checksum under ignored `artifacts/`; it excludes local SDKs, signing material, credentials, operational artifacts and the independently versioned website. The three small test-audio files are synthetic. Changed/new binary assets must be reviewed before updating `scripts/public-binary-assets.json`.

For an actual publication directory, run `python3 scripts/stage-public-source.py --output ../Ramble-Android-public`. The destination must be outside the development folder and must not exist yet. Only allowlisted files are copied and both sides are scanned; local archives, lab artifacts, SDK/build output and the publisher-site repository stay outside that copy.

Test totals are read from current Gradle/instrumentation reports rather than kept as a fixed README number.

There is no publication command in these checks. A working folder or historical ZIP is not a reviewed repository snapshot. When creating a Git repository, inspect the staged file list, run `python3 scripts/check-public-source.py --git-index`, and scan the complete history if importing any prior commits.

## License

Ramble application code is licensed under **PolyForm Noncommercial 1.0.0**. Noncommercial use, modification and redistribution are permitted under its terms. There is no general requirement to publish the source of your modifications. Commercial use is not granted by this license. See [LICENSE](LICENSE) and [NOTICE](NOTICE) for the complete terms and required attribution.

Because it restricts commercial use, this is a source-available project. Third-party dependencies retain their own licenses. The app includes the license text in its assets and Settings → License.
