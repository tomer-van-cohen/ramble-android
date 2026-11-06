# Live Google Drive test and beta handoff

## Established

- Publisher Drive API, Android release OAuth client and drive.file-only scope are registered.
- Pilot homepage/privacy/terms are live on GitHub Pages and saved in Google Branding.
- Google OAuth is now In production. Tester email allowlisting is no longer required. Keep all user credentials private.
- 27 JVM and 13 native emulator tests pass. Four Drive HTTP-interceptor cases cover reconnect, failed folder lookup, account isolation and multipart/idempotent upload. They do not prove Google consent or actual remote storage.
- v0.2.1 fixes reconnecting a known Drive folder and preserves failed pending-folder lookup errors.

## Live test — completed November 2, 2026

The owner completed the real Google sign-in/consent and provider-key setup in the release-signed app on a Google Play API 35 emulator. A new 7.081-second synthetic AAC/M4A was copied into the granted Voice Recorder source after baseline. The real app detected it, waited for repeated completion proof, transcribed through Groq, saved one 368-byte Downloads transcript and uploaded the same text to the app-created normal Drive folder. A separate Google Drive connector read the remote text and an exact comparison with the phone copy passed. Relaunching the app for another scan kept one local file and one remote file with unchanged remote modification time.

Scans were accelerated through normal app startup rather than waiting for the full 15-minute schedule. This establishes live provider/OAuth/local export/Drive export/agent-connector retrieval for this synthetic recording. It does not establish idle Samsung scheduling, real Samsung pause/stop behavior, all accounts/providers, separate ChatGPT/Claude connector behavior, or the provider's external billing ledger. Credentials were entered by the owner in the app/Google UI and were not read or logged by the agent. No personal audio was used. Private IDs and detailed evidence remain in ignored artifacts.

Reproduction:

1. Start `scripts/google-emulator.sh`. It uses a Google Play image on emulator-5556, separate from the AOSP test emulator. A Google account is entered only in Google's UI. Never pass passwords, codes, provider keys or tokens as shell arguments, in chat, in logs or screenshots.
2. Install the actual locally release-signed v0.2.1 APK. Its certificate matches the publisher Android client. Do not substitute an unregistered Debug signature for the OAuth test.
3. Sign into the allowed Google account and enter a test Groq/OpenAI key in the app. Complete the real Android folder grant and Google consent. Create an empty `Recordings/Voice Recorder` folder before setup if the emulator has no recorder. No fake token can establish this step.
4. After the app's baseline is complete, add a short synthetic AAC/M4A with a current recording date. Run normal scan/worker behavior; use Check now twice more than 60 seconds apart only to shorten the test. Do not mark the recording queued manually.
5. Verify the actual provider result is saved locally and the same text is present as one file in the app-created normal My Drive folder. Open the actual Drive file URL. Record only opaque test IDs/outcomes, not tokens, keys or account identifiers.
6. Run another scan/worker and check that there is still one remote file and no new transcription request for known completed work. Disconnect/reconnect Drive and verify existing folder reuse and resumed export. Test Drive export retry without another provider call.
7. Give an already-supported AI assistant its separate Google Drive connection, paste the generated Hebrew/English prompt, ask about the synthetic recording and verify it cites the actual file. This checks connector access/indexing separately from upload.

## Before sending a limited beta

- Finish the live test above or explicitly label the release as testing that unproven path.
- Send directly to beta testers; OAuth is In production and no Google email allowlist is required. Google reports that data-access verification is not required for drive.file. Consent branding remains unverified and is not currently shown; this is separate from audience availability. Do not claim Google branding or Play Store approval.
- Send the signed APK, SHA256 and TESTER-QUICKSTART.txt. No Android developer mode, ADB or computer is needed on a real Samsung.
- Have at least one Samsung tester verify native new recordings, pause/resume (no early upload), screen lock/background delivery and optional supported call recordings. A Google emulator cannot certify Samsung lifecycle or battery behavior.
- Collect only model/software, time-to-result, status and redacted errors. Do not ask for personal audio, transcripts, keys or tokens.

## Actual evidence boundary

The short synthetic live path has passed. Repeat with Samsung hardware and additional beta accounts before broad compatibility or timing claims. Live Drive disconnect/reconnect remains separate from the mocked reconnect regression test.
