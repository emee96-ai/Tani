# Tani Verified App Links

Debug password recovery uses `tani://auth/reset`. Release defaults point to
`https://tani.invalid/auth/reset` and are deliberately rejected by the production
gate. A successful candidate build with `ci.example.com` does not configure public links.

## Production setup

1. Set `TANI_APP_LINK_HOST` to the real hostname, without a scheme, path or port.
2. Set `TANI_RESET_REDIRECT=https://HOST/auth/reset` and add this exact URL to the
   Supabase Auth redirect allowlist. Verify the email flow on the installed release.
3. Publish `https://HOST/.well-known/assetlinks.json` with relation
   `delegate_permission/common.handle_all_urls`, namespace `android_app`, package
   `com.tani.app`, and the SHA-256 fingerprint of the certificate signing the
   **distributed app**.
4. When Play App Signing uses a different certificate from the upload keystore,
   provide the distributed certificate as `TANI_APP_SIGNING_SHA256` in production
   environment variables. Otherwise the gate derives it from the configured keystore.
5. Publish public HTTPS pages for Terms, Privacy, account deletion and recovery
   on this hostname. The production gate checks their availability and redirects.

The manifest supports `/auth/reset`, `/product/UUID` and `/store/UUID` on the verified
host. Product/store routing also supports `tani://product/UUID` and `tani://store/UUID`.
Product/store IDs and paths are validated; extra paths, credentials, queries and
fragments are rejected. Password recovery has its separate authenticated callback.

## Device verification before release

- Install the build signed with the certificate actually distributed to users.
- Verify Android's domain association, then open a real product and store link
  with the app closed and with an existing activity open.
- Request an actual password-reset email, open its link and complete recovery.
- Check invalid/foreign-host links do not open an unintended item.

Do not approve `auth_email` or link readiness from a successful HTTP fetch alone.
