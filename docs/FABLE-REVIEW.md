# Fable review follow-up — 2026-11-06

Scope: security, reliability, privacy, publication boundary, onboarding and maintainability. License terms and third-party artwork are unchanged by owner instruction. The repository has not been initialized or published.

| Finding | Result |
| --- | --- |
| Private files beside source | New `stage-public-source.py` copies only the reviewed allowlist into a separate directory outside the workspace, checks both ends and refuses existing destinations. Working artifacts, SDK/build outputs, APKs, old archives and the independent website remain private and are excluded. Do not publish the working directory. |
| External quick-record activation | The direct recording entry is a non-exported activity. Main UI and our immutable tile PendingIntent use it. The exported compatibility/launcher-shortcut entry always requires a visible Record or Stop-and-save tap; forged extras grant no trust. There is still exactly one app launcher icon. External shell entry to the private activity is rejected. |
| Provider failures and retries | Definite 4xx rejections except 408 stop with a rejection message. DNS/connect failures before upload are safely retried. Once upload may have begun, transport failures, 408 and 5xx retain explicit uncertainty approval. Safe failures do not consume a billable attempt. Delayed retry wakeups allow other eligible jobs to proceed. |
| OpenAI model | `gpt-transcribe` is valid in the current official speech-to-text guide. The multipart language field is corrected to `languages[]`; Groq retains `language`. No speculative model replacement. |
| Long decode timeouts | Timestamp-aligned decode windows commit each finished WAV/chunk. Work-budget expiry yields at a boundary and resumes from the persisted ordinal; it does not discard the completed prefix. Missing windows are repaired independently. Impossible source/PCM storage budgets stop clearly. Schema 1/2 migration preserves earlier complete plans. |
| Audio retention | New installations remove app-private audio copies after a durable transcript by default. Existing installations retain their prior audio until the user changes the explicit setting. Settings provide Keep original audio and Clear completed audio; individual recordings can be deleted. Pending/error audio is retained for recovery. User source-folder originals and already exported Downloads/Drive files are not erased. |
| Interrupted recordings | Valid interrupted captures appear as recovered recordings. Interrupted trials and unrecognized orphan captures require explicit Recover and transcribe approval; they never silently upload. Unusable partials remain visible for removal. Abandoned unsubmitted shared copies expire after 24 hours. |
| Full storage | Native/wrapped ENOSPC, EDQUOT and SQLite-full failures enter recoverable storage state. A delayed worker retries when storage is available. |
| MainActivity size | Platform activity is now a small entry; welcome, provider, source access, settings, detail, license display and shared chrome are separate files. Workflow orchestration remains in RambleFlow; this is not a claim of a complete architecture rewrite. |
| Inline UI text / Hebrew default | User messages moved to English/Hebrew Android string resources, using Android-compatible `values-iw`. Default ASR language follows Hebrew/English interface language, with explicit Auto detect and other choices preserved. |
| Folder onboarding | No timed or grant-triggered advancement. Successful connection remains on the folder page with Continue, Add folder and folder controls. Back navigation also leaves the page open. Empty but accessible standard sources are accepted. |
| Documentation | Removed fixed stale README test-count claims; refreshed architecture/validation and public synthetic screenshots. SECURITY points to the public maintainer profile for requesting a private reporting channel, without inventing an email address. |

## Verification and limits

JVM tests, selected disposable API-35 emulator regressions, debug/release lint and debug/release compilation passed. Regression coverage includes forged exported intents, genuine private entry, rejected HTTP versus pre-upload DNS failure, storage recovery, PCM and compressed AAC resume beyond five minutes, existing-row schema migration, audio cleanup/deletion/reimport, interrupted-trial approval, resource localization and explicit folder Continue. Test logs are private build artifacts; they are not bundled in public source.

The long-audio fixtures are 301-second generated PCM and AAC, including interruption/resume and repair comparisons. They exercise a chunk boundary but do not prove multi-hour real-device endurance, all source codecs or overnight OEM power behavior. Real provider speech quality, current Google account consent and physical Pixel/Xiaomi controls require separate device checks. No new real-provider upload or Google consent run is claimed by this review.

Publication checks scan only the separately staged source snapshot. The working root has no Git history; any later imported history must be scanned independently before publication. Source preparation does not grant authorization to publish.

Reference: [official OpenAI speech-to-text guide](https://developers.openai.com/api/docs/guides/speech-to-text).
