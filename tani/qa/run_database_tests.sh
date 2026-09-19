#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DB_URL="${TANI_DATABASE_URL:-postgresql://postgres:postgres@127.0.0.1:5432/postgres}"
PSQL=(psql "$DB_URL" -X -v ON_ERROR_STOP=1)

"${PSQL[@]}" -f "$ROOT/qa/database/bootstrap.sql"
"${PSQL[@]}" -f "$ROOT/supabase/20260918_product_variants_checkout_v4.sql"
"${PSQL[@]}" -f "$ROOT/supabase/20260919_fix_variant_rowtype_assignment.sql"
"${PSQL[@]}" -f "$ROOT/supabase/fix_recursive_order_rls.sql"
"${PSQL[@]}" -f "$ROOT/qa/database/checkout_regression.sql"
"${PSQL[@]}" -f "$ROOT/qa/database/release_regression.sql"
"${PSQL[@]}" -f "$ROOT/qa/database/concurrency_setup.sql"

run_case() {
  local case_name="$1"
  "${PSQL[@]}" -v case_name="$case_name" -f "$ROOT/qa/database/concurrent_checkout.sql" \
    >"${RUNNER_TEMP:-/tmp}/tani-$case_name.log" 2>&1
}

set +e
run_case idem-a & idem_a_pid=$!
run_case idem-b & idem_b_pid=$!
wait "$idem_a_pid"; idem_a_rc=$?
wait "$idem_b_pid"; idem_b_rc=$?
run_case stock-a & stock_a_pid=$!
run_case stock-b & stock_b_pid=$!
wait "$stock_a_pid"; stock_a_rc=$?
wait "$stock_b_pid"; stock_b_rc=$?
set -e

if (( idem_a_rc != 0 || idem_b_rc != 0 )); then
  cat "${RUNNER_TEMP:-/tmp}"/tani-idem-*.log
  echo "Concurrent idempotency calls must both succeed" >&2
  exit 1
fi
if ! { (( stock_a_rc == 0 && stock_b_rc != 0 )) || (( stock_a_rc != 0 && stock_b_rc == 0 )); }; then
  cat "${RUNNER_TEMP:-/tmp}"/tani-stock-*.log
  echo "Exactly one competing stock checkout must succeed" >&2
  exit 1
fi

"${PSQL[@]}" -f "$ROOT/qa/database/concurrency_assertions.sql"
echo "PASS: checkout database regression suite"
