# Tani Release Status — 2026-08-31

Fixed in this release:
- Removed duplicate data models.
- Hardened Supabase REST error handling and session initialization.
- Signup now correctly handles email-confirmation mode.
- Added validation for auth, seller requests, products and checkout.
- MVP checkout rejects mixed-seller carts instead of creating inconsistent orders.
- Added complete Supabase schema, indexes, RLS policies, auth profile trigger and transactional place_order RPC.
- Added demo categories, three approved demo stores and six realistic test products.
- Added Gradle 8.7 wrapper configuration.

Supabase setup: run `supabase/schema.sql`, create the three Auth users named in `supabase/seed_demo.sql`, then run `supabase/seed_demo.sql`.

Verification: ZIP integrity passed. A full Android compile could not be executed here because this environment has no Android SDK/Gradle runtime.
