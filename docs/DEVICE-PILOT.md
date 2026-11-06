# Samsung device pilot

Use synthetic speech and consenting test calls. Keep audio, transcripts and keys on the tester's device. Record only model/software and technical outcomes.

Install the built APK by opening it and approving installation from that source, or `scripts/install.sh` with an existing authorized development device. Developer mode/ADB is a testing option, not an app requirement.

1. Validate a test Groq/OpenAI key. Invalid/offline must preserve setup without claiming success. Never paste the key in a bug report.
2. In the Android picker approve Recordings. Verify Voice Recorder is recognized and old recordings do not enter paid processing. Enabling calls should reuse the same parent grant, not display another picker.
3. Complete setup with and without Drive. Verify Ready and the first recording invitation occur after the baseline is persisted. Configure Side button manually if supported; don't assume opening Settings changed it.
4. Record a new spoken test memo. Wait for two observations; verify no per-recording confirmation is shown and processing starts automatically after stop. Confirm one complete Downloads text and optional Drive text. Reopen/reboot and verify there is no duplicate upload or paid request.
5. Record, pause for 20 minutes, resume, stop. Observe document identity, metadata, pending markers and file publication at every stage. Stable size is not permission to automatically upload. The app requires a final sample index, not size stability. Verify paused files never acquire accepted completion proof and stopped AAC/M4A files do; collect format diagnostics if a device waits instead of guessing.
6. Test manual and automatic native phone-call recording only if the native Phone app offers it. Check incoming/outgoing, hold/mute and both voices. Record what the other party hears about recording; Ramble must not change that behavior.
7. Process a long synthetic file (>25 MB, >5 minutes). Verify ordered complete text, no omitted tail and checkpoints across process death. A killed billable request should enter uncertain state, never blind retry.
8. Revoke source/Drive authorization, delete target folder, turn off network, fill storage using safe test fixtures, pause/resume, change provider while a partially processed job exists. Work and original audio must be retained, and credentials/destinations must not silently change for prior chunks.
9. Verify Hebrew/English agent prompt selection and actual copied text, large font (200%), TalkBack, dark/light mode, cancel/back on every system surface and source/output links.
10. Document 24-hour background latency under default Samsung battery settings, ordinary swipe-away, reboot/first unlock, force-stop/relaunch. Never publish a 15-minute SLA or claim force-stop immunity.

Do not mark real-device gates passed using only an emulator or fake-provider report. Do not send a tester's private recordings to the maintainer to prove the feature.
