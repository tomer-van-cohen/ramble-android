# Ramble Android 2027 — UX and design handoff

Status: proposed interaction design for review, not an implemented Android app. Synthetic content
only. The product name on screen is **Ramble**; the project folder is Ramble Android 2027.
`PRODUCT.md` preserves the accepted scope and the exact agent prompt. This document maps the scope
to screens and recovery behavior. Do not confuse this app with the earlier Mac/ADB diagnostic.

## Scope and intent

Let people keep recording in Samsung's native Voice Recorder and, where supported, Samsung Phone.
Ramble reads completed new recording files, sends audio directly to Groq or OpenAI with the user's
own API key, saves one plain-text transcript locally and optionally uploads that text to Google
Drive. Existing assistants can then retrieve the text using their own authorized Drive access.

No Ramble account, subscriptions, payment, maintainer-operated backend, desktop dependency,
in-app AI chat, live recording, automatic task execution, user-engagement nudges or historical
recording import. Those were discussed but explicitly deferred. Do not place a recording button
on the home screen implying this app captures audio. It processes files from the native recorder.

## Brand and Android adaptation

Use the existing logo without redrawing its waveform. The copied reference assets are authoritative:
`docs/design/assets/logo.svg`, `app-icon-1024.svg` and `onboarding-wave.svg`.
`ramble-reference-tokens.json` comes from the existing Ramble desktop design system. Preserve brand
identity, not macOS window geometry or the desktop's 22-point minimum touch targets.

- Brand fill: `#25D366`; text on that fill: `#121212`. Never white text on brand green.
- Light canvas/surface/inset: `#F4F6F4`, `#FFFFFF`, `#EDF0EE`.
- Dark canvas/surface/inset: `#1C1E1D`, `#262928`, `#303433`.
- Body and secondary text use the reference light/dark text colors. Green text uses the accessible
  accent (`#0F7A3E` light, `#3DDC84` dark), not the bright logo green.
- Quiet grouped surfaces, hairline separators, a 4dp spacing grid, 16–24dp page insets, 48dp or
  larger Android touch targets. Adapt rounded corners to Android, without copying an iPhone shell.
- Android/system typography with Hebrew support; proposed body 16sp, supporting text 13–14sp,
  titles 26–28sp. Respect font scale, system contrast and reduced motion. No continuous decorative
  animation, noisy waveform backgrounds, chat bubbles or dashboard metrics.
- RTL mirrors app navigation, row alignment and directional arrows. Paths, API key fields, URLs,
  product/provider names and timestamps get appropriate bidirectional isolation.
- Reuse the wave sparingly on first setup and success/empty states. Android and Google screens
  retain platform/provider styling; the app does not recolor their system permission surfaces.

## Navigation map

```text
First launch / resume incomplete setup
  Provider and key → Recording sources → Save destinations → Ready / agent handoff → Home
                         │                       │
                         ├─ Android source picker └─ Google authorization → Automatic Drive folder
                         └─ Call recording help

Ready → Optional quick-record setup guide / Open native recorder → Finish
      → Bilingual agent instructions (when Drive is connected)

Home → Transcript detail
     → Settings → Provider / Sources / Save destinations / Agent instructions
     → Repair current issue → Return to same queued work
```

Use a back action throughout setup. Keep non-secret choices when moving back; never reset baseline,
reprocess files or trigger provider calls because the user previews another settings screen.
Persist the current setup step so an interrupted setup resumes at the first unfinished requirement.

## Screen 1 — provider and API key

Header: Ramble logo + Ramble. Copy: “הקלט כרגיל. התמלול יגיע לתיקייה שלך.”
Small setup marker, not a dedicated welcome splash. Choose Groq (default) or OpenAI. Display one
masked API-key field and a provider-specific link to create a key. For Groq, a short “Free tier
available” note may link to current provider limits; never promise unlimited free hours.

Primary action: Continue. Disabled/validation behavior should explain a missing key. Validate using
a provider-supported low-cost/non-audio method when possible; do not upload a real recording merely
to test credentials. Show checking, accepted, offline/retry and invalid-key states inline. Switching
provider must not expose the previous provider's key. Never log or copy keys. The prototype uses a
dummy field and simulated validation only; it must not invite the user to paste real credentials.

Privacy copy is short and explicit: audio is sent to the selected provider with the user's key;
it is not sent to a Ramble server. Do not call this offline/on-device transcription.

## Screen 2 — recording sources

Voice memos is the primary source. Detect likely Samsung folders, show a readable name, and offer
Allow access. Detection is a suggestion, not a permission grant. Use the system folder picker with
`Recordings` as the initial parent location where supported and retain the returned grant. The user still confirms. This one parent-tree grant normally covers both known Samsung child folders; do not ask twice just because there are two logical sources.
If detection fails, explain and allow choosing a folder. On cancellation/denial, remain here and
allow retry; do not claim readiness or substitute broad access silently.

Separate optional row: Also process phone-call recordings. Off by default, with a Help action.
When enabled, reuse the parent Recordings grant and establish its own activation baseline. Request another narrow grant only if the call folder is outside the already-authorized tree. Typically
`Recordings/Call`; voice memos are usually `Recordings/Voice Recorder`. Do not switch off a working
voice memo source when calls are unsupported or permission is declined.

Help screen: check Samsung Phone → Settings → Record calls. Explain manual/automatic capture,
device/model/regional availability and that Ramble reads already-saved call recordings. No firmware
changes, accessibility workarounds, microphone permission request or claim of WhatsApp support.

Copy: “רק הקלטות חדשות מכאן והלאה.” Baseline semantics must survive reinstall/reconfiguration
decisions explicitly; a UI repair must not unexpectedly bill the user for historical recordings.

## Screen 3 — save destinations

Local text saving is automatic, not an onboarding question. Proposed public default is
Download/Ramble using app-owned MediaStore.Downloads entries on supported Android versions.
Validate plain-text MIME type, relative path, access and lifecycle in the device pilot. No output
folder picker is needed for this app-owned default. Keep recoverable app-private text as needed,
but don't label private storage as a public Documents directory. A custom output destination may
use an explicit persisted tree grant through settings later.

Google Drive export is included in v1 and optional for each user. This screen is a connection step,
not a folder-configuration form: offer Connect Google Drive or Continue locally. Google
authorization is to the user's account, not Ramble login. After authorization automatically create
or reuse the app's Ramble folder and persist its ID; no folder picker in the default flow.
Retries must not create duplicate folders. Return from cancel/error without losing local settings.
Show the account and default folder once connected; changing destinations belongs in settings.
Do not broaden sharing or make transcripts public. Choose the
narrowest workable Google scope and validate SDK/OAuth verification requirements before coding.

Persist local output before attempting Drive upload. Track transcript and upload as separate jobs;
Drive failure never causes a second paid transcription. Export only text by default, not raw audio.
Keep the upload queued while offline. Revocation requires reconnecting Google, not resetting setup.

## Screen 4 — ready and agent handoff

“הכול מוכן” plus compact source/output summary, not a wall of setup instructions. Explain that the
user can close the app and keep recording as usual. Do not promise immediate processing while
background behavior remains unvalidated.

With Drive: show “התמלולים שלך, גם אצל האייג׳נט שלך”, prompt language tabs **עברית / English**,
the approved short prompt from `PRODUCT.md`, and one Copy button. Prompt language is independent
of the app UI language. Keep and restore the user's explicit language choice. Substitute the real
Drive folder URL. Keep URL display LTR in a Hebrew paragraph. Copy only selected plain text, not
both languages or tab labels. No additional explanatory label above the copy button was requested.

With local-only storage: hide cloud-agent copy and Drive links; show the local destination and
Finish. Do not imply a cloud agent can read a phone-only path. Drive can be connected in settings.

Add the explicitly requested optional quick-record invitation before Finish: a compact illustration
of the phone Side button and double press, copy “הקלטה בלחיצה כפולה”, and “כך מגדירים”. It opens a
short guide: Settings → Advanced features → Side button → Double press → Voice Recorder → Start
recording. Show that availability depends on device/software/region, and validate lock-screen
operation on a real Samsung. The app must not claim it changed the mapping or verify completion
just because settings were opened. This is setup guidance, not a new always-on recording feature.

Offer “פתיחת המקליט” to launch Samsung's native recorder if installed; opening it is not equivalent
to starting a recording. Do not request Ramble microphone permission for that action. Provide
manual instructions if the app/settings target cannot be opened. The user may finish without
configuring the shortcut or making a recording. Establish/persist the source baseline before
launching the recorder, so this first recording is eligible for later processing. Keep the screen
short: agent instructions can use a compact row opening the same bilingual prompt component.

No recurring reminders, automatic outreach or other habit-building experiments in v1. When the
user later opens agent instructions, use the same reusable component and approved copy.

## Screen 5 — home and queue

Top app bar with Ramble identity, settings action. A small status surface says Active / Paused /
Waiting for internet / Action needed, with text plus symbol (never color alone). A Pause/Resume
action stops/continues new processing without deleting files or queued work. Show last successful
source check where useful; a live-looking status must not conceal stale background execution.

Chronological rows show recording title, original date/time, source type, duration when known and
the exact current state. States: Waiting, Transcribing, Saved locally, Upload pending, Saved to
Drive, Needs action. A locally completed transcript is openable even while Drive upload is pending.
Separate completed text from pending audio; never open an unfinished transcript as final content.

Empty: a short line indicating that only new recordings will appear; keep settings accessible.
Do not show synthetic samples as if they were the user's first real recordings. A visual prototype
can show explicitly synthetic samples, but production must not prepopulate fake user content.

No required search bar, analytics, streaks, quota chart, assistant composer or recording CTA in v1.
Opening a row leads to the transcript; an actionable error leads to the relevant repair screen.

## Screen 6 — transcript detail

Title + original recording date/time + source + duration if known. Readable plain-text transcript.
Copy text, share the generated text file via Android, and Open in Drive only after verified upload.
These actions require the user's click; never send a summary to someone automatically.

No generated summary/tasks/chat inside this screen in v1. Do not invent speakers or show identified
names without verified metadata/diarization. Keep original names/contact details out of diagnostic
logs even if included in user-facing metadata with appropriate access.

Long transcripts scroll naturally in the Android app, respect text-size settings and retain reading
position. The design review surface should fit without obscuring controls or clipping paragraphs.

## Screen 7 — settings and repair

Simple grouped list: Transcription provider/key; recording source folders and independent call
source; local output folder; Drive account/folder; agent instructions; processing Pause/Resume.
Keys stay masked; revealing/editing is deliberate. Explain effects before removing a source or
disconnecting Drive; never delete originals/transcripts as a side effect. Keep queued work and
local text when Drive is disconnected. No Ramble account/login/logout row.

General app-language switching is an implementation decision; the prompt's Hebrew/English switch
is explicitly required regardless of that decision. The proposed prototype uses Hebrew app UI.

## Recovery state matrix

| State | User sees | Action and retained work |
| --- | --- | --- |
| Invalid API key | Provider-specific credential error | Edit key; retain queue; no automatic charge retry loop |
| Provider quota/rate limit | Waiting with reason | Back off/retry; change key/provider deliberately |
| Offline | Waiting for internet | Retry when available; don't restart setup |
| Source permission revoked/moved | Folder access needed | Regrant same source; preserve activation baseline |
| Output not writable/storage full | Save destination needs attention | Free space/choose destination; preserve recoverable completed text |
| Drive authorization cancelled | Drive not connected | Continue locally or try again |
| Drive revoked/token expired | Saved locally, reconnect Drive | Retry upload only after permission restored |
| Upload transient failure | Saved locally, upload pending | Retry upload; never re-transcribe solely because upload failed |
| Paused | Processing paused | Resume; queued work is retained |
| Source file unfinished | Waiting for recording to finish | Don't submit or bill yet; handle long pause/hold |
| Source file removed mid-job | Clear skipped/unavailable state | No source edits; clean app-owned temporary audio according to retention policy |
| Provider timeout with unknown outcome | Retry status with bounded policy | Prevent blind double billing; define ambiguity policy before implementation |

Android notifications are a separate OS permission and must be requested contextually if the chosen
execution mechanism needs them. Permission denial does not equal consent to a hidden alternative.
If active foreground processing requires a visible notification, show truthful work status and a
relevant action; do not promise an invisible permanent service.

## Accepted background behavior and validation gates

1. Background latency is decided for v1: scheduled, best-effort scans at an approximately 15-minute
   interval, with OS/battery delays and transfer/transcription time in addition. The user accepts
   this latency. No permanently active foreground watcher or mandatory persistent notification.
   Use WorkManager-style durable periodic work, plus app-open/manual retry where appropriate.
   Still verify platform constraints for individual long-running transfers; no blanket promise
   that every job can run indefinitely without a notification. Never present 15 minutes as an SLA.
2. New-file detection and completion: persistent SAF grants alone do not guarantee content-change
   events or a reliable end-of-recording signal. Validate actual Samsung file lifecycle, including
   call hold and long pause. Stable size/time is only a heuristic.
3. Local/Google storage permissions: validate grants across process death/restart/revocation and
   account/folder changes. Resolve Google app verification/release requirements separately.
4. Speech quality/chunking: test Hebrew and mixed-language calls, long recordings and both providers.
   Provider limits/model availability must be checked again at implementation time.
5. Credential handling: app-private, Keystore-backed protection as appropriate; no maintainer key
   in source/APK. No key, audio, transcript or contact data in logs/crash diagnostics.
6. Distribution: first signed APK to consenting testers from a project release. No store account,
   commercial hosted service or public GitHub repo has been created by this design work.

Acceptance: walk onboarding with and without Drive; deny every grant/cancel every external flow;
repair API/storage/Drive failures; process a real new memo and supported manual/automatic call;
verify local text and optional Drive text with phone locked, offline recovery, reboot and app
force-stop. State plainly where a user must relaunch the app; don't claim a force-stop exemption.

## Visual deliverables

The written requirements take precedence over the earlier interactive sketch. In particular,
the final optional Samsung Side-button guide and first-recording invitation must be included in
the designer's updated screens even if absent from that sketch. Output-folder pickers belong in
advanced settings only, never the default onboarding.

The interactive design review covers onboarding, simulated Android/Google handoffs, ready states,
empty/mixed queue, transcript detail, settings, prompt languages and representative errors. It makes
no external calls and uses no real account, recording or secret. Shared widget state stores only
design choices; never store keys or transcript contents there.
