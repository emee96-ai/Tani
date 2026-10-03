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

MODE="${1:-}"
[[ $# -le 1 && ( "$MODE" == "" || "$MODE" == "--ci" ) ]] || fail "only --ci is supported"

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

umask 077
TANI_GATE_DIR="$(mktemp -d)"
trap 'rm -rf "$TANI_GATE_DIR"' EXIT
keytool -list \
  -keystore "$STORE_FILE" \
  -storepass:env TANI_RELEASE_STORE_PASSWORD \
  -alias "$KEY_ALIAS" >/dev/null \
  || fail "release keystore or alias could not be verified"
keytool -certreq -keystore "$STORE_FILE" -storepass:env TANI_RELEASE_STORE_PASSWORD \
  -keypass:env TANI_RELEASE_KEY_PASSWORD -alias "$KEY_ALIAS" -file "$TANI_GATE_DIR/signing.csr" >/dev/null \
  || fail "a usable private signing key is required"

if [[ "$MODE" == "--ci" ]]; then
  [[ "$HOST" == "ci.example.com" ]] || fail "CI mode requires ci.example.com"
  echo "PASS: CI candidate gate; production readiness remains a separate required gate"
  exit 0
fi

python3 - <<'PY' || fail "production configuration is incomplete or invalid"
import os,re,urllib.parse,ipaddress
host=os.environ['TANI_APP_LINK_HOST'].lower()
assert all(re.fullmatch(r'[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?',p) for p in host.split('.')), 'invalid host labels'
assert host.split('.')[-1] not in {'invalid','test','example','localhost','local','internal'}, 'reserved host'
assert not any(host==d or host.endswith('.'+d) for d in ['example.com','example.org','example.net']), 'example host'
try: ipaddress.ip_address(host)
except ValueError: pass
else: raise AssertionError('IP address is not an App Link host')
for key,previous_key in [('TANI_VERSION_CODE','TANI_PREVIOUS_VERSION_CODE'),('TANI_ADMIN_VERSION_CODE','TANI_PREVIOUS_ADMIN_VERSION_CODE')]:
 value=os.environ.get(key,'')
 assert re.fullmatch(r'[1-9][0-9]*',value) and 7<=int(value)<=2100000000, key+' must be 7..2100000000'
 previous=os.environ.get(previous_key,'')
 assert re.fullmatch(r'0|[1-9][0-9]*',previous) and int(previous)<=2100000000, previous_key+' must be the latest published code, or 0 for the first release'
 assert int(value)>int(previous), key+' must exceed '+previous_key
for key in ['TANI_TERMS_URL','TANI_PRIVACY_URL','TANI_ACCOUNT_DELETION_URL','TANI_RESET_REDIRECT']:
 p=urllib.parse.urlparse(os.environ.get(key,''))
 assert p.scheme=='https' and p.hostname==host and not p.username and not p.password and p.port in {None,443} and not p.fragment, key+' must use the verified HTTPS host'
sender=os.environ.get('TANI_FIREBASE_SENDER_ID','')
assert re.fullmatch(r'[0-9]{6,20}',sender), 'Firebase sender ID is missing'
app=os.environ.get('TANI_FIREBASE_APPLICATION_ID','')
assert re.fullmatch(r'1:'+re.escape(sender)+r':android:[0-9a-fA-F]+',app), 'Firebase application ID must match its sender'
assert re.fullmatch(r'AIza[A-Za-z0-9_-]{35}',os.environ.get('TANI_FIREBASE_API_KEY','')), 'Firebase Android API key is missing'
assert re.fullmatch(r'[a-z][a-z0-9-]{4,61}[a-z0-9]',os.environ.get('TANI_FIREBASE_PROJECT_ID','')), 'Firebase project ID is missing'
assert os.environ.get('TANI_SUPABASE_SERVICE_KEY',''), 'server-only launch-gate credential is missing'
PY

for PAGE in "$REDIRECT" "$TANI_TERMS_URL" "$TANI_PRIVACY_URL" "$TANI_ACCOUNT_DELETION_URL"; do
  curl -fsS --proto '=https' --connect-timeout 10 --max-time 20 --max-redirs 3 -L \
    -o "$TANI_GATE_DIR/page" -w '%{http_code}\n%{url_effective}' "$PAGE" > "$TANI_GATE_DIR/page-result" \
    || fail "public launch page is unavailable"
  python3 - "$TANI_GATE_DIR/page-result" <<'PY' || fail "public page returned an unexpected response or host"
import os,sys,urllib.parse
lines=open(sys.argv[1]).read().splitlines()
assert lines[0]=='200' and urllib.parse.urlparse(lines[1]).hostname==os.environ['TANI_APP_LINK_HOST'].lower()
PY
done

if [[ -n "${TANI_APP_SIGNING_SHA256:-}" ]]; then
  export TANI_EXPECTED_SHA256="$TANI_APP_SIGNING_SHA256"
else
  keytool -exportcert -keystore "$STORE_FILE" -storepass:env TANI_RELEASE_STORE_PASSWORD -alias "$KEY_ALIAS" \
    -file "$TANI_GATE_DIR/signing.der" >/dev/null
  export TANI_EXPECTED_SHA256="$(openssl x509 -inform DER -in "$TANI_GATE_DIR/signing.der" -noout -fingerprint -sha256 | cut -d= -f2)"
fi
curl -fsS --proto '=https' --connect-timeout 10 --max-time 20 \
  "https://$HOST/.well-known/assetlinks.json" -o "$TANI_GATE_DIR/assetlinks.json" || fail "assetlinks.json is unavailable"
python3 - "$TANI_GATE_DIR/assetlinks.json" <<'PY' || fail "App Link certificate or package association is missing"
import json,os,re,sys
cert=os.environ['TANI_EXPECTED_SHA256'].replace(':','').upper()
assert re.fullmatch(r'[0-9A-F]{64}',cert), 'invalid signing certificate fingerprint'
assert any('delegate_permission/common.handle_all_urls' in row.get('relation',[]) and row.get('target',{}).get('namespace')=='android_app' and row['target'].get('package_name')=='com.tani.app' and cert in [x.replace(':','').upper() for x in row['target'].get('sha256_cert_fingerprints',[])] for row in json.load(open(sys.argv[1])))
PY

printf 'apikey: %s\nAuthorization: Bearer %s\nContent-Type: application/json\n' \
  "$TANI_SUPABASE_SERVICE_KEY" "$TANI_SUPABASE_SERVICE_KEY" > "$TANI_GATE_DIR/headers"
curl -fsS --proto '=https' --connect-timeout 10 --max-time 20 \
  --header @"$TANI_GATE_DIR/headers" --data '{}' \
  https://sihttimibjzoahvwuwbm.supabase.co/rest/v1/rpc/maintenance_launch_gate \
  -o "$TANI_GATE_DIR/gate.json" || fail "server launch readiness could not be verified"
python3 - "$TANI_GATE_DIR/gate.json" <<'PY' || fail "server launch requirements are not approved"
import json,sys
value=json.load(open(sys.argv[1]));checks=value.get('checks',{})
required={'active_admin','merchant_records_verified','live_catalog','worker_and_push','queues_healthy','launch_evidence'}
assert value.get('ready') is True and all(checks.get(k) is True for k in required), 'launch gate is not ready'
PY

echo "PASS: production release gate"
