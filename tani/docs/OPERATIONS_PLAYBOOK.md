# Tani Operations Playbook

## Daily
1. Check app_errors and critical operational alerts.
2. Check pending merchant applications and identity documents.
3. Check pending orders, cancellations, complaints and support tickets.
4. Check low/out-of-stock merchant alerts.
5. Check failed/rejected order spikes and abnormal cancellation rate.

## Merchant verification
- Verify phone and identity document ownership.
- Validate store name/category/city/delivery settings.
- Approve, request changes or reject through protected admin RPCs only.
- Never edit protected verification fields directly from a public client.

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
