# Android Studio, devices, and APKs

Open **familyguard/android-app** as a project in Android Studio, not frontend/. Install Android SDK Platform 36, Build Tools 35.0.0 and Platform Tools through SDK Manager. Set the Gradle JDK to Android Studio's compatible bundled JDK (17 or 21), then sync. The checked-in wrapper uses Gradle 8.13 and Android Gradle Plugin 8.13.0; native source uses Java 17 syntax. The backend separately uses Java 21.

Copy local.properties.example to local.properties (preserve Android Studio's sdk.dir) and replace BOTH API and web URLs with your actual Render HTTPS URLs. Do not add /api to the API base. No real production URL exists until you deploy. The defaults intentionally contain REPLACE and are unusable for pairing. WebView enables JavaScript and DOM storage for React, blocks untrusted top-level navigation and mixed content, and exposes no JavaScript bridge.

The manifest requests internet/network, optional Usage Access, optional call-state, foreground location, and Android notification permission. There is no microphone, camera, contacts, SMS, READ_CALL_LOG, QUERY_ALL_PACKAGES, accessibility service, device-owner provisioning, or private-message extraction permission. NotificationListenerService is bound only by Android using BIND_NOTIFICATION_LISTENER_SERVICE; enable it through the visible child setup button. Package queries cover launcher applications and four named social apps, so the app inventory is intentionally incomplete. No background-location permission is requested: the user starts a location-type foreground service while the Activity is visible, which may continue in the background. It is not restarted from boot.

## First real-device run

1. Deploy backend, PostgreSQL and React; verify health and sign-in in a browser.
2. Connect a real Android 8+ phone, enable Developer Options / USB debugging, select it and Run app. Android 13–16 devices are particularly important for permission tests. Keep the application icon visible.
3. On the parent's Android phone, install the same APK and choose Parent Admin Mode. Alternatively the one parent can use the React site on a computer. Sign in using the configured admin account.
4. On Device Management generate a 12-character code, valid for five minutes. On the supervised phone choose Child Mode, review and accept the notice, enter the code, then pair. An internet connection is required on both sides; distance is irrelevant.
5. Enable only agreed local categories. Open Usage Access settings and Notification Access settings if wanted. Grant call-state permission separately. Choose notification applications. Tap Enable monitoring and allow the visible supervision notification. All local category switches default OFF. Disabling notification visibility pauses activity collection; the child app continues reporting minimum connection/permission status while paired.
6. For location, tap Start location sharing, grant location (approximate is supported), grant the visible notification, then tap Start again. An ongoing notification provides Stop sharing. Review location timestamp and accuracy on the parent's map. Location older than two minutes is marked outdated.
7. Check Privacy & Permissions and last synchronization on the website. A successful sync indicates recent connectivity, not a continuously reachable remote phone.

## Debug APK

From android-app in PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Output: app/build/outputs/apk/debug/app-debug.apk. Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, or copy it to each phone and permit installation from that trusted source. Debug signing is for development. Do not distribute a build pointing at someone else's backend.

## Signed release APK

Use Android Studio: Build → Generate Signed App Bundle or APK → APK → Create new keystore (or select your existing private key) → release. Keep the keystore and passwords outside the repository, back them up securely, and retain the same key for upgrades. The project excludes key files. Choose signature schemes supported by your target phones.

For CLI release signing, copy keystore.properties.example to ignored keystore.properties and configure your private keystore path and credentials locally. Then:

```powershell
.\gradlew.bat testDebugUnitTest assembleRelease
```

Without keystore.properties Gradle produces an **unsigned** release artifact. It is not an installable signed release. With signing configured, verify using SDK `apksigner verify --verbose app/build/outputs/apk/release/app-release.apk`. Never commit secrets or use a generated development signing key as a claimed production identity. Release signing and installation require your keystore and physical devices; they cannot be certified by source compilation.

## Required hardware acceptance tests

- Fresh install: no collection before pairing, consent and category enablement. Deny every permission individually and verify useful status, no crash and no data from the denied category.
- Pair invalid, expired and reused code; try a second device simultaneously; one must be rejected. Revoke on parent; old token fails and child clears its encrypted queue at next connection.
- Grant Usage Access; use two applications; compare Android's reported usage with server snapshots. Android's daily aggregate buckets are approximate and may not equal Digital Wellbeing; multi-window time can overlap. FamilyGuard reports foreground-app usage, not exact screen-on duration.
- Install/remove/update a launcher app both online and offline. Confirm only visible apps appear. WorkManager/inventory polling may detect a change later than the broadcast. Transient install/remove entirely between polls may be missed if Android suppresses broadcasts.
- Send selected-app notifications; check one metadata record per post identity. Send a private message and verify server payload has only count, package and timestamp. Hidden/grouped/redacted/sensitive notifications can be absent. Updated notifications may produce an event; counts are not message counts.
- Exercise an incoming cellular call with READ_PHONE_STATE granted/denied. Check RINGING/OFFHOOK/IDLE where delivered; these are state transitions, not a reliable count of completed calls. VoIP is excluded. OEM background restrictions can suppress events.
- Start GPS sharing while visible; move outside/inside a configured zone, verify accuracy, timestamp, stale labeling and deduplicated transition alerts. Stop through notification and through Child Mode. Deny location while sharing; verify service stops. Test providers disabled and approximate-only permission.
- Airplane mode: observe encrypted Room rows accumulating (limit 20,000 / seven days); restore internet and verify acknowledged uploads, retry and no duplicate rows. Inspect Room to ensure payload is ciphertext. Test 429, timeout, server cold start, server restart after commit-before-ack, expired local queue, pause and clear.
- Test Doze, battery saver, reboot, force stop, app update and manufacturer restrictions. Android controls scheduling: WorkManager minimum periodic interval is 15 minutes and not exact. No automatic restart of foreground location. No claims of remote live monitoring while disconnected.
- Confirm notification access and Usage Access revocations are reflected on next collector run or app resume. Parent privacy preferences apply at the server immediately and reach the child at next sync.
- Test WebView sign-in/refresh/logout on each supported OS/WebView version. Some browser privacy policies block cross-site refresh cookies; use frontend/api custom domains under the same registrable domain when needed.
