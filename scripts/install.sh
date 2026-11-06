#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
ADB="${ANDROID_HOME:-$PWD/.tooling/android-sdk}/platform-tools/adb"
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
if [[ ! -f "$APK" ]]; then echo 'Build the APK first with scripts/build.sh' >&2; exit 1; fi
"$ADB" install -r "$APK"
"$ADB" shell am start -n io.github.tomervancohen.rambleflow/.MainActivity
