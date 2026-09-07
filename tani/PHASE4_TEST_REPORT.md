# TANI Phase 4 — Merchant Operations Test Report

## Implemented from the full TANI specification
- Merchant onboarding: phone verification flow, identity document, business/store/category/delivery data, merchant policy acceptance, review states.
- Verification workflow: pending, changes_requested, approved, rejected, suspended.
- Approval creates the seller, public store, delivery settings and upgrades the profile role to seller.
- Store management: profile, logo, cover, description, contact, operating status and visibility.
- Product management: create/edit/delete-or-deactivate, category, images, variants, price, stock and visibility.
- Merchant order management using the protected order state machine from Phase 3.
- Merchant dashboard: orders, delivered/cancelled, GMV, customers, repeat customers, product views, conversion, completion/cancellation rates, rating, active products, response time and best sellers.
- Private identity storage and public store/product media storage with owner policies.

## Static checks
- Android resource XML + manifest: PASS (26 XML files parsed successfully).
- Android R.id references: PASS (0 missing IDs).
- Kotlin delimiter scan: PASS (no unmatched braces/brackets/parentheses).
- Admin JavaScript syntax: PASS (`node --check`).
- Kotlin/Android resource references: PASS (no missing app-owned IDs/layouts/drawables).
- SQL dependency scan: PASS (all tables and RPCs referenced by Android/admin code have definitions in the bundled SQL chain).
- ZIP integrity: checked after packaging.

## Supabase checks
- Direct merchant-profile insert/update by authenticated users: blocked.
- Direct seller creation/update by authenticated users: blocked.
- Merchant application RPC: authenticated only.
- Admin review RPC: authenticated entry point with explicit admin check.
- Merchant dashboard RPC: authenticated entry point with explicit approved-seller ownership check.
- Product/store/delivery writes remain RLS-restricted to approved merchant ownership.
- Merchant identity bucket is private; identity document must exist in Storage before application submission and approval.
- Product image upload paths are tied to user + owned product.

## Environment limitations
- Gradle compilation could not run in this environment because the Gradle 8.9 distribution cannot be downloaded from services.gradle.org.
- The live project currently has zero phone-confirmed Auth users. The app implements Supabase phone-change OTP verification, but real SMS delivery requires Phone Auth plus an SMS provider configured in Supabase.


## Delivery-package repair verification
- Added `phase0_full_product_foundation.sql` because the original ZIP omitted tables used by Phase 2/3/4.
- Added `phase4_merchant_operations.sql`; the original ZIP contained Phase 4 application code but not its reproducible database migration.
- `schema.sql` now defines `current_user_role()` before Phase 1 policies reference it.
- Direct seller/merchant verification writes are revoked from client roles.
- Gradle wrapper JAR is present; wrapper configuration is Gradle 8.9.
- Java core-library desugaring is enabled for `java.time` with minSdk 24.
