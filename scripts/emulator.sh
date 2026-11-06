#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-$PWD/.tooling/android-sdk}"
export ANDROID_HOME="$SDK"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
if ! "$SDK/cmdline-tools/latest/bin/avdmanager" list avd | rg -q 'Name: Ramble_API_35'; then
  printf 'no\n' | "$SDK/cmdline-tools/latest/bin/avdmanager" create avd -n Ramble_API_35 -k 'system-images;android-35;default;arm64-v8a' --device pixel_6
fi
exec "$SDK/emulator/emulator" -avd Ramble_API_35 -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect "$@"
