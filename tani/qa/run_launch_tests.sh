#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
: "${TANI_DATABASE_URL:?Set an isolated regression database URL}"
PSQL=(psql "$TANI_DATABASE_URL" -X -v ON_ERROR_STOP=1)
DB_NAME="$("${PSQL[@]}" -Atc 'select current_database()')"
if [[ "$DB_NAME" != tani_launch_regression* ]]; then
  echo 'Refusing to load a test baseline outside a tani_launch_regression database' >&2
  exit 1
fi
if [[ "$("${PSQL[@]}" -Atc "select count(*) from pg_tables where schemaname='public'")" != 0 ]]; then
  echo 'The regression database must be fresh and empty' >&2
  exit 1
fi
"${PSQL[@]}" -f "$ROOT/qa/database/live_auth_bootstrap.sql"
"${PSQL[@]}" -f "$ROOT/qa/database/live_schema_baseline.sql"
for migration in "$ROOT"/supabase/migrations/*.sql; do
  "${PSQL[@]}" -f "$migration"
done
"${PSQL[@]}" -f "$ROOT/qa/database/launch_security_regression.sql"
"${PSQL[@]}" -f "$ROOT/qa/database/launch_commerce_regression.sql"
"${PSQL[@]}" -f "$ROOT/qa/database/launch_worker_regression.sql"
"${PSQL[@]}" -f "$ROOT/qa/database/launch_merchant_verification_regression.sql"
echo 'PASS: full-schema launch regression suite'
