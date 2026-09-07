# Tani Full Product Release Checklist

## Code
- [ ] `qa/run_static_checks.sh` PASS.
- [ ] `./gradlew assembleDebug` or release build PASS in Codespace/CI.
- [ ] Install APK on at least one supported Android device and run smoke tests.

## Supabase
- [ ] All migrations through `final_security_hardening.sql` applied.
- [ ] All public tables have RLS.
- [ ] Storage buckets/policies verified.
- [ ] No Service Role secret in Android/Admin.
- [ ] Security/Performance Advisors reviewed.
- [ ] Enable Auth leaked-password protection if available on the production plan/config.

## End-to-end smoke
- [ ] Signup/login/password recovery.
- [ ] Merchant application → admin review → approved store.
- [ ] Product create/edit/image/stock.
- [ ] Search/store/product/cart.
- [ ] Multi-merchant COD checkout.
- [ ] Merchant state transitions → delivered.
- [ ] Post-delivery review.
- [ ] Complaint/support lifecycle.
- [ ] Favorites/notifications/referrals.
- [ ] Sponsored content labeled «ممول».
- [ ] Account deletion disables public merchant assets.

## Launch operations
- [ ] Real approved merchants/products in Kosti.
- [ ] Delivery settings verified.
- [ ] Support channel staffed.
- [ ] Terms/Privacy/Merchant agreement legally reviewed and published.
- [ ] Backup/recovery configured and tested.
