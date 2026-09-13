# Tani Admin Android

Separate Android application for Tani administration and support.

- Application ID: `com.tani.admin`
- Installs alongside the customer/merchant app.
- Uses the same Supabase publishable key and authenticated user JWT.
- Only active profiles with role `admin` or `support` can enter.
- No service-role or secret key is embedded in the APK.
- Sensitive session tokens are encrypted with Android Keystore.
- Administrative writes continue to rely on existing RLS and protected RPC functions.

Build: `./gradlew :adminApp:assembleDebug`

Debug APK: `adminApp/build/outputs/apk/debug/adminApp-debug.apk`
