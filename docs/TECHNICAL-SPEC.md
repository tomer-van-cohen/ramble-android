# Ramble Android technical implementation specification

Revision 1.0, 2026-11-01. Product: **Ramble**, developer/BYOK edition. This is an implementation contract, not a claim that an APK, native integration or device validation already exists. Implement in this project directory. The hosted commercial edition has its own requirements in the sibling `ramble-android-commercial` directory; do not mix accounts, billing, historical import or backend dependencies into this edition.

## 1. Read order and authority

Read this file, `PRODUCT.md`, `UX-HANDOFF.md`, `../DESIGNER-START-HERE.md`, and the brand assets/tokens under `design/`. Product scope governs behavior; this file governs implementation. Final approved wireframes govern placement and styling, not queue semantics or permissions. The source-access refinement here supersedes earlier language requiring two Samsung folder grants: prefer **one grant to Recordings**, then restrict application reads to the mapped, enabled child sources. Update conflicting UX text before implementation.

The app is Kotlin-native, source-available and independent of a Ramble server. Users supply a Groq or OpenAI key. Existing Samsung apps capture audio; Ramble processes completed files, writes local text and optionally exports text to the user's Google Drive. No Ramble account, payment, in-app chat, live-call capture, ADB, Termux or desktop dependency. Version 0.3 adds an optional foreground-only onboarding recorder; see FIRST-RECORDING.md. No import of recordings from before source activation. No analytics SDK or remote configuration in v1.

## 2. Product invariants

1. Audio leaves the device only for the user-selected transcription provider after setup and source activation. Drive receives generated text only.
2. Never alter, move, rename or delete source recordings. Never use contact/call-log permissions to enrich titles.
3. No network transcription before eligibility and completion checks, source and provider consent, a durable job, and a durable request-attempt record.
4. Resume completed chunks and local/Drive exports independently. A Drive failure cannot trigger transcription again.
5. Source identity, eligibility cutoff, chunk plan, result identity and destination bindings survive process death and updates.
6. Do not turn a file's arrival or modification time into a claimed recording start time.
7. No invisible permanent service. Background scans are best effort, about every 15 minutes; this is not a delivery deadline.
8. Provider outcomes may be ambiguous. Promise deduplication of known work, not exactly-once remote billing.
9. Setup and recovery work for Hebrew/English, large text and older/nontechnical users. At most one primary and one secondary action on each step; move advanced controls into dedicated subpages.
10. No claim of Samsung compatibility until the corresponding real-device gate passes. Mac/emulator mocks are not device proof.

## 3. Platform and project structure

Use minSdk 30 (Android 11). Choose the latest stable supported compile/target SDK at implementation, satisfying the intended distribution channel; pin the exact SDK, AGP, Kotlin, JDK, libraries and Gradle wrapper in the repository. Do not target an obsolete SDK to evade current behavior. Initial target audience is Samsung with Google Play services; devices without those services retain local-only BYOK processing and show Drive unavailable.

Use Kotlin, coroutines/Flow, Jetpack Compose/Material 3 adapted to Ramble tokens, Navigation Compose, ViewModel/SavedStateHandle, Room with exported schemas/migrations, DataStore for non-secret preferences, WorkManager, OkHttp and Kotlin serialization. Use Android's MediaExtractor/MediaCodec/MediaMuxer for audio; no abandoned FFmpeg wrapper dependency. Use Google Identity AuthorizationClient for Drive authorization, not a bespoke password form or mandatory Google login. Constructor injection and an AppContainer suffice; avoid a mandatory DI framework.

Start with one Gradle `:app` module and clear packages:

```text
app/src/main/java/.../rambleflow/
  app/             AppContainer, application startup, navigation
  domain/          IDs, states, policies, use cases, interfaces
  data/db/         Room entities, DAOs, transactions, migrations
  data/settings/   DataStore, credentials, configuration versions
  sources/         Samsung profile registry, SAF enumeration, completion adapters
  audio/           probe, bounded streaming decode/encode, chunk planner
  providers/       Groq/OpenAI adapters, request ledger, response validation
  exports/         private/local files, MediaStore, custom SAF, Drive
  workers/         scan, processing pump, Drive pump, work reconciliation
  ui/              setup, home, detail, settings, repairs, agent instructions
  diagnostics/     redacted event codes and opt-in report
```

Keep domain logic free of Activity/Compose and testable with fake clocks/content providers/network. No Swift executable or Mac path in the APK. Reuse policies/test cases from `ramble-flow-cli`, not sandbox/launchd/SQLite assumptions specific to macOS.

## 4. Samsung source mapping and the single permission flow

Ship a versioned `DeviceProfileRegistry`, selected by manufacturer and validated model/software capabilities, not language or geographical guesswork. Initial profile `samsung.recordings.v1` contains known **candidate** relative paths:

| Logical source | Default path | Default enablement |
| --- | --- | --- |
| Voice memo | `Recordings/Voice Recorder` | On after setup |
| Phone call | `Recordings/Call` | Explicit opt-in |

The parent candidate is `Recordings` on primary shared storage. These are documented defaults, not a universal filesystem guarantee. Before a grant, the app can suggest this location but must not pretend to enumerate protected contents. Use `ACTION_OPEN_DOCUMENT_TREE`, set an initial URI hint when the system/provider supports it, and retain the returned read grant via `takePersistableUriPermission`. The picker can ignore the initial-location hint. The user must approve; the app cannot self-grant. Do not request storage root, Android/data, broad all-files access or a nonexistent direct-file permission dialog.

One parent-tree grant covers descendants. After approval resolve the known child document IDs **inside that tree** using DocumentsContract/ContentResolver. Do not convert content URIs into raw paths or concatenate URI strings. Application scanning is allowlisted to selected child document roots and audio types, even though the OS tree permission covers more. Calls remain logically disabled until the user opts in. Do not inspect other Recordings children opportunistically.

Validate whether the chosen tree contains the expected source(s). If a child is absent, show a meaningful state, not "permission denied": e.g. no call recordings folder yet. Keep an absent optional child pending discovery under the same authorized parent; never create Samsung's source directories. A memo source requires a completed baseline before Ready. If its default child is absent, allow creation of a first native memo followed by baseline activation, clearly explaining that this initial setup sample is excluded; then invite the first eligible recording after activation. Never silently fall back to another app's recordings.

A manual picker is a recovery path when a validated profile does not match or storage is on another volume. If sources are under different parent trees, request a second narrow grant. Reuse an already-held ancestor grant; do not ask twice for the same coverage. Persist a grant record separately from each logical source; disable/removal releases only grants with no remaining consumers. An arbitrary user folder must not be auto-classified as phone calls based only on filenames.

Regrant to the same document tree preserves cutoff/baseline. A new tree is a new source with its own baseline and an explicit confirmation; it does not inherit an old source's eligibility. Parent/child overlapping selections share document identities and content deduplication. Do not scan cloud document providers as though they were Samsung local recording folders in v1.

## 5. Permissions and device actions

Release manifest: INTERNET, ACCESS_NETWORK_STATE and only the additional permissions required by the selected, documented AndroidX/Google components. Audit the merged release manifest. SAF is the external source-access mechanism. RECORD_AUDIO is optional and requested only for the native onboarding trial; no READ_MEDIA_AUDIO, MANAGE_EXTERNAL_STORAGE, contacts, call log, phone state, AccessibilityService, device admin, root or debug permission is requested. WorkManager may contribute its documented boot/wakelock components; do not add a second custom boot scheduler.

No notification permission during initial setup for a permanent watcher: there is no such watcher. Error status is visible on next app open. If later implementation needs a user-initiated foreground operation, specify its notification/permission behavior separately; do not silently add it to background v1.

Samsung's Side button is user configured. Provide optional instructions and a tested settings deep link if available, otherwise manual navigation. Do not use private settings writes, shell commands, Accessibility clicks or Knox enterprise management to change the binding. Use a public launcher intent to open Samsung Voice Recorder when available, with package visibility limited to the necessary packages/intents. Label it **Open recorder**, not **Start recording**. Fail gracefully when unavailable. Do not invent a direct start intent.

Side-button voice recording and phone-call recording are separate native capture flows. Calls start inside Samsung Phone or its supported automatic-call-recording settings. Ramble neither enables that feature nor changes announcements; behavior varies by regional firmware/software. Record actual behavior in device QA, never promise silent capture or WhatsApp support.

## 6. Setup transaction and configuration changes

Persist `onboardingStep` and non-secret choices. Steps: provider/key -> source access and optional calls -> automatic local output and optional Drive authorization -> Ready/agent handoff/optional quick-record guide. Default Groq; link to key creation and say only that a free tier is available. Do not promise a fixed free-hours allowance.

Validate a key using the provider's current models/credential endpoint without submitting audio. Distinguish offline/temporary failure from rejected credentials. Offline can save the key as unverified; automatic uploads wait until validation succeeds. Keep raw keys out of UI state snapshots, navigation arguments, saved instance state and clipboard. Do not show a full key after saving.

For each source, commit baseline/cutoff and activation state before inviting the first recording. If baseline enumeration fails or is incomplete, leave that source initializing, not active. A process death during setup resumes the unfinished step. No paid calls during preview, baseline or back-navigation. Ready requires at least one active source, a saved provider configuration and usable local export. Drive is optional in BYOK and must not block local readiness.

Preferences include provider profile ID, model policy version, language default auto, processing pause, Wi-Fi-only (default false with explicit mobile-data disclosure), optional Drive enabled/account/target binding, preferred prompt language and app locale. Changing provider/model affects unstarted recordings. A partially transcribed job stays bound to its original profile; explain and request a deliberate restart if the user wants to change it. Retain the old encrypted credential while pending jobs need it, or block those jobs if user removes it. Never silently concatenate chunks from different models.

Pause stops starting new transcription and export network requests; local discovery can retain pending files. An in-flight request may finish and its result must be checkpointed. Resume does not rebuild a baseline or replay old results. Source disable stops new ingestion and pending sends from that source; preserve jobs and originals. Removing configuration is distinct from deleting local data.

## 7. Data model and persistence

Room database is the queue authority; WorkManager is a trigger, not the source of truth. All IDs are opaque UUIDs except stable content hashes. UTC instants stored with explicit precision; retain original offset/timezone provenance separately. Proposed entities and required fields:

| Entity | Important fields / constraints |
| --- | --- |
| SourceGrant | id, treeUri, providerAuthority, persistedFlags, lastVerifiedAt, state |
| RecordingSource | id, grantId, rootDocumentId, type, profileVersion, activationCutoffUtc, baselineComplete, enabled, lastScanAt, scanCursor |
| SourceObservation | sourceId + documentId unique, displayName, size, modifiedAt, observedAt, candidateRecordedAt, dateProvenance, stabilityFingerprint, stableSince, completionEvidence, disposition |
| BaselineEntry | sourceId + documentId unique, initial metadata/content evidence; never upload baseline entries |
| Recording | id, primarySourceId, originDocumentId, contentSha256, encodedBytes, durationUs, recordedAtUtc nullable, originalOffset/zone nullable, dateConfidence, firstSeenAt, title, state, reason, immutableProfileId, planVersion |
| RecordingOrigin | grant/source/document identity -> recordingId; map duplicates and moves without making a new billable job |
| Chunk | recordingId + ordinal + planVersion unique, startUs, endUs, sha256, privatePath, bytes, state, transcriptPath, responseHash |
| RequestAttempt | id, chunkId, state, createdAt, sendStartedAt, completedAt, safeErrorCode, providerRequestId nullable, credentialVersion; no key/body |
| Transcript | recordingId unique, stableExportId, privatePath, sha256, createdAt, localState, localUri nullable |
| DriveTarget | id, accountBinding, folderId, enabled, generation; immutable upload binding |
| ExportJob | transcriptId + targetId unique, generatedRemoteId, state, nextAttemptAt, attempts, remoteVersion/hash, lastError |
| WorkLease | resource unique, ownerToken, processSession, acquiredAt, expiresAt; transactionally claimed |

Use foreign keys, indexed due-state queries and Room transactions. Export Room schema JSON; never destructive-migrate production. Retain completed identities/tombstones so normal cleanup cannot make old recordings eligible again. Store secrets separately from Room. Full audio/transcript content is not a WorkManager Data argument (pass only opaque IDs).

## 8. New-only discovery, dates and duplicate handling

Capture an activation cutoff before baseline scanning and finish a coherent baseline before activation. Files created during enumeration need an explicit scan boundary policy: baseline only files whose reliable recorded-start time is <= cutoff; leave later candidates for active discovery. Unknown-time baseline files are excluded. An interrupted baseline restarts enumeration without moving the cutoff forward.

Eligibility requires: source enabled + baseline complete, document not in baseline/excluded set, reliable recorded-start time strictly after cutoff, and completion evidence. Prefer validated embedded/native metadata. Filename date parsing is allowed only under a tested device-profile parser; preserve original string/provenance, reject invalid/ambiguous local times. Modification time and firstSeen are not sufficient proof of recording date. A late copy/restore of historical audio remains historical even if its filesystem timestamps changed. When date is uncertain, leave the candidate waiting; no background billing.

When reliable dates are unavailable across a device's entire recording workflow, flag the device integration as unsupported for automatic new-only processing rather than claim new-only automation. Do not force a product-level promise through a heuristic.

Keep source identity independent of filename. Rename should update display metadata, not make a new job. Once complete, hash the candidate's actual bytes. Deduplicate exact content across enabled sources against canonical completed/in-flight content and baseline-known hashes when available. Hash-based dedup is best effort for historical items whose original bytes were never hashed; disclose that gap and fail closed on untrusted newness. Do not pre-read a huge archive solely to deduplicate it.

Use streaming DocumentsContract child queries, bounded pages/batches (initial target 200 rows), direct mapped children only, no whole-device traversal. Persist progress safely; do not advance a high-water mark past an unprocessed entry or use modification time as the sole scan cursor. Every periodic cycle includes eventual reconciliation for missed/renamed documents; overflow/backlog is visible, not silently dropped. Persist observation metadata; a process restart must not reset the stability clock.

## 9. Completion detection and immutable snapshots

SAF has no universal recorder-finished API. V0.2 uses bounded structural evidence for finalized non-fragmented single-track AAC/M4A, rather than per-recording confirmation. `FinalizedMp4` checks ftyp, finite boxes, moov/mvhd/mdhd duration, one mp4a track, stsz/stts/stsc/stco/co64 consistency and every sample's mdat bounds. Open-ended, truncated, fragmented and unsupported files wait. Require two identical proofs separated by >=60 seconds and stable source metadata; a stable size alone is insufficient.

Read the actual seekable descriptor length, because a Samsung provider may under-report stream size. Capture an immutable private snapshot, validate its exact completion proof, and revalidate the source before upload. A change returns an unbilled job to waiting. Date eligibility uses a recognized Samsung filename date (YYYYMMDD_HHMMSS or YYMMDD_HHMMSS) or conservative MP4 created-time minus duration; preserve provenance in output. Undated files are not billed automatically. Baselines remain excluded across migration/regrant.

The Android platform encoder/muxer is exercised before and after stop in native tests. This proves the Android MP4 path, not every Samsung recorder mode. Real memo pause/resume, locked recording and call hold/mute still require hardware validation. If a recorder finalizes a valid file while intending to resume it later, the adapter needs additional lifecycle evidence; never claim structural evidence is universal. See AUTOMATIC-COMPLETION.md for limits and source references.

No source edits while snapshotting; private partial files are replaced only after complete copy. Originals remain owned by the user.

## 10. Audio planning and long recordings

Probe via MediaExtractor/MediaMetadataRetriever with time/size caps; identify actual audio track/container, duration, channels, sample rate and decodability. The extension is not trusted MIME proof. Corrupt/unsupported/no-audio files go to Needs action without provider requests. A silent but valid file is not corruption.

Build a persisted plan before uploading. Initial conservative policy: maximum 300 seconds per chunk; maximum encoded file 24,000,000 bytes, including a separate multipart overhead margin checked against the endpoint request cap. Use shorter contiguous chunks when needed. Provider capabilities can reduce these limits, never increase them based on an assumed paid Groq tier. No splitting raw compressed bytes in the middle of a container.

For a supported original <= duration/byte caps, use an immutable pass-through copy if decodable and accepted by that provider. Otherwise stream decode/downmix/resample and encode standalone AAC-LC M4A segments (target mono, 16 kHz where supported, 64 kbps). Probe encoder capabilities; a tested 44.1/48 kHz fallback is allowed and recorded in the plan. The resampler must retain fractional phase and timestamps; simple sample dropping is not acceptable. Pipeline decoded PCM through bounded buffers; never hold an entire recording in memory. Reuse one decoding pass, checkpoint completed encoded chunks, and regenerate an interrupted chunk deterministically. No shell execution or native binary downloads.

V1 chunks cover the timeline contiguously without overlap; concatenate text by ordinal with paragraph separators. No LLM rewriting/summarization or guessed overlap removal. Prefer nearby measured silence only within a bounded window and preserve full coverage. Slight boundary loss is an acknowledged quality tradeoff, tested with Hebrew and mixed-language fixtures. Planned intervals use integer microseconds and the actual codec sample timeline; account for encoder delay/padding, avoid empty final chunks, verify every emitted file and actual byte cap. Binary-reduce/reencode a segment if too large before any request. Preserve all real audio tracks' speech using a tested mix; don't silently transcribe only one speaker channel.

Initial safety limits: 8 hours per recording, 2 GB source file, 256 chunks, 2 GiB temporary app-owned audio, and free-space reserve 256 MiB (also require enough space for the next bounded copy/encode). These are engineering limits, not provider entitlements. Above limits: Needs action without truncation. Pilot tuning can change versioned limits, not silently omit audio. Long planning is resumable; reusing encoded chunks must validate hashes/plan version.

## 11. Provider adapter contracts

`TranscriptionProvider` exposes credential validation, immutable capabilities, and `transcribe(chunk, profile, attemptId) -> text + bounded metadata`. Use fixed HTTPS allowlisted hosts; no arbitrary base URL field in v1. Never forward bearer tokens across redirects. Disable automatic client/proxy retries for billable POSTs. Separate provider and Google clients; do not put provider keys on Drive requests.

As verified on 2026-11-01, use Groq `whisper-large-v3` as default via `https://api.groq.com/openai/v1/audio/transcriptions`; OpenAI `gpt-transcribe` via `https://api.openai.com/v1/audio/transcriptions`. OpenAI's current documentation recommends `gpt-transcribe`; do not copy the Mac engine's older model assumptions or use `gpt-4o-transcribe` as a new default. Validate current model availability and request schema again at implementation. [Groq speech API](https://console.groq.com/docs/speech-to-text), [OpenAI file transcription](https://developers.openai.com/api/docs/guides/speech-to-text).

Send multipart file/model and only supported optional fields. Normalize their JSON text responses. Language auto means omit hints; explicit language needs adapter-specific mapping (OpenAI's current model uses `languages`, not the older singular field). No diarization, translation or timestamp promise in v1. Never auto-switch provider to spend money on a different account. Do not use public audio URLs to bypass upload limits; Groq's direct attachment cap is 25 MB even where its documented URL input tier allows more.

Bound response text to 2 MiB per chunk and read errors through a small sanitized parser; store no raw error bodies in logs. Valid empty transcript becomes a no-speech result with the recording preserved and a visible empty state. Malformed success/oversized response after sending is an uncertain outcome, not a free retry. Authentication checks must not use a real recording.

## 12. Job and request state machines

```text
observation -> excluded | waiting_for_date_approval | waiting_for_completion
            -> snapshotting -> planning -> queued -> transcribing
            -> assembling -> local_export_pending -> saved_local

per chunk: planned -> ready -> attempt_prepared -> in_flight
                   -> result_saved | retry_due | needs_credentials
                   | quota_blocked | outcome_uncertain | permanent_error

Drive: disabled | queued -> uploading -> verified
                      -> retry_due | needs_authorization | target_missing | conflict
```

An error in Drive never changes `Recording` back to queued. Local exported text remains readable during Drive failures. Display meaningful combined states without conflating current activity and completed artifacts.

For a chunk request: acquire lease; save an attempt in a transaction; check pause/profile/consent; mark send-start conservatively before network I/O; submit once; atomically store response artifact and hash, then commit chunk success. Reconcile durable response files after a crash before any repeat send. If a prior attempt is send-started with no validated response, mark outcome uncertain and require a user action acknowledging potential repeat cost. Never assume process death means the provider did no work. A local attempt ID is not provider-supported idempotency.

Safe automatic retry cases: explicit documented rate-limit rejection (respect Retry-After), definite authentication rejection after user repairs key, and proven pre-send connectivity failure. Ambiguous timeouts/disconnects/5xx without a documented no-processing guarantee require review. For safe transient failures use jittered exponential backoff (30 sec base, 6 h cap, max 8 automatic attempts), persisted `nextAttemptAt`; then show Needs action. Distinguish daily quota exhaustion from ordinary rate limits; don't poll every minute for a billing problem.

Use one billable request at a time globally on device. Pause/new profile changes are checked before each chunk. Room leases with a process session token protect against simultaneous app-open/manual/periodic workers. A process-local mutex alone is insufficient. A stale lease can be recovered but an abandoned in-flight attempt still becomes uncertain. No transaction is held over media/network work. Track monotonic time for in-process deadlines and UTC for persistence; handle clock changes conservatively.

## 13. Durable background scheduling

Register one unique periodic discovery request `ramble.scan.v1` at 15 minutes; preserve rather than recreate its timer on every app launch. Schedule an additional unique one-shot scan on app-open, successful grant, resume or explicit retry. Discovery has no network requirement. A transactional outbox/reconciler ensures crashes between DB commit and WorkManager enqueue don't strand work.

Use bounded processing and Drive pumps with CONNECTED constraint (UNMETERED if chosen), unique names and DB leases. Do not enqueue an unbounded request per file or replace an active worker when a new candidate arrives. Exponential retry waits are scheduler waits, not worker sleeps. Worker input/output includes only IDs/status codes.

Normal CoroutineWorkers are finite. Design each invocation for <=7 minutes wall time with <=120 seconds per network attempt, media checkpoints and cooperative cancellation; never assume the platform grants even this budget. Stop starting new work with <150 seconds remaining. A long recording spans multiple invocations. If networking has started at cancellation, persist uncertainty; checkpoint successful responses even when paused. Small requests improve success probability but do not guarantee execution under Android/Samsung quotas. Do not use expedited work, exact alarms or foreground promotion to simulate an invisible daemon.

On process death/reboot let WorkManager recover scheduled jobs; reconcile pending state on next startup. Force-stop may prevent runs until the user opens the app again. Explain this in troubleshooting. No mandatory battery-optimization exemption in setup; test Samsung default battery policy first, offer contextual guidance only for demonstrated restrictions. The UI shows last completed source check; "Active" means enabled, not proof of continuous execution.

[Periodic scheduling](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work), [finite workers](https://developer.android.com/reference/androidx/work/ListenableWorker), [long-running workers and Android 16 quotas](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running).

## 14. Local transcript storage and crash-safe publishing

First assemble a canonical UTF-8 private transcript in app no-backup files, with SHA-256 and stable export UUID; atomically commit it before public/Drive exports. Retain it while local/Drive delivery is incomplete. Store readable output metadata in Room; never use the title as identity.

Default public destination: `Download/Ramble/` via app-owned MediaStore.Downloads entries, `text/plain`, no output picker. Use a stable filename, e.g. `2026-11-01_143000_voice-memo_a1b2c3d4.txt`; sanitize separators, control/bidi override characters and path tricks. Date in filename is recorded time if known; otherwise explicitly use `undated`, not an invented recording date. Human-readable original title lives in the file header. A collision cannot overwrite unrelated text.

Insert with pending publication, persist the allocated content URI, write and verify bytes, then finalize. Reconcile crash windows by URI and deterministic ID/name; never search/delete arbitrary Downloads items. A DB-write failure after insert requires reconciliation before another insert. Stale app-owned pending entries can be cleaned after safe identification; never delete user-modified public files. If a known URI has been deleted, mark local file missing; restoration is deliberate and uses the retained canonical text, not another transcription.

Header format, plain text (not a required sidecar):

```text
Title: <original title>
Recorded at: <ISO-8601 with known offset, or Unknown>
Timezone: <known zone or Unknown>
Source: Voice memo | Phone call
Duration: <actual duration>
Transcript ID: <stable UUID>
Transcribed at: <ISO-8601 UTC>
Provider/model: <non-secret identifiers>

<transcript paragraphs in original language>
```

Preserve Hebrew/English output; don't translate or add invented speaker labels. Mark unverified recording time as such. Retain initial metadata even if source title changes during retries; deliberate metadata corrections update known outputs without paying for transcription.

Custom local folder is optional advanced settings via SAF. Bind existing exports to their destination generation; changes affect future exports and never silently duplicate history. Sharing uses content URI + temporary read permission through a non-exported FileProvider restricted to actual transcript paths. Do not expose app root or credentials.

[Shared storage](https://developer.android.com/training/data-storage/shared/media), [pending publication](https://developer.android.com/reference/android/provider/MediaStore.MediaColumns#IS_PENDING).

## 15. Google Drive without a Ramble backend

Drive is included in v1, optional to connect. Use current Google AuthorizationClient with `https://www.googleapis.com/auth/drive.file`. Request it contextually, distinct from authentication; no Ramble login is needed. Keep tokens transient where possible and let Google Play services reacquire authorized access. No embedded client secret or service-account key; no Google password handling. Background workers cannot launch consent UI: set Needs authorization and resume from the app.

The release publisher configures Google Cloud Drive API, consent branding, Android package and signing certificate fingerprints. Client IDs are public configuration, not secret credentials. Local-only builds work without a Google project; self-builders wanting Drive configure their own project/client identity. Official release needs production OAuth publishing/verification as actually required for its scopes, audience and branding; sideloading an APK does not remove that requirement. Test debug and release signing separately. Account management restrictions and absent Play services are recoverable states.

After authorization, create/reuse a **normal My Drive** folder named `Ramble`, tagged with an app property/schema version, and persist account/folder binding. Never use hidden appDataFolder for transcripts, public sharing or broad whole-Drive scopes. If multiple app-owned candidates exist, select deterministically only after verifying stored target; don't adopt a same-name unrelated folder. Use one provisioning lock, pre-generated Drive IDs persisted before create, and lookup/reconciliation after an ambiguous create response. Do not create a fresh folder blindly on every retry.

Generate and persist a Drive file ID before uploading each transcript. Upload plain text under that same ID and immutable account/target binding; attach an opaque export ID and text hash in appProperties. After uncertainty, GET that ID and reconcile size/checksum or bounded content comparison before retry. If a create reports existing ID, inspect ownership/app tag/hash; never overwrite an unrelated object. Use conditional version/ETag where supported when updating a known object; remote user edits yield Conflict and no automatic overwrite. Revoke/account change stops old-target jobs. An old account's queued file must never be uploaded under a new account just because tokens changed.

On account change, preserve unsent local exports and offer an explicit migration choice on a separate screen; default future-only. On target deletion, show repair, do not silently make a new folder/link. On disconnect, stop uploads and clear/revoke authorization as appropriate without deleting local or Drive files. Folder readiness means authorized and provisioned; agent-ready file state requires successful text upload. Each user must separately authorize their chosen agent to Drive; our Google consent cannot authorize ChatGPT/Claude/Instinct on their behalf.

[Android authorization](https://developer.android.com/identity/authorization), [Drive scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth), [pre-generated IDs](https://developers.google.com/workspace/drive/api/guides/create-file).

## 16. Agent handoff and UI behavior contract

No in-app assistant or automated task/message execution. Ready offers the existing approved short agent prompt with independent Hebrew/English tabs. Substitute `https://drive.google.com/drive/folders/<actualFolderId>`; copy only the selected plain text. Prompt language persists independently of UI language. Show no cloud prompt while disconnected/provisioning; keep it accessible in settings after setup. Provide Open transcript after first verified upload without claiming every connector has indexed it.

Use exact approved copy from `PRODUCT.md`; don't broaden the trigger to every task/meeting mention, add "explicitly", or require a no-transcript-found response. Both locale resources must implement semantic parity, bidirectional isolation, TalkBack labels, >=48dp touch targets, font scaling, accessible green contrast and reduced motion. External/system screens retain native styling. UI controls for advanced error approval must expose the actual consequence (possible paid retry), not generic Allow.

Home exposes enabled/paused/waiting/action-needed plus last scan time and real user jobs. Detail is readable transcript, copy/share and verified Drive link. No summaries, chat, invented sample transcripts or decorative live-recording state. Submitting an already-completed job again requires a deliberate separate action; normal tap opens it.

## 17. Security, retention and operational behavior

Encrypt provider keys with AES-GCM using a non-exportable Android Keystore key, randomized IV per write, app-private ciphertext and versioned aliases. No biometric-required key for automatic background access. Handle locked-before-first-unlock and key invalidation by waiting/re-entry, not data reset. Disable automatic cloud/device-transfer backup of credentials, Room jobs/baselines, tokens and raw/private text; ship both applicable legacy backup rules and data-extraction rules. A restored partial profile cannot be considered activated. Document that uninstall removes private state; public text and Drive files may remain. A reinstall starts a new baseline and never retroactively charges the archive.

Release logging contains only redacted event/error codes, durations, byte counts and opaque support IDs. No raw source names/paths, contacts, transcript, audio, request/response bodies, keys or authorization headers in Logcat/crash reports. No network/body logging interceptor in release. Diagnostic export is user initiated, previewable and metadata only. Treat provider content and transcripts as untrusted text, not instructions or executable links. Allow only fixed HTTPS endpoints; cleartext disabled in release. Test-only localhost behavior cannot compile into release.

Original audio retention is unchanged. App-owned snapshots/chunks: remove within 24 hours after final canonical transcript and terminal attempt reconciliation; keep failed/uncertain jobs at most 7 days subject to 2 GiB cap, then evict only app-owned retryable audio and retain ledger/results. Eviction requires source re-copy with matching content hash before resuming; if unavailable, show needs-source. Never evict an in-flight chunk or the sole unexported text. Private canonical text persists until the user deletes it; no automatic deletion of public/Drive copies. Provide a clear local-data deletion operation distinct from reset/retranscribe; cancel workers first and retain/explain external copies. Secure physical erasure on flash is not promised.

[Keystore](https://developer.android.com/privacy-and-security/keystore), [backup rules](https://developer.android.com/identity/data/autobackup).

## 18. Implementation milestones and proof gates

| Milestone | Build | Acceptance gate |
| --- | --- | --- |
| M0 foundations | pinned Gradle/CI, interfaces, schema, fake adapters, theme | build/lint/JVM tests; no network from synthetic mode |
| M1 storage pilot | Samsung parent grant, child mapping, baseline, lifecycle recorder | real device proves access/metadata/pause/stop; no historical sends |
| M2 local pipeline | snapshots, chunking, fake provider, Room recovery, local export | killed at each checkpoint; exactly one known output; 2h audio bounded memory |
| M3 BYOK | real adapters/key storage/error ledger | owner-consented Hebrew/English memo with each provider; no auto repeat on uncertain timeout |
| M4 background | unique WorkManager scans/pumps and cancellation | locked/offline/reboot/Samsung defaults; report actual latency and force-stop limits |
| M5 Drive/agents | authorization, idempotent folders/text, prompt component | production-signed OAuth path, real text retrieval by at least one existing agent |
| M6 calls/release | call-source opt-in, automatic/manual calls, signed APK | supported real Samsung calls and full release checklist below |

Do not implement UI polish as a substitute for M1. A Mac can run JVM tests, emulator and APK builds; a consenting tester with Samsung is needed for storage/call/background validation. No hardware is currently available locally; record blocked device tests honestly rather than claiming all-green release readiness. If completion detection fails, withhold the unsupported automatic format/profile; changing to a recording app is not an implicit fallback.

## 19. Required tests

Unit/fake integration: activation crash/retry; late old file; rename/move; overlapping grants; calls disabled under a shared grant; revoked tree; absent optional child appearing later; incomplete baseline; unknown/invalid/future/DST dates; device clock jumps; zero/corrupt/multitrack/huge files; long pause vs stop; source changing during copy; all chunks contiguous/ordered/below cap; no omitted tail; codec delay/resampler; low space; concurrent scan/manual worker; lease expiry; pause/cancel before/during send; response saved before DB commit; unknown outcome; 401/403/429/quota/5xx; Unicode/bidi titles; MediaStore insert/write/publish crash; Drive folder/file creation uncertainty; same-name unrelated folder; account switch/revoke/target delete/user edits; retry export without provider call; uninstall/reinstall/new baseline; key invalidation; no secrets in diagnostics; schema upgrades from every released version.

Instrumentation/emulator: SAF with fake DocumentsProvider, Room migration tests, WorkManager TestDriver, lifecycle/process recreation, MediaStore plain text/open/share, Keystore locked/invalidated cases, Hebrew RTL/English prompt tabs/font scale 200%/TalkBack, cancellation of all external flows. Test Play-services account authorization on a supported environment; a fake Google response is not OAuth verification.

Real Samsung pilot matrix: at least one recent S-series and one A-series where feasible; record model, Android, One UI, native recorder/Phone version and regional context without identifiers. Test native memo standard/stereo modes, long pause, recording while locked/using phone, 2h recording, calls manual/automatic/incoming/outgoing/hold/mute/Bluetooth where supported, first recording after setup, delayed folder creation, Wi-Fi/mobile/offline, low battery, app swipe-away vs force-stop, device reboot/first unlock, grant revoke/move, day-long background scans. Confirm both sides in call audio locally; do not ask testers to send private audio/transcripts.

Acceptance per supported device: all new eligible completed recordings eventually produce one correct local text, no old recording sent, no duplicate known chunk requests on retry, Drive retry independent, completion guard survives pause, errors actionable, last-scan truthful. Record measured detection/transcription/upload P50/P95, peak memory, battery impact and manual interventions; no fabricated SLA.

## 20. Release and handoff checklist

Initial deliverable is a reproducibly built **release-signed APK** and checksum, optionally GitHub release for testers; not a promise of Play approval. Choose/verify an owned package ID before first release; proposed `io.github.tomer_van_cohen.rambleflow` is a placeholder until ownership/repository is confirmed. Debug and release identities/fingerprints differ. Never commit signing keys/passwords; persist the release signing identity securely across updates. App versionCode increases; test upgrade installs preserving Room/cutoff/grants. Android APK signing is required even without a paid store account. Current store/developer-verification rules must be checked for actual date/territories before wider distribution. [Signing](https://developer.android.com/studio/publish/app-signing).

Before public distribution add README/build/install/uninstall/recovery guide, license consistent with the owner and existing Ramble assets (do not assume trademark/logo rights follow a code license), THIRD_PARTY_NOTICES/SBOM, SECURITY.md, privacy/data-flow/retention disclosure, contribution guide, sanitized fixtures, dependency verification and CI. Match code license to the existing CLI's actual license after inspection; don't invent ownership. Screenshots/fixtures contain synthetic data only. No keys, OAuth tokens, account emails, model artifacts, test audio from users or local absolute home paths in commits. Releases/publication are separate actions, not authorized by this planning document.

An implementing agent must keep this specification linked to decisions, implement milestone order, include actual commands and results in progress notes, and distinguish simulated vs physical validation. Do not operate the existing Mac tool, modify its profile, deploy Railway or create a public repository as a side effect. No native implementation has been performed by writing this specification.

## 21. Evidence and revisiting external dependencies

Technical platform references were consulted on 2026-11-01. Linked references above support API constraints; limits/defaults marked engineering policy are this project's choices. Re-check providers/SDK/OAuth/distribution rules at implementation and release. Samsung source defaults are documented by [Samsung's storage guide](https://www.samsungsvc.co.kr/solution/38418) and [PC export guide](https://www.samsungsvc.co.kr/solution/38168); these guides are discovery evidence, not verification of our SAF adapter. [Samsung call recording](https://www.samsung.com/us/support/answer/ANS10004613/) and [Side-button setup](https://www.samsung.com/lb/support/mobile-devices/how-to-customise-the-side-button-with-new-features-on-your-galaxy-phone-and-tablet/) do not establish universal regional/model availability.

The remaining empirical gates are source date/completion metadata, Samsung background behavior, native text publication, codec compatibility, release-signed Google authorization and actual agent retrieval. They have explicit implementation paths and tests; they are not reasons to wait for final button designs or substitute guesses for verification.
