# TANI Phase 3 Test Report

## Static Android checks
- XML/manifest files parsed: 26
- XML parse errors: 0
- Missing `R.id` references: 0
- Missing app layout references: 0

## Supabase transaction tests (rolled back)
- Multi-merchant checkout created 2 merchant orders from one Order Group.
- Calculated grand total: 450.00 for product totals + independent merchant delivery fees.
- Repeating checkout with the same idempotency key returned the same Order Group.
- Group cancellation restored stock and resulted in `cancelled`.
- Full state machine reached `delivered`; status history count was 6 (creation + 5 transitions).
- Delivered COD order and Order Group both reached payment status `paid`.
- After rollback, live counts remained 0 sellers / 0 products / 0 order groups / 0 orders / 0 order items.

## Security checks
- Anonymous role cannot execute checkout, cart-sync, status-transition, or group-cancel RPCs.
- Authenticated role can execute those RPCs.
- Authenticated clients cannot directly insert into orders/order_items/order_groups.
- Anonymous role cannot read customer addresses.

## Build
- Gradle wrapper JAR exists.
- Full Android build could not run in this environment because `services.gradle.org` cannot be resolved, so Gradle 8.9 cannot be downloaded.
