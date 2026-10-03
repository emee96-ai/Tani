# Tani Release Checklist — 3 October 2026

**NO-GO:** all eight CI jobs pass on the tested code/workflow checkpoint
bb66cad, including 30 instrumentation executions and measured 16 KB runtime.
Production configuration and genuine operational evidence remain incomplete.
The live gate snapshot at 2026-10-03 12:59:46 UTC is still `ready=false`.

## Verified technical work

- [x] Static QA, source secret scan, XML/resources and RPC dependency checks.
- [x] Debug customer/admin builds and compiled customer instrumentation APK.
- [x] 35 customer + 3 admin unit tests pass in both Debug and Release.
- [x] Signed, R8-minified candidate AABs for both applications and customer release APK.
- [x] APK signature v2/v3, AAB signatures and customer APK `zipalign -P 16` verified.
- [x] All six 64-bit native-library copies in Debug/Release APKs and customer AAB
      pass LOAD/RELRO 16 KB checks after the DataStore 1.2.1 update.
- [x] Four full-schema SQL suites pass with all eight launch migrations.
- [x] Server function strict typecheck and four security tests pass.
- [x] Real account-erasure evidence covers eight checks and fixture cleanup.
- [x] Production gate rejects missing, repeated, decreasing and malformed version codes.
- [x] Release CI Lint: zero Error/Fatal, 319 customer + 36 admin warnings; one admin Hint.
      Debug CI Lint: zero Error/Fatal, 318 customer + 36 admin warnings; one admin Hint.
- [x] PostgreSQL 17.11 concurrency regressions, full-schema suites, and all eight CI
      jobs pass on bb66cad (the tested code/workflow checkpoint).
- [x] Ten instrumentation tests on each of Android 11/API30, Android 15/API35
      with 16 KB pages, and Android 16/API36: 30 executions, zero failures/errors/skips.
- [x] 16 KB runtime smoke tests on x86_64, measured as 16384-byte pages before tests.
- [ ] Production-signed ARM64, weak-network checkout/recovery and complete user journeys.

CI generates a disposable signing key for each run with a two-day lifetime.
These candidates are for validation; production keys and configuration remain required.

## Supabase and production operations

- [x] Local migration versions match the eight applied production history entries,
      from `20260930213526` through `20261001062831`; do not apply them twice.
- [x] All 75 public tables have account-validity protection in addition to RLS.
- [x] Deployed worker and account-deletion functions match the restored source.
- [ ] Explicitly identified owner admin is active and tested; no guessed promotions.
- [ ] Approved merchants have genuine identity, current phone and policy evidence.
- [ ] Production catalog, prices, stock and delivery settings validated in Kosti.
- [ ] Auth leaked-password protection enabled and email settings verified.
- [ ] Advisor warnings reviewed by function/policy; performance measured at target scale.
- [ ] All six private launch approvals are current: `backup_restore`, `auth_email`,
      `live_push`, `merchant_order`, `account_erasure`, `legal_operations`.
- [ ] `maintenance_launch_gate()` returns `ready=true` with all required checks true.

## Production configuration

| Setting | Required value / storage |
|---|---|
| Android Firebase | Four `TANI_FIREBASE_*` values in `PUSH_NOTIFICATIONS_SETUP.md` |
| Push sender | `FIREBASE_SERVICE_ACCOUNT` in Supabase Edge Function secrets |
| Signing | Production workflow secrets `TANI_RELEASE_KEYSTORE_B64`, `TANI_RELEASE_STORE_PASSWORD`, `TANI_RELEASE_KEY_ALIAS`, `TANI_RELEASE_KEY_PASSWORD` |
| Current version | Workflow `version_code`; greater than both applications' latest published codes |
| Previous versions | `previous_version_code` and `previous_admin_version_code`, from the publishing account; use `0` only for a genuine first release |
| Domain/recovery | Workflow `app_link_host`, `reset_redirect=https://HOST/auth/reset` |
| Public pages | Environment variables `TANI_TERMS_URL`, `TANI_PRIVACY_URL`, `TANI_ACCOUNT_DELETION_URL` on the verified host |
| Distributed certificate | `TANI_APP_SIGNING_SHA256` if different from the upload key; matching public `assetlinks.json` |
| Server-only gate credential | `TANI_SUPABASE_SERVICE_KEY` in production secrets; never supplied to Android BuildConfig |

The gate compares supplied previous codes; it does not read Google Play automatically.
The publishing owner must verify those values against the actual release history.
Locally the gate uses `TANI_PREVIOUS_VERSION_CODE` and
`TANI_PREVIOUS_ADMIN_VERSION_CODE` environment variables.

## End-to-end evidence

- [ ] Signup, email confirmation, login, recovery, refresh and logout.
- [ ] Merchant application → evidence review → approval → product/image/stock update.
- [ ] Search/store/product/cart and multi-merchant COD checkout.
- [ ] Ambiguous response, process restart and repeated submission produce one order.
- [ ] Merchant transitions → actual delivery → review with no partial ratings.
- [ ] Complaint/support lifecycle and staffed escalation channel.
- [ ] Background push, permission refusal, logout and account-switch isolation.
- [ ] Links open the intended product/store and complete password recovery.
- [ ] Favorites/referrals and paid content labeled «ممول».
- [ ] Backup restored into an isolated project with measured recovery targets.
- [ ] Terms, Privacy, merchant/delivery/retention policies approved and publicly reachable.

## Publication

- [x] Explicit authorization received on 3 October to upload the repair branch and full schema.
- [x] Uploaded the reviewed files and verified exact initial Git tree equality.
- [x] Reviewed passing CI on bb66cad and checked device/XML/archive evidence.
- [ ] Produce and approve a production-signed release after the live gates pass.
- [ ] Separate authorization for Google Play publication when the release is ready.

Details and limitations: [LAUNCH_REPAIR_STATUS_2026-10-03.md](LAUNCH_REPAIR_STATUS_2026-10-03.md).

Machine evidence: [launch-checks-2026-10-03.json](../qa/evidence/launch-checks-2026-10-03.json).
CI: [all eight jobs passed](https://github.com/emee96-ai/Tani/actions/runs/37124506987).
