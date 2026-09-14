# Tani Production Repair Plan

**Baseline:** main @ cdf86c5  
**Started:** 2026-09-14  
**Rule:** no phase is complete until code, tests, CI, security verification, and smoke testing pass.

## P0 — Release blockers

- Fix recursive order/order-item RLS and verify customer, merchant, and unrelated-user isolation.
- Bind Fragment coroutines to the view lifecycle.
- Clear all Android Lint errors; classify remaining warnings.
- Make insecure custom-scheme password recovery debug-only.
- Add finite network timeouts to customer and admin clients.
- Disable cleartext traffic in both Android applications.
- Keep production release blocked until a real verified HTTPS App Link host is configured.

## P1 — Architecture and testability

- Split the 43 KB Repository by domain.
- Split SellerFragment into onboarding, catalog, orders, and analytics screens with ViewModels.
- Introduce repository interfaces, use cases, dependency injection, and typed UI state.
- Add unit, repository, RLS, concurrency, UI, and end-to-end tests.
- Separate development, staging, and production configuration.

## P2 — Performance and low-bandwidth UX

- Replace large ScrollView/LinearLayout lists with RecyclerView/Paging 3.
- Replace the manual image loader with a disk-backed, cancellable image pipeline.
- Add Room cache with expiry/versioning and explicit offline states.
- Move image decoding/compression off the main thread.
- Optimize RLS policies and missing foreign-key indexes after measuring live queries.
- Add Baseline Profiles and Macrobenchmark coverage.

## P3 — Product experience

### Customer
Guest browsing, real variant selection, reliable push notifications, clearer tracking, returns/refunds, saved searches, and resilient checkout recovery.

### Merchant
Dedicated navigation, instant order alerts, order search/filtering, per-variant stock/SKU, operating hours, customer messaging, and date-filtered analytics.

### Admin
Orders/customers/products/categories modules, case workspace, pagination/search/export, fine-grained RBAC, MFA, configuration management, and operational alerts.

## P4 — Release

- CI: static QA, lint, unit/database/security/UI tests, Debug and Release builds.
- Signed AAB, R8, dependency verification, changelog, rollback procedure.
- Real-device smoke tests on weak, interrupted, and offline networks.
- Production gate: zero Critical/High security defects and a passing end-to-end purchase flow.

## Execution status

- [x] Recursive order RLS fixed and verified live.
- [x] Fragment lifecycle repair prepared.
- [x] Lint API-level error repaired.
- [x] Network timeout and cleartext hardening prepared.
- [x] Production recovery restricted to verified HTTPS.
- [x] Guest browsing with protected checkout/account routes prepared.
- [x] CI verification for the P0 release-blocker repair.
- [x] Authoritative cart quotation deployed and verified live.
- [x] Product-details and search requests parallelized.
- [x] Image decoding moved off the main thread.
- [x] Marketplace cache expiry/versioning added with unit coverage.
- [ ] CI verification for this performance phase.
- [ ] Real-device smoke test.
