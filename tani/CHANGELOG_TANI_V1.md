# tani v1

Build-ready maintenance version based on the latest full-product source snapshot plus the current GitHub `main` BuildConfig fix.

Implemented:
- Android Keystore-backed encrypted session token storage with legacy migration.
- Server-authoritative `quote_cart` RPC before final checkout confirmation.
- Checkout now displays server totals and blocks confirmation when quote warnings exist.
- Existing atomic/idempotent checkout remains authoritative for price, stock and delivery.
- Explicit order state-machine helper and unit tests.
- Verified HTTPS App Link scaffolding without breaking the currently functional recovery path before a real production domain exists.
- Stronger CI: wrapper validation, static QA, Android lint, unit tests, APK build and artifact upload.
- Updated full implementation specification and maintenance plan included in `docs/`.

Versioning rule: the next maintained archive is `tani v2`.
