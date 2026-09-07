#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

printf '== Tani pre-build QA ==\n'
./qa/run_static_checks.sh

printf '\n== Android debug build ==\n'
chmod +x gradlew
./gradlew assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK" ]]; then
  echo "Build completed but APK was not found at $APK" >&2
  exit 1
fi
mkdir -p release
cp -f "$APK" release/Tani-debug.apk
sha256sum release/Tani-debug.apk | tee release/Tani-debug.apk.sha256
printf '\nAPK ready: %s\n' "$ROOT/release/Tani-debug.apk"
