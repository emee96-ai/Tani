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

## Supabase database: source of truth
The live project schema is the 2026-09-30 snapshot in `qa/database/live_schema_baseline.sql` plus every file in `supabase/migrations/`, applied in filename order. CI rebuilds exactly this in `qa/run_launch_tests.sh`.

- **New database changes** go only into `supabase/migrations/` with a `YYYYMMDDHHMMSS_name.sql` prefix.
- The top-level `supabase/*.sql` files (`schema.sql`, `phase0`–`phase8`, and the dated fixes) are the **historical** history that produced the baseline. They are already applied to the live project and must not be re-run against it.
- `live_schema_baseline.sql` is schema-only and meant for isolated test databases; it is not a production restore. Production recovery follows `docs/BACKUP_RECOVERY_RUNBOOK.md`.
- There is currently no verified script that builds a fresh production database from scratch. If one is needed (new region or staging), take a `supabase db dump` of the live schema and validate it in an isolated project first.

### Applying migrations to production
`supabase db push` does not work: the live project has about 95 dashboard-era versions with no local file. Never mark those as reverted. Apply each new file on its own and record it:

```bash
supabase db query --linked -f supabase/migrations/<version>_<name>.sql
supabase migration repair --status applied <version> --linked
```

### Scheduled upkeep
- `tani-launch-maintenance` (pg_cron, every minute) runs the maintenance worker.
- `tani-purge-old-records` (pg_cron, daily) deletes app errors after 90 days, app events after 180 days, read notifications after 180 days and pg_cron run history after 7 days. Audit logs are kept.
- GitHub workflows: `Tani Database Backup` (weekly), `Tani Supabase Keepalive` (daily, stops the free project from pausing) and `Tani Usage Report` (monthly, opens an issue above 80% of the free plan limits). Scheduled workflows only run from `main`, and GitHub turns them off after 60 days without commits in a public repository — re-enable them from the Actions tab if that happens.

### Release signing
The release key lives in `~/.tani-keys/tani-release.jks` (alias `tani`) with its passwords in `~/.gradle/gradle.properties`. Every future update must be signed with this key, and its SHA-256 is published in `https://emee96-ai.github.io/.well-known/assetlinks.json`. Keep two encrypted copies off the machine. Moving App Links to another domain needs an app update and a period where both hosts serve `assetlinks.json`.

## Authentication setup
For password recovery add `tani://auth/reset` to Supabase **Authentication → URL Configuration → Redirect URLs**. See `supabase/AUTH_SETUP.md`.

## Build
Requires JDK 17+ and the Android SDK (set `ANDROID_HOME`, or put `sdk.dir=...` in an uncommitted `local.properties`). From the project root:
```bash
chmod +x gradlew
./gradlew assembleDebug
```
The Supabase project is read from Gradle properties `TANI_SUPABASE_URL` and `TANI_SUPABASE_PUBLISHABLE_KEY` (default: the current live project). To build against staging:
```bash
./gradlew assembleDebug -PTANI_SUPABASE_URL=https://<staging>.supabase.co -PTANI_SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
```
The build rejects any non-HTTPS URL and any key that does not start with `sb_publishable_`. The admin web dashboard (`admin/app.js`) still has these values hardcoded.

APK output after a successful build:
`app/build/outputs/apk/debug/app-debug.apk`

The Gradle wrapper is pinned to Gradle 8.11.1 (with SHA-256 verification) and its JAR is bundled. If the first wrapper download is blocked by network/DNS, run the build in the configured GitHub Codespace/CI environment.

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
