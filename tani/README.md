# تاني | Tani — Full Product

تاني منصة سوق سودانية متعددة التجار، مبنية Android/Kotlin/XML مع Supabase PostgreSQL/Auth/Storage وWeb Admin. التنفيذ الحالي يغطي مراحل المنتج الكامل 1–8 حسب `TANI_Full_Product_Implementation_Spec`، مع كوستي كسوق الإطلاق الافتراضي.

## Architecture
- Android: Kotlin + XML، طبقات UI/Data/Repositories مستقلة.
- Backend: Supabase Auth + PostgreSQL + RLS/RBAC + Storage + RPC workflows.
- Admin: Web dashboard يستخدم Publishable key + authenticated RLS فقط.
- Low-bandwidth: cache محلي لنتائج discovery/recommendations، وواجهات بسيطة منخفضة التعقيد.
- Replaceable integrations: Notification Gateway وPayment/Delivery abstractions بدون ربط منطق الطلب بمزود خارجي واحد.

## Product coverage
### Phase 1 — Foundation
Auth، profiles، design system، Storage، analytics، error monitoring، Admin foundation، RLS/RBAC.

### Phase 2 — Marketplace Core
Categories، Home، ranked discovery، Products، Stores، product/store details، public-safe views.

### Phase 3 — Commerce
Persistent cart، saved addresses، multi-merchant checkout، Order Group، per-merchant delivery fees، COD، stock reservation/restore، order state machine، order history.

### Phase 4 — Merchant Operations
Phone verification، private identity docs، merchant approval workflow، store/delivery/product/inventory/order management، merchant dashboard.

### Phase 5 — Trust & Support
Delivered-order reviews، merchant/order reviews، merchant trust score، complaints، support tickets/messages، review moderation، audit trail.

### Phase 6 — Growth
Favorites، in-app notifications/preferences، referrals، sharing، banners/campaigns، merchant metrics، stock/order/account notifications.

### Phase 7 — Monetization
Configurable subscription plans/entitlements، subscription requests، fee rules/quotes، Featured requests، sponsored labeling (`ممول`)، ad campaigns، payment-method abstraction. COD is the only active payment method by default; no unapproved fee/commission is invented.

### Phase 8 — Scale
Kosti city gate، delivery-provider abstraction، city-scoped ranked search، personalized recommendations، inventory health، weekly marketplace KPIs، operational alerts، high-value indexes، low-bandwidth fallback.

## Security highlights
- All public tables in the live project have RLS enabled.
- Merchant identity documents are stored in the private `merchant-private` bucket.
- No Service Role key is shipped in Android/Admin.
- `allowBackup=false` and cleartext HTTP is disabled in Android.
- Account deletion soft-deactivates public merchant assets and anonymizes the profile while preserving the minimum operational/audit history.
- Sensitive workflows use protected RPCs and server-side ownership/role checks.

## Fresh Supabase deployment order
Run in this exact order:
1. `supabase/schema.sql`
2. `supabase/phase0_full_product_foundation.sql`
3. `supabase/phase1_analytics_admin.sql`
4. `supabase/phase2_marketplace_core.sql`
5. `supabase/phase3_commerce.sql`
6. `supabase/phase4_merchant_operations.sql`
7. `supabase/phase5_trust_support.sql`
8. `supabase/phase6_growth.sql`
9. `supabase/phase7_monetization.sql`
10. `supabase/phase8_scale.sql`
11. `supabase/final_security_hardening.sql`
12. Configure Auth redirect URL / Storage as documented; optional demo seed only after required Auth users exist.

## Authentication setup
For password recovery add `tani://auth/reset` to Supabase **Authentication → URL Configuration → Redirect URLs**. See `supabase/AUTH_SETUP.md`.

## Build
From the project root:
```bash
chmod +x gradlew
./gradlew assembleDebug
```
APK output after a successful build:
`app/build/outputs/apk/debug/app-debug.apk`

The Gradle wrapper is pinned to Gradle 8.9 and its JAR is bundled. If the first wrapper download is blocked by network/DNS, run the build in the configured GitHub Codespace/CI environment.

## QA
Run:
```bash
./qa/run_static_checks.sh
```
This checks wrapper integrity, Admin JS, XML/resources, internal imports, SQL table/RPC coverage, privileged-secret patterns, unfinished code, manifest security, SQL delimiter structure and required launch documents.

See `FINAL_QA_REPORT.md` and `docs/RELEASE_CHECKLIST.md` before a production release.

## Legal / launch note
The policy documents under `docs/` are product-ready drafts based on the implementation specification, but must be reviewed for Sudan/local law and business terms before commercial launch. Production also requires real approved supply, tested delivery operations, staffed support, backup configuration and a successful APK/device smoke test.


## tani v1 maintenance
See `CHANGELOG_TANI_V1.md` and `docs/TANI_Maintenance_Modernization_Plan.md`.
