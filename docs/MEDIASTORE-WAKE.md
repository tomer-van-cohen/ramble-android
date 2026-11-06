# MediaStore-triggered discovery — 0.3.2-alpha

MediaWake registers a one-shot JobScheduler content trigger for external audio, including descendants. It coalesces changes (1-second update delay, 5-second maximum trigger delay; these are scheduler hints, not delivery guarantees). The JobService durably enqueues the ordinary SAF ScanWorker and re-arms the trigger. SourceReader's baseline, finalized-file proof, deduplication, provider queue, and Drive exporter remain authoritative. No MediaStore URI is consumed as an import instruction, and no ungranted source is read.

There is no READ_MEDIA_AUDIO/all-files permission and no polling ContentObserver kept alive in the app process. The system owns the content trigger. WorkManager's periodic 15-minute scan and bounded file-stabilization follow-ups remain the fallback. App start and scan requests repair missing trigger registration. BOOT_COMPLETED and MY_PACKAGE_REPLACED restore the non-persistable content job. Paused processing suppresses scanning and unregisters on the next arming/event; resume scans register again.

The system-protected exported JobService requires BIND_JOB_SERVICE; the non-exported receiver accepts boot/package-replaced events from Android. RECEIVE_BOOT_COMPLETED is a normal install-time permission, not an added runtime prompt. A local timestamp records successful event-to-queue handoff without file paths/content/account details.

## Validation boundary

The integration test publishes actual finalized AAC/M4A to the emulator MediaStore, with the launcher foreground and no Ramble Activity opened. All periodic work is canceled first. Android must invoke the real registered JobService; a synthetic granted DocumentsProvider supplies the matching candidate. A second MediaStore event after canceling all WorkManager work proves re-arming and processing to a local transcript with a fake provider. The test advances the fixture's proof timestamp for the second event to avoid waiting for the normal stabilization interval. No scheduler force-run, paid transcription, live Drive upload, physical OEM recorder, or process-kill proof is implied.

A native recorder must publish changes into MediaStore for this wake-up optimization to apply. Private/unindexed OEM recordings still depend on periodic discovery, if accessible at all. Doze, background restrictions, force-stop and OEM sleeping-app behavior still apply. Samsung hardware is the next gate; it is not valid to infer lock-screen latency from this emulator run.

References:
- https://developer.android.com/topic/performance/background-optimization
- https://developer.android.com/reference/android/app/job/JobInfo.Builder

## Additional cold-process check

After the suite, an empty instrumentation filter initialized Application without an Activity. The process was then killed with `am kill` (not force-stop); pidof confirmed no PID. An audio row inserted by the shell into real MediaStore triggered Android to launch a new app process (PID 2494), with successful event-to-scan handoff timestamp 1793874489378. No job was force-run. This proves cold process wake-up for an external-UID event on this emulator. The inserted row did not contain speech; transcription/local export were proven separately by the integration test, and no new live Drive call was made. Evidence: artifacts/mediawake-cold-result.json and mediawake-cold-job-evidence.txt.

Validation: 29 JVM + 34 native tests passed; lint and release build passed.
