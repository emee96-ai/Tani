# TANI Verified App Link setup

The project builds safely without a production domain by keeping the legacy `tani://auth/reset` recovery path as the default.

For production:
1. Set Gradle property `TANI_APP_LINK_HOST` to the real HTTPS host.
2. Set `TANI_RESET_REDIRECT=https://<host>/auth/reset`.
3. Add the exact HTTPS redirect URL in Supabase Auth redirect URLs.
4. Publish `https://<host>/.well-known/assetlinks.json` with package `com.tani.app` and the production signing SHA-256 fingerprint.
5. Verify the release APK/AAB uses the production values before shipping.

Do not switch the reset redirect to HTTPS until `assetlinks.json` and Supabase redirects are live; otherwise password recovery can be broken.
