# Tani Push Notifications — Production Setup

## Implemented, awaiting real configuration and delivery verification

The Android app includes pinned Firebase Messaging 25.1.2, manual `FirebaseOptions`,
notification permission/channel handling and a non-exported `TaniMessagingService`.
`PushMessaging` registers only a signed-in account with notification permission,
serializes registration against logout and checks the session generation. Logout
unregisters the current token and deletes it within a bounded timeout; account
changes cancel local notifications. A received message must match the current user.

The database contains the RLS-protected `push_devices` registry, preferences,
notification-to-delivery queue and leased retry/acknowledgment RPCs. The deployed
`maintenance-worker` sends through FCM HTTP v1, respects current ownership and
preferences, and disables unregistered tokens. Its payload contains only `user_id`
and `notification_id`; private details are fetched after authenticated app opening.

These code paths are implemented. Real system push is **not approved for launch**:
the production Firebase configuration and successful delivery evidence are missing.

## Android configuration

Create or verify the Firebase Android application with package `com.tani.app`.
Supply these four public Android configuration values as Gradle properties:

| Property | Firebase value |
|---|---|
| `TANI_FIREBASE_API_KEY` | Android API key |
| `TANI_FIREBASE_APPLICATION_ID` | Firebase app ID, `1:SENDER_ID:android:...` |
| `TANI_FIREBASE_PROJECT_ID` | Project ID |
| `TANI_FIREBASE_SENDER_ID` | Numeric project number / sender ID |

The project initializes Firebase manually; it does not require the Google Services
Gradle plugin or commit a `google-services.json` file. Missing configuration leaves
system push inactive. Supply all four values to the production workflow's named
secrets; they are passed to Android through `-P` properties.

## Server configuration

1. Enable FCM HTTP v1 for the same Firebase project and grant the sender only the
   permissions needed for messaging.
2. Store the service account JSON, including `project_id`, `client_email` and
   `private_key`, in the Supabase Edge Function secret `FIREBASE_SERVICE_ACCOUNT`.
   Keep this credential on the server; Android receives only the four values above.
3. The existing minute-by-minute cron calls `maintenance-worker` with the private
   worker key. Its custom authorization is required even though gateway JWT
   verification is disabled for this worker. Do not expose or replace that key.
4. Check System → Health in the admin app: heartbeat must be recent and push
   configuration must be healthy. Missing Firebase configuration does not consume
   delivery retry attempts; account cleanup remains operational.

## Required verification

- A customer receives a real order-status message in foreground, background and
  after ordinary app closing. Separately test Android's explicit force-stop state;
  do not assume it behaves like ordinary closing.
- A merchant receives a new-order message in background and after ordinary closing.
- Permission refusal prevents display and device registration without breaking inbox access.
- Logout and switching accounts never display the previous account's message.
- Disabling push preferences prevents delivery while preserving the in-app inbox.
- Invalid tokens are deactivated, retries are bounded, and failed jobs are visible.
- Tapping a message opens the authenticated account's notification screen.

Record dated `live_push` evidence only after testing the real sender and device.
Unit tests and a healthy configuration flag do not prove delivery.

## Maintenance

The pinned SDK's registration-token callbacks remain supported but are deprecated
in current upstream documentation. Review the Firebase Installation ID migration
weekly and test actual delivery before changing the client/server protocol.

See [Firebase Android setup](https://firebase.google.com/docs/cloud-messaging/android/get-started)
and [multiple-project configuration](https://firebase.google.com/docs/projects/multiprojects).
