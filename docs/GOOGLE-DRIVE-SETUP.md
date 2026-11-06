# Google Drive setup for a publisher or self-builder

Local-only BYOK use needs no Google configuration. Optional Drive export uses Google Play services AuthorizationClient and the `drive.file` scope. No web-client secret or service-account key belongs in the source, APK, or native-app setup.

1. Create your own Google Cloud project and enable the Google Drive API.
2. Configure the OAuth consent audience, branding, policy links, and any required verification or testing access for your distribution.
3. Register an **Android** OAuth client for the application ID in `app/build.gradle.kts` and the certificate of the APK you install. Use `./gradlew signingReport` for debug builds, or `apksigner verify --print-certs` for a signed release. Debug, local release, and Play App Signing certificates differ.
4. Request only `https://www.googleapis.com/auth/drive.file`. Install on a device with supported Google Play services, sign into the intended Google account through Google's UI, and approve authorization.
5. Verify one Ramble folder and one generated text export, then retry and confirm no duplicate. Account switching, revoked access, or a missing folder must stop export instead of silently changing destination.
6. If desired, separately connect an AI assistant to that Google account and paste Ramble's generated instructions. Verify readback using a synthetic recording. An upload alone does not establish assistant indexing.

Keep publisher project IDs, client registrations, account emails, certificate inventories, device identifiers and consent screenshots in private operational records. Android client IDs and certificate fingerprints are public identifiers, not secrets, but they are unnecessary for someone building their own copy. Never include signing keys, API credentials or tokens.

## Folder settings

Drive settings provides Open Google Drive, Change folder, and Disconnect. The chooser lists folders already accessible under `drive.file`, supports pagination and can create a folder. It does not request access to the entire Drive. Selection validates account identity, folder type, trash status and permission to add files.

Changing the destination affects new transcripts. Existing exports stay where they are; queued work retains its original target and deterministic file ID. Disconnect does not delete user-owned Drive files.

## Troubleshooting

- Confirm the exact installed package/certificate registration before diagnosing an OAuth registration error. Do not create duplicate clients as a workaround.
- If Google Play services rejects AuthorizationClient before showing a consent resolution, update Play services through the official Play Store page and retry. A service API gate is not by itself proof of a certificate mismatch.
- Check device internet connectivity independently of Google sign-in. An offline API-key draft remains encrypted and is promoted only after validation succeeds.
- `DriveAuthorizationDiagnosticTest` is opt-in and reports only fixed status categories and resolution/token presence. It does not print credentials or launch consent. Other Drive fixture tests reset test state and belong on disposable emulators.

## Language

The app follows the phone or per-app language setting, with Hebrew and English UI. Assistant instructions initially follow the UI language; selecting a tab stores an explicit preference. Transcription language is independent and defaults to automatic detection.

References: [Google OAuth verification](https://support.google.com/cloud/answer/13463073), [Drive authorization scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth), [Android authorization](https://developers.google.com/identity/authorization/android).
