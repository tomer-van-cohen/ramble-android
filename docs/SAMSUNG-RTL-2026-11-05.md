# Samsung Remote Test Lab — 2026-11-05

Device: Galaxy S24 FE SM-S721B (India), Android 14 / One UI 6.1.1, 30-minute free-credit reservation. RDB downloaded from Samsung's web client; macOS exception was explicitly approved by the user. Tests use the 0.3.2 application code, debug instrumentation, synthetic provider credentials and a fake Transcriber. No owner's Google account/key or paid transcription was used.

## Passed on physical hardware

1. `MediaWakeTest.systemMediaEventsScanAndTranscribeWithoutActivity`: real MediaStore insert/publication events, actual JobScheduler delivery and re-arming, SAF fixture input, local output, single fake-provider call. Passed in 5.686 seconds. Periodic work was canceled to isolate the events. No forced scheduler run or Ramble Activity in this test.
2. `SamsungNativePilotTest.manualNativeRecorderBackgroundPilot`: user-visible Android picker granted the actual Recordings parent. The existing recording was baselined. Test armed the event job, canceled periodic work, and returned Home. Samsung Voice Recorder made a new 21.4-second lab recording, `Recordings/Voice Recorder/Voice 261105_163110.m4a`. It was saved around 16:31:40 India time. MediaStore wake timestamp was 1793876503367 (16:31:43.367); Ramble persisted one local transcript at 16:32:46.983 without reopening the app. Approximate stop/save-to-output latency: 67 seconds, including the normal 60-second stability proof. Provider was fake, so this is delivery/decoding/queue/local-export evidence, not speech recognition accuracy or network processing latency.

The opt-in pilot is skipped in the default suite; run only on a dedicated lab device with `-e rambleLabPilot true` and select its class explicitly. It clears test app state and waits for an operator to grant the folder and save a native recording. It leaves processing paused afterward.

## Limits

The laboratory device had Stay Awake enabled and was connected to lab infrastructure. This does not establish screen-off/Doze latency, overnight reliability, cellular performance, different Samsung models, real Drive OAuth/export, live-provider accuracy, or native phone-call recording. No physical-device production reliability claim should be made from these two tests.

Logs (ignored local artifacts): `artifacts/samsung-mediawake-test.txt`, `artifacts/samsung-native-pilot.txt`.

## Cold-process follow-up

An additional attempt to repeat the emulator cold-process check on this Samsung was not completed. The RDB connection dropped twice during the am-kill/run-as setup sequence, before a controlled event could be sent. Cause is not established; this is not evidence that the app failed to wake. No physical cold-process or Doze success is claimed.
