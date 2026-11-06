# Ramble Android — design alignment, 2026-11-02

Version: 0.2.4-alpha (versionCode 6). Reference: `design/prototype.html`, last modified 2026-11-02, plus the owner's spoken corrections during this implementation. This is the standalone Android app, separate from the iPhone research and Ramble Desktop projects.

## Implemented

- Primary screen actions are in a Scaffold bottom bar, separate from scrolling content. This covers onboarding, provider/key/help, permission, optional calls, Drive, assistant instructions, quick-record guide/check, ready, output destination, Home, and saved/error transcript actions. The current settings source controls remain inline navigation/switches.
- Main action remains green/ink. Secondary actions now use smaller underlined text with a minimum 48dp touch area, per the owner's correction to the original outlined-button design.
- Each screen gets its own scroll state. The setup stepper is fixed below the app bar, with four stage names. A missing call source has a usable primary Continue instead of a disabled primary button.
- Permission CTA: **מתן גישה להקלטות** / **Allow recording access**. Copy: **לחצו על ״מתן גישה להקלטות״, ואז אשרו את התיקייה שנפתחה.** The Android picker still opens at `primary:Recordings`; its own Use this folder and Allow controls are unchanged. Choosing the Voice Recorder child works for voice only and cannot authorize the sibling Call folder. The app no longer claims an unseen sibling is definitely nonexistent during onboarding.
- Welcome precedes provider selection. It uses the designer's original `WAVE_LINES`, `waveD`, SVG and `drawWave` verbatim, bundled locally in `app/src/main/assets/ramble-wave.html`. Only its page-sizing wrapper differs to give WebView a concrete nonzero height. No remote fonts/assets, network loads, file/content access, DOM storage or JavaScript/native bridge. The stock reduced-motion check in the original code is retained. It is a decorative component, excluded from accessibility navigation, destroyed when leaving the screen.
- API key has an explicit Paste control (reads clipboard only on that tap), masked input, debounced audio-free validation after entry, success/invalid/connection states, provider-site primary action when empty, and a Continue/Save action after validation. No key is added to saved Compose state, logs, screenshots or documentation. Existing encrypted keys remain usable.
- Assistant instructions include the designer's simplified ChatGPT/Claude/Gemini marks and Meta's official Muse app icon. They identify compatible external assistants, not a new integration. Hebrew text is explicitly right-aligned/RTL and English left-aligned/LTR, independent of device UI language. Copy confirmation explains what to do next. Settings can open the exact Drive folder.
- Side-button guide has a physical-right-side-key illustration and numbered instructions, plus a self-check after opening Settings. No claim that Ramble changes the system binding.
- Ready screen offers first recording as its primary action and Home as its secondary. Starting recording completes setup before opening the existing Samsung recorder, so returning opens Home. The actual Samsung app is still external.
- Home has an icon/status card, date-grouped transcript rows with state chips, and fixed record/repair/resume/check actions. Transcript details show date/source and save state above the text, with Copy and Share fixed below long text. Retry/possible duplicate-charge behavior is preserved.
- Display name and Android launcher label are **Ramble**. Google OAuth App name was also saved as Ramble. Package ID, private preferences, signing certificate, database and queue identity are unchanged.
- New app-owned Drive folders are **Ramble Transcripts**. Existing connected folder IDs are reused; metadata reads recover the actual existing name. No name-only lookup or extra Drive scope was added. New local exports use `Download/Ramble`. Recovery also checks the legacy destination so an interrupted export is reused rather than duplicated.

## Validation

Build gate: JVM tests, Android lint, debug + instrumentation APK build, release APK build, same-certificate signing verification. 27 JVM and 22 Android instrumentation tests (49 total) passed on API 35. Instrumentation includes 200% font continuation without scrolling, visible permission CTA, Welcome before provider, actual WebView wave paint, assistant marks/language controls, and new Drive folder name via isolated HTTP fixtures. No paid provider calls were made by these tests.

Original wave code was independently compared against the prototype as exact substrings (constants/SVG/path generator and animation loop). Synthetic emulator screenshots are in ignored `artifacts/design-*.png`; no tester video, owner transcript or credential is included in source packages. The previous live Groq→local→Drive E2E remains evidence for **0.2.1-alpha**, not a new live-provider test of this design update. Samsung hardware/locked background latency remains unverified.

## Still open — do not conflate with the validated design update

The owner subsequently requested a **first-recording success before the optional Side-button guide**, ideally without leaving Ramble. That revised sequence is **not implemented in 0.2.4**. The existing prototype sequence and external Samsung recorder are retained until the recording mechanism is resolved. A clarification is pending: native Ramble recording (new microphone permission) versus external Samsung recorder. Do not silently add a recorder/microphone permission or promise an OEM API.

Samsung documents a recording widget and Android documents third-party widget hosting; their combination is only a hypothesis until the actual Samsung provider is tested. The Google emulator cannot prove Samsung widget availability, PendingIntent behavior, foreground service permission behavior or non-navigation on tap. No Samsung background-start API has been verified. See the research note below.

## Samsung and Pixel research references

- Samsung widget: https://www.samsung.com/ae/support/mobile-devices/how-to-record-a-voice-note-from-the-lock-screen/
- Android AppWidgetHost and user-approved widget binding: https://developer.android.com/develop/ui/views/appwidgets/host
- Standard record intent launches another app and can return an audio URI: https://developer.android.com/reference/android/provider/MediaStore.Audio.Media#RECORD_SOUND_ACTION
- Samsung emulator skins explicitly omit One UI: https://developer.samsung.com/galaxy-emulator-skin/guide.html
- Remote real Samsung devices: https://developer.samsung.com/remote-test-lab

Next Samsung experiment: on a real Galaxy, enumerate Recorder's installed widget providers, identify whether an ordinary AppWidgetHost can bind one with user approval, display it, tap Start/Stop, observe whether Recorder takes foreground, confirm a finalized audio file appears in the granted tree, then run the ordinary transcription pipeline once. A lock-screen widget may be proprietary rather than a normal hostable AppWidget; do not assume it is embeddable. A small Now Bar indicator alone proves neither third-party control nor callable Start/Stop.

Pixel's latest Recorder has **Access recordings in other apps**, documented for Android12+ and enabled by default, exposing copies while originals remain private: https://support.google.com/pixelphone/answer/16267668?hl=en . This provides a credible input path, but current code still maps Samsung source children and launches Samsung's package. Pixel support needs provider/path discovery, a matching recorder launch and device proof. Generic emulator tests validate the shared Android engine, not OEM integration. Do not advertise Pixel as out-of-the-box yet.

## Scope boundaries

No Railway deployment, service restart, server configuration, or hosted Ramble feature was changed. No Android microphone, call log, contacts, accessibility, all-files or widget-host permission was added. iPhone research lives separately in `the separate hosted project-iphone-research`; Desktop analysis did not change either Desktop repository.
