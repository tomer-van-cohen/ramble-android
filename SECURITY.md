# Security and privacy

## Data and credentials

Ramble sends audio directly to the selected Groq/OpenAI HTTPS endpoint using the user's API key. It has no Ramble server or analytics SDK. Optional Google Drive export receives generated plain text, not audio. The user explicitly chooses the provider, sources and export destination during setup; new supported audio in enabled folders is subsequently processed automatically. Do not connect a folder whose contents you do not intend to send to that provider.

API keys and unvalidated pasted drafts use Android Keystore AES-GCM encryption in app-private preferences. A draft becomes usable only after provider validation. Older encrypted credential profiles can remain for already-queued work. Keys are never copied to the clipboard by Ramble. Tokens are obtained through Google Play services; no web-client secret or service account is embedded. Backups and device transfer are disabled. Provider requests do not follow redirects or log bodies/credentials; diagnostics use fixed status categories.

The queue database, cached audio and canonical transcripts are private to the app sandbox but are **not separately encrypted by Ramble**. Generated Downloads/custom-folder transcripts are ordinary text files; other apps with suitable access may read them. Optional Drive files are governed by the user's Google account permissions. Do not describe all user data as encrypted merely because API keys are encrypted.

## Android entry points

Folder imports use user-granted Storage Access Framework trees; source selection/removal does not change or delete original recordings. No contacts, call-log, Accessibility, device-admin or all-files permission is requested. The app transcribes saved call recordings; it does not intercept or record telephone-call audio itself.

Microphone permission is required for Ramble's own recorder. The onboarding trial is limited to 30 seconds and cancels on leaving before stopping. Normal/quick recording uses a microphone foreground service and visible controls/notification, can continue with the display off, and stops without restarting capture automatically after a crash or reboot. The exported recording shortcut is confirmation-only: opening it cannot start or stop the microphone, even with forged intent extras. Only the non-exported internal recording activity can act immediately. In-app recording uses that private activity; the Quick Settings tile uses an explicit immutable PendingIntent created under the app's UID. The capture service is also not exported. Quick Settings launches visible permission-aware controls after unlock. Samsung's optional floating indicator requires a separate user-granted overlay permission; the app does not bypass that grant.

Pixel audio sharing stages a bounded local copy before confirmation; staging does not upload. Only content URIs are accepted; cross-user authorities, file URIs and this app's own FileProvider are rejected. Media type/duration/size and immutable-copy checks apply before transcription. FileProvider exposes only the private transcripts subdirectory with temporary explicit grants. The main, confirmation-only quick shortcut and share activities are user-visible exported entry points; the internal recording activity is not exported; the system job/tile services require their platform bind permissions.

The welcome wave is painted directly using native Compose Canvas, using the supplied design geometry. It loads no web pages, runs no JavaScript and has no browser/native bridge.

## Retention and retry limits

Uninstall removes private state, keys and app-owned private audio/text. Public Downloads/custom-folder exports and user-owned Drive files can remain. Disconnect does not delete them. New installs default to removing app-private original audio after a durable transcript has been saved. Existing users retain their previous audio until they explicitly change Keep original audio or use Clear completed audio. Pending/failed audio is preserved. Unsubmitted shared copies expire after 24 hours. Interrupted app recordings become visible for recovery/deletion; an interrupted trial is never silently submitted. Delete recording removes private audio/text, retaining only a minimal source-origin tombstone to prevent automatic reimport. It never deletes source-folder originals or public/Drive exports. Processing and deletion are serialized against the worker lease. An uncertain provider request requires explicit approval before another attempt because remote charges cannot be rolled back.

Supported file finalization, size/duration budgets, snapshot hashes, queue idempotency and account/folder checks reduce accidental duplicate uploads. They are not a guarantee of perfect speech recognition, continuous OEM background delivery, malicious-codec resistance, or provider retention/deletion policy.

## Reporting and public-source hygiene

Do not put real recordings, transcripts, account details, API keys, tokens or signing material in a public issue. Security contact: [project maintainer](https://github.com/tomer-van-cohen). Ask for a private reporting channel before sending technical details; do not disclose them in a public issue. When the source repository is published, enable GitHub private vulnerability reporting. No unverified email address is listed.

Read [the publication review](docs/OPEN-SOURCE-REVIEW.md). `scripts/check-public-source.py` and the source packager use an explicit public-file boundary, redacted checks and a reviewed binary-asset inventory. CI adds Gitleaks scans of current files and full fetched history. These checks supplement review; they do not prove absence of every secret or vulnerability. Local SDKs, artifacts, session files, signing keys and the separately versioned publisher website are not publication inputs.


## Publication directory

Never initialize or publish Git from the development working folder. Create a separate allowlisted copy with `python3 scripts/stage-public-source.py --output ../Ramble-Android-public`. This leaves local artifacts, lab captures, SDK/build output, archives and the separately versioned publisher website in their original location. Review and scan the staging directory before any Git initialization. The staging command itself never initializes Git or publishes anything.
