#!/usr/bin/env bash
# Google-enabled emulator for real OAuth checks. No credentials or consent are automated.
set -euo pipefail
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-$PWD/.tooling/android-sdk}"
export ANDROID_HOME="$SDK"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
IMAGE='system-images;android-35;google_apis_playstore;arm64-v8a'
if [ ! -f "$SDK/system-images/android-35/google_apis_playstore/arm64-v8a/package.xml" ]; then
  echo "Install the official Google Play image with sdkmanager first: $IMAGE" >&2
  exit 1
fi
if ! "$SDK/cmdline-tools/latest/bin/avdmanager" list avd | rg -q 'Name: Ramble_Google_API_35'; then
  printf 'no\n' | "$SDK/cmdline-tools/latest/bin/avdmanager" create avd -n Ramble_Google_API_35 -k "$IMAGE" --device pixel_6
fi
exec "$SDK/emulator/emulator" -avd Ramble_Google_API_35 -port 5556 -no-snapshot -no-boot-anim -gpu swiftshader_indirect "$@"
