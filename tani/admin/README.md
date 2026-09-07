# TANI Admin Foundation

Separate web admin foundation required by the full product specification.

Security model:
- Uses only the Supabase publishable key.
- Admin/support users authenticate normally with Supabase Auth.
- Database RLS is the authority for all reads/writes.
- `support` is read-only in this foundation.
- Merchant approve/reject uses the protected `admin_set_merchant_status` RPC.
- No `service_role` or secret key is stored in these files.
- Session tokens are kept in `sessionStorage`, not committed to source.

Current foundation surfaces:
- Marketplace counts.
- Merchant applications with admin-only approve/reject.
- Recent complaints.
- Recent app errors.
- Audit log.
