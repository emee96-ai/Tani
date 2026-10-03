#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

EXPECTED_PAGE_SIZE="${1:?Expected page size is required}"
EXPECTED_API_LEVEL="${2:?Expected Android API level is required}"
case "$EXPECTED_PAGE_SIZE" in
  4096|16384) ;;
  *) printf 'FAIL: unsupported expected page size %s\n' "$EXPECTED_PAGE_SIZE" >&2; exit 1 ;;
esac
[[ "$EXPECTED_API_LEVEL" =~ ^[0-9]+$ ]] || { printf 'FAIL: invalid API level\n' >&2; exit 1; }

DEVICE_PAGE_SIZE="$(adb shell getconf PAGE_SIZE | tr -d '\r')"
DEVICE_API_LEVEL="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
DEVICE_ABI="$(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
export DEVICE_PAGE_SIZE DEVICE_API_LEVEL DEVICE_ABI EXPECTED_PAGE_SIZE EXPECTED_API_LEVEL
python3 - <<'PY'
import json
import os
from pathlib import Path

page_size = int(os.environ['DEVICE_PAGE_SIZE'])
api_level = int(os.environ['DEVICE_API_LEVEL'])
expected_page_size = int(os.environ['EXPECTED_PAGE_SIZE'])
expected_api_level = int(os.environ['EXPECTED_API_LEVEL'])
if page_size != expected_page_size or api_level != expected_api_level:
    raise SystemExit(
        f'FAIL: device is API {api_level} with {page_size}-byte pages; '
        f'expected API {expected_api_level} with {expected_page_size}-byte pages'
    )
evidence = Path('app/build/outputs/androidTest-results/device-environment.json')
evidence.parent.mkdir(parents=True, exist_ok=True)
evidence.write_text(json.dumps({
    'api_level': api_level,
    'page_size_bytes': page_size,
    'abi': os.environ['DEVICE_ABI'],
    'verified_before_instrumentation': True,
}, indent=2) + '\n')
print(f'PASS: device API {api_level}, ABI {os.environ["DEVICE_ABI"]}, page_size={page_size}')
PY

./gradlew --no-daemon :app:connectedDebugAndroidTest --stacktrace
