# TANI Auth setup

Authentication implemented in the Android app:
- Email/password login.
- Account creation with name + phone stored in `raw_user_meta_data`.
- `public.handle_new_user()` creates the matching `profiles` row automatically.
- Email password recovery.
- Password reset through the Android deep link `tani://auth/reset`.

## Required Supabase Dashboard setting

In **Authentication → URL Configuration → Redirect URLs**, add:

`tani://auth/reset`

Without this redirect URL, Supabase may refuse to return the recovery link to the Android app.

## Email confirmation

The implementation supports both Supabase configurations:
- If Confirm Email is disabled, signup returns a session and opens the app.
- If Confirm Email is enabled, the user is told to confirm email and then log in.

## Phone verification

The full product specification places phone verification inside Merchant Onboarding. It is intentionally not treated as completed by this customer-auth implementation; it should be implemented with the merchant verification flow.

## Session persistence and refresh

The Android client now stores both the Supabase `access_token` and `refresh_token`.
Before authenticated REST requests it checks the JWT `exp` claim and refreshes the
session shortly before expiration. If a request still returns HTTP 401, it refreshes
once and retries the request. When Supabase rotates the refresh token, the new token
replaces the stored value.

Password-recovery deep links must include both `access_token` and `refresh_token`.
The configured redirect remains:

`tani://auth/reset`
