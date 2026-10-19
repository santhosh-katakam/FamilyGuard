# Two-phone acceptance checklist

Status: **not executed**. ADB has reported no attached devices during implementation. Successful builds, native unit tests, instrumented-test compilation and backend WebSocket tests do not verify physical notification delivery. Record actual results here only after running them.

Use two Android phones (API 26+), with at least one Android 13+ and preferably Android 14–16 device for notification/foreground restrictions. Keep automatic date/time enabled. Record model, Android version, WebView version, network type and results. No test requires covert operation, SMS permission, microphone access or bypassing Android permissions.

## Install and run instrumented checks

Run from `android-app/`. Replace serial placeholders using `adb devices -l`.

```powershell
adb -s PARENT_SERIAL install -r app/build/outputs/apk/parent/debug/app-parent-debug.apk
adb -s CHILD_SERIAL install -r app/build/outputs/apk/child/debug/app-child-debug.apk
adb -s PARENT_SERIAL install -r app/build/outputs/apk/androidTest/parent/debug/app-parent-debug-androidTest.apk
adb -s CHILD_SERIAL install -r app/build/outputs/apk/androidTest/child/debug/app-child-debug-androidTest.apk
adb -s PARENT_SERIAL shell am instrument -w com.familyguard.android.parent.test/androidx.test.runner.AndroidJUnitRunner
adb -s CHILD_SERIAL shell am instrument -w com.familyguard.android.child.test/androidx.test.runner.AndroidJUnitRunner
```

The instrumented tests use in-memory Room databases to check deduplication, notified-state persistence, retention, Keystore ciphertext, metadata allowlists and role service/permission declarations. Record test counts and failures. Permission prompts and real delivery are separate checks below.

## Connect the real environment

- [ ] Updated backend is deployed; `/actuator/health` returns `UP`, V2 migration is applied, React login works, and both APKs contain the intended matching HTTPS origins. Reuse existing keys; do not regenerate encryption keys.
- [ ] Open the Parent APK and sign in with the existing Parent Admin account. Register the phone; the backend should report one receiver. Web Settings shows its name/expiration without exposing its credential.
- [ ] Deny Android notification permission: no hidden receiver should start. Grant permission through the visible UI and tap Start live alerts; a visible persistent connection notification and Stop action appear.
- [ ] Open Device Management on the parent web dashboard and create a code. On the Child APK read the notice, acknowledge supervision, pair once, and verify a second child cannot pair. Invalid/expired/reused codes fail.
- [ ] Turn on only agreed child categories and Android permissions. The supervision notification and permission status stay visible. Notification access is granted in Android Settings by the phone user, not through ADB permission bypasses.

## Event delivery

- [ ] Install a harmless launcher test app on the child. With monitoring/process/network available, verify source package, INSTALLED type and original timestamp in PostgreSQL/dashboard and a standard notification on the parent. Record detection/upload/delivery timestamps and any delay.
- [ ] Uninstall that test app only. Verify REMOVED event and parent notification. Update an application and confirm an update does not create a false uninstall/install pair. Repeat with child process absent to record polling delay/visibility limits.
- [ ] Select WhatsApp/Instagram/Snapchat/Telegram or another visible supported app on the child and opt into its notification counts. Enable that parent notification category. Trigger an authorized test notification manually and verify package/type/time only on parent/API. Confirm no message body, sender, phone number or conversation history is stored or displayed.
- [ ] Deselect the source app and revoke notification access separately; no subsequent events from a denied category should be collected. Hidden/redacted/grouped notifications may be absent; record actual behavior without claiming complete message coverage.
- [ ] With explicit phone-state permission, receive a test cellular call. Verify the RINGING-based incoming event and timestamp. Deny the permission and repeat: no call collection. OFFHOOK/IDLE do not establish outgoing direction or a completed-call total. SMS and VoIP interception remain unsupported.
- [ ] Close the dashboard while keeping the receiver running: a new event must produce an Android system notification. Verify channel controls, generic lock-screen public text and event timestamp. Inspect the ongoing connection notification to confirm visible status.
- [ ] Test parent category switches and quiet hours, including a midnight-crossing window: suppressed OS alerts remain in approved dashboard history and do not flood the phone after quiet hours end.

## Disconnections and replay

- [ ] Disable internet on the child, trigger permitted activity, and confirm its encrypted local queue/offline status. The parent must not report remote live delivery. Restore connectivity; verify uploads and notifications arrive with the original timestamps and no duplicate stored UUIDs.
- [ ] Disable parent internet while child events synchronize. Restore it and verify authenticated socket reconnect, durable feed catch-up, cursor acknowledgment and stable notification tags.
- [ ] Interrupt the network after server commit but before child upload acknowledgment; replay should acknowledge the same event without another delivery row. Interrupt parent acknowledgment and repeat; Room should prevent ordinary duplicate display.
- [ ] Restart the API during an active socket. New tickets/reconnect must work; persisted pending feed records survive. Record Render cold-start delay and service sleep behavior.
- [ ] Parent logout/login in the web dashboard must not revoke the registered native receiver. Revoking the parent phone in web Settings or replacing its registration must close the old receiver and make its token unusable. Explicit parent replacement starts from the replacement time.
- [ ] Pause child monitoring, revoke category permission, and unpair. No newly denied activity should be collected; local pending events should be cleared as disclosed. Server unpairing erases the child delivery queue and rejects the old child token.

## Android restrictions

- [ ] Swipe away the dashboard, lock the parent phone, enable battery saver, allow idle/Doze and test an OEM-restricted background state. Record whether delivery is delayed. A foreground socket is not a wakeup guarantee.
- [ ] Stop the parent using its notification action: the service/connection ends and remains visibly stopped. Disable global notifications/connection channel while running: the service stops.
- [ ] Force-stop and reboot each phone: document Android's behavior. Parent live receiver is not boot-started and requires manual Start after being stopped. No hidden restart, battery-optimization exemption or permission bypass should occur.
- [ ] Confirm the Parent APK requests no child location/usage/phone-state permissions, and the Child APK registers no parent receiver. Neither APK requests SMS, microphone, call-log or QUERY_ALL_PACKAGES permission.

Acceptance requires recording actual received notifications and privacy checks on both real phones. Until then, physical behavior remains unverified even when every automated check passes.
