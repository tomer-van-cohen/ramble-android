# Contributing

Read docs/PRODUCT.md, docs/TECHNICAL-SPEC.md and design/RAMBLE-FLOW-DESIGN.md first. Preserve the existing Ramble visual system and the standalone, new-recordings-only contract. The commercial provider stays unavailable; no backend or billing belongs in this edition.

Use JDK 17, Android SDK 36 and the checked-in Gradle wrapper. Run `./gradlew testDebugUnitTest lintDebug assembleDebug` and device tests with `./gradlew connectedDebugAndroidTest`. Do not claim Samsung recording completion, call recording or battery behavior is validated by an emulator test.

Keep fixtures synthetic. Never commit API keys, signing keys, recordings, transcripts, tokens, account information or machine-specific local.properties. Changes to queue states need crash/retry tests. Any automatic provider retry must state how it avoids ambiguous repeat charges. Source originals must remain untouched.

Application code is distributed under PolyForm Noncommercial 1.0.0. Preserve LICENSE, NOTICE and upstream third-party notices. Do not label this noncommercial distribution as MIT or imply a requirement to publish modifications that the selected license does not contain.
