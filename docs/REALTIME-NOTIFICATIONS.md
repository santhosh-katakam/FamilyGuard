# Native parent alerts using the existing stack

FamilyGuard uses Java Spring Boot, PostgreSQL, authenticated WebSockets, native Java Android and Android notification channels. No Firebase, Google Messaging SDK, external push provider, MongoDB or Supabase is used.

## What was reused and added

The existing child app already collected opt-in notification event counts, supported call states, visible app inventory changes, and encrypted Room events. Its WorkManager uploader, one-child pairing enforcement, server payload allowlists, PostgreSQL event storage, and React activity views remain in use.

Added: native Parent Mode registration and notification controls; an authenticated WebSocket foreground receiver; encrypted Room notification receipts; a durable PostgreSQL delivery queue; incoming-call alerts; parent receiver revocation in web Settings; and separate parent/child APK variants. The original two-mode app is retained as the universal variant.

App installation/removal detection now also uses a process-lifetime runtime receiver. The manifest receiver uses the supported package-fully-removed exception. Inventory reconciliation remains the fallback when Android suppresses broadcasts or kills the process. Only launcher-visible packages and explicitly visible supported social apps can be observed; this is not an unrestricted app inventory. OS/package visibility changes can affect inventory comparisons.

## Delivery and security

1. The existing Parent Admin signs into the Parent APK to register exactly one receiver. The endpoint verifies the existing BCrypt password; it never creates another account. Explicit replacement revokes the old phone and starts delivery from the replacement time. Web dashboard sessions remain independent.
2. The backend stores only the hash of a random 256-bit receiver credential, valid for 30 days. The phone stores it encrypted with its Android Keystore key. Reauthentication is required after expiration. Native receiver credentials can read/acknowledge the notification feed and request WebSocket tickets; they cannot upload child events or access parent administration endpoints.
3. The child uploads permitted metadata to the existing `/api/device/sync` endpoint. UUID receipts deduplicate uploads. Sanitized, privacy-approved installation/removal, notification and incoming-RINGING events enter `parent_delivery_events` in the same database transaction. Each event remains distinct even when generic dashboard alerts are rate-limited.
4. After transaction commit, the existing WebSocket sends a refresh signal. The parent connects using a one-use, 30-second ticket and the configured allowed web Origin. WSS uses normal system TLS trust; no certificate bypass or cleartext configuration is added. Revocation/replacement closes existing receiver sockets, and each API call revalidates the credential and administrator.
5. The parent fetches up to 100 ordered events per page, writes only allowlisted metadata to an encrypted Room inbox, posts Android notifications, and acknowledges the server cursor. The server rejects acknowledgment of a cursor that was never offered. Reconnection fetches retained events, rather than assuming WebSocket signals were durable.
6. Event UUIDs are unique on the server and in Room. Stable Android notification tags and `onlyAlertOnce` make replay updates idempotent while the notification exists. A process crash exactly between Android display and the Room notified marker can redisplay a previously dismissed notification; delivery is not a transactional exactly-once guarantee across Android and PostgreSQL.

The feed contains event ID, ordering sequence, category, event type, source package, event timestamp and receipt timestamp. It contains no notification text, sender, phone number, location, SMS body or conversation history. The Android display reconstructs an allowlisted metadata object and supplies a generic public lock-screen notification. Event payloads and Android credentials remain encrypted as documented in [SECURITY.md](SECURITY.md); PostgreSQL feed/query metadata is not field-encrypted.

Pending delivery is capped at 20,000 records and seven days, further reduced by the current general retention preference. An acknowledged page is removed. Privacy settings are rechecked before delivery. Unpairing erases the pending child feed; account deletion also erases the parent receiver credential. Registering a replacement intentionally skips earlier pending alerts; retained dashboard history remains available through normal parent authentication.

## Parent phone controls

Allow Android notification permission and the **Live alert connection** channel, then tap **Start live alerts** from the visible Parent Activity. A persistent notification displays connection/retry status and provides **Stop live alerts**. It is not started from boot, does not restart after force-stop, and does not request battery-optimization exemptions or hidden privileges.

The service declares `specialUse` on Android 14+, its foreground-service permission, and an explicit user-started family-safety receiver subtype. It starts from the visible activity and stops when global notifications/its connection channel are disabled. This is a declared use case, not a claim of Play approval; store distribution requires review of the declared foreground-service purpose. Shared services removed from another flavor cause lint false positives, so only the service-type check is narrowly suppressed on those shared methods. Built role manifests were separately checked and an instrumented manifest test was added.

Choose installation/removal, selected notification activity and incoming call categories on the parent phone. Notification activity alerts default off on the parent; child collection categories also remain opt-in. Notification channels can be disabled individually in Android Settings. Quiet hours use the parent phone's current time zone, include the start and exclude the end, and support midnight crossover. Equal times disable the quiet window. Quiet/disabled phone-alert categories are acknowledged without an OS alert; approved activity remains in the normal dashboard.

The child retains its visible supervision notice, ongoing monitoring notification, local consent switches, notification-app selection, pause/queue-clear controls and unpairing. Unsupported permissions are not granted by parent settings. Event storage continues offline only for permitted categories. Significant activity schedules a coalesced follow-up sync so activity arriving during a running worker is not stranded by WorkManager's KEEP policy. WorkManager still controls actual execution timing.

## Supported calls and restricted features

Incoming alerts are based on Android's supported `RINGING` phone-state broadcast with explicit `READ_PHONE_STATE` consent. `OFFHOOK` and `IDLE` may be recorded as generic states but do not reliably identify outgoing calls, call completion, duration, caller identity or call totals. OEM/background restrictions can suppress states. No call log, outgoing-call interception, SMS permissions, SMS database access, microphone, call recording or VoIP interception is implemented. Notification access never reconstructs chat history and can miss hidden/grouped/redacted notifications.

## Limits without a push provider

- A parent OS notification can be displayed while the React dashboard is closed **while the native receiver remains running and has network access**.
- An app-owned socket cannot reliably wake a force-stopped, rebooted, killed or Dozing app. Android battery saver/OEM restrictions can suspend network delivery even with a visible service. Users must reopen FamilyGuard and restart the receiver after it is stopped.
- If either phone or the backend is offline, immediate remote delivery is unavailable. The child retries permitted uploads and the parent catches up its retained feed after reconnecting. Old event timestamps stay visible; a backlog is not represented as live.
- Free Render services can sleep, cold-start, disconnect sockets or hit resource limits. Multiple API instances require shared ticket storage/fanout and distributed rate limiting; the current deployment is a single API instance.
- Manifest package-added/replaced broadcasts are restricted on modern Android. Runtime observation depends on process lifetime; inventory polling is delayed and can miss transient changes between polls. Immediate observation of every install/uninstall is not guaranteed.
- Notification access cannot guarantee every incoming notification, and Android notification display limits may suppress/rate-limit a large backlog.

These limitations are intentional constraints of the requested stack. No third-party push dependency or unsupported wakeup workaround is substituted.

Android references: [foreground-service types and special use](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use), [background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [implicit broadcast exceptions](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions), [Doze and networking limits](https://developer.android.com/training/monitoring-device-state/doze-standby).

## Build and deployment

Deploy the updated Spring Boot backend before using the new native registration endpoints. Flyway applies V2 to the existing PostgreSQL database without replacing accounts or keys. Keep the existing secrets, HTTPS API/web origins, exact CORS origin and single Render API instance. No new environment secrets, provider SDK, cloud database, notification service or paid hosting plan is needed. Deploy the React update to expose receiver status/revocation in Settings.

From `android-app/`, with Java 21 and SDK 36 configured:

```powershell
.\gradlew.bat assembleParentDebug assembleChildDebug
.\gradlew.bat testParentDebugUnitTest testChildDebugUnitTest
.\gradlew.bat lintParentDebug lintChildDebug
.\gradlew.bat assembleParentDebugAndroidTest assembleChildDebugAndroidTest
```

Outputs:

```text
C:\Users\DELL\Downloads\parental\familyguard\android-app\app\build\outputs\apk\parent\debug\app-parent-debug.apk
C:\Users\DELL\Downloads\parental\familyguard\android-app\app\build\outputs\apk\child\debug\app-child-debug.apk
```

Application IDs: `com.familyguard.android.parent` and `com.familyguard.android.child`. The parent manifest removes child monitoring permissions/components. The child manifest removes the parent receiver and its special-use permission. Both use the same existing native Java source architecture.

For the original two-mode identity, use `gradlew.bat assembleUniversalDebug`, output `app/build/outputs/apk/universal/debug/app-universal-debug.apk`, application ID `com.familyguard.android`. Existing universal installations can upgrade with this identity/key. Moving an already paired phone to the distinct child application ID requires explicitly revoking the old pairing and pairing the new app; Android credentials do not transfer between application IDs.

Release commands: `gradlew.bat assembleParentRelease assembleChildRelease`, using the existing ignored keystore.properties configuration. Without a release keystore these are unsigned release artifacts; development APKs use the Android debug certificate.

## Verification status

Backend: 14 tests passed, including a real embedded-server WebSocket handshake, one-use ticket rejection, commit-triggered notification signal, web-login independence and immediate receiver revocation. Native unit tests passed; role APK builds and instrumented-test compilation passed. React tests passed. V2 migrated successfully on the local real PostgreSQL instance. These are software checks, not evidence of phone notification delivery.

At the last ADB inspection no physical phones were attached. The user intends to connect both phones; physical delivery, OS permission/background behavior and offline reconnection are **pending**. Use [PHYSICAL-DEVICE-CHECKLIST.md](PHYSICAL-DEVICE-CHECKLIST.md). Do not claim verified real-time phone alerts before both devices have been exercised.
