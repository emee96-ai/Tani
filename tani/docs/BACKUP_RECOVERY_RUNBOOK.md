# Tani Backup & Recovery Runbook

## Database
- The project is on the free Supabase plan, which has no managed backups. The `Tani Database Backup` workflow takes a weekly encrypted dump (Fridays 02:30 UTC) and keeps it as a workflow artifact for 90 days. It needs two repository secrets: `TANI_BACKUP_DB_URL` (the session-pooler connection string) and `TANI_BACKUP_PASSPHRASE` (24+ characters, stored outside GitHub as well — without it the dumps cannot be opened).
- Restore a dump: `gpg --decrypt tani-YYYYMMDD.sql.gz.gpg | gunzip | psql "$DATABASE_URL"`, into an empty project first, never over the live one.
- On a paid plan, use Supabase managed backups/PITR instead.
- Before high-risk schema changes, create/confirm a current backup and record migration version.
- Keep all schema changes reproducible in `supabase/*.sql`; never rely on dashboard-only DDL.

## Storage
- Product/store images are reconstructable from Storage + DB metadata; merchant identity documents are private and require stricter recovery/access controls.
- Do not make `merchant-private` public during recovery.

## Recovery order
1. Restore database to the selected recovery point.
2. Validate Auth/profile links and RLS.
3. Validate Storage buckets and policies.
4. Run smoke queries for catalog, checkout, orders, merchant operations, support and admin.
5. Verify order/payment status consistency before reopening writes.

## Recovery checks
- No public table without RLS.
- No service-role key in client artifacts.
- Order totals/stock consistent.
- Merchant-private bucket remains private.
- Latest migration list matches release record.
