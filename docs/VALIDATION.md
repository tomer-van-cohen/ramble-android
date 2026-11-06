# Validation boundaries

This alpha has been tested using synthetic fixtures, local Android emulators, selected real provider requests and Samsung remote-lab runs. Detailed operational evidence is private and excluded from public source. No user credentials, account inventories, transcripts or device identifiers are required to reproduce synthetic tests.

## Current review cases

The review regression suite now includes exported-entry spoofing, private recorder entry, HTTP rejection versus pre-upload network failure, incremental PCM/AAC decode resume, schema 1→3 migration, storage recovery, private-audio cleanup/deletion, interrupted-trial confirmation, localized resource lookup and explicit Continue after folder connection. Test totals are taken from current Gradle/instrumentation reports; they are not fixed README claims. Source staging is generated separately from the development folder and never initializes or publishes Git.

## Latest source checks

The November 2026 source passed JVM tests and targeted emulator checks for source grants, key-draft persistence, sharing, compact home rows, transcription settings and quick capture. The normal recorder shares one foreground-service implementation across manufacturers. Floating Record remains fixed over the scrolling list; the settings gear and transcription toggle are independently reachable.

See [OPEN-SOURCE-REVIEW.md](OPEN-SOURCE-REVIEW.md) for exact latest security/publication checks and remaining limitations. Build tasks and tests must be rerun after changes; this document is not a blanket validation of every revision or handset.

## What has been demonstrated

- Synthetic file discovery, finalization, chunking, fake-provider processing and deterministic local/Drive export behavior on disposable emulators.
- Short real Groq transcription for imported synthetic audio on a Samsung lab handset, both with Ramble visible and with the app in the background and display locked.
- A historical Google Play emulator run of release-signed consent and Drive text export. This does not establish current consent success for every build/account/device.
- Shared recorder capture surviving backgrounding and screen sleep in emulator tests, with notification Stop and one durable queued recording.

## What remains unproven

Real live-microphone speech quality through the remote lab, native telephone-call finalization, multi-hour real-device recordings, sustained overnight/deep-idle delivery, reboot recovery across OEMs and physical Pixel/Xiaomi gestures/battery behavior require separate verification. Generated 301-second PCM/AAC resume tests exercise a five-minute chunk boundary; they do not establish multi-hour endurance. A synthetic or fake-provider test does not establish provider billing behavior or speech accuracy. User-facing Drive setup must be tested with the actual installed package/certificate and Google account.

## Reproduction

Use JDK 17 and the pinned Gradle wrapper. Run `./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleDebugAndroidTest`. Run fixture instrumentation only on a disposable emulator: several tests intentionally clear test app state. Do not run the full fixture suite on a handset containing real credentials or recordings. Live lab/authorization diagnostics are explicitly opt-in and must be selected by class.
