# Tani Full Product — Final QA Report

**Date:** 2026-09-06  
**Target:** Android full-product source + Supabase live backend + Web Admin.

## Automated static QA — PASS
Command: `./qa/run_static_checks.sh`

Passed checks:
- Gradle wrapper shell syntax.
- Bundled Gradle wrapper JAR integrity.
- Admin JavaScript syntax (`node --check`).
- 26 Android XML/Manifest files parse successfully.
- 138 Android `R.id` references resolve.
- Android app-owned resource references resolve.
- Internal Kotlin imports resolve to project sources/generated Android classes.
- 39 detected table/view dependencies are represented in local SQL.
- 19 detected RPC dependencies are represented in local SQL.
- SQL dollar-quote structural balance.
- No privileged Service Role/private-key pattern shipped in code/config.
- No TODO/FIXME/NotImplemented code markers in production Kotlin/Admin JS.
- No `.commit().commit()` regression.
- No runtime mutation `arguments = null` in fragments.
- Android backup disabled.
- Cleartext HTTP disabled.
- 11 required launch/legal/operations documents present.

## Kotlin parse signal — PASS with environment limitation
Standalone `kotlinc` was used only as a parser signal. It reported **0 `expecting` parser errors**. It cannot provide a valid Android type-check in this environment because Android/Ktor/Serialization dependencies and generated `R`/`BuildConfig` are not on its classpath.

## Live Supabase QA — PASS for implemented scope
Verified on project `sihttimibjzoahvwuwbm`:
- 22 selected Phase 5–8 advanced tables present.
- 12 selected advanced RPCs present.
- Public tables without RLS: **0**.
- Active launch city: **Kosti only**.
- Active payment method: **COD only**.
- Active delivery provider: **merchant delivery only**.
- `pg_trgm` moved out of `public` to the `extensions` schema.
- High-value FK indexes applied.
- Robust authenticated account soft-delete applied.
- Merchant-private Storage remains private; customer-facing image buckets have size/MIME restrictions.

## Security Advisor review
Fixed directly:
- Extension-in-public warning for `pg_trgm`.
- Public execution permissions on trigger-only helper functions.
- Anonymous access to account-deletion RPC.
- Duplicate indexes introduced by overlapping migrations.

Remaining advisor items include intentionally callable authenticated `SECURITY DEFINER` workflow RPCs that perform their own authentication/role/ownership validation, RLS-policy performance warnings, and Supabase Auth leaked-password protection being disabled. Leaked-password protection should be enabled in production Auth settings where available.

## Android Gradle compile — BLOCKED BY EXECUTION ENVIRONMENT
Attempted:
`./gradlew --version`

Result before compile:
`java.net.UnknownHostException: services.gradle.org`

The environment has no preinstalled Gradle 8.9 distribution and no Android SDK. Therefore `assembleDebug` cannot be truthfully marked PASS here. This is an infrastructure/network block, not an observed Android compiler failure.

## Required final build gate
In the existing configured GitHub Codespace, run:
`./scripts/build_apk.sh`

A release is build-verified only when that command finishes and produces `release/Tani-debug.apk`, followed by installation/smoke testing on a supported Android device.
