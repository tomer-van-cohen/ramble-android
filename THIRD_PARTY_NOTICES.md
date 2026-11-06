# Third-party notices

The Ramble Android application code uses PolyForm Noncommercial 1.0.0, copyright 2026 Tomer Cohen. See LICENSE and NOTICE. This does not change the licenses of upstream dependencies or the separate Ramble CLI. Ramble logo and brand assets are reused from the owner's existing project; a code license does not grant a third party trademark rights.

AndroidX/Jetpack, Android Gradle Plugin, Kotlin and kotlinx.coroutines: Apache License 2.0. OkHttp: Apache License 2.0. JUnit: Eclipse Public License 1.0. Google Play services is subject to Google's SDK terms; its use is optional at runtime for local-only processing. Android SDK/system images are development tools under their own terms, not bundled in an APK or source release.

No audio codec binary, speech model, FFmpeg executable or personal user audio is redistributed. The three explicitly inventoried short speech/silence WAV files under androidTest are synthetic test fixtures. The app uses Android platform media codecs and remote transcription APIs. Provider/Google service terms and usage limits apply to the user's accounts.

Build dependency reports can be generated with `./gradlew :app:dependencies`. Before public distribution, review merged third-party notices and the exact dependency graph for the pinned release.

## Assistant identity marks

Assistant names and artwork retain their owners' rights and are not covered by the application code license or a grant of trademark rights. They identify examples, not integrations or endorsement. See [asset provenance](docs/ASSISTANT-ARTWORK.md) for the current files. The existing artwork remains unchanged by owner decision.

The welcome wave's geometry and timing are ported from the supplied prototype to native Compose Canvas. The original green color, paired curves, left-hand swing, narrowing ripples and reveal timing are retained; no embedded browser or JavaScript/native bridge is used.
