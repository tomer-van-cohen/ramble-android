# Publisher website content — published pilot

The public pilot pages are now at https://tomer-van-cohen.github.io/ramble-flow-android-site/ (source: https://github.com/tomer-van-cohen/ramble-flow-android-site). The live HTML in that repository is authoritative; the text below is the earlier drafting reference. No changes were deployed to ramble.baby.

## Homepage

**Ramble for Android — Say it once. Your AI remembers.**

Ramble turns your new supported Samsung voice recordings and optional saved call recordings into plain text. It runs on your phone, uses your own Groq or OpenAI API key, and saves transcripts locally. You can also connect Google Drive so your existing AI assistants can find the generated text.

No Ramble account or Ramble transcription server is used by this standalone edition. An optional first-recording trial uses your microphone after you tap Record and grant access. It stops on Stop, the 30-second limit, Cancel or leaving the foreground; an unsaved cancelled/interrupted trial is not sent. Ramble does not record phone calls itself. Background timing depends on Android. This alpha has emulator validation; real Samsung compatibility is still being tested.

Links: **Download signed Android APK**, **Source code and license**, **Privacy**, **Usage information**. Point these to the actual release/source/policy pages when published.

## Privacy

Ramble standalone Android privacy information. Updated 2026-11-02.

- **Recording access:** You grant access to a folder through Android's folder picker. The app reads enabled recording sources within that grant. Microphone permission is optional for the first-recording trial only; contacts, call-log, Accessibility, device-admin and all-files permissions are not requested. Originals are never modified or deleted.
- **Transcription:** Eligible new finalized recordings are sent directly from your phone to your selected Groq/OpenAI API using your key. Large recordings are split into bounded chunks. These providers receive the audio and apply their own data/billing policies. No audio or transcript is sent to a Ramble server.
- **Local data:** The phone stores queue/history, encrypted API credentials, temporary working audio and transcripts. Keys are encrypted with Android Keystore. App backup/device-transfer is disabled. Generated transcripts are also written to Downloads or the folder you authorize.
- **Optional Google Drive:** If you authorize Google, the app creates a normal Ramble folder and uploads generated transcript text there. It requests only drive.file, limiting access to files created/used with this app. It does not scan your unrelated Drive files or upload source audio to Drive. Tokens are managed through Google Play services rather than a Ramble server.
- **AI assistants:** Connecting your AI assistant to Google Drive is a separate action under that assistant's permissions and policies. Copying our suggested prompt does not itself grant the assistant access or establish indexing.
- **No tracking:** This app contains no advertising, analytics or transcript/body-logging SDK. The developer does not receive your recording/transcript content through this app.
- **Control and deletion:** Pause processing, disable recording sources, disconnect Drive or revoke app permissions to stop future access. Disconnecting does not delete generated user-owned files. Uninstall removes app-private data, but exported Downloads/Drive files can remain; delete those copies yourself if desired. Provider retention follows the provider's policy.
- **Alpha limitations:** Unsupported/incomplete/undated files wait without upload. Uncertain provider outcomes may require retry approval to avoid duplicate billing. Existing baseline recordings are excluded. Device lifecycle/background behavior still needs Samsung hardware testing.

Add the real publisher support address chosen for the Google consent screen and link the chosen provider/Google privacy policies when publishing. Do not use another Ramble product's hosted-data policy as though it describes this standalone app.

## Usage information

This is the noncommercial source-available standalone edition of Ramble. It requires a supported Android device, a user-owned transcription key, internet access for provider requests, and optional Google authorization for Drive. Provider costs and quotas belong to the user's provider account. Ramble-hosted transcription remains Coming soon. Background work is best effort; this alpha is not a guaranteed recording/archive service. Keep original recordings and review important transcript details. The code license and third-party notices are supplied with the source; do not represent those as a grant to unrelated brands. A publisher can add separate terms after reviewing their distribution model.
