# Tani Push Notifications — Production Setup

## Current state

The app already has a Supabase-backed in-app notification inbox. This foundation adds a separate, secure device registry for real system push notifications.

Implemented:
- `public.push_devices` with RLS.
- Authenticated users can access only their own registrations.
- `register_my_push_device(...)` safely reassigns a provider token to the currently authenticated account on account/device changes.
- `unregister_my_push_device(...)` removes only the caller's registration.
- Android `PushRegistrationGateway` for provider-token registration.
- No push-provider secret is stored in Android or committed to GitHub.

Not activated yet:
- Firebase Cloud Messaging (FCM) Android SDK/configuration.
- Server-side sender credentials.
- Notification insert -> push delivery trigger/webhook.

## Recommended production provider

For the current Android app (`com.tani.app`), use Firebase Cloud Messaging (FCM). Keep the Firebase server credential/service-account material outside Android and outside this repository.

## Activation sequence

1. Create/configure the Android app in Firebase for package `com.tani.app`.
2. Add the Android-side Firebase configuration through the normal Firebase Android setup and pin dependency versions.
3. Add a `FirebaseMessagingService` that:
   - receives refreshed FCM tokens;
   - calls `PushRegistrationGatewayProvider.gateway.register("fcm", token)` when a Tani session exists;
   - displays incoming notifications using Android notification channels;
   - routes notification data to the appropriate Tani screen.
4. Before logout, unregister the current FCM token while the Supabase user session is still valid, then revoke/clear the auth session.
5. Deploy a server-side sender (recommended: Supabase Edge Function) that reads the target user's active device rows with server-side credentials and sends through FCM HTTP v1.
6. Store FCM sender credentials in Supabase project secrets, never in Android and never in GitHub.
7. Trigger the sender when a row is inserted into `notifications`, or invoke it explicitly from trusted server-side order/status workflows.
8. The sender must honor `notification_preferences` (`push_enabled`, `order_updates`, `promotions`, `messages`) before delivery.
9. On invalid/unregistered provider tokens, mark the matching `push_devices` row inactive so future sends do not repeatedly fail.

## Required verification before enabling for users

- Customer receives order-status push while app is foreground, background, and fully closed.
- Merchant receives new-order push while app is background/closed.
- Logging out stops pushes for the previous account on that device.
- Switching accounts reassigns the provider token to the current account only.
- Disabling `push_enabled` prevents push delivery while preserving the in-app inbox.
- Expired/invalid provider tokens are deactivated.
- Push payload contains no sensitive customer/order data beyond what is necessary for routing; fetch sensitive details after authenticated app open.
