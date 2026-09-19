#!/usr/bin/env bash
set -euo pipefail

HOST="${TANI_APP_LINK_HOST:-}"
REDIRECT="${TANI_RESET_REDIRECT:-}"
STORE_FILE="${TANI_RELEASE_STORE_FILE:-}"
STORE_PASSWORD="${TANI_RELEASE_STORE_PASSWORD:-}"
KEY_ALIAS="${TANI_RELEASE_KEY_ALIAS:-}"
KEY_PASSWORD="${TANI_RELEASE_KEY_PASSWORD:-}"

fail() {
  echo "RELEASE GATE FAILED: $*" >&2
  exit 1
}

[[ -n "$HOST" ]] || fail "TANI_APP_LINK_HOST is required"
[[ "$HOST" != "tani.invalid" ]] || fail "placeholder app-link host is forbidden"
[[ "$HOST" != "localhost" && "$HOST" != "127.0.0.1" ]] || fail "local app-link host is forbidden"
[[ "$HOST" != *.invalid ]] || fail ".invalid app-link host is forbidden"
[[ "$HOST" =~ ^[A-Za-z0-9.-]+\.[A-Za-z]{2,}$ ]] || fail "app-link host must be a real hostname"

EXPECTED_REDIRECT="https://${HOST}/auth/reset"
[[ "$REDIRECT" == "$EXPECTED_REDIRECT" ]] || fail "TANI_RESET_REDIRECT must equal $EXPECTED_REDIRECT"

[[ -n "$STORE_FILE" ]] || fail "release keystore path is required"
[[ -f "$STORE_FILE" ]] || fail "release keystore file does not exist"
[[ -n "$STORE_PASSWORD" ]] || fail "release store password is required"
[[ -n "$KEY_ALIAS" ]] || fail "release key alias is required"
[[ -n "$KEY_PASSWORD" ]] || fail "release key password is required"

keytool -list \
  -keystore "$STORE_FILE" \
  -storepass "$STORE_PASSWORD" \
  -alias "$KEY_ALIAS" >/dev/null \
  || fail "release keystore or alias could not be verified"

echo "PASS: production release gate"
