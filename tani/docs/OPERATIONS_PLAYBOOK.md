# Tani Operations Playbook

## Daily
1. Check app_errors and critical operational alerts.
2. Check pending merchant applications and identity documents.
3. Check pending orders, cancellations, complaints and support tickets.
4. Check low/out-of-stock merchant alerts.
5. Check failed/rejected order spikes and abnormal cancellation rate.
6. Open System → Health in the admin app. Investigate a missing worker heartbeat,
   failed push delivery or an erasure request older than 24 hours.
7. Keep the named support operator and escalation contact available during trading hours.

## Merchant verification
- Verify phone and identity document ownership.
- If Supabase Auth has already confirmed the same phone, approval uses that actual confirmation.
  Otherwise contact the merchant, then use «توثيق تحقق الهاتف» and record the method, date and outcome.
  Only an active admin can record this evidence. A changed phone requires new verification.
- Validate store name/category/city/delivery settings.
- Approve, request changes or reject through protected admin RPCs only.
- Never edit protected verification fields directly from a public client.

## Maintenance worker
- `maintenance-worker` is protected by a private server key. A cron job checks for work every minute;
  idle runs refresh the heartbeat after approximately 3–4 minutes. A heartbeat older than 5 minutes is unhealthy.
- Account erasure uses Auth and Storage APIs. Do not replace physical file deletion with SQL metadata deletion.
- Push delivery needs `FIREBASE_SERVICE_ACCOUNT` in Edge Function secrets. The app only receives public Android Firebase configuration.
- Retry queues have leases and backoff. Invalid FCM tokens are disabled. Check failed jobs and the worker error code before retrying.
- `maintenance_launch_gate()` must return `ready=true` before a production release. Staff health does not substitute for launch approval.

## Release evidence
Record dated evidence in the private launch approvals only after checking the actual result:
`backup_restore`, `auth_email`, `live_push`, `merchant_order`, `account_erasure`, `legal_operations`.
Evidence expires after 30 days. Never approve a missing test or use a CI-signed artifact as the production build.

## Weekly and monthly
- Weekly: review access, merchant evidence, retries, Auth security settings, dependency advisories and delivery/support KPIs.
- Monthly: restore a backup into an isolated project, measure recovery time and data age, review retention and record evidence.
- On every release: run full-schema SQL and concurrency regressions, units, lint, R8/signature checks,
  16 KB LOAD/RELRO and packaging checks, UI on Android 11 and 16, and real reset/push/order checks.
- FCM registration-token callbacks remain supported in the pinned SDK but are deprecated upstream;
  plan and validate the Firebase Installation ID migration with real delivery evidence before switching.

## Complaint resolution
- Open linked order and status history.
- Confirm customer/merchant identities and timeline.
- Record decision/resolution and reason.
- Escalate unresolved payment/legal/safety issues.

## Incident response
- Critical: auth bypass, data exposure, payment inconsistency, mass checkout failure.
- Stop risky feature via configuration/feature flag where available.
- Preserve logs and audit evidence.
- Restore service using the least destructive action.
- Document root cause and prevention action.

## Expansion gate
Do not activate a new city merely because its row exists. Require validated supply, delivery capability, support capacity and KPI evidence first. Kosti remains the default active city until those gates are met.
