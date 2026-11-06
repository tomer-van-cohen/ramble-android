# First recording before quick-record setup — Ramble 0.3

This supersedes the trial/quick-record ordering in the original design prototype and the 0.2.4 audit's open-question section. The owner explicitly chose in-app microphone capture, rather than embedding Samsung's recorder/widget.

## User flow

Welcome → provider/key → external recording-folder access (or connect later) → optional phone-call source → optional Drive → copy AI-assistant instructions → **Record your first sample** → **actual transcript and first list item** → **Set up quick recording** → **visual device-specific guide** → Home.

The trial shows a short suggested sentence in the UI language. The user taps Record and approves Android's microphone permission. Only then does the app capture AAC/M4A. The fixed bottom CTA becomes Stop and transcribe; Cancel is a subtle text action. Stop closes the file and queues the normal transcription pipeline. Thirty seconds is a disclosed maximum; the timer stops/saves automatically then. Leaving the app or cancelling an unsaved recording discards it without uploading. No foreground recording service, background-microphone permission, notification permission or external recorder is involved in this trial.

The loading state comes from the actual durable queue. A saved result shows actual canonical transcript text, its local/Drive status and the first item of the real recording list. A Drive error never causes another transcription request. Only verified Drive upload earns the Drive check mark/assistant-availability statement. Offline, quota, invalid key, storage and uncertain-provider outcomes use the existing retry/approval semantics. Continue later preserves the queue. An existing saved trial is reused on relaunch, without re-recording/rebilling.

The last primary CTA is Set up quick recording. The secondary is Go to home. The optional guide opens the phone's settings (Samsung/Pixel) or Recorder/Home-screen shortcut instructions (Xiaomi/other). Done finishes setup into Home with the same first item. Existing installations that had already completed setup do not have their onboarding state reset.

## Native input and device guides

- Samsung: parent `Recordings`, known `Voice Recorder` / `Call` children. Guide: Advanced features → Side button → Double press → Voice Recorder → Start recording, where the model/software offers it. Physical key illustration and three highlighted instruction panels. Older versions may only open Recorder.
- Pixel: suggested `Recordings` with known children if present, otherwise the explicitly granted root. Recorder's latest Android12+ Access recordings in other apps can expose copies. The app does not access private originals. Guide uses Quick Tap on the back, not a claim that Pixel's power button can launch arbitrary apps. Quick Tap opens Recorder; the user still taps Record. Quick Tap is documented for Pixel4a(5G)+.
- Xiaomi/Redmi/POCO: suggests the historical `MIUI/sound_recorder` layout, recognizes `sound_recorder` and optional `call_rec`, supports a user-selected alternative. Some MIUI/HyperOS versions use app-private storage or Google Phone recordings, so automatic external import cannot be promised from the model name. No Android/data access workaround is used. The visual guide uses a Home-screen Recorder shortcut instead of inventing a universal Action button.

The app's in-app trial is platform-neutral. Native recorder import support still depends on the selected folder actually being exposed, recording finalization format and device validation. The success copy says **connected sources**, and explains how to connect sources later if none were granted. It does not promise automatic call capture or arbitrary private-recorder access.

## Code and durability

`TrialRecorder`: Android ViewModel, MediaRecorder, lifecycle cancellation and timer. `AppRecordings`: UUID-only private storage, finalized-MP4 evidence and immutable hash, deterministic queue ID, DB/preference recovery. `ProcessWorker`: same normal provider/chunk/publish/Drive path, app-source guard exemption only for private source type. No Room schema change; no baseline reset. `FirstRecordingPage`: live progress/result/list UI. `RecorderDevice` + `QuickRecordingGuide`: manufacturer detection, package/root suggestions and independent visual guides.

Store `source=app`, `document=id`; input cannot be an arbitrary path or external URI. Media remains in app-private no-backup storage. Unsaved partials are not eligible for processing. A snapshot must match both its hash and completed-container proof. The first sample follows the same paid-request ledger as external audio.

## Validation boundaries

Instrumentation uses the real Android MediaRecorder/AAC path on a disposable emulator, with emulator audio and an in-process fake provider. It checks zero provider calls before Stop, actual exported text/list, quick guide only after success, persistence after relaunch, and leaving the foreground without a queued upload. Separate tests use a native AAC fixture and the real worker, verify idempotent submit/recovery, reject changed/partial input and reject path traversal. Existing provider HTTP and Drive-export tests remain. This is not a real-speech quality evaluation or a new live Google OAuth/Drive E2E; physical Samsung/Pixel/Xiaomi validation remains required.

## Primary references checked 2026-11-02

- https://support.google.com/pixelphone/answer/7443425?hl=en — Quick Tap and limited power-button choices.
- https://support.google.com/pixelphone/answer/16267668?hl=en — public copies versus private originals.
- https://r1.community.samsung.com/t5/tips-how-to/how-to-customise-the-side-button-with-new-features-on-your/ba-p/37310989 — device-dependent Side button choices.
- https://www.mi.com/ph/support/article/KA-07847/ — historical public/private recorder paths and Android11 restrictions.
- https://www.mi.com/uk/support/faq/details/KA-541461/ — Google Phone call recordings and sharing; no universal public-call-folder guarantee.

## Speech feedback and short clips (2026-11-05)

- The first-recording screen and Settings offer a persistent transcription language, including automatic detection. This preference is independent of the Android interface language and is sent as the provider's `language` parameter.
- During capture, MediaRecorder peak amplitude is sampled every 100 ms for a live microphone meter. This is an input-level display, not proof of intelligible speech.
- Digital silence in the normalized PCM WAV is rejected locally before sending audio. This conservative check does not classify background noise as speech or impose a duration threshold. Empty provider text is a `no_speech` outcome, never a successful export. Groq requests verbose metadata and also rejects a result when every segment has both no_speech_prob > 0.6 and avg_logprob < -1.0. Low confidence alone and clip length do not reject speech. These signals are heuristics, not a guarantee that all incorrect transcripts are detected.
- Silent chunks inside a longer recording contribute no text; they do not discard speech recognized in other chunks. Only a recording with no remaining text gets the no-speech outcome.
- The first-recording screen explains this outcome and offers Record again plus language selection. Retrying creates a new recording, preserves the previous original and survives activity recreation. Settings > Try a recording starts a fresh attempt.
- Unit coverage checks one-word output, selected language, silence, non-speech hallucination metadata and low-confidence speech. Emulator coverage checks capture/stop, the visible meter, persisted language, no export on empty text and the retry flow.
- `LiveShortSpeechTest` is opt-in only (`-e rambleLiveSpeech true`), uses the configured provider/key without logging either audio text or credentials, and sends synthetic Hebrew/English WAVs shorter than two seconds plus a separate silence fixture. It never clears app data or changes the user's settings. Run individual test methods to avoid unnecessary requests. This verifies API recognition of those clips; it does not establish microphone forwarding from a computer to Samsung's remote lab or arbitrary real-world speech accuracy.

Validation: 36 host unit tests and 3 emulator first-recording UI tests passed. On the Samsung lab device, the opt-in live provider test recognized the two short synthetic speech clips and rejected the silent clip after the local digital-silence guard was added. The initial silence test exposed a provider hallucination despite its metadata; this is why the local guard is required. No claim is made that metadata detects every hallucination.
