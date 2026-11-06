# Ramble Android 2027 — product specification

Status: design and planning. No APK exists yet. Earlier ADB transfer diagnostics are a separate
experiment, not the architecture of this native Android project. Their simulated tests do not
validate Samsung background work, native folder permissions, Google authorization or live APIs.

## Agreed product direction

A source-available, standalone Android app. No maintainer-operated server or user account. The phone
reads newly created native Samsung recordings, sends audio directly to the user's selected
transcription provider using their own API key, and saves text locally. Optional Google Drive
authorization enables uploading the text directly from the phone. A desktop, Termux, ADB and
FolderSync are not required for this native app. The provider still receives audio for transcription;
"standalone" does not mean offline/on-device speech recognition.

First launch should request access to detected recording folder(s), provider/key and optional
Drive authorization. Output folders are provisioned automatically by default. Prefer persistent user-selected folder grants over broad file
access. Detect likely source locations where possible, but auto-detection never substitutes for
the system permission flow. Exclude pre-existing recordings when each source is first activated.

Onboarding refinement: do not ask users to configure output folders. Provision local text storage
automatically (proposed public destination: Download/Ramble using app-owned MediaStore.Downloads
entries on supported Android versions; validate text-file creation and access in the device pilot).
After the user authorizes Google Drive, create/reuse an app-managed Ramble folder automatically.
Persist its ID and make retrying creation safe. Destination overrides belong in settings only.
Google account authorization and source access grants still require user interaction; automatic
folder detection/provisioning does not grant access to existing recordings or a Google account.

Google Drive export is part of v1, not deferred to a later release. Connecting a Google account
is optional for the user: local transcription still works without it. With Drive enabled, retain
the local transcript and retry cloud export independently without retranscribing/billing the audio
again. Store plain text files in the automatically provisioned Drive folder readable by their existing agents; destination overrides belong in settings.

## Agent handoff in onboarding

V1 has no in-app AI chat, semantic question answering, personal assistant, task manager or agent
connector. Existing external agents search the user's Drive using their own supported connection
and permissions. Do not promise every agent has a Drive connector, immediate indexing, access to
the selected folder or persistent memory of pasted instructions.

After connecting Drive and provisioning the default folder, show a short "Use this with your AI assistant" step:

- Show the actual Drive folder name and a link that identifies its folder ID, not just an
  ambiguous display name. Do not create a public sharing link or change sharing permissions.
- Provide **Copy instructions for my agent**. Fill the prompt with the actual user's folder URL
  (display the folder name separately). Never include API keys, OAuth tokens, audio, or a
  maintainer's account information.
- Provide two tabs/segmented choices, **עברית** and **English**, for the prompt only. The selection
  is independent of the app UI language: a Hebrew-speaking user must be able to copy English
  instructions without changing the app language. Initially follow the app language (English
  fallback), then remember the user's explicit choice. Use RTL for the Hebrew text and LTR for
  English and isolate the URL so mixed-direction text remains readable.
- **Copy instructions** copies only the selected language's plain text, substituting the actual
  Drive folder URL. It must not copy both versions, tab labels, Markdown quote markers or a
  placeholder. Show a brief copied confirmation without leaving the screen.
- Explain briefly that the user pastes it into the agent they already use and connects that agent
  to their own Drive if needed. This does not create a Ramble account or authorize an agent for them.
- Give one immediately useful starter question, e.g. "What commitments came up in my latest
  recorded meeting?" After the first successful upload, show the specific exported transcript link
  so the user can test retrieval. Do not show "ready in your agent" before upload succeeds.
- Keep **Copy instructions** and **Open transcript folder** available in settings afterward.
  If Drive is disconnected, explain that cloud-agent access needs Drive export; do not generate
  a prompt containing a local-only path as though a cloud agent can read it.

Approved copy for the Hebrew tab:

‏כשאני שואל על תוכן של שיחה או פגישה, למשל מה נאמר או אילו נושאים עלו, חפש תמלול בתיקייה הזאת ב-Google Drive: {folder_url}. אם מצאת, השתמש בו וציין את המקור.

Matching copy for the English tab:

When I ask about the content of a conversation or meeting, such as what was said or which topics
came up, look for a transcript in this Google Drive folder: {folder_url}. If you find one, use it
and cite the source.

Keep this onboarding copy short. The trigger is a question about conversation/meeting content;
do not broaden it to every mention of a meeting, task or decision. Do not add "explicitly", a
mandatory "no transcript found" response, or a disclaimer about not every meeting being recorded.
Those additions were considered and removed by the user. Copy text as normal flowing prose with
the actual folder URL, not as an unresolved template. Verify both tab values and copied content,
including a Hebrew app UI with English prompt selected and persistence after reopening.

Transcripts need stable identity plus the original recording date/time and timezone, rather than
only transcription/upload time. Include a readable title and source type (voice memo or phone call)
where known. This lets an agent interpret "tomorrow" relative to when it was spoken. Do not invent
dates, speakers, names or diarization. Never change an existing file's identity on retry.

## Longer-term direction, outside v1

The user envisions the quick record button as input to an existing personal assistant: a meeting
can yield a summary, a spoken intention can become a dated task, and later an agent may maintain
a to-do list or perform follow-up actions. Preserve that direction without expanding v1 into an
assistant app or adding a required Ramble backend/account.

Potential future, explicitly configured agent workflows can process new transcripts, draft summaries,
extract proposed tasks with owner/due date/source, and eventually perform selected actions. Separate
capture, transcription and export from these workflows. A statement recorded from another person
is not automatically a task for the user, and a relative date must use recording time/timezone.
Sending messages, changing calendars or other external actions require the user's configured
authorization; a transcript alone is not authorization. No automatic messaging is implemented or
authorized by this roadmap.

General reminders, notifications and engagement experiments remain deferred. A subsequent explicit
request adds a narrow exception: the final onboarding screen should invite the user to configure
Samsung's Side button double-press to Voice Recorder → Start recording, with a small illustration
and short instructions, and offer Open Voice Recorder for a first recording. The invitation is
optional and must not block completion. It is not a Ramble recording engine or a recurring nudge.

The native recorder launch action must be labeled Open recorder, not Start recording, unless the
exact start-recording integration has been separately validated. Do not silently change Samsung's
button mapping. Settings links should open an actually supported screen with manual navigation
instructions if no documented deep link exists. Model/version/region differences and lock-screen
behavior remain device-test items. Provide a fallback for devices without the direct action.
Establish the source activation baseline before inviting the first recording, so an onboarding
recording is not accidentally excluded as historical when the user later presses Finish.

## Shared Samsung permission and two logical sources

Use the built-in Samsung source profile to suggest `Recordings` in the system tree picker. One
parent grant covers the known `Voice Recorder` and `Call` children. Read only the enabled mapped
children; call processing still requires opt-in. Separate logical baselines do not require separate
permission popups. If validated device paths differ, manual selection/additional narrow grants are
recovery only. See `TECHNICAL-SPEC.md` for the binding implementation contract.

## Two independent sources

- Voice memos: usually `Recordings/Voice Recorder`; recording is started in Samsung's existing app.
- Phone calls: usually `Recordings/Call`; recording is started manually or automatically in
  Samsung Phone, when supported by the device's regional software and model. Offer this source
  separately and only after the user opts in. Do not change call-recording settings automatically.

Ramble processes completed existing recordings. It does not capture live phone-call audio or
promise support for WhatsApp/other VoIP calls. No exact speaker identity is implied by a transcript.

## Native-device acceptance tests

Run voice memo and call recording source tests separately. For calls:

1. Confirm Record calls is present in Samsung Phone settings; record model, Android/One UI and
   regional/import context where known. If absent, mark call capture unsupported on this device;
   do not imply Ramble can enable it or require firmware modifications.
2. Grant access to the call-recording folder and establish a new-recordings baseline.
3. Manual-record a short test call and verify both voices and final duration locally.
4. If supported, temporarily test automatic recording, both incoming and outgoing. Restore the
   original setting afterward and avoid capturing unrelated calls during testing.
5. Verify no unfinished call file is accepted while a call is active, including mute/hold/long
   quiet periods. Unchanged size alone is not proof that a recorder closed its output file.
6. Test screen lock, ordinary phone use, offline call then reconnection, process death, restart,
   battery restrictions, multiple queued calls and long calls requiring chunking.
7. Verify one complete text output per recording, no duplicate billable submissions after retry
   where avoidable, durable partial progress, no overwrite of originals, and useful error status.
   Ambiguous provider timeouts need an explicit retry/cost policy; do not promise exactly-once API
   billing without provider idempotency support.
8. If Drive is enabled, verify text arrives without a computer, revoked authorization is handled,
   and no raw audio is uploaded to Drive unless the user separately chooses it.

Report only technical outcomes, latency, errors and manual interventions. Do not collect audio,
transcript text, contact names, phone numbers, API keys or OAuth credentials from testers.

## Next engineering decisions

Accepted v1 background behavior: scheduled, best-effort processing rather than a permanently
running foreground watcher. The user accepts an approximately 15-minute scan schedule and OS
delays, plus upload/transcription time. This is not a 15-minute delivery guarantee. Do not require
a persistent always-on foreground-service notification. Transient notifications or foreground
execution for a particular long-running job remain subject to platform rules and must be designed
truthfully if they become necessary; do not claim periodic work exempts all uploads from those rules.

The next proof is native folder access plus durable scheduled background discovery on a real
Samsung. Use the accepted best-effort periodic approach for v1: periodic WorkManager is
not an instant trigger, while foreground work has notification/start/duration restrictions. Do not
promise immediate processing or invisible permanent execution. Verify supported content-change
notifications for the chosen storage mechanism; a persistent folder grant alone does not establish
a reliable background new-file trigger. Retry connectivity/provider throttling and verify sleep,
force-stop and reboot behavior rather than assuming a general exemption from Samsung power policy.

Store credentials in app-private storage with Android Keystore-backed protection as appropriate.
Keep transcription and optional Drive authorization separate; use current platform/OAuth guidance
before implementing. Native package signing/distribution and any Google authorization verification
requirements need their own release plan. Do not silently embed a maintainer's provider keys or
Drive client secrets in the APK.
