# Background delivery follow-up — 0.3.1-alpha

A Samsung beta tester reported that completed recordings/export appeared after opening the app, sometimes more than an hour after recording. Her screenshots show an older Ramble release. They establish successful transcription/Drive delivery for some recordings, not continuous background reliability.

## Confirmed code contribution

SourceReader requires two observations of identical finalized MP4 proof, at least 60 seconds apart. Previously ScanWorker returned success after the first observation and scheduled no follow-up. In unattended operation it could wait for another 15-minute periodic scan, with each scan also subject to Android battery/Doze delays. MainActivity.onCreate explicitly schedules a scan, explaining why reopening can advance the pipeline. We have no tester scheduler logs, so this is not a proven sole cause of her hour-long delay.

## Change

New periodic and manual ScanWorker requests specify linear 60-second backoff. Existing periodic registrations are retained by KEEP during upgrades, so those retain WorkManager’s prior backoff (30-second exponential default); the bounded retry decision still applies, with a possible second follow-up before the 60-second proof age is met. When a complete scan finds enabled-source finalized candidates still stabilizing, ScanWorker returns retry for at most two follow-ups. The worker reuses the normal scan, proof, queue, duplicate prevention and processing pipeline; it does not bypass finalization. Paused processing, incomplete scans, disabled sources and files lacking finalization evidence do not start this short retry loop. Ongoing recordings do not cause an unlimited polling loop. Periodic discovery remains enabled and no new permissions/services are introduced.

Refresh now is placed above the status/list so it is visible immediately, including with larger text. It queues a scan; it does not guarantee immediate cloud completion.

## Remaining limits and physical-device pilot

This is still best-effort WorkManager, not an immediate background monitor. Android may delay even the follow-up. No foreground service, exact alarm, battery exemption request or system-setting change has been added silently.

For the next Samsung pilot: update without uninstalling; verify the app's battery setting allows background operation (use Unrestricted for the diagnostic run, and check Samsung Sleeping/Deep sleeping apps). Save one short recording, leave Ramble closed, note save time and actual Drive arrival time. Compare with/without the battery restriction. Do not force-stop the app; that prevents normal background execution. Collect app version, model/One UI version, last-check time and pending-job status, not API keys or private audio. Pixel/Xiaomi need separate physical validation.

Reference: https://developer.android.com/reference/androidx/work/PeriodicWorkRequest (15-minute minimum; battery/Doze can defer execution).
