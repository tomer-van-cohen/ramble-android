#!/usr/bin/env bash
# Incremental local UI update. Preserves app data; no test suites, release build or cloud writes.
set -euo pipefail
cd "$(dirname "$0")/.."
RAMBLE_PREVIEW_SERIAL="${1:-emulator-5554}"
RAMBLE_PREVIEW_SDK="${ANDROID_HOME:-$PWD/.tooling/android-sdk}"
RAMBLE_PREVIEW_ADB="$RAMBLE_PREVIEW_SDK/platform-tools/adb"
RAMBLE_PREVIEW_HARDWARE="$("$RAMBLE_PREVIEW_ADB" -s "$RAMBLE_PREVIEW_SERIAL" shell getprop ro.hardware | tr -d '\r')"
case "$RAMBLE_PREVIEW_HARDWARE" in
  ranchu|goldfish) ;;
  *) echo 'UI preview requires a local Android emulator.' >&2; exit 1 ;;
esac
if [[ -z "${JAVA_HOME:-}" && -d /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ]]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
fi
./gradlew :app:assembleDebug --offline --console=plain
"$RAMBLE_PREVIEW_ADB" -s "$RAMBLE_PREVIEW_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
"$RAMBLE_PREVIEW_ADB" -s "$RAMBLE_PREVIEW_SERIAL" shell am start -W -n io.github.tomervancohen.rambleflow/.MainActivity
